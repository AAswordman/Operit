# Current Action Contract

## Conversation scope

The public identity is `chatId`. The public API does not expose the internal `MAIN`/`FLOATING` runtime selector; both internal slots are aggregated into the conversation aiBehavior state.

`Tools.Chat.getChatRuntimeState(chatId?)` returns the current aiBehavior state for the default conversation when `chatId` is omitted, or for the specified conversation when it is supplied.

AI behavior phases:

- `idle`
- `thinking`
- `calling_tool`
- `waiting_tool_result`
- `waiting_tool_confirmation`
- `generating_response`
- `retrying`
- `error`

User interaction states are independent of the AI phase:

- `typing`
- `waiting_for_ai`

Application visibility is independent:

- `foreground`
- `background`

## Error semantics

AI errors use the existing pure-thinking warning path and identify the recovery as a retry. Tool errors identify parameter or execution failure. Error details stay structured and do not expose prompts, chain-of-thought, or tool arguments through the state API.

## ToolPkg hook

`ToolPkg.registerChatActionStateHook({ id, function })` receives `state_snapshot` during registration replay and `state_changed` for subsequent changes. The payload contains either the global activity state or a conversation snapshot, plus active conversation IDs for global events.

