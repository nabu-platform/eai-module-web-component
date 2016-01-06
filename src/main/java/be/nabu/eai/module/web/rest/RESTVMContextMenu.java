package be.nabu.eai.module.web.rest;

import java.io.IOException;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.MenuItem;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.api.EntryContextMenuProvider;
import be.nabu.eai.module.authorization.vm.VMAuthorizationService;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.libs.services.api.DefinedService;

public class RESTVMContextMenu implements EntryContextMenuProvider {

	@Override
	public MenuItem getContext(Entry entry) {
		if (entry.isNode() && RESTVMService.class.isAssignableFrom(entry.getNode().getArtifactClass()) && entry.isEditable()) {
			try {
				final RESTVMService service = (RESTVMService) entry.getNode().getArtifact();
				if (service.getArtifact("security") == null) {
					MenuItem item = new MenuItem("Add Security");
					item.setGraphic(MainController.loadGraphic("add.png"));
					item.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
						@Override
						public void handle(ActionEvent arg0) {
							service.addArtifact("security", new VMAuthorizationService((DefinedService) service.getArtifact("implementation")), service.getConfiguration(service.getArtifact("implementation")));
							try {
								new RESTVMManager().save((ResourceEntry) entry, service);
							}
							catch (IOException e) {
								throw new RuntimeException(e);
							}
						}
					});
					return item;
				}
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return null;
	}

}
