# Markdown settings UI

Status: DONE

Replace profile selection, questionnaires, category locks, and onboarding with a single document screen.

The screen provides:

- A compact document toolbar for edit, preview, dirty state, and explicit save
- A low-contrast, monospace Markdown editor with localized empty-state guidance
- Theme-aware Markdown source coloring without changing the stored document
- Stable long-press selection and scrolling through coordinated text and scroll state
- Character count and limit inside the editor status bar
- Unsaved-change confirmation
- Reset-to-template confirmation
- Low-frequency reset and archive actions in an overflow menu
- Selectable raw legacy Markdown and one-click whole-archive copy in a large bottom sheet

An untouched instructional template is normalized to an empty document so an empty profile is not
injected into the system prompt. Guidance remains presentation-only and is never stored in user.md.
