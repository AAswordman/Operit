# 6. Data Layer And Request Integration

## Previous State

Provider usage is normalized into a large ledger pipeline with identities, stable
event UUIDs, spool fencing, recovery, price snapshots, and cleanup operations.

## Intended Change

- Retain provider usage normalization and `Long` token counts.
- Insert a compact event directly through a repository when a provider request ends.
- Add only the two final Room entities, a dedicated statistics preferences store,
  focused DAO methods, and the required schema migration. Do not create intermediate
  version-21 tables.
- Store `provider` and `model` separately. Use `configId` only for new
  requests where the application actually knows the configuration.
- Import existing DataStore counters, model prices, and exchange rate once,
  then remove every token-statistics DataStore key.
- Give each imported cumulative total a stable nullable `importKey`; repeated initialization
  replaces that row instead of duplicating it if the process stops between Room and
  Preferences commits.
- Record only `importedAtMs` in the dedicated statistics preferences.
- Restore normal application backup and restore behavior.

## Expected State

The request path has one understandable statistics write and no filesystem spool or
cross-component lifecycle coordinator.
