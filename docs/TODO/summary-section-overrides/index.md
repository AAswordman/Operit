---
fork: https://github.com/AAswordman/Operit
---

# Summary Section Overrides

## Current State

Conversation summaries use a fixed prompt. PR #1098 introduced editable summary sections by
searching and replacing section text inside that prompt. When a later section is disabled before
an earlier section is replaced, the replacement loses its end marker and removes following
sections.

## Intent

Keep the four built-in summary sections configurable without changing the default prompt for
users who have no overrides. Build the configured prompt from structured sections so any
combination of enablement, title, and instruction changes has deterministic boundaries.

## Scope

- Add field-level summary-section overrides to the model configuration
- Render the existing context-summary settings with editable sections
- Pass overrides through manual and automatic summary generation
- Replace prompt string slicing with structured prompt generation
- Cover default, combined disable-and-edit, and persistence-diff behavior with unit tests

## Pull Request

Target branch: `dev`
