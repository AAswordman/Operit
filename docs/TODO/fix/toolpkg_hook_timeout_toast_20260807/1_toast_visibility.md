---
title: Trace and repair timeout notice visibility
status: completed
---

# Timeout Notice Visibility

## Previous behavior

The ToolPkg chat-input bridge sets `noticeMessage` when the shared Hook deadline expires during `submit_requested`. The existing floating `ChatToastHost` reads Toast events from `UiStateDelegate`. AI retry messages reach that state through `MessageProcessingDelegate.nonFatalErrorEvent`; the message-injection Prompt Hook timeout previously stopped at `ToolPkgPromptHookBridge` and only logged.

## Intended behavior

After the message-injection Prompt Input Hook times out, the bridge reports through the same non-fatal error stream as AI retry messages, so the existing chat and floating-window `ChatToastHost` presents the localized timeout notice while the message continues sending. The notice identifies the timed-out ToolPkg by its container package name and Hook ID. Prompt history/finalize and summary Hook timeouts remain log-only.

## Verification

- Restore the chat-input Hook message path to `ChatToastHost`
- Give every Toast event an ID and queue the events in `UiStateDelegate`
- Require dismissal to name the displayed event, so an older Toast cannot clear a newer one
- Thread the Prompt Input Hook timeout callback through `InputProcessor` and `AIMessageManager`
- Publish it through `MessageProcessingDelegate.nonFatalErrorEvent`
- Include the timed-out container package name and Hook ID in the localized notice
- Inspect the final diff and call sites

No build or test commands are run unless explicitly requested.

## Result [DONE]

`ChatInputBottomBar` continues to use `ChatToastHost`. `UiStateDelegate` queues distinct Toast events and `ChatToastHost` dismisses by event ID. Prompt Input Hook timeout feedback now uses `MessageProcessingDelegate.nonFatalErrorEvent`, the same path used for AI retry notices, so the floating-window Toast host receives it with the timed-out container package name and Hook ID.
