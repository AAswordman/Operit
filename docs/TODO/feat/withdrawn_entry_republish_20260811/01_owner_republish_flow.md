# Owner Republish Flow

## Previous behavior

Withdrawing an entry changes its state to `withdrawn`. The management page
only exposes the new-version action for public entries, leaving the owner
without a path to submit a reviewed update.

## Intended behavior

The owner of a withdrawn entry can open the existing new-version flow. The
screen loads the owner's private entry detail and submits the version against
the original entry ID. When that version passes review, the existing Worker
approval mutation sets the entry state to `approved`, allowing the market
projections to list it again.

## Scope

- `UnifiedMarketManageScreen`
- `UnifiedMarketManageViewModel`
- Market Worker review-approval handler verification

## Verification

- Statically verify the withdrawn owner action uses `getMyEntryDetail`
- Statically verify the existing entry ID reaches `publishNewVersion`
- Verify the Worker routes withdrawn-entry approval through `reviewApproveEntry`
- Verify `reviewApproveEntry` writes `approved` to both the entry and version

[DONE] The Android management page now exposes the existing new-version flow to
the owner of a withdrawn entry and loads its private entry detail before
navigation. The Worker already routes approval of a withdrawn entry through
`reviewApproveEntry`, which restores the entry and approved version to
`approved` before rebuilding the relevant projections.
