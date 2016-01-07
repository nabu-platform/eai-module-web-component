package be.nabu.eai.module.web.module;

import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import be.nabu.eai.repository.artifacts.web.WebFragment;
import be.nabu.eai.repository.jaxb.ArtifactXMLAdapter;

@XmlRootElement(name = "webModule")
@XmlType(propOrder = { "path", "webFragments" })
public class WebModuleConfiguration {
	private String path;
	private List<WebFragment> webFragments;
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
}
