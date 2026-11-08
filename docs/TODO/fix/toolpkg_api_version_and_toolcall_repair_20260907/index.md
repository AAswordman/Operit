---
title: ToolPkg toolCall dispatch repair
status: completed
---

# ToolPkg toolCall dispatch repair

## Original state

The versioned ToolPkg runtime sent every `toolCall` through a global active-call slot. The native tool dispatch did not consume the version passed through that slot, so calls initiated by package registration or host-owned paths failed despite having no version-specific requirement.

## Intended result

`toolCall` dispatches directly through its native bridge and does not depend on an execution-global call ID. ToolPkg API method selection remains driven by the manifest version held by its execution session.

## Scope

- JavaScript tool-call bridge and dead native call-ID dispatch paths
- Focused source-level regression tests

## Steps

- [DONE] 2_toolcall_dispatch.md
