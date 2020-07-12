package be.nabu.eai.module.web.component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.module.web.application.WebApplication;
import be.nabu.eai.module.web.application.WebFragment;
import be.nabu.eai.module.web.application.WebFragmentConfiguration;
import be.nabu.eai.module.web.application.WebFragmentProvider;
import be.nabu.eai.module.web.application.api.RateLimit;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.artifacts.jaxb.JAXBArtifact;
import be.nabu.glue.api.ScriptRepository;
import be.nabu.glue.core.repositories.ScannableScriptRepository;
import be.nabu.libs.authentication.api.Permission;
import be.nabu.libs.events.api.EventSubscription;
import be.nabu.libs.http.api.HTTPEntity;
import be.nabu.libs.http.api.HTTPRequest;
import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.resources.VirtualContainer;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.memory.MemoryItem;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.io.api.WritableContainer;

public class WebComponent extends JAXBArtifact<WebComponentConfiguration> implements WebFragment, WebFragmentProvider {

	public static class WebFragmentConfigurationImplementation implements WebFragmentConfiguration {
		private DefinedType type;
		private String path;

		public WebFragmentConfigurationImplementation(DefinedType type, String path) {
			this.type = type;
			this.path = path;
		}

		@Override
		public ComplexType getType() {
			return (ComplexType) type;
		}

		@Override
		public String getPath() {
			return path;
		}
	}

	/**
	 * In theory we could just scan the pub/priv and always remove the resources/scripts based on that
	 * However it is possible that in between start() and stop() the folders have changed but we still need to correctly unregister everything
	 * So we keep copies of exactly what we registered per artifact
	 */
	private Map<String, List<ResourceContainer<?>>> resources = new HashMap<String, List<ResourceContainer<?>>>();
	private Map<String, List<ResourceContainer<?>>> scripts = new HashMap<String, List<ResourceContainer<?>>>();
	private Map<String, List<WebFragment>> fragments = new HashMap<String, List<WebFragment>>();
	private Map<String, List<EventSubscription<?, ?>>> subscriptions = new HashMap<String, List<EventSubscription<?, ?>>>();
	private Map<String, String> paths = new HashMap<String, String>();
	
	private Logger logger = LoggerFactory.getLogger(getClass());
	
	public WebComponent(String id, ResourceContainer<?> directory, Repository repository) {
		super(id, directory, repository, "web-module.xml", WebComponentConfiguration.class);
	}

	private String getKey(WebApplication artifact, String path) {
		return artifact.getId() + ":" + path;
	}
	
	private String getPath(String parent) throws IOException {
		if (getConfiguration().getPath() == null || getConfiguration().getPath().isEmpty() || getConfiguration().getPath().trim().equals("/")) {
			return parent == null || parent.isEmpty() ? "/" : parent;
		}
		else {
			if (parent == null) {
				return getConfiguration().getPath().trim();
			}
			else {
				return parent.replaceFirst("[/]+$", "") + "/" + getConfiguration().getPath().trim().replaceFirst("^[/]+", "");
			}
		}
	}
	
	@Override
	public void start(WebApplication artifact, String path) throws IOException {
		if (isStarted(artifact, path)) {
			stop(artifact, path);
		}
		String key = getKey(artifact, path);
		
		ResourceContainer<?> publicDirectory = (ResourceContainer<?>) getDirectory().getChild(EAIResourceRepository.PUBLIC);
		ResourceContainer<?> privateDirectory = (ResourceContainer<?>) getDirectory().getChild(EAIResourceRepository.PRIVATE);
		
		ResourceContainer<?> meta = privateDirectory == null ? null : (ResourceContainer<?>) privateDirectory.getChild("meta");
		ScriptRepository metaRepository = null;
		
		EventSubscription<HTTPResponse, HTTPResponse> postProcessor = null;
		if (meta != null) {
			synchronized(subscriptions) {
				if (!subscriptions.containsKey(key)) {
					subscriptions.put(key, new ArrayList<EventSubscription<?, ?>>());
				}
				metaRepository = new ScannableScriptRepository(null, meta, artifact.getParserProvider(), Charset.defaultCharset());
				postProcessor = artifact.registerPostProcessor(metaRepository);
				EventSubscription<HTTPRequest, HTTPEntity> preProcessor = artifact.registerPreProcessor(metaRepository);
				subscriptions.get(key).add(preProcessor);
				subscriptions.get(key).add(postProcessor);
				preProcessor.promote();
			}
		}
		
		synchronized(paths) {
			paths.put(key, getPath(path));
		}
		if (publicDirectory != null) {
			ResourceContainer<?> resources = (ResourceContainer<?>) publicDirectory.getChild("resources");
			if (resources != null) {
				logger.debug("Adding resources found in: " + resources);
				artifact.addResources(resources);
				synchronized(this.resources) {
					if (!this.resources.containsKey(key)) {
						this.resources.put(key, new ArrayList<ResourceContainer<?>>());
					}
					this.resources.get(key).add(resources);
				}
			}
			ResourceContainer<?> pages = (ResourceContainer<?>) publicDirectory.getChild("pages");
			if (pages != null) {
				try {
					// we map the public pages to a virtual subpath because they are actually accessible pages by the client and should be accessible at whatever subpath this module has registered
					pages = mapToVirtual(paths.get(key), pages);
					logger.debug("Adding public scripts found in: " + pages);
					artifact.addGlueScripts(pages, true);
					synchronized(scripts) {
						if (!this.scripts.containsKey(key)) {
							this.scripts.put(key, new ArrayList<ResourceContainer<?>>());
						}
						this.scripts.get(key).add(pages);
					}
				}
				catch (URISyntaxException e) {
					logger.error("Could not register public pages", e);
				}
			}
		}
		if (privateDirectory != null) {
			// currently only a scripts folder, but we may want to add more private folders later on
			ResourceContainer<?> scripts = (ResourceContainer<?>) privateDirectory.getChild("scripts");
			if (scripts != null) {
				logger.debug("Adding private scripts found in: " + scripts);
				// we do _not_ map the private scripts as they are utilities used by the public scripts
				// if however they had a dynamic namespace based on where they were mounted, it would be impossible to call them properly
				artifact.addGlueScripts(scripts, false);
				synchronized(this.scripts) {
					if (!this.scripts.containsKey(key)) {
						this.scripts.put(key, new ArrayList<ResourceContainer<?>>());
					}
					this.scripts.get(key).add(scripts);
				}

			}
			if (EAIResourceRepository.isDevelopment()) {
				ResourceContainer<?> resources = (ResourceContainer<?>) privateDirectory.getChild("resources");
				if (resources != null) {
					logger.debug("Adding private resources found in: " + resources);
					artifact.addResources(resources);
					synchronized(this.resources) {
						if (!this.resources.containsKey(key)) {
							this.resources.put(key, new ArrayList<ResourceContainer<?>>());
						}
						this.resources.get(key).add(resources);
					}
				}
			}
			
			Resource providedResources = ResourceUtils.resolve(privateDirectory, "provided/resources");
			if (providedResources != null) {
				artifact.addResources((ResourceContainer<?>) providedResources);
				synchronized(this.resources) {
					if (!this.resources.containsKey(key)) {
						this.resources.put(key, new ArrayList<ResourceContainer<?>>());
					}
					this.resources.get(key).add((ResourceContainer<?>) providedResources);
				}
			}
			
			// externally provided scripts
			Resource providedArtifacts = ResourceUtils.resolve(privateDirectory, "provided/artifacts");
			if (providedArtifacts != null) {
				logger.debug("Adding private provided artifacts found in: " + providedArtifacts);
				artifact.addGlueScripts((ResourceContainer<?>) providedArtifacts, false);
				synchronized(this.scripts) {
					if (!this.scripts.containsKey(key)) {
						this.scripts.put(key, new ArrayList<ResourceContainer<?>>());
					}
					this.scripts.get(key).add((ResourceContainer<?>) providedArtifacts);
				}
			}
		}
		List<WebFragment> webFragments = getConfiguration().getWebFragments();
		if (webFragments != null) {
			// new list so we don't affect the original order
			webFragments = new ArrayList<WebFragment>(webFragments);
			Collections.sort(webFragments, new Comparator<WebFragment>() {
				@Override
				public int compare(WebFragment o1, WebFragment o2) {
					if (o1 == null) {
						return 1;
					}
					else if (o2 == null) {
						return -1;
					}
					else {
						return o1.getPriority().compareTo(o2.getPriority());
					}
				}
			});
			synchronized(scripts) {
				if (!this.fragments.containsKey(key)) {
					this.fragments.put(key, new ArrayList<WebFragment>());
				}
			}
			for (WebFragment fragment : webFragments) {
				if (fragment != null) {
					fragment.start(artifact, paths.get(key));
					fragments.get(key).add(fragment);
				}
			}
		}
		if (postProcessor != null) {
			postProcessor.demote();
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private ResourceContainer<?> mapToVirtual(String path, ResourceContainer<?> pages) throws URISyntaxException {
		path = path.replaceFirst("^[/]+", "").replaceFirst("[/]+$", "");
		VirtualContainer container = new VirtualContainer(new URI(getId() + ":/"));
		ResourceContainer<?> root = container;
		if (path != null && !path.isEmpty()) {
			for (String part : path.split("/")) {
				VirtualContainer<?> child = new VirtualContainer(container, part);
				container.addChild(part, child);
				container = child;
			}
		}
		Resource index = pages.getChild("index.glue");
		if (index == null) {
			index = pages.getChild("index.eglue");
		}
		if (index == null) {
			index = pages.getChild("index.gcss");
		}
		// we register the index page with the same name as the container, this means if we mount for example "/documentation" and it there is a "/documentation/index.glue", it will be shown when accessing "/documentation"
		if (index != null && container.getParent() != null) {
			try {
				MemoryItem resource = new MemoryItem(container.getName() + "." + index.getName().replaceFirst("^.*\\.([^.]+)$", "$1"));
				WritableContainer<ByteBuffer> writable = resource.getWritable();
				ReadableContainer<ByteBuffer> readable = ((ReadableResource) index).getReadable();
				try {
					IOUtils.copyBytes(readable, writable);
				}
				finally {
					readable.close();
					writable.close();
				}
				((VirtualContainer) container.getParent()).addChild(container.getName() + "." + index.getName().replaceFirst("^.*\\.([^.]+)$", "$1"), resource);
			}
			catch (IOException e) {
				throw new RuntimeException("Could not re-register the index");
			}
		}
		for (Resource resource : pages) {
			container.addChild(resource.getName(), resource);
		}
		return root;
	}

	@Override
	public void stop(WebApplication artifact, String path) {
		String key = getKey(artifact, path);
		if (resources.containsKey(key)) {
			synchronized(resources) {
				if (resources.containsKey(key)) {
					for (ResourceContainer<?> container : resources.get(key)) {
						artifact.removeResources(container);
					}
					resources.remove(key);
				}
			}
		}
		if (scripts.containsKey(key)) {
			synchronized(scripts) {
				if (scripts.containsKey(key)) {
					for (ResourceContainer<?> container : scripts.get(key)) {
						artifact.removeGlueScripts(container);
					}
					scripts.remove(key);
				}
			}
		}
		if (fragments.containsKey(key)) {
			synchronized(fragments) {
				if (fragments.containsKey(key)) {
					for (WebFragment fragment : fragments.get(key)) {
						fragment.stop(artifact, paths.get(key));
					}
					fragments.remove(key);
				}
			}
		}
		if (subscriptions.containsKey(key)) {
			synchronized(subscriptions) {
				if (subscriptions.containsKey(key)) {
					for (EventSubscription<?, ?> subscription : subscriptions.get(key)) {
						subscription.unsubscribe();
					}
					subscriptions.remove(key);
				}
			}
		}
	}

	@Override
	public List<Permission> getPermissions(WebApplication artifact, String path) {
		try {
			List<Permission> permissions = new ArrayList<Permission>();
			List<WebFragment> webFragments = getConfiguration().getWebFragments();
			if (webFragments != null) {
				for (WebFragment fragment : webFragments) {
					if (fragment != null) {
						permissions.addAll(fragment.getPermissions(artifact, getPath(path)));
					}
				}
			}
			return permissions;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public List<RateLimit> getRateLimits(WebApplication artifact, String path) {
		try {
			List<RateLimit> rateLimits = new ArrayList<RateLimit>();
			List<WebFragment> webFragments = getConfiguration().getWebFragments();
			if (webFragments != null) {
				for (WebFragment fragment : webFragments) {
					if (fragment != null) {
						List<RateLimit> children = fragment.getRateLimits(artifact, getPath(path));
						if (children != null) {
							rateLimits.addAll(children);
						}
					}
				}
			}
			return rateLimits;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}


	@Override
	public boolean isStarted(WebApplication artifact, String path) {
		String key = getKey(artifact, path);
		return fragments.containsKey(key) || scripts.containsKey(key) || resources.containsKey(key);
	}

	@Override
	public List<WebFragmentConfiguration> getFragmentConfiguration() {
		List<WebFragmentConfiguration> configurations = new ArrayList<WebFragmentConfiguration>();
		try {
			if (getConfig().getConfigurationType() != null) {
				for (DefinedType type : getConfig().getConfigurationType()) {
					// the path can be null here
					configurations.add(new WebFragmentConfigurationImplementation(type, getConfig().getPath()));
				}
			}
			if (getConfiguration().getWebFragments() != null) {
				final String path = getConfiguration().getPath() == null ? "/" : (getConfiguration().getPath().endsWith("/") ? getConfiguration().getPath() : getConfiguration().getPath() + "/");
				for (WebFragment fragment : getConfiguration().getWebFragments()) {
					if (fragment == null) {
						continue;
					}
					List<WebFragmentConfiguration> fragmentConfiguration = fragment.getFragmentConfiguration();
					if (fragmentConfiguration != null && !fragmentConfiguration.isEmpty()) {
						// if the path is empty or only slashes, we don't add anything to the path
						if (getConfiguration().getPath() == null || getConfiguration().getPath().trim().replace("/", "").isEmpty()) {
							configurations.addAll(fragmentConfiguration);
						}
						else {
							for (final WebFragmentConfiguration configuration : fragmentConfiguration) {
								if (configuration == null) {
									continue;
								}
								configurations.add(new WebFragmentConfiguration() {
									@Override
									public ComplexType getType() {
										return configuration.getType();
									}
									@Override
									public String getPath() {
										return path + (configuration.getPath() == null ? "" : configuration.getPath().replaceFirst("^[/]+", ""));
									}
								});
							}
						}
					}
				}
			}
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		return configurations;
	}

	@Override
	public List<WebFragment> getWebFragments() {
		return getConfig().getWebFragments();
	}

	@Override
	public String getRelativePath() {
		return getConfig().getPath() == null ? "/" : getConfig().getPath();
	}
	
}
