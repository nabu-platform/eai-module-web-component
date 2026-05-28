package be.nabu.eai.module.web.component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import be.nabu.eai.module.web.application.WebFragment;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.CreatableArtifactFragmentManager;
import be.nabu.eai.repository.api.DynamicArtifactFragmentManager;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.impl.BaseNodeMetadataArtifactFragmentManager;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.resources.ResourceReadableContainer;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.resources.ResourceWritableContainer;
import be.nabu.libs.resources.api.ManageableContainer;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.api.WritableResource;
import be.nabu.libs.validator.api.Validation;
import be.nabu.libs.validator.api.ValidationMessage;
import be.nabu.utils.io.IOUtils;

public class WebComponentArtifactFragmentManager extends BaseNodeMetadataArtifactFragmentManager<WebComponent> implements CreatableArtifactFragmentManager<WebComponent>, DynamicArtifactFragmentManager<WebComponent> {

	private static final String WEB_COMPONENT_FRAGMENT_PATH = "web-component.xml";
	private static final String ARTIFACT_RESOURCE_PATH = "web-module.xml";
	private static final String CONTENT_TYPE = "application/xml";
	private static final String ARTIFACT_TYPE = "webComponent";
	private static final String ARTIFACT_CATEGORY = "web";
	private static final String GUIDELINES_PATH = "/guidelines/web-component.md";
	private static final List<String> HIDDEN_FIELDS = Arrays.asList("configurationType", "audience");
	private static final List<String> RETAINED_FIELDS = Arrays.asList("configurationType");

	@Override
	public Entry createArtifact(Entry parent, String name) {
		try {
			RepositoryEntry entry = ((RepositoryEntry) parent).createNode(name, new WebComponentManager(), true);
			WebComponent artifact = new WebComponent(entry.getId(), entry.getContainer(), entry.getRepository());
			new WebComponentManager().save(entry, artifact);
			return entry;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public List<ArtifactFragment> listFragments(final WebComponent artifact) {
		List<ArtifactFragment> fragments = new ArrayList<ArtifactFragment>(getSharedFragments(artifact));
		final Entry entry = EAIResourceRepository.getInstance().getEntry(artifact.getId());
		final boolean editable = entry instanceof ResourceEntry;
		fragments.add(new ArtifactFragment() {
			@Override
			public boolean isEditable() {
				return editable;
			}

			@Override
			public boolean isRemovable() {
				return false;
			}

			@Override
			public String getPath() {
				return WEB_COMPONENT_FRAGMENT_PATH;
			}

			@Override
			public String getContent() {
				try {
					ByteArrayOutputStream output = new ByteArrayOutputStream();
					artifact.marshal(artifact.getConfig(), output);
					return filterFragmentXml(new String(output.toByteArray(), StandardCharsets.UTF_8));
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}

			@Override
			public String getContentType() {
				return CONTENT_TYPE;
			}

			@Override
			public String getArtifactId() {
				return artifact.getId();
			}

			@Override
			public String getFragmentType() {
				return ARTIFACT_TYPE;
			}

			@Override
			public Map<String, String> getProperties() {
				return new LinkedHashMap<String, String>();
			}

			@Override
			public Long getLastModified() {
				return getFragmentLastModified(artifact.getId(), ARTIFACT_RESOURCE_PATH);
			}
		});
		if (editable) {
			ResourceContainer<?> container = ((ResourceEntry) entry).getContainer();
			addResourceFragments(artifact, fragments, (ResourceContainer<?>) container.getChild(EAIResourceRepository.PUBLIC), EAIResourceRepository.PUBLIC, true);
			addResourceFragments(artifact, fragments, (ResourceContainer<?>) container.getChild(EAIResourceRepository.PRIVATE), EAIResourceRepository.PRIVATE, true);
		}
		return fragments;
	}

	@Override
	public List<Validation<?>> updateFragment(WebComponent artifact, String path, String oldContent, String newContent) {
		if (!WEB_COMPONENT_FRAGMENT_PATH.equals(path)) {
			if (path.startsWith(EAIResourceRepository.PUBLIC + "/") || path.startsWith(EAIResourceRepository.PRIVATE + "/")) {
				return updateResourceFragment(artifact, path, newContent);
			}
			return super.updateFragment(artifact, path, oldContent, newContent);
		}
		ResourceEntry entry = (ResourceEntry) EAIResourceRepository.getInstance().getEntry(artifact.getId());
		List<Validation<?>> validations = new ArrayList<Validation<?>>();
		try {
			String mergedContent = mergeRetainedFields(artifact, newContent);
			WebComponent candidate = new WebComponent(artifact.getId(), entry.getContainer(), entry.getRepository());
			candidate.setConfig(candidate.unmarshal(new ByteArrayInputStream(mergedContent.getBytes(StandardCharsets.UTF_8))));
			validateWebFragments(candidate.getConfig(), validations);
			if (!hasErrors(validations)) {
				validations.addAll(new WebComponentManager().save(entry, candidate));
			}
			if (!hasErrors(validations)) {
				artifact.setConfig(candidate.getConfig());
			}
		}
		catch (Exception e) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, e.getMessage() == null ? e.getClass().getName() : e.getMessage()));
		}
		return validations;
	}

	@Override
	public List<Validation<?>> createFragment(WebComponent artifact, String path, String initialContent) {
		List<Validation<?>> validations = new ArrayList<Validation<?>>();
		if (!isCreatableResourcePath(path)) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "Creating fragments is only supported in 'public/' and 'private/'"));
			return validations;
		}
		Entry entry = EAIResourceRepository.getInstance().getEntry(artifact.getId());
		if (!(entry instanceof ResourceEntry)) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "Creating resource fragments requires a resource-backed web component"));
			return validations;
		}
		try {
			Resource resource = ResourceUtils.resolve(((ResourceEntry) entry).getContainer(), path);
			if (resource != null) {
				validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "Fragment '" + path + "' already exists"));
				return validations;
			}
			WritableResource writable = createWritableResource((ResourceEntry) entry, path);
			try (OutputStream output = IOUtils.toOutputStream(new ResourceWritableContainer(writable))) {
				output.write((initialContent == null ? "" : initialContent).getBytes(StandardCharsets.UTF_8));
			}
		}
		catch (Exception e) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, e.getMessage() == null ? e.getClass().getName() : e.getMessage()));
		}
		return validations;
	}

	@Override
	public List<Validation<?>> deleteFragment(WebComponent artifact, String path) {
		List<Validation<?>> validations = new ArrayList<Validation<?>>();
		if (!isManagedResourcePath(path)) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "Deleting fragments is only supported in 'public/' and 'private/'"));
			return validations;
		}
		Entry entry = EAIResourceRepository.getInstance().getEntry(artifact.getId());
		if (!(entry instanceof ResourceEntry)) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "Deleting resource fragments requires a resource-backed web component"));
			return validations;
		}
		try {
			int separator = path.lastIndexOf('/');
			String parentPath = separator < 0 ? null : path.substring(0, separator);
			String name = separator < 0 ? path : path.substring(separator + 1);
			ResourceContainer<?> parent = parentPath == null ? ((ResourceEntry) entry).getContainer() : (ResourceContainer<?>) ResourceUtils.resolve(((ResourceEntry) entry).getContainer(), parentPath);
			if (!(parent instanceof ManageableContainer)) {
				validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "Parent for fragment '" + path + "' is not manageable"));
				return validations;
			}
			if (parent.getChild(name) == null) {
				validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "Fragment '" + path + "' does not exist"));
				return validations;
			}
			((ManageableContainer<?>) parent).delete(name);
		}
		catch (Exception e) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, e.getMessage() == null ? e.getClass().getName() : e.getMessage()));
		}
		return validations;
	}

	@Override
	public String getGuidelines(List<String> fragmentTypes) {
		List<String> filtered = new ArrayList<String>();
		if (fragmentTypes == null || fragmentTypes.isEmpty() || fragmentTypes.contains(ARTIFACT_TYPE) || fragmentTypes.contains(WEB_COMPONENT_FRAGMENT_PATH)) {
			String guidelines = EAIRepositoryUtils.loadCachedClasspathResource(WebComponentArtifactFragmentManager.class, GUIDELINES_PATH);
			if (guidelines != null) {
				filtered.add(guidelines);
			}
			filtered.add(super.getGuidelines(Arrays.asList("metadata")));
		}
		return filtered.isEmpty() ? null : String.join("\n\n", filtered);
	}

	@Override
	public Class<WebComponent> getArtifactClass() {
		return WebComponent.class;
	}

	@Override
	public String getArtifactType() {
		return ARTIFACT_TYPE;
	}

	@Override
	public String getArtifactCategory() {
		return ARTIFACT_CATEGORY;
	}

	private List<Validation<?>> updateResourceFragment(WebComponent artifact, String path, String newContent) {
		List<Validation<?>> validations = new ArrayList<Validation<?>>();
		Entry entry = EAIResourceRepository.getInstance().getEntry(artifact.getId());
		if (!(entry instanceof ResourceEntry)) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "Updating resource fragments requires a resource-backed web component"));
			return validations;
		}
		try {
			Resource resource = ResourceUtils.resolve(((ResourceEntry) entry).getContainer(), path);
			if (!(resource instanceof WritableResource)) {
				validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "Fragment '" + path + "' is not writable"));
				return validations;
			}
			try (OutputStream output = IOUtils.toOutputStream(new ResourceWritableContainer((WritableResource) resource))) {
				output.write(newContent.getBytes(StandardCharsets.UTF_8));
			}
		}
		catch (Exception e) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, e.getMessage() == null ? e.getClass().getName() : e.getMessage()));
		}
		return validations;
	}

	private WritableResource createWritableResource(ResourceEntry entry, String path) throws Exception {
		int separator = path.lastIndexOf('/');
		String parentPath = separator < 0 ? null : path.substring(0, separator);
		String name = separator < 0 ? path : path.substring(separator + 1);
		ResourceContainer<?> parent = parentPath == null ? entry.getContainer() : ResourceUtils.mkdirs(entry.getContainer(), parentPath);
		if (!(parent instanceof ManageableContainer)) {
			throw new IllegalStateException("Parent for fragment '" + path + "' is not manageable");
		}
		Resource created = ((ManageableContainer<?>) parent).create(name, null);
		if (!(created instanceof WritableResource)) {
			throw new IllegalStateException("Fragment '" + path + "' is not writable");
		}
		return (WritableResource) created;
	}

	private boolean isCreatableResourcePath(String path) {
		return isManagedResourcePath(path) && !path.endsWith("/");
	}

	private boolean isManagedResourcePath(String path) {
		return path != null
			&& (path.startsWith(EAIResourceRepository.PUBLIC + "/") || path.startsWith(EAIResourceRepository.PRIVATE + "/"))
			&& path.length() > EAIResourceRepository.PUBLIC.length() + 1;
	}

	private void validateWebFragments(WebComponentConfiguration configuration, List<Validation<?>> validations) {
		if (configuration.getWebFragments() == null) {
			return;
		}
		for (WebFragment fragment : configuration.getWebFragments()) {
			if (fragment == null) {
				continue;
			}
			if (!(fragment instanceof WebFragment)) {
				validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "Configured web fragment '" + fragment.getId() + "' does not implement " + WebFragment.class.getName()));
			}
		}
	}

	private boolean hasErrors(List<Validation<?>> validations) {
		for (Validation<?> validation : validations) {
			if (validation != null && validation.getSeverity() == ValidationMessage.Severity.ERROR) {
				return true;
			}
		}
		return false;
	}

	private String filterFragmentXml(String content) throws Exception {
		Document document = parseDocument(content);
		Element root = document.getDocumentElement();
		for (String field : HIDDEN_FIELDS) {
			removeDirectChild(root, field);
		}
		return toXml(document);
	}

	private String mergeRetainedFields(WebComponent artifact, String content) throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		artifact.marshal(artifact.getConfig(), output);
		Document current = parseDocument(new String(output.toByteArray(), StandardCharsets.UTF_8));
		Document updated = parseDocument(content);
		Element currentRoot = current.getDocumentElement();
		Element updatedRoot = updated.getDocumentElement();
		for (String field : HIDDEN_FIELDS) {
			removeDirectChild(updatedRoot, field);
		}
		for (String field : RETAINED_FIELDS) {
			Element existing = getDirectChild(currentRoot, field);
			if (existing != null) {
				updatedRoot.appendChild(updated.importNode(existing, true));
			}
		}
		return toXml(updated);
	}

	private Document parseDocument(String content) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		return factory.newDocumentBuilder().parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
	}

	private Element getDirectChild(Element parent, String name) {
		NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child instanceof Element) {
				Element element = (Element) child;
				String childName = element.getLocalName() == null ? element.getNodeName() : element.getLocalName();
				if (name.equals(childName)) {
					return element;
				}
			}
		}
		return null;
	}

	private void removeDirectChild(Element parent, String name) {
		Element child = getDirectChild(parent, name);
		while (child != null) {
			parent.removeChild(child);
			child = getDirectChild(parent, name);
		}
	}

	private String toXml(Document document) throws Exception {
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		transformer.transform(new DOMSource(document), new StreamResult(output));
		return new String(output.toByteArray(), StandardCharsets.UTF_8);
	}

	private void addResourceFragments(WebComponent artifact, List<ArtifactFragment> fragments, ResourceContainer<?> container, String prefix, boolean editable) {
		if (container == null) {
			return;
		}
		for (Resource child : container) {
			if (child instanceof ResourceContainer) {
				addResourceFragments(artifact, fragments, (ResourceContainer<?>) child, prefix + "/" + child.getName(), editable);
			}
			else if (child instanceof ReadableResource) {
				fragments.add(new ResourceFragment(artifact, prefix + "/" + child.getName(), editable));
			}
		}
	}

	private class ResourceFragment implements ArtifactFragment {

		private final WebComponent artifact;
		private final String path;
		private final boolean editable;

		private ResourceFragment(WebComponent artifact, String path, boolean editable) {
			this.artifact = artifact;
			this.path = path;
			this.editable = editable;
		}

		@Override
		public boolean isEditable() {
			return editable;
		}

		@Override
		public boolean isRemovable() {
			return false;
		}

		@Override
		public String getPath() {
			return path;
		}

		@Override
		public String getContent() {
			Entry entry = EAIResourceRepository.getInstance().getEntry(artifact.getId());
			if (!(entry instanceof ResourceEntry)) {
				throw new RuntimeException("Could not resolve resource-backed entry for: " + artifact.getId());
			}
			try {
				Resource resource = ResourceUtils.resolve(((ResourceEntry) entry).getContainer(), path);
				if (resource == null) {
					throw new FileNotFoundException("Can not find " + path);
				}
				try (InputStream input = IOUtils.toInputStream(new ResourceReadableContainer((ReadableResource) resource))) {
					ByteArrayOutputStream output = new ByteArrayOutputStream();
					byte[] buffer = new byte[4096];
					int read;
					while ((read = input.read(buffer)) >= 0) {
						output.write(buffer, 0, read);
					}
					return new String(output.toByteArray(), StandardCharsets.UTF_8);
				}
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public String getContentType() {
			return null;
		}

		@Override
		public String getArtifactId() {
			return artifact.getId();
		}

		@Override
		public String getFragmentType() {
			return "resource";
		}

		@Override
		public Map<String, String> getProperties() {
			return new LinkedHashMap<String, String>();
		}

		@Override
		public Long getLastModified() {
			return getFragmentLastModified(artifact.getId(), path);
		}
	}
}
