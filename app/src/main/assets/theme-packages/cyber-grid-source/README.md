# Cyber Grid Source Package

`cyber-grid.otheme` is the bundled reference package generated from this directory.

Rebuild after changing `operit-theme.json` or any file under `assets/`:

```bash
cd app/src/main/assets/theme-packages/cyber-grid-source
zip -X -q -z ../cyber-grid.otheme operit-theme.json ATTRIBUTION.md assets/cyber-nebula.jpg assets/outer-frame.png assets/header-frame.png assets/composer-frame.png
```

Set the ZIP comment to `Operit Theme Package` when prompted. Update each changed asset's SHA-256 and byte size in `operit-theme.json` before rebuilding.

The background is a locally darkened/cropped derivative of NASA Hubble's Ring Nebula (M57). Attribution and source guidance are retained in `ATTRIBUTION.md` and the package manifest.
