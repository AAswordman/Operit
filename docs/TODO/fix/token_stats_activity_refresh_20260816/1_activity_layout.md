# 1. Activity-First Layout And Contribution Grid

## Previous State

The activity controls, four summary tiles, and visualization are grouped in a
single range-analysis card after lifetime totals and filters. The daily heatmap
only draws queried dates, so leading calendar positions and short ranges look
empty instead of like a contribution grid.

## Intended Change

- Put the activity controls, summary tiles, and visualization before all other
  token-statistics sections.
- Keep the controls with activity because they define the visualization mode and
  date range.
- Give the visualization its own themed card.
- Fill available short-range width with noninteractive no-history cells before
  real dates, positioning real activity at the right edge.
- Keep zero-token dates inside the selected range distinct from no-history cells.

## Expected State

The first visible statistics content is the activity overview. A short range has
a complete contribution grid with selectable real dates, visible leading cells,
and no artificial activity values.

## Completion

[DONE] The activity section is the first page item. Its controls and four summary
tiles remain above a separate visualization card. Daily ranges now fill available
short-range width with visible, noninteractive no-history cells before real dates.
