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
		return ApplicationManager.newNode("application/application-manager.png", "Manager Application", "A web application aimed at technical support.");
	}
	

	@Override
	public String getSubType() {
		return "manager";
	}

	@Override
	public Node getSummaryView(Entry entry) {
		return ConsumerApplicationProvider.getSummaryView(entry, "application/application-manager.png");
	}
	
	@Override
	public void initialize(Entry newApplication) {
		ConsumerApplicationProvider.getOrCreateWebApplication((RepositoryEntry) newApplication, TargetAudience.MANAGER);
	}

	@Override
	public String suggestName(Entry entry) {
		return entry.getChild("manage") == null ? "Manage" : null;
	}
}
