# Chat Runtime State Contract

## Conversation scope

The public identity is `chatId`. The public API does not expose the internal `MAIN`/`FLOATING` runtime selector; both internal slots are aggregated into the conversation aiBehavior state.

`Tools.Chat.getCurrentChatRuntimeState(chatId?)` returns the current aiBehavior state for the default conversation when `chatId` is omitted, or for the specified conversation when it is supplied.

`Tools.Chat.getGlobalChatRuntimeState()` returns the aggregated application state with `globalActivity`, `applicationState`, `activeChatIds`, and `updatedAt`.

AI behavior phases:

- `idle`
- `requesting`: the model request is in flight and the first response event has not arrived
- `thinking`
- `calling_tool`
- `waiting_tool_result`
- `waiting_tool_confirmation`
- `generating_response`
- `summarizing`
- `retrying`
- `cancelled`: the user cancelled the current operation; this terminal state is replaced by the next non-terminal state
- `error`

User interaction states are independent of the AI phase:

- `typing`
- `waiting_for_ai`

Application visibility is independent:

- `foreground`
- `background`

## Error semantics

AI errors use the existing pure-thinking warning path and identify the recovery as a retry. Tool errors identify tool-boundary permission, validation, or execution failure. API errors identify provider request failures such as authentication, model lookup, quota, rate limiting, timeout, network, and service availability. System errors are uncategorized host-side failures.

`errorCode` is an extensible normalized string rather than a closed provider enum. Common values include `authentication_failed`, `permission_denied`, `invalid_endpoint`, `invalid_request`, `request_too_large`, `model_not_found`, `insufficient_balance`, `quota_exceeded`, `rate_limited`, `timeout`, `network_error`, `server_overloaded`, `service_unavailable`, `content_policy`, `context_window_exceeded`, `server_error`, `gateway_error`, `provider_error`, and `unknown`. When available, `errorProviderCode` and `errorHttpStatusCode` preserve provider-specific details.

Error details stay structured and do not expose prompts, chain-of-thought, or tool arguments through the state API.

The existing processing pipeline may publish `thinking` before `requesting`: `Processing` is emitted during request preparation, while `Connecting` is emitted when the provider connection phase begins. This API change keeps that underlying ordering unchanged.

## ToolPkg hook

`ToolPkg.registerChatRuntimeStateHook({ id, function })` receives `state_snapshot` during registration replay and `state_changed` for subsequent changes. The payload contains either the global activity state or a conversation snapshot, plus active conversation IDs for global events. A retained `cancelled` conversation snapshot is replayed until the next non-terminal state.
