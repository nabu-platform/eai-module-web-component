package be.nabu.eai.module.web.component;

import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import be.nabu.eai.api.Advanced;
import be.nabu.eai.api.Comment;
import be.nabu.eai.module.web.application.WebFragment;
import be.nabu.eai.module.web.application.api.TargetAudience;
import be.nabu.eai.repository.jaxb.ArtifactXMLAdapter;
import be.nabu.libs.types.api.DefinedType;

@XmlRootElement(name = "webModule")
@XmlType(propOrder = { "path", "configurationType", "webFragments", "audience" })
public class WebComponentConfiguration {
	private String path;
	private List<WebFragment> webFragments;
	private List<DefinedType> configurationType;
	private TargetAudience audience;
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
	@Advanced
	@Comment(title = "Set the main audience for this component, this can be used for some automatic actions")
	public TargetAudience getAudience() {
		return audience;
	}
	public void setAudience(TargetAudience audience) {
		this.audience = audience;
	}
}
