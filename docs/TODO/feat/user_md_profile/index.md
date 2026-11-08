---
title: User Markdown Profile
fork: https://github.com/luojiaping/Operit
branch: agent/user-md-profile
status: complete
---

# User Markdown Profile

## Current state

The released application stores six structured user fields inside preference profiles. The same profile identifiers also select ObjectBox memory databases and can be bound to character cards. This couples the human user's identity, assistant roles, and memory isolation.

## Intent

Replace structured user preferences with one private `user.md` document. Keep multiple assistant roles and migrate the old profile identifiers into memory spaces so existing memories and character-card bindings remain valid.

## Expected result

- One editable and previewable `user.md` is the only user-profile source injected into prompts
- Character cards continue to define assistant personas
- Existing profile-backed memory databases become named memory spaces without moving their ObjectBox data
- The active legacy profile initializes `user.md`; other legacy profiles are preserved in a Markdown archive
- Legacy structured preference runtime code is removed after the one-time migration

## Scope

1. [Storage and migration](1_StorageAndMigration.md) — DONE
2. [Prompt and tools](2_PromptAndTools.md) — DONE
3. [Markdown settings UI](3_MarkdownSettingsUi.md) — DONE
4. [Memory-space cleanup](4_MemorySpaceCleanup.md) — DONE
5. Static source and call-site validation without compilation, build, or tests — DONE
