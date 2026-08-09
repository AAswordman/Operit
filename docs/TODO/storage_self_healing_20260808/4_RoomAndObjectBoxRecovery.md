# Room and ObjectBox recovery

Status: completed

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

[DONE]
