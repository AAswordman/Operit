# Script Market Origin

## Previous State

Published standalone JavaScript packages are minified but do not carry marketplace provenance, and their import result has no source notice.

## Change

Write the encoded marketplace origin into the existing script `METADATA` block during direct-upload minification. The script author, runtime package ID, and selected version form the origin. Read and validate this metadata when a standalone script is imported.

## Expected Result

Standalone scripts receive the same provenance protection without executing ToolPkg-only registration APIs.

[DONE]
