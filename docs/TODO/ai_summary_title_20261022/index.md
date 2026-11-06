---
topic: AI summary title from the chat history long-press menu
status: in-progress
fork_repository: https://github.com/yoruuuchan/Operit.git
working_branch: feat/ai-summary-title
issue: https://github.com/AAswordman/Operit/issues/1378
---

# 会话历史长按菜单的 AI 总结标题

## 背景

会话一多，标题大多是系统按首条消息自动命名的结果，信息量低，列表难以扫读。目前只有「编辑标题」一个手动入口，整理几十个会话要逐个改名。

仓库里已经有标题能力，缺的是「在会话列表界面直接触发」的入口：

- `FunctionType.TITLE_GENERATION` 及其在 `MultiServiceManager` 中的服务与参数解析。
- `ConversationService.generateConversationTitle`，按首条用户消息和附件名生成标题。
- `ChatHistoryManager.updateChatTitle` 与 `ChatHistoryDelegate.updateChatTitle` 负责落库和刷新列表状态。

## 意图

在长按菜单新增「AI 总结标题」，点击后异步读取该会话的历史消息、调用已配置的标题模型、写回标题并刷新列表，失败时给出原因和重试入口。

范围只包含单会话，批量重命名留给后续独立需求。

## 作用域

- `core/chat/ConversationTitleTranscript.kt`：把会话消息整理成标题模型可读的对话记录，剥离思考块、工具调用、附件与媒体链接。
- `core/config/FunctionalPrompts.kt`：新增按对话记录生成标题的系统提示与用户提示。
- `api/chat/enhance/ConversationService.kt`、`api/chat/EnhancedAIService.kt`：新增 `generateConversationTitleFromHistory`，失败时向上抛出而不是吞掉。
- `services/core/ChatHistoryDelegate.kt`：新增可等待的 `applyChatTitle` 和按条数读取近期消息的 `getRecentChatHistory`。
- `ui/features/chat/viewmodel/ChatViewModel.kt`、`viewmodel/AiTitleGenerationState.kt`：触发、状态机、写回与提示。
- `ui/features/chat/components/ChatHistorySelector.kt`：菜单项、进行中指示、失败提示与重试。
- `app/src/test/.../ConversationTitleTranscriptTest.kt`：对话记录整理的单元测试。

## 行为约定

- 只有用户显式点击才会覆盖标题；已有的首条消息自动命名逻辑保持不变，不会改写用户手改的标题。
- 生成过程在后台执行，菜单里用进度指示和「正在生成」文案表示进行中，期间的重复点击被忽略。
- 成功写回后列表立即刷新，并弹一条包含新标题的提示。
- 失败在菜单内展示原因并提供「重试」；若菜单已经关闭，改为用提示条告知，下一次长按仍可重试。
- 会话没有可总结内容或模型返回空标题时按失败处理，文案区分这两种情况。

## 验证

- `ConversationTitleTranscriptTest` 覆盖顺序、发送者过滤、协议标记剥离、预算截断与空输入。
- 真实设备上的标题生成、写回、列表刷新与异常路径需要联网环境，交由维护者或后续实测补充。

## 步骤

- [01_ai_summary_title_menu_action.md](./01_ai_summary_title_menu_action.md) [DONE] 长按菜单入口、调用链、写回与状态反馈
