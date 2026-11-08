---
fork_repository: https://github.com/luojiaping/Operit.git
working_branch: fix/provider-logos-token-statistics-main
---

# Token Statistics Mode And Range Linkage

## Previous State

The Daily, Weekly, and Cumulative controls changed only the activity
visualization. The date range beside them remained independent, so switching
the activity mode did not change the selected range or the query window.

## Intended Change

- Daily selects the current anchor day.
- Weekly selects the existing Sunday-first calendar week containing the anchor.
- Cumulative selects the filtered history start through the anchor day.
- The date picker remains a free custom range control and keeps its existing
  three-year manual range limit.
- The selected activity mode is stored with the statistics preferences so the
  mode and range remain consistent after re-entering the page.

## Scope

- Token statistics ViewModel and date-range policy.
- Token usage DAO/query service for the earliest timestamped record.
- Token statistics preferences.
- Focused JVM tests and verification notes.

## Completion

[DONE] Mode changes now update the persisted query range, while manual date
selection remains independent of the mode's automatic range policy.
