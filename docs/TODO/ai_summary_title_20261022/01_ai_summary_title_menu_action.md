# 长按菜单入口、调用链、写回与状态反馈

## 旧实现

- `ChatHistorySelector` 的长按菜单只有编辑标题、上移、下移、置顶、锁定、删除。
- `ConversationService.generateConversationTitle` 只读首条用户消息和附件文件名，用于新会话的自动命名，异常被吞掉并返回空串。
- `ChatHistoryDelegate.updateChatTitle` 在内部 `launch`，调用方拿不到成功或失败。

## 修改

- `ConversationTitleTranscript` 把最近消息整理成 `User: ...` / `Assistant: ...` 的行，跳过 `summary` 和系统消息，剥离思考块、工具调用、记忆标签、附件标签和媒体链接；按字符预算从新到旧取，保证记录里留下的是最近的对话走向。
- `FunctionalPrompts` 增加 `conversationTitleHistorySystemPrompt` 与 `conversationTitleHistoryUserPrompt`，明确「整段对话」而非「第一句」，并要求只输出一行标题。
- `ConversationService.generateConversationTitleFromHistory` 复用 `FunctionType.TITLE_GENERATION` 的服务与参数，复用现有的 `sanitizeConversationTitle` 清洗，但不捕获异常。
- `EnhancedAIService` 透传上述方法。
- `ChatHistoryDelegate` 新增 `applyChatTitle`（可等待、异常上抛）与 `getRecentChatHistory`（按条数取近期消息）；`updateChatTitle` 改为调用 `applyChatTitle`，行为不变。
- `ChatViewModel.generateAiSummaryTitle` 负责整条链路：读消息、整理记录、调用模型、写回、刷新与提示，状态写进 `aiTitleGenerationStates`。
- `ChatHistorySelector` 在编辑标题下方新增菜单项，进行中显示进度指示并忽略重复点击，失败时在项内显示原因并给出「重试」。

## 期待

- 用户在任意会话上长按即可触发，不需要先切换到该会话。
- 标题生成失败不影响会话数据，失败原因可见且可重试。
- 用户手动设置的标题只在显式触发时被改写。

## 未包含

- 多选会话的批量 AI 命名，作为后续独立需求。
- 建议标题预览与用户确认后再应用。
