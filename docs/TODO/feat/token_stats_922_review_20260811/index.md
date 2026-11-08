---
fork_repository: https://github.com/AAswordman/Operit.git
source_pr: https://github.com/AAswordman/Operit/pull/922
working_branch: fix/token-stats-922-review
---

# Token Statistics PR 922 Redesign

## Background

PR #922 introduces useful statistics and model-management UI, but its unpublished
storage design adds a request ledger, spool, recovery generations, quarantine,
cleanup outbox, baseline migration, and token-specific backup coordination. The
implementation is much larger than the product requirement and duplicates behavior
already owned by the application database and normal backup system.

## Intent

Keep the useful UI and provider usage extraction while replacing the unpublished
storage design completely. Use two Room tables for structured token statistics data,
keep scalar UI state in a dedicated Preferences DataStore, and perform aggregation
with SQL. Copy existing messages and every message variant once during migration so
the statistics domain remains self-contained after the schema migration.

## Scope

- Preserve the #922 commit topology through merge commit `663a3a59`.
- Delete every unpublished spool, baseline, quarantine, cleanup, cutoff, generation,
  token-specific restore, and historical-price mechanism.
- Add `token_usage_records` and `token_stats_models`.
- Add a dedicated `token_stats_preferences` file for currency, exchange rate, time
  selection, and the completed-import timestamp.
- Preserve provider, model, and configuration ownership as separate identity
  dimensions instead of flattening them into a `providerModel` assignment.
- Import released DataStore counters once as authoritative upgrade-time lifetime
  totals. Copy messages and all message variants as recoverable historical
  conversation history without adding them to lifetime totals again.
- Keep existing `chats` and `messages` token columns unchanged.
- Query totals, trends, categories, statuses, and activity with SQL.
- Store billing mode and price overrides in structured Room rows.
- Store currency, exchange rate, time selection, and `importedAtMs` in the dedicated
  Preferences DataStore.
- Do not run compilation, builds, or tests without an explicit user request.

## Steps

1. [DONE] [Merge baseline](1_merge_baseline_and_reproduction.md)
2. [DONE] [Restore integrity investigation](2_restore_integrity.md)
3. [Provider capabilities and token types](4_provider_capabilities_and_token_types.md)
4. [Final storage design](5_final_storage_design.md)
5. [Data layer and request integration](6_data_layer_and_request_integration.md)
6. [SQL queries and UI adaptation](7_sql_queries_and_ui.md)
7. [Legacy history and identity](8_legacy_history_and_identity.md)
8. [Verification](3_verification.md)
