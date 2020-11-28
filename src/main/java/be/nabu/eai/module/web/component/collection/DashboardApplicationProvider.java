package be.nabu.eai.module.web.component.collection;

import be.nabu.eai.developer.api.ApplicationProvider;
import be.nabu.eai.developer.collection.ApplicationManager;
import be.nabu.eai.module.web.application.api.TargetAudience;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.resources.RepositoryEntry;
import javafx.scene.Node;

public class DashboardApplicationProvider implements ApplicationProvider {

	@Override
	public Node getLargeCreateIcon() {
		return ApplicationManager.newNode("application/application-dashboard-large.png", "Dashboard Application", "Get insight into the data and take action.");
	}
	
	@Override
	public String getSubType() {
		return "dashboard";
	}

	@Override
	public Node getSummaryView(Entry entry) {
		return ConsumerApplicationProvider.getSummaryView(entry, "application/application-dashboard-large.png");
	}

	@Override
	public void initialize(Entry newApplication, String version) {
		ConsumerApplicationProvider.getOrCreateWebApplication((RepositoryEntry) newApplication, version, TargetAudience.BUSINESS);
	}
	
	@Override
	public String suggestName(Entry entry) {
		return entry.getChild("dashboard") == null ? "Dashboard" : null;
	}

	@Override
	public String getMediumIcon() {
		return "application/application-dashboard-medium.png";
	}

	@Override
	public String getSmallIcon() {
		return "application/application-dashboard-small.png";
	}

	@Override
	public String getLargeIcon() {
		return "application/application-dashboard-large.png";
	}
}
