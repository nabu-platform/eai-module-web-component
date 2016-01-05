package be.nabu.eai.module.web.rest;

import java.util.ArrayList;
import java.util.List;

import be.nabu.eai.repository.artifacts.container.ContainerArtifactManager;
import be.nabu.libs.resources.api.ResourceContainer;

public class RESTVMManager extends ContainerArtifactManager<RESTVMService> {
	
	@Override
	public RESTVMService newInstance(String id) {
		return new RESTVMService(id);
	}

	@Override
	public Class<RESTVMService> getArtifactClass() {
		return RESTVMService.class;
	}

	@Override
	protected List<ResourceContainer<?>> getChildrenToLoad(ResourceContainer<?> directory) {
		List<ResourceContainer<?>> children = new ArrayList<ResourceContainer<?>>();
		children.add((ResourceContainer<?>) directory.getChild("api"));
		children.add((ResourceContainer<?>) directory.getChild("implementation"));
		children.add((ResourceContainer<?>) directory.getChild("security"));
		return children;
	}

}
