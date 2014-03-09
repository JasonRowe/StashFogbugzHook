package com.protolabs;

import com.atlassian.stash.hook.repository.*;
import com.atlassian.stash.repository.*;
import com.atlassian.stash.setting.*;
import java.net.URL;
import java.util.Collection;

public class FogbugzHook implements AsyncPostReceiveRepositoryHook, RepositorySettingsValidator {

    /**
     * TODO - Loop through refChanges find last commit
     * TODO - Determine if FogbugzID is in commit message
     * TODO - If so, pull out info (file name, Repo location, etc) and send to Fogbugz endpoint 
     */
    @Override
    public void postReceive(RepositoryHookContext context, Collection<RefChange> refChanges) {
        String url = context.getSettings().getString("url");
        if (url != null) {
            try {
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