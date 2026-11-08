---
title: Memory Space Profile Documents
fork: https://github.com/luojiaping/Operit
branch: main
status: complete
---

# Memory Space Profile Documents

## Current state

Release 1.12.0+5 stores one global private `user.md` document. Memory spaces are already isolated by stable identifiers, but they do not own profile documents. Release +4 stored multiple structured profiles, each with the same identifier as its memory database.

## Intent

Every memory space owns one `user.md`; the active space supplies the document context and receives automatic profile updates. User-facing management is a dedicated User Preferences settings screen, rather than part of the memory library. The released global document is no longer read, injected, copied, or shown.

## Compatibility

- +5 data matches the ordered `legacy-user-profiles.md` archive against the ordered memory-space list, then assigns the root `user.md` to the unique omitted space. This keeps the original ownership even when the active space changed after +5 migration. The source files remain untouched after that one-time migration.
- +4 data migrates each legacy structured profile directly into the matching memory space document while retaining the identifier and ObjectBox database.
- The published `update_user_profile` and `update_user_preferences` tool names continue to write the active memory-space document.

## Scope

1. [Storage and migration](1_StorageAndMigration.md) - [DONE]
2. [Runtime and automatic updates](2_RuntimeAndAutoUpdate.md) - [DONE]
3. [Memory-space configuration UI](3_MemorySpaceConfigurationUi.md) - [SUPERSEDED]
4. [Standalone user-configuration UI](4_StandaloneUserConfigurationUi.md) - [DONE]
5. [Standalone UI polish](5_StandaloneUiPolish.md) - [DONE]
6. Static source and resource validation without compilation, build, or tests - [DONE]
7. [Raw snapshot restore compatibility](6_RawSnapshotRestoreCompatibility.md) - [DONE]
