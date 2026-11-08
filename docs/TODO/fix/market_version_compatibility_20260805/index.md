---
fork: https://github.com/tuxKOH/Operit.git
---

# Marketplace client-version compatibility

Marketplace entries already publish `minAppVer` and `maxAppVer`, but the two clients applied that metadata inconsistently. Operit 2 accepted incompatible downloads, while the Android client disabled them without an explicit reason.

Scope:

- Require an app-version argument for the Operit 2 market install command and reject incompatible entries before downloading assets.
- Pass the Flutter app version to that command and show the incompatibility reason when an install is blocked.
- Explain the Android disabled-download state with the current version and the violated bound.

## Steps

- [1-install-version-enforcement.md](1-install-version-enforcement.md)
