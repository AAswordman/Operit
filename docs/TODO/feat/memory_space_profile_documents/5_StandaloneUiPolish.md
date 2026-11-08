# Standalone UI polish

Status: [DONE]

## Problem

The first standalone screen stacked profile actions, two full-width policy rows, tabs, the editor,
and the save action into one uninterrupted column. It did not retain the visual hierarchy or the
state-based Markdown editor from the released +5 screen.

## Revision

- Restore the released +5 editor structure: centered content width, compact tab/save toolbar,
  full-height editor, syntax highlighting, empty placeholder, character count, and document menu.
- Keep the useful part of the +4 profile manager in one compact selector bar.
- Put activation, rename, and deletion in a profile action menu.
- Put automatic-update and whole-document-lock controls in a dedicated policy sheet and persist
  those switches immediately.
- Provide a compact jump action that activates the selected profile's associated memory space and
  opens the memory library directly.

No storage, migration, runtime injection, or memory binding behavior changes in this step.

[DONE]
