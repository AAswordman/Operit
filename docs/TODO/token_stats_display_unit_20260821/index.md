---
fork_repository: https://github.com/luojiaping/Operit.git
working_branch: fix-some-bug
---

# Token Statistics Display Unit

## Background

Token statistics currently use one automatic compact formatter. Large values
are readable in millions, but the main overview and peak values can be easier
to compare in billions.

## Intent

Let the two large Token values act as invisible M/B switch entry points while
keeping one display unit across the statistics page. Do not add another visible
row to Statistics Settings.

## Scope

- Persist the selected display unit in the existing statistics DataStore.
- Apply the selected unit to Token summaries, cards, charts, activity details,
  rankings, composition rows, configuration details, and detail dialogs.
- Keep currency, request counts, percentages, database records, and Room
  migrations unchanged.
- Preserve compact K/raw formatting for values below the million scale.
- Add pure formatter coverage for M/B values and unit toggling.

## Steps

1. [DONE] Add the display-unit model, preference storage, and ViewModel state.
2. [DONE] Connect the two hidden click entry points and all Token displays.
3. [DONE] Add formatter tests and review the final diff.
4. [DONE] Push the change and build the Release APK through the builder API.
