# 8. Legacy History And Identity

## Previous State

The compact rewrite imports released counters into a table keyed only by the
combined `providerModel` string. Imported rows are projected with an empty
configuration ID, and model-group changes reduce complete identity IDs back to the
same combined string. Existing messages and message variants are not visible to the
statistics queries.

## Intended Change

- Represent a new request identity as configuration, provider, and model.
- Represent an imported cumulative-counter identity as configuration-unscoped provider and model.
- Store group assignments and price overrides in one model row keyed by the complete
  identity.
- Copy assistant messages and all generated variants during migration for token
  trends and model distribution.
- Keep historical conversation request counts out of time buckets because a saved response can
  aggregate multiple provider calls.
- Include imported DataStore totals in the normal lifetime aggregate.

## Expected State

The model-management UI retains its hierarchy and configuration-level operations.
The statistics UI can show recoverable historical conversation usage without
inventing configuration ownership, request events, or duplicate lifetime totals.

## ToolPkg Released-Key Identity Fix

The released DataStore decoder now accepts each registered ToolPkg `providerId`, its
legacy `TOOLPKG_<providerId>` form, and display name as exact prefixes. This preserves
provider IDs containing underscores without changing runtime statistics identities,
database structure, or UI behavior. It reads with the original prefix and imports with
the registered display identity so the historical total and new requests remain in the
same model entry.

It also preserves released custom-provider totals after a provider is removed or renamed.
Those keys have no registry metadata, so migration decodes the historical provider name
from the first encoded separator instead of failing the entire import. Known ToolPkg IDs
continue to use the longest registered prefix, which keeps underscores in provider IDs
unambiguous.

[DONE]
