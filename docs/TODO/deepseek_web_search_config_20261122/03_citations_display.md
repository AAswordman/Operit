# 03 Chat Completions 引用展示

## 旧实现

OpenAIProvider 的 Chat Completions 解析只读取 `delta`/`message` 中的 `content`、`reasoning_content` 与 `tool_calls`；DeepSeek 联网响应中的 `annotations` 与 `server_tool_calls` 被丢弃，聊天界面看不到搜索来源。Responses 路径已有 `<search provider="deepseek">` 展示与 `CustomXmlRenderer` 渲染。

## 意图修正

官方文档：`annotations`（嵌套 `url_citation.url_citation`，含 url/title）与完整 `server_tool_calls`（分步 calls，含 action.queries、sources、page、status、kind）在流式下只随最后一个 delta chunk 发送一次，非流式在 `message` 上。把它们解析为与 Responses 路径同构的 `<search>` 展示块即可复用现有渲染。

## 新实现

- OpenAIProvider 新增 `onChatCompletionMessageExtras(extras, emitter)` 钩子，在 delta 与 message 分支检测到 `annotations`/`server_tool_calls` 时调用，调用前关闭可能开启的思考块。
- DeepseekProvider 实现钩子：从 `server_tool_calls` 的 calls 中收集 queries（action.queries）、sources（sources 数组与 page）与状态；`annotations` 的 url_citation 作为补充来源按 url 去重；输出 `<search provider="deepseek" action="..." status="...">` 块。
- 两个 Provider 共用的 XML 转义辅助提升为 DeepseekProvider.kt 文件内顶层私有函数，消除 DeepseekResponsesProvider 内的重复实现。

[DONE]
