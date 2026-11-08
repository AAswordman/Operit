# Dual-Source Market Registration

## Old Implementation

After choosing a local artifact, publication always created or updated an asset in the current user's `OperitForge` repository. The market asset table already had GitHub reference columns, but the publish mutation, persistence layer and projections did not retain them.

## Change

The publish page now offers its source choice after local artifact selection:

- Upload the current local artifact through `OperitForge`
- Reference an asset from an author-maintained GitHub Release

For a referenced asset, the Android client loads Releases from the entered repository, lets the author choose a Release and asset, downloads that asset, and requires it to match the local file before market registration. The Worker still validates the canonical GitHub Release and its author, then persists and emits the owner, repository and Release tag.

## Verification

- The Worker accepts the same asset contract for both publication paths.
- `market_assets` receives `gh_owner`, `gh_repo` and `gh_release_tag`.
- Entry and asset-detail projections expose the stored GitHub reference.
- The author guide and the Sandbox Package development skill describe manual Release creation and Operit-side market registration.

Status: [DONE] Code and static reference checks are complete. Test execution was not run because it was not requested.
