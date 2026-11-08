---
scope: process startup and workflow runtime preparation
status: done
---

# Runtime Initialization

## Previous state

`androidPermissionPreferences` was initialized from `OperitApplication.initializeMainApplication()`.
That method is called by UI and foreground-service paths, but a scheduled Worker or broadcast receiver can start a process without either path.

## Change

- Initialize Android permission preferences from `OperitApplication.onCreate()`.
- Make the initializer synchronized and idempotent for concurrent component startup.
- Call the same initializer from `WorkflowExecutor.prepareRuntime()` to state the workflow runtime precondition at its execution boundary.
- Document that background workflow entry points do not require a foreground Activity.

## Result

A cold-start workflow has an initialized permission preference repository before default tool registration can access it.

[DONE]
