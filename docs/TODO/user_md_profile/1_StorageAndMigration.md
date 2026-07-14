# Storage and migration

Status: DONE

The current DataStore profile payload contains both display metadata and structured user fields. Add a private UTF-8 `user.md` repository with atomic writes and a 12,000-character limit.

For schema version 2 migration:

- Convert the active legacy profile into `user.md`
- Export non-active structured profiles into `legacy-user-profiles.md`
- Rewrite legacy profile metadata as memory-space metadata while retaining identifiers
- Preserve the active identifier and every ObjectBox database
- Mark the migration complete only after file and DataStore writes succeed
