---
title: Prompt 与摘要 Hook
status: complete
---

# Prompt 与摘要 Hook

Prompt 与摘要 Hook 通过不同注册方法接入聊天处理链。它们共用一套结构化上下文类型，但事件、可修改字段和字符串返回解释都由 Hook 家族及当前 stage 决定。

## 公共事件封套

```ts
interface HookEventBase<TEventName, TPayload> {
  event: TEventName;
  eventName: TEventName;
  eventPayload: TPayload;
  toolPkgId?: string;
  containerPackageName?: string;
  functionName?: string;
  pluginId?: string;
  hookId?: string;
  timestampMs?: number;
}
```

`event` 和 `eventName` 在当前桥接中对应同一 stage/event 名；业务载荷放在 `eventPayload`，不要把 payload 字段当作外层字段读取。

## 结构化历史 `PromptTurn`

```ts
type PromptTurnKind =
  | 'SYSTEM' | 'USER' | 'ASSISTANT'
  | 'TOOL_CALL' | 'TOOL_RESULT' | 'SUMMARY';

interface PromptTurn {
  kind: PromptTurnKind;
  content: string;
  toolName?: string | null;
  metadata?: ToolPkg.JsonObject;
}
```

宿主会把历史项编码为 `kind`、`content`、`toolName`、`metadata`。Hook 返回 JSON 数组时，每项必须是对象且 `kind` 能映射到上述枚举；无效项会被跳过，`content` 缺失时按空字符串读取。`toolName` 只保留非空值，`metadata` 只接受对象。

## Prompt 输入

### 注册

```ts
ToolPkg.registerPromptInputHook({
  id: string,
  function: (event: ToolPkg.PromptInputHookEvent) => ToolPkg.PromptInputHookReturn
}): void
```

- **API 版本：** `1.0.0` ToolPkg 基线。
- **事件：** `before_process`、`after_process`。
- **payload：** 通用 Prompt 字段，包括 `stage`、`chatId`、`functionType`、`promptFunctionType`、`useEnglish`、`rawInput`、`processedInput`、历史、Prompt 文本、模型参数、可用工具和 metadata。具体可选字段见 `PromptHookEventPayload`。
- **字符串返回：** 被解析为新的 `processedInput`。
- **对象返回：** 可提供 `rawInput`、`processedInput`、`chatHistory`、`preparedHistory`、`systemPrompt`、`toolPrompt`、`availableTools` 和 `metadata`。
- 正常输入处理先分发 `before_process`，以变更后的 `processedInput` 作为输入再分发 `after_process`；两次是独立 dispatch，各自创建 Hook 总预算。

## Prompt 历史

### `registerPromptHistoryHook`

- **API 版本：** `1.0.0`。
- **事件：** `before_prepare_history`、`after_prepare_history`。
- **数组返回：** `before_prepare_history` 替换 `chatHistory`；`after_prepare_history` 替换 `preparedHistory`。
- **对象返回：** 可分别提交 `chatHistory`、`preparedHistory` 或其他 `PromptHookObjectResult` 字段。
- 历史准备前后的数据分别用于不同阶段；需要改写已准备结果时，应根据 `stage` 选择字段，不能无条件把同一数组写入两者。

### `registerPromptEstimateHistoryHook`

- **API 版本：** `1.0.0`。
- 使用相同的两个事件名、payload 和返回结构，但接入 token 估算路径。
- 估算 Hook 与正式历史 Hook 是不同注册列表，修改其中一个不会自动修改另一个。

## 系统提示词组合

### `registerSystemPromptComposeHook`

- **API 版本：** `1.0.0`。
- **事件：** `before_compose_system_prompt`、`compose_system_prompt_sections`、`after_compose_system_prompt`。
- **字符串返回：** 替换当前 `systemPrompt`。
- **对象返回：** 可更新任意公共 Prompt mutation 字段。
- 系统提示词结构化分段阶段允许通过对象字段传递组合后的 Prompt 数据；`metadata` 会与此前 metadata 做浅层合并。

## 工具提示词组合

### `registerToolPromptComposeHook`

- **API 版本：** `1.0.0`。
- **事件：** `before_compose_tool_prompt`、`filter_tool_prompt_items`、`filter_tool_call_tools`、`after_compose_tool_prompt`。
- **字符串返回：** 替换 `toolPrompt`。
- **对象返回：** 可更新 `toolPrompt`、`availableTools` 及其他公共 mutation 字段。
- `ToolPromptItem` 字段包括 `categoryName`、`name`、`description`，以及可选的 category header/footer、参数文本、details、notes 和 `parametersStructured`。
- `parametersStructured` 项含 `name`、`description`，可选 `type`、`required`、`default`。这些结构是发给模型的工具描述，不等同于执行期 `ToolParams`。

## Prompt finalize 与估算 finalize

### `registerPromptFinalizeHook`

- **API 版本：** `1.0.0`。
- **事件：** `before_finalize_prompt`、`before_send_to_model`。
- **字符串返回：** 在当前实现中按 `processedInput` 解析。
- **数组返回：** 解析为 `preparedHistory`。
- **对象返回：** 使用 `PromptHookObjectResult` 字段更新当前 Prompt 上下文。

### `registerPromptEstimateFinalizeHook`

- **API 版本：** `1.0.0`。
- 使用相同 stage 名和返回解析规则，接入 token 估算路径，不会替代正式请求的 finalize Hook。

## 摘要生成

### `registerSummaryGenerateHook`

```ts
ToolPkg.registerSummaryGenerateHook({
  id: string,
  function: (event: ToolPkg.SummaryGenerateHookEvent) => ToolPkg.SummaryGenerateHookReturn
}): void
```

- **API 版本：** `1.0.0`。
- **事件：** `before_prepare_summary_prompt`、`before_send_to_model`、`after_generate_summary`。
- **payload：** `stage`、`functionType`、`useEnglish`、`previousSummary`、两类历史、`systemPrompt`、`summaryPrompt`、`summaryResult`、`modelParameters`、`metadata`。
- **字符串返回：** 在 `after_generate_summary` 写入 `summaryResult`；其他 stage 写入 `summaryPrompt`。
- **对象返回：** 可更新 `chatHistory`、`preparedHistory`、`systemPrompt`、`summaryPrompt`、`summaryResult` 和 `metadata`。

## 顺序与 mutation 合并

每种注册列表独立按 ToolPkg 容器加载顺序、包名和 Hook ID 排序，并串行调用。每个 Hook 接收此前 Hook 更新后的上下文。

- 返回 `null`、`void`、无法解码的值或抛出异常时，不改变当前上下文；执行错误会写日志，后续 Hook 继续。
- 对象字段只有在解析为有效值时才更新；缺失/JSON null 字段保留当前值。
- 当前 parser 对不少文本字段仅接受非空字符串，因此不能用 `""` 表示清空。
- 非空 `metadata` 与当前 metadata 做浅层 map 合并；同名键由后续 Hook 的值覆盖。
- Hook 返回 Promise 时宿主等待其完成；返回值必须符合对应 Hook 的字符串、数组或对象结构。

## 超时

Prompt 与摘要的每次 ToolPkg bridge dispatch 创建一个共享截止时间。设置范围为 1 至 60 秒，默认 10 秒；每个 Hook 只得到剩余毫秒数。

超时会中断当前 JS 调用、丢弃超时 Hook 的返回值并停止本次列表后续 Hook；宿主使用已累积的上下文继续。Prompt bridge 在调用方提供 `onHookTimeout` 时通知具体包名和 Hook ID；摘要 bridge 当前记录超时日志后继续摘要调用。

## 权威调用点

- 输入阶段：`api/chat/enhance/InputProcessor.kt`
- 历史准备与摘要：`api/chat/enhance/ConversationService.kt`
- 系统提示词：`core/config/SystemPromptConfig.kt`
- 工具提示词：`core/config/SystemToolPrompts.kt`
- 模型发送前：`api/chat/EnhancedAIService.kt`
- 运行时 mutation 与序列化：`plugins/toolpkg/ToolPkgPromptHookBridge.kt`、`ToolPkgSummaryHookBridge.kt`

相关页面：[Hook 总索引](./index.md)、[注册方法](../03_runtime/registry.md)、[版本规则](../09_compatibility/api_versions.md)。