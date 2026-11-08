# Publish Origin

## Previous State

ToolPkg archive minification reads the manifest and minifies each JavaScript file. No publish-time marker is written.

## Change

Create an ASCII JSON payload from the primary manifest identity and append an XOR-encoded `ToolPkg._m` call only to the primary entry before minification.

## Expected Result

The published primary entry carries marketplace, ToolPkg ID, version, and author data without exposing readable metadata in the source.

[DONE]
