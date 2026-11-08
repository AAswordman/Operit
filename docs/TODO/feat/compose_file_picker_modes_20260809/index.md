---
fork: https://github.com/tuxKOH/Operit
branch: feat/expand-compose-file-picker
---

# Compose File Picker Modes

The just-merged photo picker API has not been released. Replace its single
`photo` mode with one uncomplicated `picker` field that selects the full
supported input source.

Scope:

- Compose DSL picker request parsing and Activity Result launchers
- TypeScript declarations and ToolPkg developer documentation
- focused request parsing tests

Expected result:

- `picker` accepts `document`, `image`, `video`, `media`, `directory`, or `camera`
- document keeps MIME filtering and optional persistent URI access
- document, visual media, and camera return staged file paths
- directory returns its selected URI without an invalid staged-file path
- the unreleased `photo` mode is removed completely

Steps:

- [Picker mode implementation](1_PickerModeImplementation.md)
