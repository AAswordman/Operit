# Prepare Native Ripgrep

## Existing behavior

`pr-check.yml` runs `:app:testDebugUnitTest` when `android_jvm` is enabled.
Gradle requires `liboperit_ripgrep.so` before its pre-build phase. The workflow
installs the NDK and builds that library only when `android_full` is enabled,
so test-only Android pull requests fail before their test suite executes.

## Intended correction

Install the NDK and build native ripgrep for either `android_jvm` or
`android_full`. Keep CMake, full dependency preparation, and all resource-only
paths scoped to the full lane.

## Verification

Validate the workflow diff statically, then use the PR Check run as the build
verification. No local build or test command is run because repository guidance
requires explicit user authorization.

[DONE]
