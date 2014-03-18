package com.protolabs;

import java.util.List;
import java.net.URL;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.atlassian.stash.content.Path;
import com.atlassian.stash.content.Change;
import com.atlassian.stash.content.Changeset;
import com.atlassian.stash.content.ChangesetsBetweenRequest;
import com.atlassian.stash.content.DetailedChangeset;
import com.atlassian.stash.content.DetailedChangesetsRequest;
import com.atlassian.stash.hook.repository.*;
import com.atlassian.stash.repository.*;
import com.atlassian.stash.setting.*;
import com.atlassian.stash.commit.CommitService;
import com.atlassian.stash.util.Page;
import com.atlassian.stash.util.PageRequest;
import com.atlassian.stash.util.PageUtils;
import com.atlassian.stash.content.MinimalChangeset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

public class FogbugzHook implements AsyncPostReceiveRepositoryHook, RepositorySettingsValidator {

    private final CommitService commitService;

    public FogbugzHook(CommitService commitService)
    {
        this.commitService = commitService;
    }

    // More info on Stash logging:
    // https://confluence.atlassian.com/display/STASH/Stash+debug+logging
    private static final Logger log = LoggerFactory.getLogger(FogbugzHook.class);

    // Proof of concept needs refactoring and unit tests
    @Override
    public void postReceive(RepositoryHookContext context, Collection<RefChange> refChanges) {
        String url = context.getSettings().getString("url");
        String regex = context.getSettings().getString("bugzidregex");
        Repository repository = context.getRepository();
        String repoName = repository.getSlug();

        log.info("Starting postReceive FogbugzHook hook for repo {}", repoName);
        log.info("Configured URL {}", url);
        log.info("Configured Regex {}", regex);

        Pattern bugzIDPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);

        try {
                for (RefChange refChange : refChanges) {
                    log.info("handling refchange type {}", refChange.getType());

                    String fromHash = refChange.getFromHash();
                    log.info("RefChange fromHash - {}", fromHash);

                    String toHash = refChange.getToHash();
                    log.info("RefChange toHash - {}", toHash);

                    Collection<Changeset> changesetsForRefChange = extractChangesets(fromHash, toHash, repository);

                    for (Changeset changeset : changesetsForRefChange) {
                        String commitMessage = changeset.getMessage();

                        log.info("CommitMessage - {}", commitMessage);

                        Matcher bugzIDMatcher = bugzIDPattern.matcher(commitMessage);
                        
                        // For each of these changesets, send Fogbugz request as needed.
                        while (bugzIDMatcher.find()){
                            String bugzID = bugzIDMatcher.group(1);
                            log.info("BugzID Found {}", bugzID);

                            String parentID = "";

                            // Parse out the old/previous ID
                            if (!changeset.getParents().isEmpty()) {
                                MinimalChangeset parent = changeset.getParents().iterator().next();
                                parentID = parent.getDisplayId();
                            }

                            log.info("ParentID {}", parentID);

                            // Parse out the new/current file SHA1
                            String currentID = changeset.getDisplayId();

                            log.info("CurrentID {}", currentID);

                            String changesetId = changeset.getId();
                            log.info("ChangesetID {}", changesetId);

                            Collection<String> fileNames = getFileNames(changesetId, repository);

                            for (String fileName : fileNames) {
                                log.info("FileName {}", fileName);

                                notifyFogbugz(parentID, currentID, bugzID, fileName, repository, url);
                                log.info("Fogbugz Notified {}", url);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                log.info("ERROR {}", e.getMessage());
        }
    }

    @Override
    public void validate(Settings settings, SettingsValidationErrors errors, Repository repository) {
        if (settings.getString("url", "").isEmpty()) {
            errors.addFieldError("url", "Url field is blank, please supply one");
        }
         if (settings.getString("bugzidregex", "").isEmpty()) {
            errors.addFieldError("bugzidregex", "Regex field is blank, please supply one");
        }
    }

    // Notify Fogbugz URL
    private void notifyFogbugz(String parentID, String currentID, String bugzID, String fileName, Repository repository, String url){
        try{
                 //# Build the FogBugz URI
                String r1 = "hp=" + parentID + ";hpb=" + parentID;
                String r2 = "h=" + currentID + ";hb="+ parentID;
                url = url + "/cvsSubmit.asp?ixBug=" + bugzID + "&sFile=" + fileName + "&sPrev="+ r1 +"&sNew=" + r2 + "&sRepo=" + repository;
                new URL(url).openConnection().getInputStream().close();
                log.info("Fogbugz Notified {}", url);
            }
            catch(Exception e){
                e.printStackTrace();
                log.info("ERROR {}", e.getMessage());
            }
    }

    // Create detailed request to get filenames
    private Collection<String> getFileNames(String changesetId, Repository repository){
        DetailedChangesetsRequest detailedRequest = new DetailedChangesetsRequest.Builder(repository)
        .changesetId(changesetId)
        .ignoreMissing(false)
        .maxChangesPerCommit(9999)
        .build();

        log.info("DetailedRequest finished building");

        Page<DetailedChangeset> detailPage = null;

        // Setup paging for request even though we only want 1
        PageRequest pageRequestDetail = PageUtils.newRequest(0, 25);

        detailPage = commitService.getDetailedChangesets(detailedRequest, pageRequestDetail);

        log.info("getDetailedChangesets finished");

        Collection<String> fileNames = Lists.newArrayList();

        // Getting the change and finding the path
        for (DetailedChangeset detailedChangeset : detailPage.getValues()){
            log.info("found detailedChangeset");
            for(Change change : detailedChangeset.getChanges().getValues()){
                log.info("found change");

                if(!change.getPath().toString().isEmpty())
                {
                    fileNames.add(change.getPath().toString());
                }
            }
        }

        return fileNames;
    }

    // For each RefChange, use the fromHash and toHash to extract the individual changesets: 
    // https://developer.atlassian.com/static/javadoc/stash/latest/api/reference/com/atlassian/stash/repository/RefChange.html
    private Collection<Changeset> extractChangesets(String fromHash, String toHash, Repository repository) {
        ChangesetsBetweenRequest request = new ChangesetsBetweenRequest.Builder(repository)
        .exclude(fromHash)
        .include(toHash)
        .build();

        // Setup paging for request
        PageRequest page_request = PageUtils.newRequest(0, 25);

        Collection<Changeset> changesetsForRefChange = Lists.newArrayList();
        Page<Changeset> page = null;

        // page through change sets and add to list
        while (page == null || !page.getIsLastPage()) {

            // Using CommitService.getChangesetsBetween get a list of changesets from RefChange.getFromHash and RefChange.getToHash
            page = commitService.getChangesetsBetween(request, page_request);

            for (Changeset changeset : page.getValues()){
                changesetsForRefChange.add(changeset);
            }

            page_request = page.getNextPageRequest();
        }

        return changesetsForRefChange;
    }
}