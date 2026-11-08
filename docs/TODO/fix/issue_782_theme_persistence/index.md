---
title: Issue 782 Character Theme Persistence
fork: https://github.com/luojiaping/Operit
branch: fix/issue-782-character-theme-persistence
status: superseded
---

# Issue 782 Character Theme Persistence

## Current State

Character and group theme snapshots are stored under target-specific preference prefixes. The active Android theme is still projected through shared preference keys. A theme edit writes the shared keys and later copies them to the captured target in a separate coroutine. A target switch between those operations can save one target's projection into another target's snapshot.

This document records the first persistence pass. The editor-session implementation in `../issue_782_theme_editor_drafts/` supersedes its two-stage save path and verification contract.

## Intent

Make target activation and theme writes serial operations. A target without a saved theme must show application defaults and must not retain the previous target's projection. Preserve existing scoped data and migrate legacy global data only when its ownership is unambiguous.

## Expected Result

- Switching from default to A and back preserves A's saved custom theme
- Rapid target changes cannot store another target's projection under A
- Cards without a saved theme use application defaults
- Existing scoped character and group themes remain unchanged

## Scope

1. [Projection and migration](1_ProjectionAndMigration.md)
2. [Target serialization](2_TargetSerialization.md)
3. [Verification](3_Verification.md)

[DONE]
