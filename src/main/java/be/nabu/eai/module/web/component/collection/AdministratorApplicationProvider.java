package be.nabu.eai.module.web.component.collection;

import be.nabu.eai.developer.api.ApplicationProvider;
import be.nabu.eai.developer.collection.ApplicationManager;
import be.nabu.eai.module.web.application.api.TargetAudience;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.resources.RepositoryEntry;
import javafx.scene.Node;

public class AdministratorApplicationProvider implements ApplicationProvider {

	@Override
	public Node getLargeCreateIcon() {
		return ApplicationManager.newNode("application/application-administrator-large.png", "Administrator Application", "A backoffice oriented web application.");
	}
	
	@Override
	public String getSubType() {
		return "administrator";
	}

	@Override
	public Node getSummaryView(Entry entry) {
		return ConsumerApplicationProvider.getSummaryView(entry, "application/application-administrator-large.png");
	}

	@Override
	public void initialize(Entry newApplication) {
		ConsumerApplicationProvider.getOrCreateWebApplication((RepositoryEntry) newApplication, TargetAudience.BUSINESS);
	}
	
	@Override
	public String suggestName(Entry entry) {
		return entry.getChild("admin") == null ? "Admin" : null;
	}

	@Override
	public String getMediumIcon() {
		return "application/application-administrator-medium.png";
	}

	@Override
	public String getSmallIcon() {
		return "application/application-administrator-small.png";
	}

	@Override
	public String getLargeIcon() {
		return "application/application-administrator-large.png";
	}
}
