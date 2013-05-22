package com.waiwaier.stash.webhook;

import java.net.URLEncoder;
import java.util.Collection;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.client.*;
import org.apache.http.client.methods.*;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.NameValuePair;

import com.atlassian.stash.hook.repository.AsyncPostReceiveRepositoryHook;
import com.atlassian.stash.hook.repository.RepositoryHookContext;
import com.atlassian.stash.repository.RefChange;
import com.atlassian.stash.project.Project;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.setting.RepositorySettingsValidator;
import com.atlassian.stash.setting.Settings;
import com.atlassian.stash.setting.SettingsValidationErrors;
import com.waiwaier.stash.webhook.service.ConcreteHttpClientFactory;
import com.waiwaier.stash.webhook.service.HttpClientFactory;

/**
 * Note that hooks can implement RepositorySettingsValidator directly.
 */
public class Webhook implements AsyncPostReceiveRepositoryHook, RepositorySettingsValidator {

	private HttpClientFactory factory;
	
	public Webhook() {
		factory = new ConcreteHttpClientFactory();
	}
	
    /**
     * Connects to a configured URL to notify of all changes.
     */
    @Override
    public void postReceive(RepositoryHookContext context, 
    		Collection<RefChange> refChanges) {
        String callbackURL = context.getSettings().getString("callbackURL");
        Boolean ignoreCerts = context.getSettings().getBoolean("ignoreCerts");
        Repository repo = context.getRepository();

        if (callbackURL == null)
        	return;
        
        Boolean usingSsl = callbackURL.startsWith("https");
        HttpClient client = null;
        
        try {
	        client = factory.getHttpClient(usingSsl, ignoreCerts);
        	HttpPost post = new HttpPost(callbackURL);
            Iterator<RefChange> it = refChanges.iterator();
            RefChange a = (RefChange)it.next();

            List<NameValuePair> params = new ArrayList<NameValuePair>(5);
            params.add(new BasicNameValuePair("project_name", repo.getProject().getName()));
            params.add(new BasicNameValuePair("repo_name", repo.getName()));
            params.add(new BasicNameValuePair("branch_name", a.getRefId()));
            params.add(new BasicNameValuePair("from_hash", a.getFromHash()));
            params.add(new BasicNameValuePair("to_hash", a.getToHash()));
            post.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));

        	client.execute(post);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
        	if (client != null)
        		client.getConnectionManager().shutdown();
        }
    }

    @Override
    public void validate(Settings settings, SettingsValidationErrors errors, Repository repository) {
        if (settings.getString("callbackURL", "").isEmpty()) {
            errors.addFieldError("callbackURL", "The url to callback is required.");
        }
    }
    
    /**
     * Used for testing purposes
     */
    protected void setFactory(HttpClientFactory factory) {
		this.factory = factory;
	}
}
