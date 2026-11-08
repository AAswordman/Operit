# Remove Filters And Historical Imports

## Previous Behavior

Range queries accept model, call-category, and result-status filters. The v20 to
v21 migration copies chat rows into the ledger, and repository initialization
imports legacy cumulative totals.

## Intended Behavior

Range analysis retains model filtering only. The ledger records direct formal
inference facts and preserves historical data through one-time upgrade imports.

## Work

- Delete category and status types, UI controls, strings, query parameters, SQL
  clauses, breakdown queries, entity columns, and indexes.
- Keep the one-time legacy usage-row import and historical conversation copy;
  removing them would discard user-visible statistics during upgrade.
- Keep current pricing settings and their storage because normal-request cost
  calculations still need them.

## Completion

[DONE]

- Category and status types, UI controls, strings, query parameters, SQL
  clauses, entity columns, and indexes are deleted; range analysis keeps model
  filtering only. Historical chat-copy and cumulative usage imports remain as
  one-time upgrade paths.
- Pricing decision: released `api_settings` custom prices and the legacy
  `usd_to_cny_exchange_rate` are migrated once into Room and token-stat
  preferences.
