# Remove Release-Body Proof

## Old Implementation

The Android client requests a signed proof from `/market/v2/publish/proof`, appends it as an HTML comment to the GitHub Release body, and then submits the asset reference. The Worker parses and verifies the comment.

## Change

The Worker queries the selected GitHub Release and requires `release.author.id` to equal the authenticated market session's `github_id`. It resolves the selected asset from that Release and uses GitHub's canonical browser download URL.

## Verification

- A publisher-created release is accepted without an Operit marker in its body.
- A release created by another GitHub account is rejected.
- Direct local publication never calls `publishProof` or updates a Release after asset upload.
- The Worker test suite covers accepted and rejected artifact publication.

Status: [DONE] Code and static reference checks are complete. Test execution was not run because it was not requested.
