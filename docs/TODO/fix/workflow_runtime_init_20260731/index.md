---
scope: workflow runtime initialization and JavaScript engine lifecycle
status: done
---

# Workflow Runtime Initialization

## Current behavior

- `androidPermissionPreferences` is initialized from `initializeMainApplication()`.
- WorkManager and broadcast entry points can create a workflow execution before that UI-oriented initialization path runs.
- The reported `JsEngine already destroyed` stack is independent from the preference exception and requires a trace of the package runtime release path before changing engine ownership.

## Intended change

- Initialize process-wide preference dependencies from `Application.onCreate()` so every Android component observes the same startup contract.
- Keep the existing workflow and package APIs unchanged.

## Acceptance checks

- A cold-start scheduled workflow can resolve Android permission preferences before its first node.
- Existing UI initialization remains idempotent.

## Scope

- `OperitApplication`
- `AndroidPermissionPreferences`
- Focused documentation for the startup contract
