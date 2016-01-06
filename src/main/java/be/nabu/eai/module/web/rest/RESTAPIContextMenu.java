package be.nabu.eai.module.web.rest;

import java.util.ArrayList;
import java.util.List;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.api.EntryContextMenuProvider;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ExtensibleEntry;
import be.nabu.eai.repository.artifacts.web.rest.WebRestArtifact;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.jfx.control.tree.TreeItem;
import be.nabu.libs.services.api.DefinedServiceInterface;
import be.nabu.libs.services.api.ServiceInterface;
import be.nabu.libs.services.vm.PipelineInterfaceProperty;
import be.nabu.libs.services.vm.SimpleVMServiceDefinition;
import be.nabu.libs.services.vm.api.VMService;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.base.ValueImpl;

public class RESTAPIContextMenu implements EntryContextMenuProvider {

	@Override
	public MenuItem getContext(Entry entry) {
		if (entry.isNode() && WebRestArtifact.class.isAssignableFrom(entry.getNode().getArtifactClass()) && entry.isEditable() && entry.getParent() instanceof ExtensibleEntry) {
			try {
				// find all implementations
				List<SimpleVMServiceDefinition> implementations = new ArrayList<SimpleVMServiceDefinition>();
				for (VMService service : entry.getRepository().getArtifacts(VMService.class)) {
					if (service instanceof SimpleVMServiceDefinition) {
						ServiceInterface iface = service.getServiceInterface();
						while (iface != null) {
							if (iface.equals(entry.getNode().getArtifact())) {
								implementations.add((SimpleVMServiceDefinition) service);
							}
							iface = iface.getParent();
						}
					}
				}
				if (implementations.isEmpty()) {
					MenuItem item = new MenuItem("Add Implementation");
					item.setGraphic(MainController.loadGraphic("add.png"));
					item.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
						@Override
						public void handle(ActionEvent arg0) {
							addImplementation(entry, null);
						}
					});
					return item;
				}
				else {
					Menu menu = new Menu("Add Implementation");
					menu.setGraphic(MainController.loadGraphic("add.png"));
					MenuItem empty = new MenuItem("New Service");
					empty.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
						@Override
						public void handle(ActionEvent arg0) {
							addImplementation(entry, null);
						}
					});
					menu.getItems().add(empty);
					for (final SimpleVMServiceDefinition implementation : implementations) {
						MenuItem item = new MenuItem(implementation.getId());
						item.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
							@Override
							public void handle(ActionEvent arg0) {
								addImplementation(entry, implementation);
							}
						});
						menu.getItems().add(item);
					}
					return menu;
				}
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return null;
	}

	private void addImplementation(Entry entry, SimpleVMServiceDefinition implementation) {
		try {
			WebRestArtifact iface = (WebRestArtifact) entry.getNode().getArtifact();
			MainController.getInstance().close(entry.getId());
			iface.forceLoad();
			ExtensibleEntry parent = (ExtensibleEntry) entry.getParent();
			parent.deleteChild(entry.getName(), false);
			RESTVMManager manager = new RESTVMManager();
			final RepositoryEntry newEntry = parent.createNode(entry.getName(), manager);
			RESTVMService newInstance = new RESTVMGUIManager().newInstance(MainController.getInstance(), newEntry);
			newInstance.addArtifact("api", iface, null);
			if (implementation != null) {
				implementation.getPipeline().setProperty(new ValueImpl<DefinedServiceInterface>(PipelineInterfaceProperty.getInstance(), new DefinedServiceInterface() {
					@Override
					public ComplexType getInputDefinition() {
						return iface.getInputDefinition();
					}
					@Override
					public ComplexType getOutputDefinition() {
						return iface.getOutputDefinition();
					}
					@Override
					public ServiceInterface getParent() {
						return iface.getParent();
					}
					@Override
					public String getId() {
						return newEntry.getId() + ":api";
					}
				}));
				MainController.getInstance().close(implementation.getId());
				newInstance.addArtifact("implementation", implementation, newInstance.getConfiguration(newInstance.getArtifact("implementation")));
				Entry serviceEntry = entry.getRepository().getEntry(implementation.getId());
				if (serviceEntry.getParent() instanceof ExtensibleEntry) {
					((ExtensibleEntry) serviceEntry.getParent()).deleteChild(serviceEntry.getName(), false);
					serviceEntry.getParent().refresh(false);
					TreeItem<Entry> resolve = MainController.getInstance().getTree().resolve(serviceEntry.getParent().getId().replace(".", "/"), false);
					if (resolve != null) {
						resolve.refresh();
					}
				}
			}
			manager.save(newEntry, newInstance);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
