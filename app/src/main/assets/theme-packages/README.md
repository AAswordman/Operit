# Bundled Theme Artifacts

This directory contains only release artifacts that must ship inside the APK.

`operit-default-v2.otheme` is copied byte-for-byte from the unpublished `operit-theme-default` `2.1.0` source artifact while the V2 frame contract is under active development.

- Package ID: `operit.default`
- Version: `2.1.0`
- Schema: V2 (`operit-theme.json` schemaVersion 2)
- SHA-256: `3ada292d108f11efaaa78e029db307229e2fada18ed15b4bd09a75b8323c8f13`
- Source artifact: `operit-default-2.1.0.otheme`

The package source, manifest and release workflow live only in the external repository. Replace this development artifact with the byte-identical upstream Release archive before publishing, then retain the exact SHA-256 lock in `ThemePackageDefaultV2`.

`operit.cyber_grid` is intentionally absent. Its standalone release artifacts live in [`luojiaping/operit-theme-cyber-grid`](https://github.com/luojiaping/operit-theme-cyber-grid/releases) and users import them through the Themes screen.
