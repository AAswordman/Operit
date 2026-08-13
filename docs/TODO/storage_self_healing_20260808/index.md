---
title: Storage self-healing
fork: https://github.com/luojiaping/Operit
branch: fix-issue-
status: implementation-complete
---

# Storage self-healing

## Current state

The released application stores authoritative state in Preferences DataStore, Room, ObjectBox,
SharedPreferences, and structured private files. This plan applies automatic replacement only to
database-style storage with a stable schema and single owner: Preferences DataStore, Room, and
ObjectBox. SharedPreferences and structured files remain in raw snapshots. DataStore instances are declared independently by
their consumers, no DataStore has a corruption handler, and the repair process can write live files
while the main process still owns them. Several model, character, and speech settings also decode
persisted JSON or enum values without persistently repairing invalid data.

`ModelConfigManager` and `ApiPreferences` both declare `api_settings`. The former declaration is not
currently read, but it violates the required single-owner structure and can become an active second
instance after an otherwise harmless future edit.

## Compatibility

The affected version is released. Keep every existing DataStore file name, Preferences key, Room
database name, ObjectBox profile directory, data-rescue route, DocumentsProvider authority, and raw
snapshot path. Recovery metadata is additive and lives under the private no-backup directory.

DocumentsProvider remains writable, but mutations of protected storage require an exclusive repair
lease. Existing read access remains available while the main process owns storage.

## Expected result

- A damaged Preferences protobuf is quarantined and replaced with the newest verified snapshot
- Invalid model, character, and speech values are repaired atomically and remain repaired on restart
- Room and ObjectBox are checked before use and can restore a verified closed-state snapshot
- The main and repair processes cannot write authoritative storage concurrently
- Startup reaches either a healthy application or the existing data-rescue experience without a crash loop
- Unrecoverable source files are retained in a quarantine directory and named in a recovery report

## Scope

1. [Storage ownership](1_StorageOwnership.md)
2. [Preferences DataStore recovery](2_PreferencesDataStoreRecovery.md)
3. [Domain validation](3_DomainValidation.md)
4. [Room and ObjectBox recovery](4_RoomAndObjectBoxRecovery.md)
5. [Startup and repair process](5_StartupAndRepairProcess.md)
6. [Verification and documentation](6_VerificationAndDocumentation.md)

The 2026-08-10 physical-recovery regression correction is complete. Implementation commit
`41c50f81` passed the origin `Android Build` workflow with `assembleDebug` in
[run 31332997568](https://github.com/luojiaping/Operit/actions/runs/31332997568). The later
independent TTS/STT profile work extends the same recovery contract to `speech_service_profiles`;
its implementation and verification record live in
[`speech_service_profiles_20260810`](../speech_service_profiles_20260810/).

The 2026-08-13 alignment is based on upstream `main` commit `e2fb823e`. It preserves the upstream
Room version 21 token-statistics schema, registers `token_stats_preferences` as the twenty-fourth
recoverable Preferences store, and retains the upstream TTS/STT profile implementation. MNN is
pinned to an explicit verified source revision because a later floating `master` revision broke the
non-vision native link. The earlier run 118 validates only its recorded pre-rebase commit; every
later final pushed SHA requires its own origin `assembleDebug` dispatch with JVM tests disabled.

[DONE]
