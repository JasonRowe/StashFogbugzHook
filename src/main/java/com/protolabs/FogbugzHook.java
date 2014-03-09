package com.protolabs;

import java.util.List;
import java.net.URL;
import java.util.Collection;

import com.atlassian.stash.content.Changeset;
import com.atlassian.stash.content.ChangesetsBetweenRequest;
import com.atlassian.stash.hook.repository.*;
import com.atlassian.stash.repository.*;
import com.atlassian.stash.setting.*;
import com.atlassian.stash.commit.CommitService;
import com.atlassian.stash.util.Page;
import com.atlassian.stash.util.PageRequest;
import com.atlassian.stash.util.PageUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

public class FogbugzHook implements AsyncPostReceiveRepositoryHook, RepositorySettingsValidator {

    private final CommitService commitService;

    public FogbugzHook(CommitService commitService)
    {
        this.commitService = commitService;
    }

    //https://confluence.atlassian.com/display/STASH/Stash+debug+logging
    private static final Logger log = LoggerFactory.getLogger(FogbugzHook.class);
    /**
     * TODO - Loop through refChanges find last commit
     * TODO - Determine if FogbugzID is in commit message
     * TODO - If so, pull out info (file name, Repo location, etc) and send to Fogbugz endpoint 
     */
    @Override
    public void postReceive(RepositoryHookContext context, Collection<RefChange> refChanges) {
        String url = context.getSettings().getString("url");
        Repository repository = context.getRepository();
        String repoName = repository.getSlug();

        log.info("Starting postReceive FogbugzHook hook for repo {}", repoName);

        if (url != null) {
            try {
                    for (RefChange refChange : refChanges) {

                        log.info("handling refchange type {}", refChange.getType());

                        String fromHash = refChange.getFromHash();
                        log.info("RefChange fromHash - {}", fromHash);

                        String toHash = refChange.getToHash();
                        log.info("RefChange toHash - {}", toHash);

                        //For each RefChange, use the fromHash and toHash to extract the individual changesets: https://developer.atlassian.com/static/javadoc/stash/latest/api/reference/com/atlassian/stash/repository/RefChange.html

                        ChangesetsBetweenRequest request = new ChangesetsBetweenRequest.Builder(repository)
                        .exclude(fromHash)
                        .include(toHash)
                        .build();

                        // TODO put page size on const
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

                        // For each of these changesets, send Fogbugz request as needed.
                        for (Changeset changeset : changesetsForRefChange) {
                             String commitMessage = changeset.getMessage();
                             log.info("CommitMessage - {}", commitMessage);
                        }

                    }
                    new URL(url).openConnection().getInputStream().close();
                } catch (Exception e) {
                    e.printStackTrace();
            }
        }
    }

    @Override
    public void validate(Settings settings, SettingsValidationErrors errors, Repository repository) {
        if (settings.getString("url", "").isEmpty()) {
            errors.addFieldError("url", "Url field is blank, please supply one");
        }
    }
}