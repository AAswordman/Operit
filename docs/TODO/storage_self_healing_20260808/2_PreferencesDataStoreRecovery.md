# Preferences DataStore recovery

Status: completed

## Old implementation

A malformed Preferences protobuf propagates `CorruptionException` to every Flow and edit call. Raw
snapshots are manual and are not suitable as an always-available recovery point.

## Change

Create a corruption handler backed by two atomic, checksummed, typed snapshots per store. Preserve a
copy of the unreadable live file before DataStore replaces it. Capture a new snapshot only after a
successful update or an explicit healthy checkpoint.

## Acceptance

- Corrupt protobuf bytes do not escape the DataStore boundary
- A missing live protobuf is restored through DataStore migration when a verified slot exists
- A live protobuf path replaced by a directory is recursively quarantined before migration
- Recovery events are recorded once, only after the DataStore migration commits
- Snapshot checksum and store identity are verified before restoration
- The corruption handler never calls a DataStore API
- Automatic replacement stops when the corrupt source cannot be copied to quarantine
- Both snapshot slots being invalid produces a clean schema state while retaining the source file

[DONE]
