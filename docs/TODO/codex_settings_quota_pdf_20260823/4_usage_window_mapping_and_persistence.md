# Usage Window Mapping And Persistence

## Problem

The WHAM usage response uses `primary_window` and `secondary_window` as
response slots. Those slots do not guarantee five-hour or seven-day semantics.
The settings UI also kept the result only in Compose state keyed by model
configuration, so changing configurations discarded the last successful query.

## Correction

- Classify windows by `limit_window_seconds`: `18000` for five hours and
  `604800` for seven days.
- Show the remaining percentage as `剩余 X%` or `% remaining`; the API field
  `used_percent` is converted to `100 - used_percent` before display.
- Keep the five-hour row when its window is absent, without drawing a progress
  bar for missing data.
- Store one latest snapshot per authenticated Codex account in the dedicated
  `codex_usage_preferences` DataStore.
- Keep the stored snapshot visible across model configuration changes and keep
  it visible when a later manual refresh fails.

## Completion

[DONE]
