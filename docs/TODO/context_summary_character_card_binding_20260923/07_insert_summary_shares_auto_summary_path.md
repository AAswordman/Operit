# 07 插入总结与自动总结同路径

## 旧实现情况

插入总结由 ChatViewModel.performInsertSummary 独立实现：自行设置与恢复输入状态机、自行读取 summaryConfig、自行调用 AIMessageManager.summarizeMemory、自行 addSummaryMessage，刷新稳定上下文窗口时只传 chatId。自动总结走 MessageCoordinationDelegate.summarizeHistory，带全参数刷新稳定上下文窗口，并有 _isSummarizing 并发保护。两条链路在配置读取之外还各自持有一份 AI 服务实例与状态机，行为随并发时序漂移，用户看到界面改写在插入总结路径上不生效。

## 意图修正

长按消息插入总结与消息中的自动总结是同一件事，只是触发时机与插入位置由用户指定。两条路径必须共用同一条链路，保证总结提示词、上下文刷新、状态机完全一致。同时插入总结只把历史压缩成一条总结消息，绝不能让模型顺着这条链路再生成回复消息。

## 期待的新实现情况

MessageCoordinationDelegate 新增 insertSummaryAtMessage 公开入口，内部调用 summarizeHistory 并写死 autoContinue 为 false。调用方只提供 chatId、插入位置与裁好的待总结消息，其余环节全部复用自动总结：readSummaryConfig 同源解析、带全参数的 refreshStableContextWindow、_isSummarizing 并发保护、输入状态机与失败提示。

summarizeHistory 增加 summaryMessages、insertBeforeTimestamp、insertAfterTimestamp 三个可选参数。传入时用调用方裁好的消息列表替代全量运行时历史装载，用调用方给的插入位置替代 findProperSummaryPosition 自动定位；不传时行为与自动总结逐字一致。

ChatViewModel.performInsertSummary 精简为参数校验、范围预检、调用 insertSummaryAtMessage、按结果提示。自建的 AI 服务调用、状态机管理、addSummaryMessage 与 refreshStableContextWindow 全部移除，失败提示交由 summarizeHistory，避免两条报错叠加。

## 细化作用域

- 修改 services/core/MessageCoordinationDelegate.kt，新增 insertSummaryAtMessage 并为 summarizeHistory 增加三个可选参数
- 修改 ui/features/chat/viewmodel/ChatViewModel.kt 的 performInsertSummary
- 删除失去引用的 chat_insert_summary_failed 字符串资源，覆盖全部语言
- 不动提示词渲染层与 loadMessagesForSummaryInsertion 的范围语义，长按 user 与 ai 消息的插入位置与原实现一致
- 不影响自动总结链路本身的行为

## 调试注释要求

在 insertSummaryAtMessage 处注释历史上界面层自建链路导致的不一致，以及 autoContinue 为何必须写死为 false。

## 实际落地记录

- summarizeHistory 增加 summaryMessages 与 insertBeforeTimestamp、insertAfterTimestamp，缺省时仍走 getRuntimeChatHistory 与 findProperSummaryPosition
- 新增 insertSummaryAtMessage，autoContinue 恒为 false，插入位置的 before 与 after 由长按消息的发送方决定：长按 ai 消息时总结落在该消息之前，长按 user 消息时落在该消息之后
- ChatViewModel.performInsertSummary 删除自建状态机、AIMessageManager.summarizeMemory 调用、readSummaryConfig、addSummaryMessage 与 refreshStableContextWindow，范围预检沿用 loadMessagesForSummaryInsertion
- chat_insert_summary_failed 在中英日韩等八个语言资源中删除，已无引用
- GetDiagnostics 对两个 Kotlin 文件零告警，:app:compileDebugKotlin 通过

[DONE]
