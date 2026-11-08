# 2. Restore Integrity

## Previous State

`RoomDatabaseRestoreManager` deletes the active database, WAL, and SHM files before
calling `replaceFile`. `replaceFile` can still fail while renaming or copying the
validated temporary database. The exception path then removes temporary files,
leaving no recoverable active database.

## Intended Change

Validate WAL/SHM compatibility before committing the restore marker. Replace each
staged database file using only same-filesystem atomic move with replacement; do not
delete the active target first or copy after a failed move. Preserve the restore
barrier semantics and the replacing marker.

## Expected State

A failed atomic replacement reports failure without deleting the user's previously
active database. A focused regression test injects the final move failure and
verifies that the existing database remains intact. [DONE]

## Statistics Repository Lifecycle

`TokenUsageRepository` survives for the process lifetime, while both Room-only
restore and raw snapshot restore close and replace `AppDatabase`. Retaining a Room
DAO in that repository would leave token queries and request recording attached to
the closed database when a user chooses to restart later.

The repository now uses one process-wide mutex for every Room-backed statistics
operation and for both restore entry points. A restore clears the initialization
state while holding that mutex, then keeps it until the database files have been
replaced. Each operation obtains the current DAO only after that barrier and the
one-time import have completed, so it cannot use a Room instance that a restore is
closing or has closed. [DONE]
