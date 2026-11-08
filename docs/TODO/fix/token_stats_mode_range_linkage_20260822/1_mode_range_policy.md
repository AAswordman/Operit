# Mode Range Policy

## State Before

`setActivityViewMode` updated `TokenActivityUiState.viewMode` only. Range data,
activity data, and the calendar label continued to use the previous
`TokenStatsTimeRange`.

## State After

The ViewModel derives a range from the current range's inclusive end date. The
weekly range follows the existing Sunday-first activity aggregation. Cumulative
mode uses the earliest timestamped record that matches the active model filter
and ends at the anchor day.

The calendar dialog still accepts custom start and end dates. A custom range
does not rewrite the selected activity mode or get normalized to a day or week.

## Data Contract

The new earliest-date query excludes records without `occurredAtMs`, matching
the existing range and activity queries. The old `time_range_start` and
`time_range_end` preferences remain unchanged. Activity mode uses a separate
DataStore key with Daily as its initial value.
