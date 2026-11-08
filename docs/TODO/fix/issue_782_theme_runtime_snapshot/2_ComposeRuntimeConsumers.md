# Compose Runtime Consumers

## Previous Behavior

Theme rendering, navigation appearance, settings-surface backgrounds, chat backgrounds, message bubbles, cursor-message display, floating fullscreen messages, and independent image-generation Compose views read global `UserPreferencesManager` theme flows.

## Change

- Provide the active scoped snapshot through `LocalThemePreferenceSnapshot` in `OperitTheme`.
- Update the Compose consumers to read their visual values from that local snapshot, including message-stat display flags and settings-surface background treatment.
- Resolve and provide the active snapshot for independent composition roots: the image-generation `ComposeView` and the floating chat window.

## Expected Result

Changing the active prompt changes every Android theme consumer from the same scoped snapshot. No rendering surface relies on a global theme projection.

[DONE]
