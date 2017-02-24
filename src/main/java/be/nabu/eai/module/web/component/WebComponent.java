package be.nabu.eai.module.web.component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
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
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.artifacts.jaxb.JAXBArtifact;
import be.nabu.libs.authentication.api.Permission;
import be.nabu.libs.resources.VirtualContainer;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.types.api.ComplexType;

public class WebComponent extends JAXBArtifact<WebComponentConfiguration> implements WebFragment, WebFragmentProvider {

	/**
	 * In theory we could just scan the pub/priv and always remove the resources/scripts based on that
	 * However it is possible that in between start() and stop() the folders have changed but we still need to correctly unregister everything
	 * So we keep copies of exactly what we registered per artifact
	 */
	private Map<String, List<ResourceContainer<?>>> resources = new HashMap<String, List<ResourceContainer<?>>>();
	private Map<String, List<ResourceContainer<?>>> scripts = new HashMap<String, List<ResourceContainer<?>>>();
	private Map<String, List<WebFragment>> fragments = new HashMap<String, List<WebFragment>>();
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
		
		ResourceContainer<?> publicDirectory = (ResourceContainer<?>) getDirectory().getChild(EAIResourceRepository.PUBLIC);
		ResourceContainer<?> privateDirectory = (ResourceContainer<?>) getDirectory().getChild(EAIResourceRepository.PRIVATE);
		
		String key = getKey(artifact, path);
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
				synchronized(scripts) {
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
	public boolean isStarted(WebApplication artifact, String path) {
		String key = getKey(artifact, path);
		return fragments.containsKey(key) || scripts.containsKey(key) || resources.containsKey(key);
	}

	@Override
	public List<WebFragmentConfiguration> getFragmentConfiguration() {
		List<WebFragmentConfiguration> configurations = new ArrayList<WebFragmentConfiguration>();
		try {
			if (getConfiguration().getWebFragments() != null) {
				final String path = getConfiguration().getPath().endsWith("/") ? getConfiguration().getPath() : getConfiguration().getPath() + "/";
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
								configurations.add(new WebFragmentConfiguration() {
									@Override
									public ComplexType getType() {
										return configuration.getType();
									}
									@Override
									public String getPath() {
										return path + configuration.getPath().replaceFirst("^[/]+", "");
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
