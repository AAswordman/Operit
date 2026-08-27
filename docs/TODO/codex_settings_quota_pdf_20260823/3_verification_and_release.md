# Verification And Release

## Checks

- Inspect the complete diff for Codex-only branching and credential redaction.
- Add unit coverage for usage payload windows, absent primary data, PDF content
  mapping, and Responses input conversion.
- Update the Codex protocol document with the usage route and PDF contract.
- Record the final commit and remote builder job result here.

## Release

Use `build.md` and trigger `POST /api/build_current_release` after the worktree
contains the intended changes. Poll `/api/status`, `/api/jobs`, and `/api/log`
until the job finishes, then record the artifact and checksum.

## Release Record

- Commit: `85d35444d`
- Action: `build_release`
- Status: `success`
- Artifact: `operit-release-fix_provider-logos-token-statistics-main-85d35444.apk`
- Size: `403019347` bytes
- SHA-256: `445026543617cbc62c244c8bb4f0b875a6ee0c6e594b1fa67acfbaaab0371083`
- Signed download: `https://69.33.213.123:8443/download/shared/operit-release-fix_provider-logos-token-statistics-main-85d35444.apk?e=1788063937&s=419d37c42a38f9e500f98dba7f1193344c9f5adb68d5051bcecf6f47892292cc`

## Completion

[DONE]

## Latest Fix Release

- Commit: `720b7bb84`
- Action: `build_release`
- Status: `success`
- Artifact: `operit-release-fix_provider-logos-token-statistics-main-720b7bb8.apk`
- Size: `403027539` bytes
- SHA-256: `d0400cc1d40446d0ebc63f4b642daa662ab8eb5d0178f638f1efbaddba745e8f`
- Signed download: `https://69.33.213.123:8443/download/shared/operit-release-fix_provider-logos-token-statistics-main-720b7bb8.apk?e=1788066458&s=c3ed8e65a3dc2141adfda4db2b49976de91e92934dd283f10169ec52d90180a2`
