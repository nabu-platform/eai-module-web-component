package be.nabu.eai.module.web.virtual;

import java.util.List;

public class VirtualHostConfiguration {
	private String host;
	private String keyAlias;
	private List<String> aliases;
	public String getHost() {
		return host;
	}
	public void setHost(String host) {
		this.host = host;
	}
	public List<String> getAliases() {
		return aliases;
	}
	public void setAliases(List<String> aliases) {
		this.aliases = aliases;
	}
	public String getKeyAlias() {
		return keyAlias;
	}
	public void setKeyAlias(String keyAlias) {
		this.keyAlias = keyAlias;
	}
}
