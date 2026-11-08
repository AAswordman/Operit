---
fork: https://github.com/AAswordman/Operit.git
status: in_progress
---

# Market Release Author Verification

## Current State

Artifact publication had a single direct-upload path through `OperitForge`, while existing author-maintained GitHub Release assets could not be registered through the same flow.

## Intent

GitHub Release bodies belong to plugin authors and must remain ordinary release notes. The market must verify a selected release through GitHub metadata without adding or reading Operit-specific body content.

## Scope

- Remove Android proof requests and Release-body proof mutation.
- Add the source choice after local artifact selection: direct upload or existing GitHub Release asset.
- Verify that the GitHub release creator matches the authenticated market publisher.
- Resolve the canonical GitHub asset URL server-side and retain the existing SHA-256 verification.
- Persist the GitHub owner, repository and release tag with each market asset.
- Explain the author-repository Release workflow in the developer guide and package-development skill.

## Expected Result

Both direct local publishing and registration of an existing GitHub Release use the same market asset contract. A Release page contains only author-provided release content, and the market records its immutable GitHub reference.
