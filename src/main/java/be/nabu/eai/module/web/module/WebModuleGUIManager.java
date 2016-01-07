package be.nabu.eai.module.web.module;

import java.io.IOException;
import java.util.List;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.managers.base.BaseJAXBGUIManager;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;

public class WebModuleGUIManager extends BaseJAXBGUIManager<WebModuleConfiguration, WebModule>{

	public WebModuleGUIManager() {
		super("Web Module", WebModule.class, new WebModuleManager(), WebModuleConfiguration.class);
	}

	@Override
	protected List<Property<?>> getCreateProperties() {
		return null;
	}

	@Override
	protected WebModule newInstance(MainController controller, RepositoryEntry entry, Value<?>... values) throws IOException {
		return new WebModule(entry.getId(), entry.getContainer(), entry.getRepository());
	}

	@Override
	public String getCategory() {
		return "Web";
	}
	
}
