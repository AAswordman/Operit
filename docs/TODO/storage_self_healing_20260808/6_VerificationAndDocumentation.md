# Verification and documentation

Status: implementation complete; each final upstream-aligned SHA requires origin Android compilation

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
- Added Android fault fixtures for every cataloged Preferences store, Room, and ObjectBox
- Preferences fault fixtures assert that a checkpointed marker survives physical corruption, a missing live file, and a live path replaced by a directory
- Room fixtures cover recursive quarantine and restoration when the primary database path and a sidecar are replaced by directories
- ObjectBox fixtures cover recovery when `data.mdb` is replaced by a directory
- ObjectBox fixtures cover recursive quarantine when `lock.mdb` is replaced by a directory
- Room and ObjectBox fixtures also cover restoration after the primary database file is missing
- Added idempotence assertions for speech, model, character, and memory-space repair
- Added independent TTS/STT profile migration, field-salvage, idempotence, and physical-recovery assertions
- Added token-statistics scalar repair and idempotence assertions for the twenty-fourth Preferences store
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
- Add an ObjectBox assertion that a corrupt newest slot is rejected while an older verified slot restores the marker
- Keep all captured device credentials and local evidence outside the repository and CI artifacts
- Compile the rebased implementation commit with the origin `Android Build` workflow after push

Implementation commit `41c50f81` passed `assembleDebug` in
[run 31332997568](https://github.com/luojiaping/Operit/actions/runs/31332997568), and the workflow
uploaded artifact `operit-android-118`. The dispatch used `run_unit_tests=false`; the added Android
instrumentation tests were not executed.

## 2026-08-13 upstream alignment verification

- Rebased the recovery implementation onto upstream `main` commit `e2fb823e`
- Preserved the upstream Room schema version 21 and `MIGRATION_20_21` token-statistics tables in the
  recovery candidate builder
- Added `token_stats_preferences` to the recovery catalog and startup logical repair
- Retained upstream independent TTS/STT profiles and applied the recovery contract to both the new
  profile store and its released one-time migration source
- Pinned MNN to the explicit revision documented in the Android building guide after a floating
  upstream `master` revision caused an unrelated native link failure
- Re-ran static ownership and conflict-marker inspection after the rebase; 24 catalog entries map to
  24 recoverable owners, `api_settings` has one owner, and direct DataStore factories remain confined
  to the registry
- Did not run local Gradle compilation, JVM unit tests, or Android instrumentation tests
- Documented D01, R01, R02, F03, O01, O02, M01, and F01 cold-start fault-injection checks; the
  device runs remain pending and must not be inferred from compilation or unexecuted fixtures
- Origin Android Build run 122 reached Kotlin compilation and exposed three integration defects:
  invalid DataStore 1.0.0 imports, two unclosed raw-snapshot lambdas, and an incorrectly qualified
  companion JSON-path token type. The defects were corrected before the next dispatch; run 122 did
  not produce an APK and is not build evidence for the corrected commit.

The earlier run 118 validates the pre-rebase implementation commit only. A new origin `Android
Build` dispatch with `assembleDebug` and `run_unit_tests=false` must validate the final pushed SHA;
its run and artifact are intentionally not claimed before that workflow completes.

[DONE]
