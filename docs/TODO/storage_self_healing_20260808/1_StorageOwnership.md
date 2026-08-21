# Storage ownership

Status: completed

## Old implementation

Each feature declares its own `preferencesDataStore` delegate. The same file name can therefore be
declared more than once, and storage ownership is not coordinated across the main, crash, and repair
processes.

## Change

Create one process registry for Preferences DataStore and one file lease shared by the main and
repair processes. Migrate every existing delegate to the registry without changing its file name.
Remove the unused `api_settings` declaration from `ModelConfigManager`.

## Acceptance

- Each Preferences file name resolves to one active instance per process
- Registry declarations contain no duplicate names
- The main process holds the storage lease for its lifetime
- Repair writes fail clearly while that lease is held

[DONE]
