# Bundled Theme Artifacts

This directory contains only release artifacts that must ship inside the APK.

`operit-default-v2.otheme` is copied byte-for-byte from [`luojiaping/operit-theme-default` v2.0.0](https://github.com/luojiaping/operit-theme-default/releases).

- Package ID: `operit.default`
- Version: `2.0.0`
- Schema: V2 (`operit-theme.json` schemaVersion 2)
- SHA-256: `ee571d8bcb4d814624c62c84f60a55ffdc37eb7296cd561ca2017ba9b2b566ad`
- Release asset: `operit-default-2.0.0.otheme`

The package source, manifest and release workflow live only in the external repository. Update this artifact only after publishing a new upstream Release and verifying its SHA-256, then update the lock in `ThemePackageDefaultV2`.

`operit.cyber_grid` is intentionally absent. Its standalone release artifacts live in [`luojiaping/operit-theme-cyber-grid`](https://github.com/luojiaping/operit-theme-cyber-grid/releases) and users import them through the Themes screen.
