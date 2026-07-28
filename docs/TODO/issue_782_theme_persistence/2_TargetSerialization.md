# Target Serialization

## Previous Behavior

The settings editor starts a coroutine for the global preference update and schedules another coroutine for the scoped snapshot. Target activation can run between them.

## Change

Use one coordinator for prompt transitions, target-bound saves, and target-bound resets. The coordinator validates the captured target while holding the same mutex used for activation, then performs the shared update and scoped snapshot synchronously.

This intermediate design was replaced by the explicit editor session and complete target snapshot commit documented in `../issue_782_theme_editor_drafts/`. The coordinator now serializes transitions and target writes without exposing the removed immediate-save validation API.

## Expected Result

An edit belongs only to the target that initiated it. Events from a page that is no longer active do not write into the new target.

[DONE]
