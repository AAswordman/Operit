---
For_Agent: Remove Android lint execution from GitHub Actions build jobs
---

# CI Android Lint

## Previous State

The Android build workflow exposed a manual lint switch and ran `:app:lintDebug`. PR checks also included the same Gradle lint task, causing builds to fail on lint findings after compilation succeeded.

## Change

Remove Android lint task execution and its manual workflow input from the Actions workflows. Keep the separate lint-baseline normalization check because it validates repository metadata and does not run Android lint.

## Expected Result

Actions builds and PR checks no longer execute `:app:lintDebug`.

[DONE]
