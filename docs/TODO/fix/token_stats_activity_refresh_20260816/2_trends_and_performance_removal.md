# 2. Three Themed Trends And Performance Removal

## Previous State

The trends section shows cost, request, token, and performance cards. Timing
values are captured by the token tracker, persisted in token usage records,
aggregated by Room queries, and rendered by the performance card.

## Intended Change

- Retain cost, request, and token trends as compact independent cards.
- Bind their surfaces and accents to primary, secondary, and tertiary Material
  theme roles.
- Remove performance timing collection, token-record columns, Room migration
  columns, DAO projections, aggregation models, UI state, resources, and dead
  formatting code.

## Expected State

Only three themed trend cards remain. Token statistics no longer store or query
TTFT and generation-duration values. Existing chat-domain timing remains owned by
the chat data model and is not copied into token statistics.

## Completion

[DONE] Cost, request, and token trends use independent Material primary, secondary,
and tertiary card styles. The performance path was removed from tracker capture,
Room schema creation, SQL aggregation, query models, UI, resources, tests, and
documentation.
