---
fork_repository: local workspace
---

# Streaming Markdown Cache Invalidation

## Context

PR 834 coalesces streaming render updates. Stable-node conversion caching still uses only
the parent content length, so a node or child type replacement with unchanged text can keep
the previous UI structure.

## Scope

Invalidate the cache entry for explicit structural mutations in the streaming renderer and
clear prior entries when a new stream starts. Add focused coordinator regression tests.

## Expected Result

Streaming block and inline LaTeX replacements render their final node types even when their
text length is unchanged, without adding full-tree conversion work to normal content appends.
