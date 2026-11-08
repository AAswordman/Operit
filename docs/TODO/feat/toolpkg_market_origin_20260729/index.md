---
For_Agent: Kotlin ToolPkg market-origin propagation
---

# Kotlin ToolPkg Market Origin

## Context

Kotlin ToolPkg publishing applies JavaScript minification but does not embed marketplace origin metadata. Imports therefore cannot identify an Operit marketplace package or show its original author.

## Intended Result

The primary ToolPkg entry receives an encoded Operit marketplace origin marker during publishing. Import registration validates and retains the marker, then reports the marketplace source after a successful external import. Packages without the marker remain importable without a source notice.

## Scope

- `app/src/main/java/com/ai/assistance/operit/util/ToolPkgArtifactMinifier.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsToolPkgRegistration.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsEngine.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/packTool/ToolPkgParser.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/packTool/ToolPkgMainRegistrationScriptParser.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/packTool/PackageManager.kt`
- Focused Kotlin unit tests

## Steps

- [1-publish-origin.md](1-publish-origin.md): encode and inject the primary-entry market origin
- [2-import-origin.md](2-import-origin.md): capture, validate, and retain origin during registration
- [3-import-notice-and-tests.md](3-import-notice-and-tests.md): report the origin after import and verify behavior
- [4-script-origin.md](4-script-origin.md): embed and report provenance for standalone scripts
- [5-script-metadata-placement.md](5-script-metadata-placement.md): accept valid script metadata outside the file header during publishing
- [6-publisher-author.md](6-publisher-author.md): use the authenticated publisher identity for market-origin authors
- [7-import-notice-dialog.md](7-import-notice-dialog.md): display validated marketplace provenance after external import

[DONE]
