---
Fork: local workspace
Status: complete
---

# Incremental Private Publisher Projection

The synchronous publish path currently rebuilds every entry in a publisher's private
R2 shard. A publisher with many entries exceeds Cloudflare's per-invocation API
request limit before the publish response is returned.

This work keeps the existing shard JSON contract and full-build path. Normal entry
mutations will identify the changed entry and update only that entry summary.

Scope:

- Private publisher projection scopes and dirty plans
- Private publisher incremental renderer
- Publish, update, review, moderation, and state-change callers
- Worker tests and market API documentation

Verification completed on 2026-07-28:

- `pnpm test`: 54 passing tests
- Production publish probe: 200 response in about 4 seconds
- Publisher shard materialization: 1629ms for publisher `gh_66207760`
- Deployed Worker version: `1d10b78f-072c-4181-999d-19b6e7da7490`
- Temporary D1 objects, R2 shard entry, and GitHub Release removed
