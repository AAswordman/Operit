# Startup and repair process

Status: completed

## Old implementation

Character initialization and Room warm-up run without a storage health gate. The repair process can
open Room or mutate files while the main process is alive.

## Change

Run physical Preferences preflight before preference consumers, run domain repair before AI and
character initialization, and require the shared storage lease for repair SQL, restore, and provider
writes. Keep the published recovery activity and provider paths.

## Acceptance

- Storage repair completes before character and TTS consumers start
- Foreground services and persistence workers refuse business startup unless storage is `READY`
- The repair process does not instantiate main-process preference managers
- Provider read descriptors remain compatible
- Provider write descriptors retain the repair lease until close
- Raw snapshots, Room backup and restore, repair SQL, and provider mutations are serialized in process
- A rejected raw snapshot leaves existing main-process storage owners open
- The main process acquires its lease in `attachBaseContext`, before ContentProvider installation
- Startup recovery holds the in-process owner gate from `attachBaseContext` until `READY`
- Memory DocumentsProvider remains published, but storage-backed calls require `READY` and the main lease
- Raw replacement atomically blocks ordinary owner reopen until final cleanup releases the gate
- ObjectBox close or closed-state checkpoint failure aborts replacement before live files are touched
- Raw restore invalidates Room and ObjectBox preflight state before validating imported databases
- Character and group repair reacquire the current DataStore actor after raw replacement
- Pending memory-space deletion markers are replayed before ObjectBox recovery-slot discovery
- Startup failure closes Preferences actors and releases the main-process lease before routing to `:repair`

[DONE]
