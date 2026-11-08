# Review And Remote Build Record

## Checks

- [DONE] Inspect the final diff for removed category/status and legacy-import paths
  (`TokenStatCategory` / `TokenStatStatus` / `TokenUsageIdentity` /
  `ReleasedProviderModelKeyDecoder` have no remaining references)
- [DONE] Agent reviews of the cancellation, stream-terminal, Room v21, and
  palette changes; all reported findings fixed and re-verified
- [DONE] Whitespace validation only (`git diff --check`, CRLF warnings only)
- [DONE] Commit and push the finished branch (`9593b12b`,
  `fix(stats): record only successful inference with confirmed usage`)
- [DONE] Dispatch Android Build / assembleDebug in GitHub Actions:
  [run 31975076398](https://github.com/luojiaping/Operit/actions/runs/31975076398)
  completed successfully in 21m8s

## Follow-up: Surface Hierarchy Fix

Flattening `surfaceVariant`/`surfaceContainerHigh/Highest` into
`primaryContainer` removed all layering between chips, calendars, dropdowns,
and the page background. Fixed in `0416adba`
(`fix(stats): restore surface hierarchy and derive page background`):

- Variant and high-container roles return to the secondary family for
  chip/calendar contrast against primary-container cards.
- The page scaffold now uses `pageBackground`, derived by shifting
  `primaryContainer` 6% toward `primary` (deeper in light theme, lighter in
  dark theme), so cards separate from the background without leaving the
  primary family.
- `OutlinedTextField` call sites set explicit primary-family colors via
  `tokenStatsTextFieldColors()` instead of relying on scheme roles.
- Remote verification:
  [run 32006932434](https://github.com/luojiaping/Operit/actions/runs/32006932434)
  completed successfully.

## Follow-up: Neutral Page Background With Colored Cards

The earlier surface hierarchy fix still derived the page background from the
primary family, which left the whole page in one tint. Reworked in `045933b2`
(`fix(stats): color cards while keeping page background neutral`):

- The scaffold uses the app's normal `background`/`onBackground`; the scoped
  theme no longer overrides any neutral surface roles, so the page and standard
  controls stay in the application's own light/dark levels.
- Cards alone carry color: primary cards blend `primaryContainer` 10% toward
  `primary`, secondary cards blend toward `secondary`. Card containers are
  forced opaque, and content colors are recomputed from the final container so
  text stays readable even with extreme custom or dynamic palettes.
- Card accents fall back to the card content color when the theme accent lacks
  contrast; page accents are checked against the page background instead.
- Chips, text buttons, and text fields inside colored cards/dialogs use
  explicit card-matched colors rather than page-level scheme roles.
- Remote verification:
  [run 32037947481](https://github.com/luojiaping/Operit/actions/runs/32037947481)
  completed successfully.

## Follow-up: Neutral And Accent Card Layering (Design Spec)

Implemented the approved UI spec: only data cards carry theme color; utility
surfaces return to the app's neutral surface scale.

- `TokenStatsColors` gains a neutral card set (`surfaceContainerHigh` container
  forced opaque, `onSurface`/`onSurfaceVariant` content, primary accent
  re-checked against the neutral container). The filter bar, configuration
  details, rate settings, and empty/no-data cards use the new
  `TokenStatsNeutralCard`.
- Trend chart cards (cost/requests/tokens) all use the primary family per spec;
  the requests card and its detail dialog no longer use the secondary family.
- Filter chips on neutral surfaces use standard M3 colors; the `useCardColors`
  parameter was removed entirely (this UI version is unreleased, so no
  compatibility shim remains).
- The rate card's `OutlinedTextField` uses default M3 colors on the neutral
  surface; primary-family text field colors remain only inside the
  primary-styled pricing dialog.
- Remote verification:
  [run 32063838307](https://github.com/luojiaping/Operit/actions/runs/32063838307)
  completed successfully.

## Follow-up: Full IA Rebuild Per Design Spec (2026-08-18)

Rebuilt the whole page against the user's design specification. The spec's
orange palette is the theme primary in disguise — every color stays
theme-bound; only structure and color-role placement changed.

- Neutral surface ladder everywhere: cards = `surfaceContainer` with a 1dp
  `outlineVariant` border, icon plinths/tooltips/pills = `surfaceContainerHigh`.
  No card is filled with the primary/secondary family anymore; the primary
  color appears only on key numbers, chart lines/bars, progress fills, the
  selected segment, icons, and tinted action buttons.
- New page order: time control row (segmented Daily/Weekly/Cumulative + date
  range + calendar) → period overview card (big token number, cost, streak
  pills, gradient area chart) → 2×2 metric grid (peak/requests/cache
  rate/output) → activity record (streak pills in header, weekday label
  column, 5-step primary heat ladder) → token composition (3 progress bars) →
  model ranking list with badges and share bars (pie chart deleted) → range
  analysis filter card → trend analysis card with three equal-weight mini
  charts (cost line / requests line / tokens stacked bars, 3-label axis) →
  configuration details rows → statistics settings (currency row + rate row +
  tinted save button).
- Deleted: the four blue/purple stat mini-cards, the lifetime totals big
  table, the model pie chart, the three stacked full-width trend cards, the
  per-card color-matched chip/textfield/button helpers, and all colored
  dialogs (DatePicker and price dialogs return to M3 defaults).
- Data sources, filters, date selection, price editing, and save logic are
  unchanged; range metrics derive from the same aggregates
  (peak = activity daily max, cache rate = cached/total input).
- New strings (zh + en): period overview, token composition, activity title,
  streak badges; also backfilled the previously missing en collapse/show-all.
- Dark mode is primary per the user's instruction; light mode follows
  automatically because every token maps to the theme's own surface ladder.
- First dispatch failed on one missing `valueSelector` argument in the
  overview area-chart call ([run 32075433502](https://github.com/luojiaping/Operit/actions/runs/32075433502));
  fixed in `6e8647cd`.
- Remote verification:
  [run 32076970360](https://github.com/luojiaping/Operit/actions/runs/32076970360)
  completed successfully.

## Follow-up: Targeted UI And Statistics Fixes

- Daily heatmap now uses a Monday-first grid, a wider weekday label column and
  larger cells. The month row, grid, tap detail and less-to-more legend are
  separate vertical rows, so they no longer overlap; month text is constrained
  to the visible canvas and drag inspection rejects gaps/out-of-bounds points.
- Weekly/cumulative views use a chart-specific tap hint instead of referring to
  heatmap cells.
- Token composition now contains only cache read, uncached input and output.
  The standalone `reasoningTokens` field was removed from the entity, Room DAO
  projections, aggregates, query models and unpublished v21 schema directly;
  no database version bump or migration was added. Provider reasoning remains
  transient only so separately billed reasoning can be folded into output.
- Trend analysis now gives cost/request/token their own rounded mini panels;
  selected-date details live inside each panel rather than in separate cards,
  and compact typography preserves the full titles on phone widths.
- Exchange-rate settings use tighter padding and a single title/currency row;
  save/validation behavior is unchanged.
- Model ranking now reflects the current range and active filter. Model filter
  provider identities are retained across range changes, and checkbox clicks
  are handled once before the menu closes.
- Remote verification:
  [run 32162595694](https://github.com/luojiaping/Operit/actions/runs/32162595694)
  completed successfully.

## Local Build Policy

No local compilation, build, or test command is run unless explicitly requested.
