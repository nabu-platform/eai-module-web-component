package be.nabu.eai.module.web.component;

import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import be.nabu.eai.module.web.application.WebFragment;
import be.nabu.eai.repository.jaxb.ArtifactXMLAdapter;
import be.nabu.libs.types.api.DefinedType;

@XmlRootElement(name = "webModule")
@XmlType(propOrder = { "path", "configurationType", "webFragments" })
public class WebComponentConfiguration {
	private String path;
	private List<WebFragment> webFragments;
	private List<DefinedType> configurationType;
	public String getPath() {
		return path;
	}
	public void setPath(String path) {
		this.path = path;
	}
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	public List<WebFragment> getWebFragments() {
		return webFragments;
	}
	public void setWebFragments(List<WebFragment> webFragments) {
		this.webFragments = webFragments;
	}
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	public List<DefinedType> getConfigurationType() {
		return configurationType;
	}
	public void setConfigurationType(List<DefinedType> configurationType) {
		this.configurationType = configurationType;
	}	
}
