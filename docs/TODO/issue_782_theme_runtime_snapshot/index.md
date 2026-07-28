---
title: Issue 782 Theme Runtime Snapshot
fork: https://github.com/AAswordman/Operit
branch: review/pr-825-theme-persistence
status: implementation_complete
---

# Issue 782 Theme Runtime Snapshot

## Current State

The scoped character-card and group theme snapshots are persisted correctly, but Android Compose still reads theme values from shared global preference keys. Activating a card or group therefore copies its snapshot into that global projection before the UI can render it.

## Intent

Use the active prompt's scoped `ThemePreferenceSnapshot` directly for Android runtime rendering. Keep the existing scoped key format and legacy migration into the default character scope, but remove every runtime projection write and read.

## Expected Result

- Active prompt changes update Compose from that target's scoped snapshot
- Theme edits write only the selected target's scoped values
- Scoped values cannot be copied into global keys through the theme-copy API
- Legacy global values are read only by the one-time migration into the default character scope

## Scope

1. [Scoped snapshot data flow](1_ScopedSnapshotDataFlow.md)
2. [Compose runtime consumers](2_ComposeRuntimeConsumers.md)
3. [Static verification](3_StaticVerification.md)

[DONE]
