# 5. Final Storage Design

## Previous State

The unpublished implementation treats token statistics as a durable billing ledger
and adds operational recovery systems around it. Released `main` already stores
conversation token state in Room and lifetime provider/model counters in DataStore.

## Intended Change

- Use `token_usage_records` for completed requests, copied conversation history, and
  imported cumulative counters. The `source` column distinguishes `REQUEST` and
  `CONVERSATION`; imported counters are timestamp-free `REQUEST` rows.
- Use `token_stats_models` for complete model identities, group membership, group
  names, and model-level or configuration-level price overrides.
- Use a dedicated `token_stats_preferences` Preferences DataStore for currency,
  exchange rate, time selection, and `importedAtMs`. These are scalar key/value
  settings and do not justify another SQL table.
- Copy AI messages and all message variants once during the Room migration. Do not
  query the chat tables at runtime after the schema migration.
- Keep new request identities as configuration, provider, and model columns.
- Key model settings by `configId + provider + model`. An empty `configId` represents
  provider/model-wide pricing and an identity without configuration ownership.
- Calculate costs from current settings only.
- Use direct SQL aggregation instead of cached daily or lifetime rollups.

## Expected State

The entire statistics feature uses two tables plus one small preferences file, with
no ledger, spool, backup, or recovery state. Lifetime totals do not double count
copied conversation rows already included in the released counters.
