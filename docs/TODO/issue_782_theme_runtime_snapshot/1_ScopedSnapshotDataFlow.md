# Scoped Snapshot Data Flow

## Previous Behavior

`UserPreferencesManager` copied the active character or group theme prefix into global keys. `OperitTheme` and chat surfaces observed those global keys, so the projection was a second mutable representation of the active target theme.

## Change

- Add an active-prompt snapshot flow in `ActivePromptManager`.
- Read the target prefix through `observeThemePreferenceSnapshot`.
- Remove the character and group projection-switch APIs.
- Remove the obsolete global theme Flow accessors from `UserPreferencesManager`.
- Keep theme mutations, draft commits, resets, avatars, and chat titles scoped to their prompt target.
- Require every snapshot reader and theme writer to receive a non-empty target prefix. `copyThemeValues` can clone one scoped prefix to another or migrate legacy global values into the default character prefix, but cannot write to global keys.

## Expected Result

There is one persisted source of truth for each character-card or group theme. The active prompt selects which source Compose observes; it does not create a copied active theme.

[DONE]
