---
fork: https://github.com/tuxKOH/Operit
branch: ci/prepare-native-ripgrep-for-jvm-checks
---

# Native Ripgrep PR Check Repair

The PR Check workflow runs Android JVM tests for test-only Android changes. Those
tests invoke the Gradle native-library verification task, but the workflow builds
the required native ripgrep library only for the full Android lane.

The workflow must prepare the Android NDK and native ripgrep library whenever it
runs the Android JVM or full Android lane. The resource-only lane remains unchanged.

Expected outcome:

- Android JVM checks receive a non-empty `liboperit_ripgrep.so`
- full Android checks retain their existing native preparation
- resource-only checks do not install the NDK or build the native library

Steps:

- [Prepare native ripgrep for JVM checks](1_PrepareNativeRipgrep.md)
