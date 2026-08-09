# Room and ObjectBox recovery

Status: post-release hardening in progress

## Old implementation

Room is opened without a preflight integrity check. Its optional backup copies live database files
after a checkpoint but does not validate the result. ObjectBox stores are opened directly and raw
snapshot export may copy `data.mdb` while it is active.

## Change

Check Room before its singleton is built, checkpoint WAL while no Room instance exists, and maintain
two verified closed-state database copies. Validate each ObjectBox profile on first open and maintain
closed-state copies through `ObjectBoxManager`.

## Acceptance

- A corrupt Room database is quarantined before any replacement
- A Room primary path or sidecar replaced by a directory is recursively quarantined before removal
- An ObjectBox data path or database directory with the wrong file type is quarantined before replacement
- An ObjectBox lock path replaced by a directory is recursively quarantined before removal
- A missing Room or ObjectBox primary file is restored when a verified slot exists
- Valid ObjectBox slot metadata discovers a profile even when its live directory and preference index are both missing
- The first Room creation publishes a verified slot before returning the singleton
- ObjectBox remnants without a verified slot are quarantined instead of becoming an empty database
- Deleting a profile first moves recovery slots out of discovery into quarantine, then removes live data
- Every Room recovery copy passes `quick_check`
- ObjectBox recovery is isolated per memory-space identifier
- Physical Room and ObjectBox preflight completes before memory-space and character reference repair
- Live Room and ObjectBox files are never promoted as recovery copies
- Manual Room replacement holds the singleton monitor from close through replacement
- Raw snapshot payloads are validated for both Room and ObjectBox before replacement starts
- Raw snapshots containing an ObjectBox directory without `data.mdb` are rejected before replacement
- Active ObjectBox stores publish a fully validated stable copy 15 seconds after the latest change, with bounded exponential retry after checkpoint failure
- Invalid profile IDs and non-directory profile paths are quarantined without being opened

## 2026-08-10 physical-corruption regression

Device evidence exposed two validation-boundary defects in the released implementation. Android's
default SQLite corruption handler could delete a corrupt Room live file before the recovery code
copied it to quarantine. ObjectBox could report an invalid MDBX file as a plain `DbException`, which
left the quarantine and slot-selection branches unreachable.

This correction does not change any published path, storage format, recovery metadata, or event
schema. It also does not add retry behavior. TTS storage is being redesigned separately and is not
part of this correction.

### Additional acceptance

- Room validation installs a non-deleting SQLite corruption handler for live files and slots
- A Room corruption signal remains corruption even when the final open exception has another type
- A corrupt Room live file is copied byte-for-byte to quarantine before a verified slot replaces it
- Without a verified Room slot, the corrupt live file remains present and the preservation event is recorded
- An operational Room open failure remains an error and does not trigger replacement
- An invalid Room slot remains available for diagnosis and does not prevent checking the other slot
- ObjectBox recognizes only `FileCorruptException` and the documented MDBX content-corruption codes
- ObjectBox version, locking, access, capacity, and I/O failures do not trigger replacement
- A corrupt ObjectBox live file is copied byte-for-byte to quarantine before a verified slot replaces it
- Without a verified ObjectBox slot, the corrupt live file remains present and the preservation event is recorded

[IN PROGRESS]
