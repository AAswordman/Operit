# Candidate Timing

## Previous Behavior

`parsePackageCandidate` produced a result for each script or ToolPkg, but only the outer scan emitted an elapsed time.

## Change

Emit a `PKG: scan candidate finish` entry from `parsePackageCandidate` after every candidate completes or throws.

## Expected Result

Each log entry includes the scan phase, candidate type, file, source path, load-error count, and elapsed milliseconds.

[DONE]
