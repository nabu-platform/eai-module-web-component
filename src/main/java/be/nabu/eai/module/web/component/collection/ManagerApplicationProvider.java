package be.nabu.eai.module.web.component.collection;

import be.nabu.eai.developer.api.ApplicationProvider;
import be.nabu.eai.developer.collection.ApplicationManager;
import be.nabu.eai.module.web.application.api.TargetAudience;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.resources.RepositoryEntry;
import javafx.scene.Node;

public class ManagerApplicationProvider implements ApplicationProvider {

	@Override
	public Node getLargeCreateIcon() {
		return ApplicationManager.newNode("application/application-manager-large.png", "Manager Application", "A web application aimed at technical support.");
	}
	

	@Override
	public String getSubType() {
		return "manager";
	}

	@Override
	public Node getSummaryView(Entry entry) {
		return ConsumerApplicationProvider.getSummaryView(entry, "application/application-manager-large.png");
	}
	
	@Override
	public void initialize(Entry newApplication, String version) {
		ConsumerApplicationProvider.getOrCreateWebApplication((RepositoryEntry) newApplication, version, TargetAudience.MANAGER);
	}

	@Override
	public String suggestName(Entry entry) {
		return entry.getChild("manage") == null ? "Manage" : null;
	}
	
	@Override
	public String getMediumIcon() {
		return "application/application-manager-medium.png";
	}

	@Override
	public String getSmallIcon() {
		return "application/application-manager-small.png";
	}

	@Override
	public String getLargeIcon() {
		return "application/application-manager-large.png";
	}
}
