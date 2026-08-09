# Verification and documentation

Status: post-release hardening in progress; Android compilation delegated to origin GitHub Actions

## Change

Add static ownership checks, deterministic corruption fixtures, domain-validator tests, database
integrity tests, and a multi-process repair-lease scenario. Document recovery paths, quarantine
retention, snapshot format, and operational diagnostics for collaborators.

Local compilation, build, and test commands remain disabled. The user authorized an
`assembleDebug` compilation in the origin repository's GitHub Actions workflow after push.

## Acceptance

- Static checks reject direct DataStore creation outside the registry
- Fault fixtures cover every registered Preferences store plus Room and ObjectBox
- Re-running each repair produces no additional mutation
- Recovery reports contain no API keys, tokens, or persisted content

## Recorded result

- Added the storage ownership CI script and its isolated Python unit tests
- Added Android fault fixtures for all 22 Preferences stores, Room, and ObjectBox
- Preferences fault fixtures assert that a checkpointed marker survives physical corruption, a missing live file, and a live path replaced by a directory
- Room fixtures cover recursive quarantine and restoration when the primary database path and a sidecar are replaced by directories
- ObjectBox fixtures cover recovery when `data.mdb` is replaced by a directory
- ObjectBox fixtures cover recursive quarantine when `lock.mdb` is replaced by a directory
- Room and ObjectBox fixtures also cover restoration after the primary database file is missing
- Added idempotence assertions for speech, model, character, and memory-space repair
- Added forward-field preservation while TTS, model, role tool access, role group, and memory-space records require known-field normalization
- Added a role-group assertion that reversed JSON array order is repaired from persisted `orderIndex` without changing the intended member order
- Added per-entry API bookmark salvage and future-field preservation assertions
- Added future functional-mapping and memory-space field preservation assertions
- Future functional-mapping entries now remain intact after a released build normally saves a known mapping
- Added an assertion that recovery-slot discovery restores a missing ObjectBox profile before memory-space index repair
- Added index-only character and ObjectBox-backed memory-space reconstruction assertions
- Raw restore archives the previous recovery epoch before imported data creates new snapshots
- Raw restore keeps existing storage owners open until archive and database validation succeeds
- Raw restore rejects duplicate ZIP entries, incomplete manifest declarations, invalid payload categories, and wrong Room/ObjectBox path types before closing owners
- Raw restore holds a replacement gate through final owner cleanup and refuses ordinary owner reopen during that interval
- Main-process lease and owner-gate acquisition now precede ContentProvider installation; Memory DocumentsProvider remains published while storage-backed calls require `READY` and the lease
- Raw snapshots, Room backup and restore, SQL rescue, and provider mutations share one in-process operation permit
- Added an in-process assertion for replacement-gate owner access
- Added a multi-process DocumentsProvider lease assertion
- Added `docs/doc-src/dev-core/STORAGE_RECOVERY.md`
- Local Gradle compilation and tests are intentionally not run; the authorized Android compilation is executed by the origin repository's GitHub Actions workflow after push

## 2026-08-10 regression verification

- Add a Room assertion that a corrupt source remains available until its byte-identical quarantine copy exists
- Add Room scenarios with and without verified slots, including recovery-event assertions
- Add an ObjectBox classification unit test for content-corruption and operational error codes
- Add ObjectBox scenarios with and without verified slots, preserving a marker entity and the original corrupt payload
- Keep all captured device credentials and local evidence outside the repository and CI artifacts
- Compile the rebased branch with the origin `Android Build` workflow after push

[IN PROGRESS]
