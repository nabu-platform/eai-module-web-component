package be.nabu.eai.module.web.component.wizard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.api.EntryContextMenuProvider;
import be.nabu.eai.developer.impl.AsynchronousRemoteServer;
import be.nabu.eai.module.http.server.HTTPServerArtifact;
import be.nabu.eai.module.http.server.HTTPServerManager;
import be.nabu.eai.module.http.virtual.VirtualHostArtifact;
import be.nabu.eai.module.http.virtual.VirtualHostManager;
import be.nabu.eai.module.jdbc.context.GenerateDatabaseScriptContextMenu;
import be.nabu.eai.module.jdbc.dialects.h2.H2Dialect;
import be.nabu.eai.module.jdbc.pool.JDBCPoolArtifact;
import be.nabu.eai.module.jdbc.pool.JDBCPoolManager;
import be.nabu.eai.module.swagger.provider.SwaggerProvider;
import be.nabu.eai.module.swagger.provider.SwaggerProviderManager;
import be.nabu.eai.module.web.application.WebApplication;
import be.nabu.eai.module.web.application.WebApplicationManager;
import be.nabu.eai.module.web.application.WebFragment;
import be.nabu.eai.module.web.component.WebComponent;
import be.nabu.eai.module.web.component.WebComponentManager;
import be.nabu.eai.module.web.resources.WebComponentContextMenu;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.artifacts.api.Artifact;
import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.resources.api.ManageableContainer;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.jdbc.api.SQLDialect;
import be.nabu.libs.types.DefinedTypeResolverFactory;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.properties.CollectionNameProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

public class WebApplicationWizard implements EntryContextMenuProvider {

	@Override
	public MenuItem getContext(Entry entry) {
		// it needs to be a writable directory
		if (!entry.isNode() && entry instanceof RepositoryEntry) {
			Menu menu = new Menu("Helper");
			MenuItem item = new MenuItem("Create Web Application");
			item.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent arg0) {
					createWebApplication(entry);
				}
			});
			menu.getItems().add(item);
			return menu;
		}
		return null;
	}
	
	private <T extends Artifact> T getApplicationArtifact(Entry entry, Class<T> clazz) {
		for (T potential : entry.getRepository().getArtifacts(clazz)) {
			if (potential.getId().startsWith(entry.getId().replaceAll("^([^.]+\\.).*", "$1"))) {
				return potential;
			}
		}
		return null;
	}
	
	// don't really need _any_ config information
	private void createWebApplication(Entry entry) {
		try {
			AsynchronousRemoteServer remote = MainController.getInstance().getAsynchronousRemoteServer();
			
			List<Integer> currentPorts = new ArrayList<Integer>();
			HTTPServerArtifact server = null;
			// we want to check if there is already an http server in your application that we can use
			for (HTTPServerArtifact potential : entry.getRepository().getArtifacts(HTTPServerArtifact.class)) {
				if (potential.getConfig().getPort() != null) {
					currentPorts.add(potential.getConfig().getPort());
				}
				if (potential.getConfig().isEnabled() && potential.getId().startsWith(entry.getId().replaceAll("^([^.]+\\.).*", "$1"))) {
					server = potential;
				}
			}
			if (server == null) {
				int port = 8080;
				// get the first available port
				while (currentPorts.indexOf(port) >= 0) {
					port++;
				}
				RepositoryEntry serverEntry = ((RepositoryEntry) entry).createNode("server", new HTTPServerManager(), true);
				server = new HTTPServerArtifact(serverEntry.getId(), serverEntry.getContainer(), entry.getRepository());
				// build http server
				server.getConfig().setPort(port);
				server.getConfig().setEnabled(true);
				new HTTPServerManager().save(serverEntry, server);
				// start remote reload
				remote.reload(server.getId());
			}
			
			VirtualHostArtifact host = getApplicationArtifact(entry, VirtualHostArtifact.class);
			if (host == null) {
				// build virtual host
				RepositoryEntry hostEntry = ((RepositoryEntry) entry).createNode("host", new VirtualHostManager(), true);
				host = new VirtualHostArtifact(hostEntry.getId(), hostEntry.getContainer(), hostEntry.getRepository());
				host.getConfig().setServer(server);
				new VirtualHostManager().save(hostEntry, host);
				remote.reload(host.getId());
			}
			
			// build virtual host
			RepositoryEntry componentEntry = ((RepositoryEntry) entry).createNode("api", new WebComponentManager(), true);
			WebComponent component = new WebComponent(componentEntry.getId(), componentEntry.getContainer(), componentEntry.getRepository());
			component.getConfig().setPath("/api/otr");
			new WebComponentManager().save(componentEntry, component);
			remote.reload(component.getId());

			// swagger
			RepositoryEntry swaggerEntry = ((RepositoryEntry) entry).createNode("swagger", new SwaggerProviderManager(), true);
			SwaggerProvider swagger = new SwaggerProvider(swaggerEntry.getId(), swaggerEntry.getContainer(), swaggerEntry.getRepository());
			new SwaggerProviderManager().save(swaggerEntry, swagger);
			remote.reload(swagger.getId());
			
			// check if there are already applications on the host, if so, we can't take their paths
			List<String> paths = new ArrayList<String>();
			for (WebApplication existing : entry.getRepository().getArtifacts(WebApplication.class)) {
				if (host.equals(existing.getConfig().getVirtualHost())) {
					paths.add(existing.getServerPath());
				}
			}
			
			// now we want to add configuration for the jdbc pool as well
			JDBCPoolArtifact jdbc = getApplicationArtifact(entry, JDBCPoolArtifact.class);
			if (jdbc == null) {
				// build virtual host
				RepositoryEntry jdbcEntry = ((RepositoryEntry) entry).createNode("jdbc", new JDBCPoolManager(), true);
				jdbc = new JDBCPoolArtifact(jdbcEntry.getId(), jdbcEntry.getContainer(), jdbcEntry.getRepository());
				
				/**
				 * jdbc:h2:[file:][<path>]<databaseName>
			     * jdbc:h2:~/test
			     * jdbc:h2:file:/data/sample
				 * jdbc:h2:file:C:/data/sample (Windows only)
				 */
				String property = System.getProperty("user.home", ".");
				jdbc.getConfig().setJdbcUrl("jdbc:h2:file:" + property.replace("\\", "/") + "/" + entry.getName());
				// no extensible generics in place?
				Object dialect = H2Dialect.class;
				jdbc.getConfig().setDialect((Class<SQLDialect>) dialect);
				jdbc.getConfig().setDriverClassName("org.h2.Driver");
				jdbc.getConfig().setAutoCommit(false);
				jdbc.getConfig().setTranslationGet((DefinedService) entry.getRepository().resolve("nabu.cms.core.providers.translation.jdbc.get"));
				jdbc.getConfig().setTranslationSet((DefinedService) entry.getRepository().resolve("nabu.cms.core.providers.translation.jdbc.set"));
			}
			// autosync all collection-named complex types
			if (jdbc.getConfig().getManagedTypes() == null) {
				jdbc.getConfig().setManagedTypes(new ArrayList<DefinedType>());
			}
			Map<String, DefinedType> definedTypeNames = new HashMap<String, DefinedType>();
			for (DefinedType managed : jdbc.getConfig().getManagedTypes()) {
				definedTypeNames.put(managed.getId(), managed);
			}
			for (ComplexType potential : entry.getRepository().getArtifacts(ComplexType.class)) {
				if (!(potential instanceof DefinedType)) {
					continue;
				}
				String collectionName = ValueUtils.getValue(CollectionNameProperty.getInstance(), potential.getProperties());
				if (collectionName != null) {
					String id = ((DefinedType) potential).getId();
					if (definedTypeNames.containsKey(id)) {
						continue;
					}
					definedTypeNames.put(id, (DefinedType) potential);
					jdbc.getConfig().getManagedTypes().add((DefinedType) potential);
				}
			}
			// if we have both the model and model version of something, remove the emodel
			for (String id : definedTypeNames.keySet()) {
				if (id.contains(".emodel.") && definedTypeNames.containsKey(id.replace(".emodel.", ".model."))) {
					jdbc.getConfig().getManagedTypes().remove(definedTypeNames.get(id));
				}
			}
			new JDBCPoolManager().save((ResourceEntry) entry.getRepository().getEntry(jdbc.getId()), jdbc);
			// we will do a synchronous reload of the jdbc pool because we want to sync the datatypes, which is a server-side operation
			MainController.getInstance().getServer().getRemote().reload(jdbc.getId());
			// make sure we sync ddls
			GenerateDatabaseScriptContextMenu.synchronizeManagedTypes(jdbc);
			
			// application
			RepositoryEntry applicationEntry = ((RepositoryEntry) entry).createNode("application", new WebApplicationManager(), true);
			WebApplication application = new WebApplication(applicationEntry.getId(), applicationEntry.getContainer(), applicationEntry.getRepository());
			application.getConfig().setVirtualHost(host);
			String path = "/";
			while (paths.contains(path)) {
				if (path.equals("/")) {
					path += entry.getName();
				}
				// this will generate 1, 11, 111 instead of 1,2,3 but the edge case is rare enough that it doesn't matter?
				else {
					path += "1";
				}
			}
			application.getConfig().setPath(path);
			application.getConfig().setWebFragments(new ArrayList<WebFragment>(Arrays.asList(component, swagger)));
			
			// update the cms configuration to have the correct JDBC
			ComplexContent configuration = application.getConfigurationFor(".*", (ComplexType) DefinedTypeResolverFactory.getInstance().getResolver().resolve("nabu.cms.core.configuration"));
			if (configuration == null) {
				configuration = ((ComplexType) DefinedTypeResolverFactory.getInstance().getResolver().resolve("nabu.cms.core.configuration")).newInstance();
			}
			configuration.set("connectionId", jdbc.getId());
			application.putConfiguration(configuration, null, false);
			
			new WebApplicationManager().save(applicationEntry, application);
			
			// fix the page builder template, this will already fix a lot of stuff
			ManageableContainer<?> publicDirectory = (ManageableContainer<?>) ResourceUtils.mkdirs(applicationEntry.getContainer(), EAIResourceRepository.PUBLIC);
			ManageableContainer<?> privateDirectory = (ManageableContainer<?>) ResourceUtils.mkdirs(applicationEntry.getContainer(), EAIResourceRepository.PRIVATE);
			WebComponentContextMenu.copyPageWithCms(applicationEntry, publicDirectory, privateDirectory);
			
			remote.reload(application.getId());
			MainController.getInstance().getRepositoryBrowser().refresh();
		}
		catch (Exception e) {
			MainController.getInstance().notify(e);
		}
	}
}
