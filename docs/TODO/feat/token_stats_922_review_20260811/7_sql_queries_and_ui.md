# 7. SQL Queries And UI Adaptation

## Previous State

The UI depends on an in-memory ledger aggregator, historical versus revalued cost
modes, baseline rows, cutoff rows, quarantine management, and event pagination.

## Intended Change

- Provide SQL projections for lifetime totals, range totals, trend buckets, models,
  categories, statuses, and activity data.
- Treat imported cumulative counters as timestamp-free `REQUEST` rows. Lifetime
  aggregation includes them; time-range SQL excludes them through its timestamp bounds.
- Use copied `CONVERSATION` rows from `messages` and every `message_variants` row as
  the available historical distribution. Treat their exact request count as unknown
  because one saved response can contain multiple provider requests.
- Keep legacy conversation rows out of lifetime addition because the DataStore total
  already includes them.
- Aggregate models by normalized model name and expose individual call configurations
  only when the user expands a model row.
- Keep the statistics, activity, and inline configuration-price editing UI.
- Remove historical-price mode, quarantine, migration, and token-backup controls.
- Present imported cumulative counters in the normal lifetime totals and model
  aggregation. They have no timestamp, so they do not appear in a selected time range
  or trend chart.
- Remove the Token Activity title, its unrelated profile card, and activity-insights
  card while retaining the activity selector, summary metrics, and visualizations.
- Derive cards, typography, charts, and heatmap colors from the
  application `MaterialTheme.colorScheme`; no statistics-specific visual palette remains.

## Expected State

The UI presents the useful #922 statistics against a compact SQL-backed repository,
while released conversation data remains available through the existing chat domain.

## 2026-08-12 Follow-up

[DONE] The imported cumulative counters use the same lifecycle aggregate and model
identity path as all other requests. The activity section no longer owns profile
state, avatar files, or profile controls.

[DONE] The token statistics UI now follows the active application theme for surfaces,
content, accents, charts, heatmap, and model management. It no longer overrides the
local `MaterialTheme` or retains a white/pink statistics-only palette.

## 2026-08-12 Activity And Lifetime Model Layout Follow-up

[DONE] The cumulative-usage card is now the first page section. The activity-insights
card and its hour-based SQL query are removed; the recent/year selector now shares
the activity-mode row.

[DONE] Lifetime model totals use a total-token distribution pie and compact rows.

[DONE] Page-level statistics headings and activity controls start at the shared page
edge. Their data cards use the same edge, with a shared 16dp internal card inset.

## 2026-08-12 Date Range And Currency Follow-up

[DONE] The range filter is a Material date-range calendar. Its calendar icon sits at
the right edge of the daily, weekly, and cumulative activity-mode row; it retains
the existing nearby recent/year activity selector. The query continues to use its
explicit half-open timestamp interval.

[DONE] The visible preset menu, rolling-window selection, automatic preset probing,
and associated preference state are removed. The first view uses the most recent
30 natural days; thereafter the selected calendar range is persisted directly.

[DONE] Display currency is a single CNY/USD dropdown rather than parallel chips.

[DONE] The first cumulative-usage card uses the active theme's primary-container
surface and matching on-primary-container content color. Remaining statistics cards
continue to use the ordinary application surface.

[DONE] The three cumulative metrics use compact single-line values. Cumulative cost
is displayed to two decimal places; detailed prices and other cost views retain
their existing precision.

## 2026-08-13 Unified Statistics Scope

[DONE] The page now has two explicit scopes: cumulative usage and cumulative model
totals always cover all history; the date-range activity, charts, and range model
details share one date range plus model, call-type, and result conditions.

[DONE] Removed the unrelated recent/year activity selector. The activity SQL query
receives the same range and query conditions as range statistics, then applies the
selected models before building daily, weekly, and cumulative views.

[DONE] The visible conditions use labelled values rather than ambiguous standalone
phrases. Currency is shown next to cumulative usage because it changes only money
display, not the records included in a query. The destructive action explicitly
states that it deletes all records in the selected date range.

## 2026-08-13 Compact Controls Follow-up

[DONE] The cumulative-usage heading reserves a fixed-width currency control, so a
narrow screen keeps the heading on one line. The date range calendar uses a compact
in-app heading and single-line selected-range summary instead of Material's oversized
default range headline. Cumulative model rows show the five largest models initially;
the complete list remains available through an explicit expand control, while the pie
continues to represent every model.

[DONE] Token statistics is a read-only history surface. Removed model, date-range,
and all-history usage-record deletion from the UI and the supporting statistics data
APIs. A custom configuration price can be removed directly from its editor because
that action does not discard recorded usage.

## 2026-08-13 Information Hierarchy Follow-up

[DONE] The statistics page now separates lifetime totals, range analysis, trends,
configuration details, and settings with one shared page-section heading style. The range
filters and activity visualization are grouped into one range-analysis section; cards
use only compact internal labels. The lifetime card no longer repeats the applied-rate
hint, which belongs to the dedicated statistics-settings section.

## 2026-08-13 Configuration Details Follow-up

[DONE] Removed model grouping and the separate model/pricing management screens.
The configuration-details list has no model grouping layer: each compact row is one
configuration, identified by its configured name and provider/model. Expanding a row
reveals its token components and the inline price editor for that configuration. A
custom price can be removed from the same editor.
