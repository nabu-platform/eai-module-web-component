package be.nabu.eai.module.web.rest;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import be.nabu.eai.module.authorization.vm.VMAuthorizationService;
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

	private Map<String, EventSubscription<?, ?>> subscriptions = new HashMap<String, EventSubscription<?, ?>>();
	
	public RESTVMService(String id) {
		super(id);
	}

	private String getKey(WebArtifact artifact, String path) {
		return artifact.getId() + ":" + path;
	}
	
	@Override
	public void start(WebArtifact artifact, String path) throws IOException {
		String key = getKey(artifact, path);
		if (subscriptions.containsKey(key)) {
			stop(artifact, path);
		}
		String restPath = artifact.getServerPath();
		if (path != null && !path.isEmpty() && !path.equals("/")) {
			if (!restPath.endsWith("/")) {
				restPath += "/";
			}
			restPath += path.replaceFirst("^[/]+", "");
		}
		synchronized(subscriptions) {
			WebRestListener listener = new WebRestListener(
				artifact.getRepository(),
				restPath, 
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
			subscription.filter(HTTPServerUtils.limitToPath(restPath));
			subscriptions.put(key, subscription);
		}
	}

	@Override
	public void stop(WebArtifact artifact, String path) {
		String key = getKey(artifact, path);
		if (subscriptions.containsKey(key)) {
			synchronized(subscriptions) {
				if (subscriptions.containsKey(key)) {
					subscriptions.get(key).unsubscribe();
					subscriptions.remove(key);
				}
			}
		}
	}
	
	private String getPath(String parent) throws IOException {
		WebRestArtifact artifact = getArtifact(WebRestArtifact.class);
		if (artifact.getConfiguration().getPath() == null || artifact.getConfiguration().getPath().isEmpty() || artifact.getConfiguration().getPath().trim().equals("/")) {
			return parent;
		}
		else {
			if (parent == null) {
				return artifact.getConfiguration().getPath().trim();
			}
			else {
				return parent.replaceFirst("[/]+$", "") + "/" + artifact.getConfiguration().getPath().trim().replaceFirst("^[/]+", "");
			}
		}
	}

	@Override
	public List<Permission> getPermissions(WebArtifact webArtifact, String path) {
		List<Permission> permissions = new ArrayList<Permission>();
		WebRestArtifact artifact = getArtifact(WebRestArtifact.class);
		permissions.add(new Permission() {
			@Override
			public String getContext() {
				try {
					return getPath(path);
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
	public boolean isStarted(WebArtifact artifact, String path) {
		return subscriptions.containsKey(getKey(artifact, path));
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
		// only run the authorization if it is a root service
		if (runtime.getParent() == null && (runtime.getService().equals(this) || runtime.getService().equals(getArtifact("implementation")))) {
			VMAuthorizationService artifact = getArtifact("security");
			if (artifact != null) {
				return new VMServiceAuthorizer(artifact);
			}
		}
		return null;
	}

}
