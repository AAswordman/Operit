---
fork: https://github.com/AAswordman/Operit.git
status: complete
---

# Third-Party Market Content Updates

## Current State

Contributors can publish a newer version of a public-update entry, but the Android client drops any edited market description and detail before submitting the version.

## Intent

Let a contributor submit only the short description and detail with a new version. Keep those changes pending until the market reviewer approves the specific version. Original-author-only fields remain protected.

## Scope

- Send a nullable, partial entry patch with version publication requests.
- Enable contributor editing of description and detail in repo and artifact update flows.
- Preserve the original-author restriction for title, category, and public-update policy.
- Keep the public entry unchanged until version approval.

## Expected Result

A contributor can describe a meaningful update for review without gaining authority to alter the entry's identity or publication policy.
