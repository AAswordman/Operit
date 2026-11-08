# Structural Cache Invalidation

## Previous Behavior

`synchronizeRenderNodes` reuses a stable node when its parent content length is unchanged.
Streaming replacements for block LaTeX, inline LaTeX, and removed empty child nodes leave that
length unchanged, so the old stable structure can remain visible.

## Change

Provide a structural-update request that invalidates the cache entry before scheduling the
existing render coordinator. Use it at each structural replacement/removal site and clear the
cache when a new input stream resets the renderer state.

## Expected Result

The cache remains effective for append-only streaming content, while structural mutations are
converted again and propagated to `renderNodes`.

## Verification

`git diff --check` completed without patch errors. Automated tests were not run because the
repository policy requires an explicit user request for build or test commands.

[DONE]
