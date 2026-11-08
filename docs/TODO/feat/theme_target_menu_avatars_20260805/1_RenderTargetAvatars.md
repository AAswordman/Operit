# Render Target Avatars

## Previous Behavior

`ThemeSettingsTargetSelector` passes a generic icon directly to every `DropdownMenuItem`. The selector already receives the card and group identifiers needed to read each target-scoped AI avatar.

## Change

Add a composable menu-leading element that observes the target's avatar flow and draws that image as a circular avatar.

## Expected Result

The target picker is visually consistent with its selected-target summary and with other character and group selectors.
