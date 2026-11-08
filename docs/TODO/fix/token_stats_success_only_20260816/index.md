---
fork_repository: https://github.com/luojiaping/Operit.git
working_branch: fix/token-statistics
---

# Token Statistics Success-Only Cleanup

## Background

The unreleased token statistics ledger records failed, cancelled, timed-out, and
test requests. It also persists call categories and result statuses solely for
filters that are no longer part of the product. Cache-write display data is not
portable across provider usage payloads, and the page still depends on theme
roles that are outside the custom primary and secondary palette.

## Intent

Keep only successful formal inference requests that report real usage. Retain
model filtering for the range analysis while deleting call-type and status
classification throughout the implementation. Keep uncached input and cache
read display values, replace cache write display with cache rate, and make the
page use only the active primary and secondary theme families.

## Scope

- The current v20-to-v21 token statistics schema is unpublished. Edit it as the
  final schema and do not introduce another database version transition.
- Import historical chat counters and legacy cumulative token totals once during
  upgrade so users do not lose visible statistics. Timestamp-free cumulative
  rows remain outside dated trend buckets.
- Retain pricing, including per-request pricing and cache-write pricing where a
  provider requires it for billing.
- Released `api_settings` prices and the legacy exchange rate are migrated once
  into the new storage after the Room import completes.
- Do not run local compilation, builds, or tests. Dispatch the Android build in
  GitHub Actions after the completed commit is pushed.

## Steps

1. [DONE] [Record only successful formal inference](1_success_only_recording.md)
2. [DONE] [Remove filters and historical imports](2_remove_filters_and_legacy.md)
3. [DONE] [Replace cache display and standardize theme colors](3_cache_ratio_and_theme.md)
4. [DONE] [Review and remote build record](4_verification.md)
