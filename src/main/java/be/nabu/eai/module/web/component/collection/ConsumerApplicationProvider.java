package be.nabu.eai.module.web.component.collection;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.api.NamingConvention;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.api.ApplicationProvider;
import be.nabu.eai.developer.collection.ApplicationManager;
import be.nabu.eai.developer.collection.EAICollectionUtils;
import be.nabu.eai.developer.impl.CustomTooltip;
import be.nabu.eai.developer.util.EAIDeveloperUtils;
import be.nabu.eai.module.http.server.HTTPServerArtifact;
import be.nabu.eai.module.http.server.HTTPServerManager;
import be.nabu.eai.module.http.virtual.VirtualHostArtifact;
import be.nabu.eai.module.http.virtual.VirtualHostManager;
import be.nabu.eai.module.swagger.provider.SwaggerProvider;
import be.nabu.eai.module.swagger.provider.SwaggerProviderManager;
import be.nabu.eai.module.web.application.WebApplication;
import be.nabu.eai.module.web.application.WebApplicationManager;
import be.nabu.eai.module.web.application.WebFragment;
import be.nabu.eai.module.web.application.api.TargetAudience;
import be.nabu.eai.module.web.component.WebComponent;
import be.nabu.eai.module.web.component.WebComponentManager;
import be.nabu.eai.module.web.resources.WebComponentContextMenu;
import be.nabu.eai.repository.CollectionImpl;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.artifacts.api.Artifact;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.resources.api.ManageableContainer;
import be.nabu.libs.resources.api.WritableResource;
import be.nabu.libs.types.DefinedTypeResolverFactory;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.WritableContainer;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Button;

public class ConsumerApplicationProvider implements ApplicationProvider {

	private static Logger logger = LoggerFactory.getLogger(ConsumerApplicationProvider.class);
	
	@Override
	public Node getLargeCreateIcon() {
		return ApplicationManager.newNode("application/application-consumer-large.png", "End-User Application", "An end-user facing web application.");
	}

	@Override
	public String getSubType() {
		return "consumer";
	}

	@Override
	public void initialize(Entry newApplication, String version) {
		getOrCreateWebApplication((RepositoryEntry) newApplication, version, TargetAudience.CUSTOMER);
		// open it!
		// note that the currenty entry is not the node itself? it is a folder that contains the node etc
//		MainController.getInstance().open(newApplication.getId());
	}
	
	public static <T extends Artifact> T getApplicationArtifact(Entry entry, Class<T> clazz, boolean allowProject) {
		if (allowProject) {
			entry = EAICollectionUtils.getProject(entry);
		}
		for (T potential : entry.getRepository().getArtifacts(clazz)) {
			if (potential.getId().startsWith(entry.getId() + ".")) {
				return potential;
			}
		}
		return null;
	}
	
	public static VirtualHostArtifact getOrCreateVirtualHost(RepositoryEntry applicationEntry) {
		VirtualHostArtifact host = getApplicationArtifact(applicationEntry, VirtualHostArtifact.class, true);
		if (host == null) {
			try {
				RepositoryEntry miscFolder = (RepositoryEntry) getSharedFolder(applicationEntry);
				// build virtual host
				RepositoryEntry hostEntry = miscFolder.createNode("host", new VirtualHostManager(), true);
//				host = new VirtualHostArtifact(hostEntry.getId(), hostEntry.getContainer(), hostEntry.getRepository());
				host = (VirtualHostArtifact) hostEntry.getNode().getArtifact();
				host.getConfig().setServer(getOrCreateServer(applicationEntry));
				new VirtualHostManager().save(hostEntry, host);
				EAIDeveloperUtils.created(hostEntry.getId());
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return host;
	}

	public static HTTPServerArtifact getOrCreateServer(Entry applicationEntry) {
		Entry projectEntry = EAICollectionUtils.getProject(applicationEntry);
		List<Integer> currentPorts = new ArrayList<Integer>();
		HTTPServerArtifact server = null;
		// we want to check if there is already an http server in your project that we can use
		for (HTTPServerArtifact potential : applicationEntry.getRepository().getArtifacts(HTTPServerArtifact.class)) {
			if (potential.getConfig().getPort() != null) {
				currentPorts.add(potential.getConfig().getPort());
			}
			// if it's in the application itself, it always wins
			if (potential.getConfig().isEnabled() && potential.getId().startsWith(applicationEntry.getId() + ".")) {
				server = potential;
			}
			// if it's in the project folder, it wins if we don't have anything else
			else if (potential.getConfig().isEnabled() && potential.getId().startsWith(projectEntry.getId() + ".") && server == null) {
				server = potential;
			}
		}
		if (server == null) {
			int port = 8080;
			// get the first available port
			while (currentPorts.indexOf(port) >= 0) {
				port++;
			}
			try {
				RepositoryEntry sharedFolder = (RepositoryEntry) getSharedFolder(applicationEntry);
				RepositoryEntry serverEntry = sharedFolder.createNode("server", new HTTPServerManager(), true);
				EAIDeveloperUtils.created(serverEntry.getId());
				// in local mode it might not work otherwise, probably to do with the version the server is seeing and this version
				// if we get it from the node, we get the same reference as the server
				//server = new HTTPServerArtifact(serverEntry.getId(), serverEntry.getContainer(), projectEntry.getRepository());
				server = (HTTPServerArtifact) serverEntry.getNode().getArtifact();
				// build http server
				server.getConfig().setPort(port);
				server.getConfig().setEnabled(true);
				new HTTPServerManager().save(serverEntry, server);
				EAIDeveloperUtils.updated(serverEntry.getId());
			}
			catch(Exception e) {
				MainController.getInstance().notify(e);
			}
		}
		return server;
	}
	
	public static Entry getSharedFolder(Entry entry) {
		// we start from the project
		Entry projectEntry = EAICollectionUtils.getProject(entry);
		// a folder there called "shared"
		Entry shared = EAIDeveloperUtils.mkdir((RepositoryEntry) projectEntry, "shared");
		if (!shared.isCollection()) {
			CollectionImpl collection = new CollectionImpl();
			collection.setType("folder");
			collection.setName("Shared");
			collection.setSmallIcon("shared/shared-small.png");
			collection.setMediumIcon("shared/shared-medium.png");
			collection.setLargeIcon("shared/shared-big.png");
			collection.setSubType("shared");
			((RepositoryEntry) shared).setCollection(collection);
			((RepositoryEntry) shared).saveCollection();
		}
		return shared;
	}

	public static String getApplicationName(Entry applicationEntry) {
		if (applicationEntry.getCollection() != null && applicationEntry.getCollection().getName() != null) {
			return applicationEntry.getCollection().getName();
		}
		return NamingConvention.UPPER_TEXT.apply(applicationEntry.getName(), NamingConvention.LOWER_CAMEL_CASE);
	}
	
	public static WebComponent getOrCreateAPIComponent(RepositoryEntry applicationEntry, String version, boolean isApi) {
		try {
			Entry child = applicationEntry.getChild("api");
			if (child == null) {
				RepositoryEntry componentEntry = applicationEntry.createNode("api", new WebComponentManager(), true);
				child = componentEntry;
				componentEntry.getNode().setName("API");
				componentEntry.getNode().setTags(new ArrayList<String>(Arrays.asList("Api")));
				componentEntry.saveNode();
				WebComponent component = new WebComponent(componentEntry.getId(), componentEntry.getContainer(), componentEntry.getRepository());
				if (version != null) {
					component.getConfig().setPath("/api/v" + NamingConvention.UNDERSCORE.apply(version));
				}
				else {
					component.getConfig().setPath("/api/" + (isApi ? "v1" : "otr"));
				}
				new WebComponentManager().save(componentEntry, component);
				EAIDeveloperUtils.created(componentEntry.getId());
			}
			return (WebComponent) child.getNode().getArtifact();
		}
		catch (Exception e) {
			MainController.getInstance().notify(e);
		}
		return null;
	}
	
	public static SwaggerProvider getOrCreateSwagger(RepositoryEntry applicationEntry, String version, boolean isApi) {
		SwaggerProvider provider = getApplicationArtifact(applicationEntry, SwaggerProvider.class, false);
		if (provider == null) {
			try {
				RepositoryEntry swaggerEntry = applicationEntry.createNode("swagger", new SwaggerProviderManager(), true);
				swaggerEntry.getNode().setName("Swagger");
				swaggerEntry.saveNode();
				provider = new SwaggerProvider(swaggerEntry.getId(), swaggerEntry.getContainer(), swaggerEntry.getRepository());
				// we can't set the base path for most applications, as cms etc don't fall under this path...
				// this would remove things like remember, login etc...
				if (isApi) {
					provider.getConfig().setBasePath("/api/v" + (version == null ? "1" : NamingConvention.UNDERSCORE.apply(version)));
				}
				new SwaggerProviderManager().save(swaggerEntry, provider);
				EAIDeveloperUtils.created(swaggerEntry.getId());
			}
			catch (Exception e) {
				MainController.getInstance().notify(e);
			}
		}
		return provider;
	}
	
	public static WebApplication getOrCreateWebApplication(RepositoryEntry applicationEntry, String version, TargetAudience audience) {
		WebApplication application = getApplicationArtifact(applicationEntry, WebApplication.class, false);
		if (application == null) {
			try {
				VirtualHostArtifact host = getOrCreateVirtualHost(applicationEntry);
				logger.info("Getting virtual host for " + applicationEntry.getId() + ": " + host.getId());
				
				// check if there are already applications on the host, if so, we can't take their paths
				List<String> paths = new ArrayList<String>();
				for (WebApplication existing : applicationEntry.getRepository().getArtifacts(WebApplication.class)) {
					if (host.equals(existing.getConfig().getVirtualHost())) {
						paths.add(existing.getServerPath());
					}
				}
				
				RepositoryEntry entry = applicationEntry.createNode("application", new WebApplicationManager(), true);
				entry.getNode().setName("Application");
				entry.saveNode();
				EAIDeveloperUtils.created(entry.getId());
				
				// @2021-12-22: for some reason, creating a new application doesn't work, but getting it from the node does...don't want to get it into now
				// the behavior was that when working locally (so not on remote server, there everything worked fine), this method would end up with a web application that does not have the virtual host configured nor the API/swagger component
				// possibly something to do with all the reloading being triggered when working locally
				// we actually use the other method (get artifact from node) more in general, so let's stick to that for now...
//				application = new WebApplication(entry.getId(), entry.getContainer(), entry.getRepository());
				application = (WebApplication) entry.getNode().getArtifact();
				application.getConfig().setHtml5Mode(true);
				application.getConfig().setVirtualHost(host);
				String path = "/";
				switch (audience) {
					case MANAGER: 
						path += "manage";
					break;
					case BUSINESS:
						path += "dashboard";
					break;
					case API:
						application.getConfig().setAllowBasicAuthentication(true);
					break;
				}
				while (paths.contains(path)) {
					if (path.equals("/")) {
						path += entry.getName();
					}
					// this will generate 1, 11, 111 instead of 1,2,3 but the edge case is rare enough that it doesn't matter?
					else {
						path += "1";
					}
				}
				logger.info("Configured host: " + application.getConfig().getVirtualHost());
				application.getConfig().setPath(path.equals("/") ? null : path);
				// add the API and the swagger
				boolean isApi = TargetAudience.API.equals(audience);
				application.getConfig().setWebFragments(new ArrayList<WebFragment>(Arrays.asList(getOrCreateAPIComponent(applicationEntry, version, isApi), getOrCreateSwagger(applicationEntry, version, isApi))));
				logger.info("Configured API and SWAGGER: " + application.getConfig().getWebFragments());
				
				// if we have swaggerui installed, add it
				Artifact resolve = applicationEntry.getRepository().resolve("nabu.web.swagger.swaggerui");
				if (resolve != null) {
					application.getConfig().getWebFragments().add((WebFragment) resolve);
				}
				
				// add the cms all, task manage etc etc
				// we add any component flagged as being standard for your target audience
				for (WebComponent potential : MainController.getInstance().getRepository().getArtifacts(WebComponent.class)) {
					if (audience.equals(potential.getConfig().getAudience()) && !application.getConfig().getWebFragments().contains(potential)) {
						application.getConfig().getWebFragments().add(potential);
					}
				}
				
				// update the cms configuration to have the correct JDBC
				ComplexContent configuration = application.getConfigurationFor(".*", (ComplexType) DefinedTypeResolverFactory.getInstance().getResolver().resolve("nabu.cms.core.configuration"));
				if (configuration == null) {
					configuration = ((ComplexType) DefinedTypeResolverFactory.getInstance().getResolver().resolve("nabu.cms.core.configuration")).newInstance();
				}
//				configuration.set("connectionId", jdbc.getId());
				
				// set the security restrictions by default
				switch(audience) {
					case BUSINESS: 
						configuration.set("security/allowedRoles", new ArrayList<String>(Arrays.asList("business", "admin")));
					break;
					case MANAGER:
						configuration.set("security/allowedRoles", new ArrayList<String>(Arrays.asList("manager", "admin")));
					break;
					case CUSTOMER:
						configuration.set("passwordRegex", "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}");
					break;
				}
				configuration.set("caseInsensitive", true);
				configuration.set("masterdata/preloadedCategories", new ArrayList<String>(Arrays.asList("language", "attachmentGroup")));
				
				application.putConfiguration(configuration, null, false);
				
				logger.info("Saving application into: " + entry.getId());
				new WebApplicationManager().save(entry, application);
				// refresh the entry so the node is reloaded!
				// the code in the copy takes the artifact from the node
				// if that is still the "old" version (so not updated from the save), we might be overwriting our changes above
				entry.refresh(false);
				logger.info("Saved application into: " + entry.getId());
				
				// fix the page builder template, this will already fix a lot of stuff
				ManageableContainer<?> publicDirectory = (ManageableContainer<?>) ResourceUtils.mkdirs(entry.getContainer(), EAIResourceRepository.PUBLIC);
				ManageableContainer<?> privateDirectory = (ManageableContainer<?>) ResourceUtils.mkdirs(entry.getContainer(), EAIResourceRepository.PRIVATE);
				
				// for API's we don't use page builder, we just add a redirect to the swaggerui
				if (isApi) {
					String content = "redirect(server.root() + 'swaggerui')";
					ManageableContainer<?> pages = (ManageableContainer<?>) ResourceUtils.mkdirs(publicDirectory, "pages");
					WritableResource create = (WritableResource) pages.create("index.glue", "text/glue");
					WritableContainer<ByteBuffer> writable = create.getWritable();
					try {
						writable.write(IOUtils.wrap(content.getBytes(Charset.forName("UTF-8")), true));
					}
					finally {
						writable.close();
					}
				}
				else {
					WebComponentContextMenu.copyPageWithCms(entry, publicDirectory, privateDirectory);
				}
				
				EAIDeveloperUtils.updated(entry.getId());
				// open the application
				MainController.getInstance().open(entry.getId());
			}
			catch (Exception e) {
				logger.error("Could not generate web application", e);
				MainController.getInstance().notify(e);
			}
		}
		return application;
	}

	@Override
	public String suggestName(Entry entry) {
		return entry.getChild("site") == null ? "Site" : null;
	}

	public static Node getSummaryView(Entry entry, String icon) {
		WebApplication applicationArtifact = getApplicationArtifact(entry, WebApplication.class, false);
		if (applicationArtifact != null) {
			Button openSite = new Button();
			openSite.setGraphic(MainController.loadFixedSizeGraphic("icons/eye.png", 12));
			new CustomTooltip("View the web application").install(openSite);
			openSite.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent arg0) {
					MainController.getInstance().open(applicationArtifact.getId());
				}
			});
			return ApplicationManager.buildSummaryView(entry, icon, openSite);
		}
		else {
			// TODO: add a button to readd it?
			return ApplicationManager.buildSummaryView(entry, icon);
		}
	}


	@Override
	public Node getSummaryView(Entry entry) {
		return getSummaryView(entry, "application/application-consumer-large.png");
	}

	@Override
	public String getMediumIcon() {
		return "application/application-consumer-medium.png";
	}

	@Override
	public String getSmallIcon() {
		return "application/application-consumer-small.png";
	}

	@Override
	public String getLargeIcon() {
		return "application/application-consumer-large.png";
	}
	
}
