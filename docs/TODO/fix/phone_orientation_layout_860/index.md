---
fork_repository: https://github.com/luojiaping/Operit.git
upstream: https://github.com/AAswordman/Operit.git
base: f83e69cba8e28ac78a35cffc4f1dae4acb118911
issue: https://github.com/AAswordman/Operit/issues/860
status: completed
---

# Phone Orientation Layout

## Original State

`MainActivity` creates its localized base context from a full copy of the startup `Configuration`. The copy becomes a persistent context override, so it can retain the startup window width and orientation after the activity handles a configuration change.

## Intent

Use a sparse locale-only configuration override. Window size, orientation, density, and other runtime configuration fields must continue to come from Android's current activity configuration.

## Scope

- `LocaleUtils`, `OperitApplication`, and `MainActivity` locale context creation
- Android instrumentation coverage for sparse locale overrides
- This TODO directory

## Preserved Behavior

The existing 600dp responsive navigation rule remains unchanged. Wide windows, including split-screen and foldable states, continue to select the permanent sidebar when their current width meets that threshold.

## Steps

1. [Sparse Locale Override](./1_SparseLocaleOverride.md)

## Completion

- Replaced complete locale context copies with a shared sparse override
- Added Android instrumentation coverage for inherited orientation and window dimensions
- Did not run Gradle tasks because no build or test command was requested

[DONE]
