---
title: ToolPkg Hook timeout Toast visibility
scope: Ensure the chat-visible timeout notice is rendered after a ToolPkg pre-send Hook times out
---

# ToolPkg Hook Timeout Toast

The ToolPkg chat-input bridge returns a timeout notice when the shared pre-send Hook deadline expires. The message-injection Prompt Hook timeout is a separate synchronous path and must reuse the non-fatal error event consumed by the chat and floating-window Toast hosts.

This work traces the Prompt Hook timeout through the existing non-fatal error event path to `ChatToastHost`, and keeps the existing behavior: the timed-out Hook is skipped and the message continues sending.

Scope:

- ToolPkg chat-input timeout notice delivery
- Message-injection Prompt Hook timeout delivery
- Chat toast event state and host rendering
- ToolPkg Hook timeout developer documentation

Implementation details are tracked in [1_toast_visibility.md](1_toast_visibility.md).
