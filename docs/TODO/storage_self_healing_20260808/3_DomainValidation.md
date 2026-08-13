# Domain validation

Status: completed

## Old implementation

Some Flows return a temporary default after parsing fails, while other enum and JSON reads throw.
Neither behavior persistently fixes the damaged value. Character and model indexes can also point to
missing records.

## Change

Add idempotent validators for model configuration, functional mapping, memory spaces, character cards
and groups, API settings, speech services, and token statistics settings.
Each validator records the invalid state, applies one atomic repair, and validates the written state
again. Valid unrelated keys remain unchanged.

## Acceptance

- Unknown TTS and STT enum values cannot terminate their Flows
- Invalid speech JSON, regex, rate, and pitch are replaced with valid persisted values
- Character indexes retain reconstructable index-only records, and group indexes contain only valid records
- Character-group member arrays are ordered by their persisted `orderIndex`, then deduplicated and renumbered without changing the user's intended roster order
- Unreadable model and character-group records retain their stable IDs as minimum valid records
- ObjectBox profile directories reconstruct missing memory-space indexes and minimum metadata
- Model lists and functional references cannot expose malformed configuration JSON
- Unknown future fields in TTS, model, role, group, memory-space, API bookmark, and functional mapping records survive an older repair pass when the enclosing record remains usable
- A bad SAF bookmark entry is removed without deleting other valid bookmark objects
- Native MNN and llama.cpp parameters are normalized before they reach local inference engines
- Invalid token-statistics currency, exchange-rate, custom-range, and migration-marker values are
  repaired before the statistics repository reads them

## 2026-08-13 upstream alignment

The upstream token-statistics implementation added `token_stats_preferences` after the original
recovery catalog was completed. It is now the twenty-fourth registered Preferences store. Its
startup validator normalizes an unknown currency to `CNY`, removes a non-finite or non-positive
exchange rate, removes both ends of an incomplete or reversed custom range, and removes a
non-positive migration timestamp. Re-running the validator makes no further change.

The upstream independent TTS/STT profile implementation is also included. The unreleased
`speech_service_profiles` format receives physical DataStore recovery and field-level logical
repair. The released `speech_services_preferences` file remains physically recoverable and is read
only as the one-time migration source until the profile migration marker is committed.

[DONE]
