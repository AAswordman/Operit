# Storage and migration

Status: [DONE]

Store each document in the private application files directory under `memory-space-profiles/<memory-space-id>/user.md`. Writes are atomic and limited to 12,000 characters.

Use a schema marker separate from the released global-document marker.

- A +5 installation matches archive entries to the published memory-space order and restores the root `user.md` to the unique space whose removal leaves that archive sequence. The current active space is not used because it could have changed after +5 wrote the root document.
- A +4 installation reads `profile_list`, `active_profile_id`, and `profile_<id>` records, creates matching memory spaces, writes each structured profile as Markdown, and preserves ObjectBox identifiers.
- Legacy category lock values map to a pending migration state. Automatic profile rewriting remains disabled until the user chooses the new whole-document lock policy.

## Released-format audit

- +4 stored `PreferenceProfile` JSON in `profile_<id>`. Its persisted fields are exactly `id`, `name`, `birthDate`, `gender`, `personality`, `identity`, `occupation`, `aiStyle`, and `isInitialized`; the migration-only `LegacyUserProfile` has the same serialized shape.
- +5 replaced those keys with `memory_space_list`, `active_memory_space_id`, and `memory_space_<id>`, while storing the active profile at `filesDir/user.md` and other migrated +4 profiles in `filesDir/legacy-user-profiles.md` under the separate `user_profile_document` schema marker.
- Direct +4 migration preserves every profile identifier rather than using the display name, so duplicate profile names never select or overwrite one another. It formats profile fields with the same Markdown sections used by the released +5 converter.
- +5 migration matches archive sections against memory-space names in their published list order, then finds the unique omitted space for the root document. This uses the order written by +5 rather than randomizing duplicate names, and remains correct after the user changes active spaces.

The released root `user.md` and `legacy-user-profiles.md` remain on disk. They are read only by the one-time +5 reconstruction migration, are never modified, and are never injected into prompts after this change.

[DONE]
