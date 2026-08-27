# Chat Runtime State API

- Branch: `feat/chat-runtime-state-api`
- Base: `main`
- Scope: expose standardized runtime state for conversations and plugin integrations.
- Public API: `Tools.Chat.getChatRuntimeState(chatId?)` → `{ chatId, aiBehavior, userState, applicationState, toolName, error*, isIdle, isActive }`
- Tool name: `get_chat_runtime_state`
- Compatibility: previous `getCurrentActionState` and `action` field have been renamed; no public alias retained (unpublished dev version).
- Excluded: reflection, response-text inference for user confirmation, viewing/away inference, market/backend changes.

Steps:

1. [DONE] Add the runtime-state model and store with global and conversation scopes.
2. [DONE] Wire AI phases, tool permission requests, user drafts, and application lifecycle into the store.
3. [DONE] Implement `Tools.Chat.getChatRuntimeState` and the `get_chat_runtime_state` tool.
4. [DONE] Add `ToolPkg.registerChatActionStateHook` with snapshot replay and change delivery.
5. [DONE] Update focused documentation and static verification.

## Field reference

| Field | Type | Description |
|-------|------|-------------|
| `chatId` | string | Target conversation ID |
| `aiBehavior` | string | AI current behavior: `idle` / `thinking` / `calling_tool` / `waiting_tool_result` / `waiting_tool_confirmation` / `generating_response` / `retrying` / `error` |
| `userState` | string? | User interaction state: `typing` / `waiting_for_ai` |
| `applicationState` | string | App visibility: `foreground` / `background` |
| `toolName` | string? | Tool associated with current behavior |
| `errorSource` | string? | Error source: `ai` / `tool` |
| `errorCode` | string? | Stable error code |
| `errorMessage` | string? | Error message |
| `errorRecoverable` | boolean | Whether the error can be recovered |
| `retryAttempt` | number? | Current retry attempt count |
| `isIdle` | boolean | `aiBehavior == "idle"` |
| `isActive` | boolean | `aiBehavior != "idle"` |

## Implementation files

- `ChatCurrentActionStore.kt` — store with global/session snapshots
- `ChatCurrentActionSnapshot.kt` — data model
- `StandardChatManagerTool.kt` — `getChatRuntimeState` implementation
- `ToolPkgChatActionBridge.kt` — plugin hook delivery
- `ToolRegistration.kt` — tool registration
- `JsTools.kt` — JS bridge
- `CurrentActionStateResultData` in `ToolResultDataClasses.kt` — result data class
- `examples/extended_chat.ts` — TypeScript wrapper
- `examples/chat_runtime_state_monitor.ts` — sample plugin (5s interval, JSONL output)
- `examples/types/chat.d.ts` — public TypeScript interface
