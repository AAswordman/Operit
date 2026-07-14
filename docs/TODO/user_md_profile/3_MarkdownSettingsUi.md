# Markdown settings UI

Status: DONE

Replace profile selection, questionnaires, category locks, and onboarding with a single document screen.

The screen provides:

- A compact document toolbar for edit, preview, dirty state, and explicit save
- A low-contrast, monospace Markdown editor with localized empty-state guidance
- Character count and limit inside the editor status bar
- Unsaved-change confirmation
- Reset-to-template confirmation
- Low-frequency reset and archive actions in an overflow menu
- Read-only access to the legacy profile archive in a large bottom sheet

An untouched instructional template is normalized to an empty document so an empty profile is not
injected into the system prompt. Guidance remains presentation-only and is never stored in user.md.
