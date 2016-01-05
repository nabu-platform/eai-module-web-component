package be.nabu.eai.module.web.rest;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import be.nabu.eai.module.authorization.vm.VMServiceAuthorizer;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.artifacts.container.BaseContainerArtifact;
import be.nabu.eai.repository.artifacts.web.WebArtifact;
import be.nabu.eai.repository.artifacts.web.WebFragment;
import be.nabu.eai.repository.artifacts.web.rest.WebRestArtifact;
import be.nabu.eai.repository.artifacts.web.rest.WebRestListener;
import be.nabu.libs.authentication.api.Permission;
import be.nabu.libs.events.api.EventSubscription;
import be.nabu.libs.http.api.HTTPRequest;
import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.http.server.HTTPServerUtils;
import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.ServiceAuthorizer;
import be.nabu.libs.services.api.ServiceAuthorizerProvider;
import be.nabu.libs.services.api.ServiceInstance;
import be.nabu.libs.services.api.ServiceInterface;
import be.nabu.libs.services.vm.SimpleVMServiceDefinition;

public class RESTVMService extends BaseContainerArtifact implements WebFragment, DefinedService, ServiceAuthorizerProvider {

	private Map<String, List<EventSubscription<?, ?>>> subscriptions = new HashMap<String, List<EventSubscription<?,?>>>();
	
	public RESTVMService(String id) {
		super(id);
	}

	@Override
	public void start(WebArtifact artifact) throws IOException {
		if (subscriptions.containsKey(artifact.getId())) {
			stop(artifact);
		}
		subscriptions.put(artifact.getId(), new ArrayList<EventSubscription<?, ?>>());
		WebRestListener listener = new WebRestListener(
			artifact.getRepository(),
			artifact.getServerPath(), 
			artifact.getRealm(), 
			artifact.getSessionProvider(), 
			artifact.getPermissionHandler(), 
			artifact.getRoleHandler(), 
			artifact.getTokenValidator(), 
			getArtifact(WebRestArtifact.class), 
			getArtifact(SimpleVMServiceDefinition.class), 
			artifact.getConfiguration().getCharset() == null ? Charset.defaultCharset() : Charset.forName(artifact.getConfiguration().getCharset()), 
			!EAIResourceRepository.isDevelopment()
		);
		EventSubscription<HTTPRequest, HTTPResponse> subscription = artifact.getDispatcher().subscribe(HTTPRequest.class, listener);
		subscription.filter(HTTPServerUtils.limitToPath(artifact.getServerPath()));
		subscriptions.get(artifact.getId()).add(subscription);
	}

	@Override
	public void stop(WebArtifact artifact) {
		List<EventSubscription<?, ?>> list = subscriptions.get(artifact.getId());
		if (list != null) {
			for (EventSubscription<?, ?> subscription : list) {
				subscription.unsubscribe();
			}
			list.clear();
		}
	}

	@Override
	public List<Permission> getPermissions() {
		List<Permission> permissions = new ArrayList<Permission>();
		WebRestArtifact artifact = getArtifact(WebRestArtifact.class);
		permissions.add(new Permission() {
			@Override
			public String getContext() {
				try {
					return artifact.getConfiguration().getPath();
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
			@Override
			public String getAction() {
				try {
					return artifact.getConfiguration().getMethod() == null ? null : artifact.getConfiguration().getMethod().toString();
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		});
		return permissions;
	}

	@Override
	public boolean isStarted(WebArtifact artifact) {
		return subscriptions.containsKey(artifact.getId()) && !subscriptions.get(artifact.getId()).isEmpty();
	}

	@Override
	public ServiceInterface getServiceInterface() {
		SimpleVMServiceDefinition artifact = getArtifact(SimpleVMServiceDefinition.class);
		return artifact.getServiceInterface();
	}

	@Override
	public ServiceInstance newInstance() {
		SimpleVMServiceDefinition artifact = getArtifact(SimpleVMServiceDefinition.class);
		return artifact.newInstance();
	}

	@Override
	public Set<String> getReferences() {
		return new HashSet<String>();
	}

	@Override
	public ServiceAuthorizer getAuthorizer(ServiceRuntime runtime) {
		if (runtime.getService().equals(this) || runtime.getService().equals(getArtifact("implementation"))) {
			return new VMServiceAuthorizer(getArtifact("security"));
		}
		return null;
	}

}
