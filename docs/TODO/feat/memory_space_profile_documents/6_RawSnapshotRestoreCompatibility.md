# Raw Snapshot Restore Compatibility

Status: [DONE]

## Problem

A raw snapshot created by +4 contains the complete `profile_list` payload. Restoring it into a
running newer process can leave both legacy profile keys and an incomplete memory-space list on
disk. Treating the mere presence of a memory-space list as +5 then skips the remaining +4 records.

The restore path also keeps the current process alive until the user chooses to restart. Its
already-open DataStore can write stale in-memory preferences after the backup file is replaced.

## Change

Classify a payload containing `profile_list` as +4 before considering memory-space metadata.
Restore directories as the snapshot's complete state rather than merging them with newer files.
This removes stale migration markers. Restore files atomically and immediately restart after a
successful raw snapshot import, so the next process is the first reader of the recovered DataStore
state.

## Expected Result

The inspected +4 raw snapshot restores its three structured user profiles and their existing
memory-space identifiers instead of leaving only an empty default space.

[DONE]
