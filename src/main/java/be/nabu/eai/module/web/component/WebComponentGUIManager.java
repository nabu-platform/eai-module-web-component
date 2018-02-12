package be.nabu.eai.module.web.component;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Side;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.managers.base.BaseArtifactGUIInstance;
import be.nabu.eai.developer.managers.base.BaseJAXBGUIManager;
import be.nabu.eai.module.web.application.WebApplicationGUIInstance;
import be.nabu.eai.module.web.application.WebApplicationGUIManager;
import be.nabu.eai.module.web.application.WebApplicationGUIManager.EditingTab;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.resources.VirtualContainer;
import be.nabu.libs.resources.api.ManageableContainer;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;

public class WebComponentGUIManager extends BaseJAXBGUIManager<WebComponentConfiguration, WebComponent>{

	private ObjectProperty<EditingTab> editingTab = new SimpleObjectProperty<EditingTab>();
	
	public WebComponentGUIManager() {
		super("Web Component", WebComponent.class, new WebComponentManager(), WebComponentConfiguration.class);
	}

	@Override
	protected List<Property<?>> getCreateProperties() {
		return null;
	}

	@Override
	protected WebComponent newInstance(MainController controller, RepositoryEntry entry, Value<?>... values) throws IOException {
		return new WebComponent(entry.getId(), entry.getContainer(), entry.getRepository());
	}

	@Override
	public String getCategory() {
		return "Web";
	}
	
	@Override
	public void display(MainController controller, AnchorPane pane, final WebComponent artifact) {
		VBox vbox = new VBox();
		AnchorPane anchor = new AnchorPane();
		display(artifact, anchor);
		vbox.getChildren().addAll(anchor);
		ScrollPane scroll = new ScrollPane();
		scroll.setContent(vbox);
		vbox.prefWidthProperty().bind(scroll.widthProperty().subtract(100));
		
		TabPane tabs = new TabPane();
		tabs.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
		tabs.setSide(Side.RIGHT);
		Tab tab = new Tab("Configuration");
		tab.setContent(scroll);
		tab.setClosable(false);
		tabs.getTabs().add(tab);
		try {
			editingTab.set(buildEditingTab(artifact));
			tabs.getTabs().add(editingTab.get().getTab());
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		AnchorPane.setLeftAnchor(tabs, 0d);
		AnchorPane.setRightAnchor(tabs, 0d);
		AnchorPane.setTopAnchor(tabs, 0d);
		AnchorPane.setBottomAnchor(tabs, 0d);
		
		pane.getChildren().add(tabs);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private EditingTab buildEditingTab(final WebComponent artifact) throws IOException, URISyntaxException {
		ResourceContainer<?> publicDirectory = (ResourceContainer<?>) artifact.getDirectory().getChild(EAIResourceRepository.PUBLIC);
		if (publicDirectory == null && artifact.getDirectory() instanceof ManageableContainer) {
			publicDirectory = (ResourceContainer<?>) ((ManageableContainer<?>) artifact.getDirectory()).create(EAIResourceRepository.PUBLIC, Resource.CONTENT_TYPE_DIRECTORY);
		}
		ResourceContainer<?> privateDirectory = (ResourceContainer<?>) artifact.getDirectory().getChild(EAIResourceRepository.PRIVATE);
		if (privateDirectory == null && artifact.getDirectory() instanceof ManageableContainer) {
			privateDirectory = (ResourceContainer<?>) ((ManageableContainer<?>) artifact.getDirectory()).create(EAIResourceRepository.PRIVATE, Resource.CONTENT_TYPE_DIRECTORY);
		}
		VirtualContainer container = new VirtualContainer(null, "web");
		if (publicDirectory != null) {
			container.addChild(publicDirectory.getName(), publicDirectory);
		}
		if (privateDirectory != null) {
			container.addChild(privateDirectory.getName(), privateDirectory);
		}
		return WebApplicationGUIManager.buildEditingTab(artifact.getId(), container);
	}
	
	@Override
	protected BaseArtifactGUIInstance<WebComponent> newGUIInstance(Entry entry) {
		return new WebApplicationGUIInstance<WebComponent>(this, entry, editingTab);
	}
}
