package be.nabu.eai.module.web.component;

import java.io.IOException;
import java.util.List;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.managers.base.BaseJAXBGUIManager;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;

public class WebComponentGUIManager extends BaseJAXBGUIManager<WebComponentConfiguration, WebComponent>{

	public WebComponentGUIManager() {
		super("Web Module", WebComponent.class, new WebComponentManager(), WebComponentConfiguration.class);
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
	
}
