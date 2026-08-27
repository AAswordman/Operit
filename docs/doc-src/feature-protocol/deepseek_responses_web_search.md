---
title: DeepSeek Responses 与服务器搜索协议
description: DeepSeek Chat Completions 兼容、Responses 路由、搜索状态和历史恢复约定
keywords: DeepSeek,Responses,web_search,配置兼容,聊天历史
For_Agent: 本文是协作实现协议，不代表功能已经进入发布版本
---

# DeepSeek Responses 与服务器搜索协议

## 配置兼容

DeepSeek 继续使用 `ApiProviderType.DEEPSEEK`。API 端点是该 provider 内部传输协议的唯一配置来源，不建立新的 provider 身份，因此模型价格、Token 统计和既有配置归属保持不变。

已发布版本保存的 `/chat/completions` 端点继续使用 Chat Completions。端点路径以 `/responses` 结尾时使用 Responses。服务器搜索开关只在 Responses 端点生效；关闭开关或使用 Chat Completions 时不声明 `web_search`。

设置中的 API 端点选择器提供官方 Chat Completions 与 Responses 完整地址，并直接保存用户选择的地址。运行时不再保存独立协议字段，也不互相改写两种协议路径。

## Responses 请求

Responses 端点复用通用 Responses provider，并保持 `DEEPSEEK` 作为运行时身份。启用服务器搜索时，在已有函数工具旁加入以下工具声明：

```json
{"type":"web_search"}
```

工具声明允许模型选择是否搜索，不把每轮搜索设为强制行为。

## 服务端工具状态

服务端工具生命周期通过 `SERVER_TOOL_STARTED` 与 `SERVER_TOOL_COMPLETED` 两种内部流事件传递。事件携带 `toolType`，只驱动用户界面状态，不写入模型正文或聊天历史。

DeepSeek Web Search 的以下服务器事件转换为 `toolType=web_search` 的服务端工具事件：

- `response.web_search_call.in_progress`
- `response.web_search_call.searching`
- `response.web_search_call.completed`

开始和搜索中事件把当前输入处理状态显示为正在搜索；完成事件恢复为接收回复。多个搜索调用同时活动时，只有全部完成后才恢复接收回复状态。其他服务端工具可以复用同一事件通道，并由 `toolType` 映射对应的本地化状态文案。

Responses 流以 `response.completed`、`response.incomplete` 或 `response.failed` 结束。`completed` 和 `incomplete` 都是服务器确认的终止事件；`failed` 按响应错误处理。连接在收到终止事件前结束仍视为网络中断。

## 搜索历史恢复

服务端 `output` 中的 `web_search_call` 保存为 assistant 内容里的隐藏协议元数据。下一轮 Responses 请求从该元数据恢复原始 input item，维持 DeepSeek 的无状态多轮搜索上下文。

隐藏元数据不是自然语言聊天内容：复制和非 Responses 请求必须移除它。普通渲染器可以从通过校验的 `web_search_call` 派生只读的服务端工具记录，但不得显示 Base64 内容或把它转换成客户端 `<tool>`。Responses 请求在恢复 item 后也从 message 文本中移除对应标签，避免把协议数据作为自然语言重复发送。

## 服务端工具记录

服务端工具记录与客户端函数工具严格分离。它不进入 `AITool`、`ToolInvocation`、工具授权、工具执行或 `function_call_output` 链路，只用于呈现供应商已经执行的调用。

`web_search` 记录从 `web_search_call` 读取调用 ID、状态和 action。消息列表直接以协议工具标识 `web_search` 作为标题，并用前置云端图标区分调用来源；摘要包含 query 和状态，详情保留服务端返回的原始调用 JSON。格式错误或缺少调用 ID 的 metadata 不渲染。

默认 Responses 返回的是调用条目和最终回答，并不提供与客户端函数工具等价的独立工具输出。引用由最终文本中的 `url_citation` 承载；完整来源列表需要请求显式声明 `web_search_call.action.sources`，当前协议不主动请求该字段。

## 客户端函数工具历史

同一 assistant 轮次同时包含可见文本和客户端函数调用时，Responses input 先写可见 message，再写该轮的 `function_call`。对应的 `function_call_output` 随后写入，不能由另一条 message 隔开。

`call_id` 从 Responses 输出进入内部工具调用标记，经过并行执行后写入对应的工具结果标记。下一轮按该 ID 绑定调用与结果，不依赖并行任务的完成顺序。旧聊天记录没有 `call_id` 时仍按已发布版本的位置关系读取。

思考模式下发生客户端函数调用时，DeepSeek 返回的纯文本 `reasoning` item 也属于后续请求必须携带的无状态历史。该 item 使用现有 Responses reasoning 隐藏 metadata 保存，并在下一轮 input 中恢复到对应的 assistant message 与 `function_call` 之前。纯文本 `content` 必须保留原始 `reasoning_text` 和 item ID；OpenAI Responses 的加密 reasoning item 继续使用既有 `encrypted_content` 格式。

## 当前边界

本协议不定义搜索引用的专用 UI。若服务端最终文本包含引用，它仍作为普通响应文本展示；结构化引用展示需要独立设计和兼容契约。
