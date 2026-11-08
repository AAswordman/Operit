---
fork_repository: https://github.com/luojiaping/Operit.git
base_pr: https://github.com/AAswordman/Operit/pull/950
working_branch: fix/token-statistics
---

# Token Statistics Activity Refresh

## Background

The token statistics implementation from PR #950 is not released. Its activity
overview starts below lifetime totals and filters, short heatmap ranges leave the
left side visually empty, and the trend area includes an unused performance view.

## Intent

Move activity controls, four summary values, and the visualization to the top of
the page. Make the visualization a distinct card and render short daily ranges as
a full-width, right-aligned contribution grid. Keep cost, request, and token
trends as compact independently themed cards. Remove performance statistics from
capture through Room storage and UI.

## Scope

- Limit product changes to the token-statistics work introduced by PR #950.
- Keep chat message duration fields in the chat domain, but stop copying them into
  token usage records.
- Do not add a database version transition or compatibility path. The existing
  unreleased v20-to-v21 schema creation is edited to the final schema.
- Do not run local compilation, builds, or tests unless explicitly requested.

## Steps

1. [DONE] [Activity-first layout and contribution grid](1_activity_layout.md)
2. [DONE] [Three themed trends and performance removal](2_trends_and_performance_removal.md)
3. [Verification record](3_verification.md)
