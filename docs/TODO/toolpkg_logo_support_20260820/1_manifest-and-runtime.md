# Manifest And Runtime

## Existing

`ToolPkgManifest` validates declared resources but has no package-level logo
reference. The runtime cache already extracts every archive entry, so a declared
logo resource can be read without adding another archive format.

## Change

- Parse optional `manifest.logo` as a resource key.
- Require the key to resolve to a file resource with a supported image MIME or
  extension.
- Carry the resolved resource key and MIME through the ToolPkg runtime and public
  container details.
- Expose a package-manager method that reads the cached logo bytes.

## Compatibility

Archives without `logo` keep their existing generic icon. No manifest version
upgrade is required.

[DONE]
