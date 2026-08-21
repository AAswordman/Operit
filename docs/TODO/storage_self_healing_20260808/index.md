---
title: Storage self-healing
fork: https://github.com/luojiaping/Operit
branch: feat/add-database-self-recovery
status: completed
---

# Storage self-healing

## Historical baseline

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

Independent TTS/STT profile storage extends the same recovery contract to
`speech_service_profiles`; its implementation and verification record live in
[`speech_service_profiles_20260810`](../speech_service_profiles_20260810/).

The 2026-08-20 alignment is based on the upstream Room version 21 token-statistics schema. All 24
Preferences DataStore owners remain in one lifecycle registry, while the 23 database-style settings
stores participate in automatic recovery. `token_stats_preferences` is intentionally lifecycle-only:
it is closed and rebound for raw restore, but it has no recovery slot, corruption replacement, startup
preflight, or logical repair. The shared Room file remains recoverable because chat and message data
coexist with the token-statistics tables.

Sensitive model, speech, GitHub, external HTTP, and permission settings now use field-level repair.
An unrelated malformed field cannot erase a valid API key, key pool entry, request header, OAuth
credential, bearer token, or custom `su` command. Raw snapshot format 2 publishes a complete sorted
payload inventory with size and SHA-256 metadata, then preserves and verifies the entire live storage
epoch before replacement. A replacement failure rolls every category back from that quarantine.

[DONE]
