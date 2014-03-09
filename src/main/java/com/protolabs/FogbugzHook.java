package com.protolabs;

import com.atlassian.stash.hook.repository.*;
import com.atlassian.stash.repository.*;
import com.atlassian.stash.setting.*;
import java.net.URL;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FogbugzHook implements AsyncPostReceiveRepositoryHook, RepositorySettingsValidator {

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

                        log.info("looking at refchange" + refChange.getType());
                        //For each RefChange, use the fromHash and toHash to extract the individual changesets: https://developer.atlassian.com/static/javadoc/stash/latest/api/reference/com/atlassian/stash/repository/RefChange.html
                        //Using HistoryService.getChangesetsBetween stream a list of changesets from RefChange.getFromHash and RefChange.getToHash
                        //For each of these changesets, validate the message as you need.
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