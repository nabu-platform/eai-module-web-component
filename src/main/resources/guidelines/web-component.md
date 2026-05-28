# Artifact: webComponent

A web component is a lightweight reusable grouping web fragments. Usually used to combine multiple related rest services together in one webfragment.

## Supported fragments
- `metadata.xml`: repository metadata around the artifact
- `web-component.xml`: the web component configuration
- `public/**`: public resource fragments
- `private/**`: private resource fragments

## Fragment: web-component.xml

Supported fields:
- `path`: base path applied by the component
- `webFragments`: mounted child web fragments

Example:

```
<webModule>
	<path>/admin</path>
	<webFragments>my.project.web.rest.AdminApi</webFragments>
	<webFragments>my.project.web.component.SharedHeader</webFragments>
</webModule>
```

## Resource fragments
Resource fragments can be created, updated and deleted under `public/` and `private/`.
Use these for pages, scripts, resources or other component-backed files.
