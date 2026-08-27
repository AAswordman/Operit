# Chat Runtime State API

- Branch: `feat/chat-runtime-state-api`
- Base: `main`
- Scope: expose standardized runtime state for conversations and plugin integrations.
- Public API: `Tools.Chat.getCurrentChatRuntimeState(chatId?)` → `{ chatId, aiBehavior, userState, applicationState, toolName, error*, isIdle, isActive }`
- Public API: `Tools.Chat.getGlobalChatRuntimeState()` → `{ globalActivity, applicationState, activeChatIds, updatedAt }`
- Tool names: `get_current_chat_runtime_state`, `get_global_chat_runtime_state`
- Compatibility: no legacy public aliases are retained because this API is still an unpublished development version.
- Excluded: reflection, response-text inference for user confirmation, viewing/away inference, market/backend changes.

Steps:

1. [DONE] Add the runtime-state model and store with global and conversation scopes.
2. [DONE] Wire AI phases, tool permission requests, user drafts, and application lifecycle into the store.
3. [DONE] Implement `Tools.Chat.getCurrentChatRuntimeState`, `Tools.Chat.getGlobalChatRuntimeState`, and their tools.
4. [DONE] Add `ToolPkg.registerChatRuntimeStateHook` with snapshot replay and change delivery.
5. [DONE] Update focused documentation and static verification.

## Field reference

| Field | Type | Description |
|-------|------|-------------|
| `chatId` | string | Target conversation ID |
| `aiBehavior` | string | AI current behavior: `idle` / `requesting` / `thinking` / `calling_tool` / `waiting_tool_result` / `waiting_tool_confirmation` / `generating_response` / `summarizing` / `retrying` / `cancelled` / `error` |
| `userState` | string? | User interaction state: `typing` / `waiting_for_ai` |
| `applicationState` | string | App visibility: `foreground` / `background` |
| `toolName` | string? | Tool associated with current behavior |
| `errorSource` | string? | Error source: `ai` / `tool` |
| `errorCode` | string? | Stable error code |
| `errorMessage` | string? | Error message |
| `errorRecoverable` | boolean | Whether the error can be recovered |
| `retryAttempt` | number? | Current retry attempt count |
| `isIdle` | boolean | `aiBehavior == "idle"` |
| `isActive` | boolean | `aiBehavior` is a state with active runtime work; `cancelled` is terminal and not active |

## Implementation files

- `ChatRuntimeState.kt` — runtime-state store, snapshots, events, and wire enums
- `StandardChatManagerTool.kt` — `getCurrentChatRuntimeState` and `getGlobalChatRuntimeState` implementations
- `ToolPkgChatRuntimeStateBridge.kt` — plugin runtime-state hook delivery
- `ToolRegistration.kt` — current/global tool registration
- `JsTools.kt` — JS bridge
- `CurrentChatRuntimeStateResultData` and `GlobalChatRuntimeStateResultData` in `ToolResultDataClasses.kt`
- `examples/extended_chat.ts` — TypeScript wrapper
- `examples/chat_runtime_state_monitor.ts` — sample plugin (runtime-state output to formatted JSON)
- `examples/types/chat.d.ts` — public TypeScript interface
