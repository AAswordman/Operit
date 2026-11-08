---
title: Issue 782 Theme Editor Drafts
fork: https://github.com/luojiaping/Operit
branch: fix/issue-782-character-theme-persistence
status: implementation_complete
---

# Issue 782 Theme Editor Drafts

## Current State

The published theme editor binds every control to the active chat prompt. Controls write the shared Android theme projection immediately, then separately copy it into the active target prefix. This makes editing an inactive target impossible and allows individual controls to persist partial state before the user intentionally saves the theme.

WebChat also reads the active prompt when it receives a theme request for any chat, so a non-active chat can receive another chat's role theme.

## Intent

Make the editor switch the active prompt and its saved theme without switching chat history, retain target-scoped changes in memory, and persist the complete target state through one save action. Keep the released card and group prefix keys, preserve global display identity and recent colors, and make WebChat resolve the requested chat binding.

## Expected Result

- The editor can activate the default role, any character card, or any group and immediately apply its saved theme without changing chat history
- Role-bound edits remain in a target draft until the user saves or discards them
- A saved target replaces its complete visual theme scope in one DataStore transaction
- Reset removes visual theme values without removing the target's AI avatar or chat title
- WebChat returns the requested chat's resolved theme rather than the active prompt's theme

## Status

The initial implementation passed the remote Nightly build. UI compaction, active-prompt switching, and removal of the internal compatibility layer are complete in source. The refinement itself has not been rebuilt. Local Gradle, lint, unit tests, and builds remain excluded unless explicitly requested.

## Scope

1. [Theme scope contract](1_ThemeScopeContract.md)
2. [Editor target drafts](2_EditorTargetDrafts.md)
3. [WebChat theme binding](3_WebChatThemeBinding.md)
4. [Route leave guard](4_RouteLeaveGuard.md)
5. [Verification](5_Verification.md)
6. [Compact target switch and cleanup](6_CompactTargetSwitchAndCleanup.md)

[DONE]
