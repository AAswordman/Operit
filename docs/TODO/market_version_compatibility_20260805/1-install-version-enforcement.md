# 1. Install-version enforcement

## Previous behaviour

`assistance2` rendered the published version range but did not compare it before downloading. The Kotlin client compared versions, but users only saw a disabled action and a generic warning state.

## Intended behaviour

The Rust market command receives the actual client version and validates the selected entry version before any download begins. Flutter passes its build version and surfaces the same reason in version selection and install errors. Kotlin keeps the blocked action and displays whether the client is below the minimum or above the maximum.

## Completion criteria

- Incompatible Operit 2 market installs fail before network asset download.
- Both clients name the current version and the violated supported version bound.

## Result [DONE]

The Rust command requires the invoking client version and checks the requested market version before resolving any asset. Flutter sends `2.0.0+5`, blocks incompatible selections with the reason, and returns that reason for direct installs. Android keeps the unavailable action and shows a detail-page banner for both lower and upper compatibility violations.
