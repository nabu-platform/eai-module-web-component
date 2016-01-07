package be.nabu.eai.module.web.module;

import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.managers.base.JAXBArtifactManager;
import be.nabu.libs.resources.api.ResourceContainer;

public class WebModuleManager extends JAXBArtifactManager<WebModuleConfiguration, WebModule> {

	public WebModuleManager() {
		super(WebModule.class);
	}

	@Override
	protected WebModule newInstance(String id, ResourceContainer<?> container, Repository repository) {
		return new WebModule(id, container, repository);
	}

}
