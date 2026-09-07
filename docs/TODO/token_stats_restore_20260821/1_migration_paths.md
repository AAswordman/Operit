# Migration Paths

The Room migration owns rows that already exist in `messages` and
`message_variants`. The repository importer owns released cumulative counters and
pricing because those values live outside Room. Both paths are one-time operations
and retain data before removing obsolete legacy keys.

The same `20 -> 21` Room migration also owns the schema indexes now declared by
`MessageEntity`: `index_messages_chatId` and `index_messages_chatId_timestamp`.
Released v20 databases can reach this migration without those indexes, so creating
them in the same transaction keeps Room schema validation aligned with the entity.

Legacy counters whose suffix is only a function name, such as `CHAT`, `SUMMARY`, or
`FILE_BINDING`, predate provider/model identities. They are intentionally skipped
during import because they cannot be assigned to a provider and model; the normal
post-import cleanup removes those obsolete keys after the migration succeeds.

[DONE]
