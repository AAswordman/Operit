---
title: DeepSeek Responses 与 Web Search 协议
description: DeepSeek Chat Completions 兼容、Responses 路由、搜索识别和历史恢复约定
keywords: DeepSeek,Responses,web_search,配置兼容,聊天历史
For_Agent: 本文是协作实现协议，不代表功能已经进入发布版本
---

# DeepSeek Responses 与 Web Search 协议

## 配置兼容

DeepSeek 继续使用 `ApiProviderType.DEEPSEEK`。API 端点是该 provider 内部传输协议的唯一配置来源，不建立新的 provider 身份，因此模型价格、Token 统计和既有配置归属保持不变。

已发布版本保存的 `/chat/completions` 端点继续使用 Chat Completions。端点路径以 `/responses` 结尾时使用 Responses。Web Search 开关只在 Responses 端点生效；关闭开关或使用 Chat Completions 时不声明 `web_search`。

设置中的 API 端点选择器提供官方 Chat Completions 与 Responses 完整地址，并直接保存用户选择的地址。运行时不再保存独立协议字段，也不互相改写两种协议路径。

## Responses 请求

Responses 端点由 `DeepseekProvider` 在内部选择，不把端点判断扩散到 `AIServiceFactory` 之外。启用 Web Search 时，在已有函数工具旁加入以下工具声明：

```json
{"type":"web_search"}
```

工具声明允许模型选择是否搜索，不把每轮搜索设为强制行为。

DeepSeek Responses 当前不声明 `include`。兼容表没有列出 `include` 支持；2026-08-28 实测返回的 `web_search_call.action` 包含 `type` 与 `queries`，不包含 `sources`，最终 `output_text.annotations` 为空数组。provider 先按 `output[].type == "web_search_call"` 识别搜索调用并读取 `action.queries`，同一轮出现多条 `web_search_call` 时合并到一个 `<search>` 展示块。若响应同时提供 `web_search_call.action.sources`、`web_search_call.action.url` 或最终文本 `annotations[].url_citation`，再把这些结构化来源写入同一个 `<search>` 块。

多轮上下文中，服务端返回的完整 `web_search_call` output item 需要继续作为 Responses `input` item 提交。当前消息模型没有独立的 provider metadata 字段，因此 provider 将该官方 JSON item 编码进 `<meta provider="openai:responses_output_item">` 协议标记；下一轮构造请求时，adapter 解码并原样写回 `input`。同一条 assistant 消息中的 `<search>` 展示块会在构造请求文本时移除，避免把 UI 展示文本交回模型。

发送前清理不根据 DeepSeek 的 providerModel 猜测端点协议。DeepSeek 历史保留 Responses 协议标记到 provider 内部；Chat Completions 内容构造移除这些标记与 `<search>` 展示块，Responses adapter 消费并恢复官方 input item。

Responses 流以 `response.completed`、`response.incomplete` 或 `response.failed` 结束。`completed` 和 `incomplete` 都是服务器确认的终止事件；`failed` 按响应错误处理。连接在收到终止事件前结束仍视为网络中断。

`response.reasoning_text.delta` 和 `response.reasoning_summary_text.delta` 到达时立即输出 reasoning。DeepSeek Responses 的 `message.phase` 可能到 `response.output_item.done` 才稳定，因此 DeepSeek 子 provider 按 output item 缓冲 `response.output_text.delta`：`phase=commentary` 输出到 `<think>`，最终回答再输出正文。`response.reasoning_text.done` 只在事件携带完整文本且该文本尚未通过 delta 输出时处理，`response.output_item.done` 只补充 reasoning item 自带的 `content[].reasoning_text`。终止事件不补写 reasoning 或正文，避免答案完成后才追加思考。

## 搜索展示

服务端 `web_search_call` 与客户端函数工具严格分离。`web_search_call` 不进入 `AITool`、`ToolInvocation`、工具授权、工具执行或 `function_call_output` 链路，也不创建独立的本地 stream 事件。

provider 从 `web_search_call.action.queries` 生成 `<query>` 子节点；从 `web_search_call.action.sources`、`web_search_call.action.url` 与最终 message content 的 `annotations[].url_citation` 合并结构化来源，生成 `<source>` 子节点。`<search>` 保留 provider、action、status、query、title、url、type 和额外来源属性；Android 与 Web 渲染层优先列出全部可点击来源，每项显示网站图标和站点名。真实响应只有查询词而没有来源 URL 时，界面列出查询词，保证用户能看到本轮确实发生了搜索。搜索块跟随相邻思考与工具调用进入同一折叠组，组标题按内容显示为思考并搜索或思考、搜索并调用工具。

默认 Responses 返回的是调用条目和最终回答，并不提供与客户端函数工具等价的独立工具输出。DeepSeek 当前可稳定读取的是 `web_search_call.action.queries`；引用可能由最终文本中的 `url_citation` 承载，完整来源列表也可能位于 `web_search_call.action.sources`。provider 只把这些结构化字段转换为展示 XML，不把搜索状态包装成工具调用行。

`openai:responses_output_item` 属于隐藏协议标记，Android、Web 渲染层和复制文本清理都不展示它。用户可见面只保留 `<search>` 中的网站图标、站点名、可点击来源或无 URL 时的查询词。

## 客户端函数工具历史

同一 assistant 轮次同时包含可见文本和客户端函数调用时，Responses input 先写可见 message，再写该轮的 `function_call`。对应的 `function_call_output` 随后写入，不能由另一条 message 隔开。

`call_id` 从 Responses 输出进入内部工具调用标记，经过并行执行后写入对应的工具结果标记。下一轮按该 ID 绑定调用与结果，不依赖并行任务的完成顺序。旧聊天记录没有 `call_id` 时仍按已发布版本的位置关系读取。

思考模式下发生客户端函数调用时，DeepSeek 返回的纯文本 `reasoning` item 也属于后续请求必须携带的无状态历史。该 item 使用现有 Responses reasoning 隐藏 metadata 保存，并在下一轮 input 中恢复到对应的 assistant message 与 `function_call` 之前。纯文本 `content` 必须保留原始 `reasoning_text` 和 item ID；OpenAI Responses 的加密 reasoning item 继续使用既有 `encrypted_content` 格式。

## 当前边界

`<search>` 块当前保存搜索查询和来源列表。若服务端最终文本包含行内引用，它仍作为普通响应文本展示，不在本协议中定义额外的行内 citation 组件。
