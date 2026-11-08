---
For_Agent: Fix standalone script publishing metadata placement
---

# Script Metadata Placement

## Previous State

Script importing locates a `METADATA` comment anywhere in the source. The direct-upload publisher instead required the same comment to begin the file before it could write marketplace provenance. A valid importable script with a preceding comment or code therefore failed before upload.

## Change

Locate the existing `METADATA` comment with the same unrestricted placement accepted by script importing. Marketplace provenance replaces that exact comment. Before JavaScript minification, remove the comment from the executable source and prepend it to the minified output so later imports can always discover it.

## Expected Result

Direct publishing accepts every script format that package loading accepts, preserves its metadata and marketplace origin, and still reports a clear error when the source has no `METADATA` comment.

[DONE]
