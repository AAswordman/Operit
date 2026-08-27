---
fork: https://github.com/AAswordman/Operit.git
status: complete
---

# ToolPkg Logo Support

## Current State

ToolPkg containers do not expose a package logo. The package manager renders the
generic Apps icon, while the market list and detail header render a title-derived
avatar. The model provider logo loader already contains AndroidSVG and bitmap
scaling code, but it is tied to APK assets and provider identifiers.

## Intent

Add an optional package logo resource that travels inside a ToolPkg archive. The
same resource is rendered locally from the package cache. Market responses may
provide an optional `logoUrl` for display, but the client does not upload or
host logo files.

Existing ToolPkg archives and existing market entries remain valid. The new
manifest field is optional and `schema_version` remains unchanged.

## Manifest Contract

```json
{
  "logo": "plugin_logo",
  "resources": [
    {
      "key": "plugin_logo",
      "path": "resources/logo.svg",
      "mime": "image/svg+xml"
    }
  ]
}
```

`logo` is a resource key. Supported static formats are SVG, PNG, JPEG and WebP.

## Scope

- ToolPkg manifest parsing, cache access and package-manager rendering
- Generic SVG and bitmap logo rendering shared with provider logos
- Artifact publication logo extraction from `manifest.logo`
- Market list and detail rendering
- ToolPkg format documentation and a small example resource
- Static review only in this change; no build or test command is run by default
- The publish screen reads and previews only the logo already declared by the
  selected ToolPkg manifest.

Android client implementation is complete. The market Worker/API remains an
external dependency because it is not part of this repository.
