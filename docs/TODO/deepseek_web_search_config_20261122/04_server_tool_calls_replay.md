# 04 server_tool_calls 持久化与回传

## 旧实现

无。Chat Completions 路径从未保存 `server_tool_calls`，多轮请求中 assistant 消息不含该字段，DeepSeek 服务端无法还原之前读过的网页（官方：请求仍可执行，但网页不再进入上下文）。

## 意图修正

官方要求下一轮把 assistant 消息中的 `server_tool_calls` 字段原样回传。Operit 的会话历史以文本 PromptTurn 存储，结构化字段需要借助已有的隐藏 meta 标签机制（Responses reasoning 重放同款）持久化，并在重建请求时还原。

## 新实现

- `ChatMarkupRegex` 新增 provider 常量 `deepseek_server_tool_calls` 与三个函数：`deepseekServerToolCallsMetaTag`、`extractDeepseekServerToolCallsPayloads`、`removeDeepseekServerToolCallsMeta`。
- 引用展示钩子在发出 `<search>` 块后，将 `server_tool_calls` JSON 以 Base64 meta 标签写入内容流（`emitMetadataTag` 独占一行，UI 的 meta 内建标签不渲染）。
- `DeepseekProvider.buildMessagesWithReasoning` 各 assistant 消息构造点：解码 meta 载荷并合并为 JSONArray 挂到 assistant 消息的 `server_tool_calls` 字段，同时从可见 content 中剔除 meta 标签；排队合并的 tool_call 消息在入队与发送两处分别剥离与聚合。

[DONE]
