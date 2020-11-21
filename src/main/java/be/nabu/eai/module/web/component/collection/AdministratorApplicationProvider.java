package be.nabu.eai.module.web.component.collection;

import be.nabu.eai.developer.api.ApplicationProvider;
import be.nabu.eai.developer.collection.ApplicationManager;
import be.nabu.eai.module.web.application.api.TargetAudience;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.resources.RepositoryEntry;
import javafx.scene.Node;

public class AdministratorApplicationProvider implements ApplicationProvider {

	@Override
	public Node getLargeIcon() {
		return ApplicationManager.newNode("application/application-administrator.png", "Administrator Application", "A backoffice oriented web application.");
	}

	@Override
	public Node getSummaryView(Entry entry) {
		// TODO Auto-generated method stub
		return ApplicationProvider.super.getSummaryView(entry);
	}

	@Override
	public void initialize(Entry newApplication) {
		ConsumerApplicationProvider.getOrCreateWebApplication((RepositoryEntry) newApplication, TargetAudience.BUSINESS);
	}

}
