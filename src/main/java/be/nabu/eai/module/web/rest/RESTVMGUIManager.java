package be.nabu.eai.module.web.rest;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.api.ContainerArtifactGUIManager;
import be.nabu.eai.developer.managers.VMServiceGUIManager;
import be.nabu.eai.repository.artifacts.web.rest.WebRestArtifact;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.services.api.DefinedServiceInterface;
import be.nabu.libs.services.vm.Pipeline;
import be.nabu.libs.services.vm.PipelineInterfaceProperty;
import be.nabu.libs.services.vm.SimpleVMServiceDefinition;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.structure.Structure;

public class RESTVMGUIManager extends ContainerArtifactGUIManager<RESTVMService> {

	public RESTVMGUIManager() {
		super("REST Service", RESTVMService.class, new RESTVMManager());
	}

	@Override
	protected List<Property<?>> getCreateProperties() {
		return null;
	}

	@Override
	public RESTVMService newInstance(MainController controller, RepositoryEntry entry, Value<?>...values) throws IOException {
		RESTVMService restvmService = new RESTVMService(entry.getId());
		WebRestArtifact webRestArtifact = new WebRestArtifact("$self:api", entry.getContainer(), entry.getRepository());
		restvmService.addArtifact("api", webRestArtifact, null);
		Pipeline pipeline = new Pipeline(new Structure(), new Structure());
		pipeline.setProperty(new ValueImpl<DefinedServiceInterface>(PipelineInterfaceProperty.getInstance(), webRestArtifact));
		SimpleVMServiceDefinition service = new SimpleVMServiceDefinition(pipeline);
		service.setId("$self:implementation");
		Map<String, String> configuration = new HashMap<String, String>();
		configuration.put(VMServiceGUIManager.INTERFACE_EDITABLE, "false");
		restvmService.addArtifact("implementation", service, configuration);
//		VMAuthorizationService authorization = new VMAuthorizationService(service);
//		authorization.setId(entry.getId() + ":security");
//		restvmService.addArtifact("security", authorization, configuration);
		return restvmService;
	}
	
	@Override
	public String getCategory() {
		return "Web";
	}
}
