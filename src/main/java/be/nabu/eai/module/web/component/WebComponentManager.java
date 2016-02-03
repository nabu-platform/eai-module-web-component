package be.nabu.eai.module.web.component;

import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.managers.base.JAXBArtifactManager;
import be.nabu.libs.resources.api.ResourceContainer;

public class WebComponentManager extends JAXBArtifactManager<WebComponentConfiguration, WebComponent> {

	public WebComponentManager() {
		super(WebComponent.class);
	}

	@Override
	protected WebComponent newInstance(String id, ResourceContainer<?> container, Repository repository) {
		return new WebComponent(id, container, repository);
	}

}
