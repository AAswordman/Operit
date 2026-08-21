# Verification and documentation

Status: implementation complete

## Change

Add static ownership checks, deterministic corruption fixtures, domain-validator tests, database
integrity tests, and a multi-process repair-lease scenario. Document recovery paths, quarantine
retention, snapshot format, and operational diagnostics for collaborators.

## Acceptance

- Static checks reject direct DataStore creation outside the registry
- Fault fixtures cover all 23 recoverable Preferences stores plus Room and ObjectBox; the managed-only
  token store has separate actor close/rebind and no-slot assertions
- Re-running each repair produces no additional mutation
- Recovery reports contain no API keys, tokens, or persisted content

## Recorded result

- Added the storage ownership CI script and its isolated Python unit tests
- Added Android fault fixtures for all 23 recoverable Preferences stores, Room, and ObjectBox
- Preferences fault fixtures assert that a checkpointed marker survives physical corruption, a missing live file, and a live path replaced by a directory
- Room fixtures cover recursive quarantine and restoration when the primary database path and a sidecar are replaced by directories
- ObjectBox fixtures cover recovery when `data.mdb` is replaced by a directory
- ObjectBox fixtures cover recursive quarantine when `lock.mdb` is replaced by a directory
- Room and ObjectBox fixtures also cover restoration after the primary database file is missing
- Added idempotence assertions for speech, model, character, and memory-space repair
- Added independent TTS/STT profile migration, field-salvage, idempotence, and physical-recovery assertions
- Kept `token_stats_preferences` lifecycle-managed while excluding it from automatic recovery
- Added forward-field preservation while TTS, model, role tool access, role group, and memory-space records require known-field normalization
- Added a role-group assertion that reversed JSON array order is repaired from persisted `orderIndex` without changing the intended member order
- Added per-entry API bookmark salvage and future-field preservation assertions
- Added future functional-mapping and memory-space field preservation assertions
- Future functional-mapping entries now remain intact after a released build normally saves a known mapping
- Added an assertion that recovery-slot discovery restores a missing ObjectBox profile before memory-space index repair
- Added index-only character and ObjectBox-backed memory-space reconstruction assertions
- Raw restore archives the previous recovery epoch before imported data creates new snapshots
- Raw restore keeps existing storage owners open until archive and database validation succeeds
- Raw restore rejects duplicate ZIP entries, incomplete inventories, missing or unlisted files,
  size/hash mismatches, invalid payload categories, and wrong Room/ObjectBox path types before closing owners
- Raw restore preserves and verifies all five live storage categories before mutation and rolls them
  back in reverse order when replacement or post-restore recovery fails
- Raw restore holds a replacement gate through final owner cleanup and refuses ordinary owner reopen during that interval
- Main-process lease and owner-gate acquisition now precede ContentProvider installation; Memory DocumentsProvider remains published while storage-backed calls require `READY` and the lease
- Raw snapshots, Room backup and restore, SQL rescue, and provider mutations share one in-process operation permit
- Added an in-process assertion for replacement-gate owner access
- Added a multi-process DocumentsProvider lease assertion
- Added `docs/doc-src/dev-core/STORAGE_RECOVERY.md`
- The ownership contract maps 24 catalog entries to one owner each, with 23 recoverable owners and
  one lifecycle-only token-statistics owner
- Room recovery retains the upstream version 21 schema and `MIGRATION_20_21`
- Focused JVM and Android instrumentation fixtures are present but have not been executed for this
  rebased branch
- Build and CI evidence must identify the current implementation commit; historical runs from
  superseded branch histories are not verification for this tree

[DONE]
