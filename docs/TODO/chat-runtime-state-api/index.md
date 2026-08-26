# Chat Current Action API

- Branch: `feat/chat-runtime-state-api`
- Base: `main`
- Scope: expose standardized current action state for conversations and desktop-pet integrations.
- Public API: `Tools.Chat.getCurrentActionState(...)` and `ToolPkg.registerChatActionStateHook(...)`.
- Compatibility: the current runtime is an unpublished development version, so the previous query name can be migrated rather than retained as a public alias.
- Excluded: reflection, response-text inference for user confirmation, viewing/away inference, market/backend changes.

Steps:

1. Add the current-action model and store with global and conversation scopes.
2. Wire AI phases, tool permission requests, user drafts, and application lifecycle into the store.
3. Migrate the previous chat-status query to `Tools.Chat.getCurrentActionState` and the `get_current_action_state` tool.
4. Add `ToolPkg.registerChatActionStateHook` with snapshot replay and change delivery.
5. Update focused documentation and static verification.

[DONE]
