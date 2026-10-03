---
title: ToolPkg AI Provider
status: draft
---

# ToolPkg AI Provider

ToolPkg 可以注册自定义模型 Provider。宿主会把它暴露在模型配置流程中，并通过四个独立回调读取模型、发送请求、测试连接和估算输入 token。

## 注册对象

```ts
ToolPkg.registerAiProvider({
  id: string,
  displayName?: string,
  description?: string,
  listModels: { function: AiProviderListModelsHandler },
  sendMessage: { function: AiProviderSendMessageHandler },
  testConnection: { function: AiProviderTestConnectionHandler },
  calculateInputTokens: { function: AiProviderCalculateInputTokensHandler }
}): void
```

- **API 版本：** `1.0.0` ToolPkg 基线。
- `id` 是 Provider 类型 ID；运行时按去空白、转小写后的 ID 查找 Provider。
- `displayName?`、`description?` 是模型配置 UI 的展示信息。
- 四个处理函数必须通过 `{ function }` 包装，并可被 ToolPkg 函数引用解析器持久化。
- Provider 清单会在启用 ToolPkg runtime 变化时重建；同一 ID 的已注册项通过 ID 索引供模型配置和请求服务查找。

## 通用事件 payload

每个回调收到的 payload 都包含：

- `providerId`
- `providerDisplayName?`
- `providerDescription?`
- `config`

`config` 字段：

- `id`、`name`：当前模型配置身份和显示名。
- `apiProviderType`、`apiProviderTypeId`：Provider 类型标识；当前服务都填注册 Provider ID。
- `apiKey`：当前模型配置中的凭证。
- `apiEndpoint`、`modelName`：接口端点与当前模型。
- `customHeaders`：解析后的 JSON 对象。
- `customParameters`：解析后的 JSON 数组。
- `enableDirectImageProcessing`、`enableDirectAudioProcessing`、`enableDirectVideoProcessing`、`enableGoogleSearch`、`enableClaude1hPromptCache`、`enableToolCall`：当前模型配置开关。
- `requestLimitPerMinute`、`maxConcurrentRequests`：调用速率与并发配置值。
- `locale?`：回调调用时宿主 locale 的 BCP-47 language tag。

`apiKey` 会作为配置数据传入插件 Provider 回调。不要将 payload 原样写日志、保存到普通配置文件或包含在错误消息中。

Provider 在 `provider` runtime context 执行。宿主还会为每个 Provider service 建立隔离的执行 context key，并把其内部 execution chat ID 放入回调 payload 的 `chatId`。该 ID 用于隔离运行和取消，不应视为用户会话 ID。

## `listModels`

### 签名与请求

```ts
(event: AiProviderListModelsEvent) =>
  AiProviderListModelsResult | Promise<AiProviderListModelsResult>
```

事件名：`toolpkg_ai_provider_list_models`。payload 只有通用 Provider 字段。

### 返回

公开类型要求 `{ models: Array<{ id: string; name: string }> }`。每个模型项：

- `id`：写入模型配置、后续发送请求时使用的模型名。
- `name`：列表显示名。

当前运行时也接受数组、模型字符串或含 `models` 数组的对象；对象读取 `id/name/model` 和 `name/displayName/title`。开发者应返回声明的规范对象，兼容解析不替代正式 schema。

空或无效模型项会被忽略；没有解析出任何有效项时结果为空列表。

## `sendMessage`

### 签名与请求

```ts
(event: AiProviderSendMessageEvent) =>
  AiProviderSendMessageResult | Promise<AiProviderSendMessageResult>
```

事件名：`toolpkg_ai_provider_send_message`。除通用字段外还含：

- `chatHistory: PromptTurn[]`：结构化消息；发送前宿主移除 OpenAI Responses 协议标记。
- `modelParameters?: JsonObject[]`：每项包含 `id`、`name`、`value`、`type`、`category`、`enabled`、`custom`。
- `availableTools?: JsonObject[]`：工具名、描述、参数文本与结构化参数。
- `enableThinking`、`stream`、`preserveThinkInHistory`、`enableRetry`：当前调用策略。

### 最终返回与流式 chunk

公开最终结果为 `{ text: string; usage?: AiProviderUsage }`。`usage` 字段可包含 `input`、`cachedInput`、`output`。

回调也可以通过运行时的 `sendIntermediateResult(...)` 通道逐步发送文本 chunk 或 usage 更新：

- Hook 返回值和每条 intermediate result 都会被 JSON/JS 值解码。
- 支持字符串，或对象中的 `chunk`、`chunks`、`text`、`content`；提取顺序为 `chunk`、数组 `chunks`，再是 `text`，只有没有 `text` 时才取 `content`。
- 只要至少发出一个非空 intermediate 文本 chunk，最终返回值中的文本就不再追加到 stream；没有 intermediate 文本时，宿主从最终返回值提取文本。
- 一个请求只有一个逻辑 sendMessage 回调；内部重试由 Provider 实现并通过 usage 的 `attempt` 标记表达，宿主不会替插件自动重试其 JS handler。

### 成功、致命错误与非致命错误

- `{ success: false, error: string }` 是致命失败；缺少 `error` 时宿主使用 Provider 调用失败的默认错误文本。
- `nonFatalError` 可在 intermediate 或 final 对象中提供非致命错误提示；宿主调用 `onNonFatalError`，但继续处理该请求。
- 回调 Promise 抛错会作为调用失败传给模型请求流程。
- 插件需要遵守事件 `stream` 标志；该 flag 表示宿主期望的请求模式，不意味着宿主会解析 Provider 专有流协议。

### Usage 协议

usage 可放在 `{ usage: {...} }` 中，也可放在顶层。字段名接受：

- 输入 token：`input` 或 `inputTokens`
- 缓存输入 token：`cachedInput` 或 `cachedInputTokens`
- 输出 token：`output` 或 `outputTokens`
- 尝试编号：`attempt` 或 `attemptNumber`

数字必须能精确转换为非负整数；负值或无法解析的字段按未知处理。

- 带 `attempt`/`attemptNumber`：表示同一 attempt 的部分更新；省略字段不会把全局 UI 计数伪装成该 attempt 的值。attempt 编号至少按 1 处理。
- 不带 attempt：旧协议语义是整个逻辑请求的累计完整快照，归一到 attempt 1；后续快照替代此前快照，不相加。
- 每次有效 usage 更新会更新 Provider 的 token 计数并调用宿主 token/usage 回调。缺失字段不会继承其他 attempt 的计数。

## `testConnection`

### 签名

```ts
(event: AiProviderTestConnectionEvent) =>
  AiProviderTestConnectionResult | Promise<AiProviderTestConnectionResult>
```

事件名：`toolpkg_ai_provider_test_connection`；payload 只有通用 Provider 字段。

### 返回

公开结构为 `{ success: boolean; message?: string; error?: string }`。

- 对象省略 `success` 时运行时按成功处理。
- `success: false` 时抛出 `error`；缺少/空 error 时使用 `Connection failed`。
- 成功对象的空 `message` 使用 `Connection successful`。
- 运行时也接受布尔值和字符串作为兼容形式：`true` 成功、`false` 失败；空字符串使用默认成功文本。
- 抛出的 `CancellationException` 会继续向上传播，不转换成普通连接失败。

## `calculateInputTokens`

### 签名

```ts
(event: AiProviderCalculateInputTokensEvent) =>
  AiProviderCalculateInputTokensResult | Promise<AiProviderCalculateInputTokensResult>
```

事件名：`toolpkg_ai_provider_calculate_input_tokens`。payload 包含 `chatHistory` 和可选 `availableTools`，不包含 sendMessage 的模型参数和 stream 开关。

公开返回值为 `{ tokens: number }`。运行时读取 `tokens`；兼容形式还接受 number、数字字符串，或含 `inputTokens`/`count` 的对象。值必须可精确转换为非负整数，否则调用失败。

## 取消与计数生命周期

宿主把 `sendMessage` 的执行关联到 Provider service 的内部 execution chat ID。`cancelStreaming()` 和 service `release()` 会请求取消该 ID 上的 ToolPkg 执行。每次新 service 的 token counters 初始为 0；`resetTokenCounts()` 清零 input/cachedInput/output 三项。

## 实现依据

- `plugins/toolpkg/ToolPkgAiProviderRegistry.kt`：Provider 注册表及排序。
- `plugins/toolpkg/ToolPkgHookBridgeSupport.kt`：Provider 注册数据与 token 统计别名。
- `api/chat/llmprovider/ToolPkgJsAiProviderService.kt`：payload 编码、调用调度、stream、usage、错误和取消。
- `examples/types/toolpkg.d.ts`：公开事件与返回类型。

相关页面：[ToolPkg 注册 API](../03_runtime/registry.md)、[Hook 总索引](./index.md)、[API 版本规则](../09_compatibility/api_versions.md)。