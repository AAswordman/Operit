---
fork_repository: local workspace
---

# Package Scan Timing

## Context

Package startup logs report only the total package scan duration. A 26-second scan across 17 ToolPkg containers cannot be attributed to one package from that aggregate entry.

## Scope

Record the phase, file, source path, error count, and elapsed time for every package-scan candidate. The diagnostic must cover both successful and failed candidates without changing package loading behavior.

## Expected Result

The next startup log identifies the exact package file with the largest scan duration.

## Follow-up Scope

Keep extracted built-in ToolPkg caches when an APK update leaves the corresponding asset unchanged. The cache signature must use the individual APK asset ZIP entry metadata rather than whole-APK metadata.
