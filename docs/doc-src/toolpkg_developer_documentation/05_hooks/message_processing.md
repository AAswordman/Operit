---
title: 消息处理插件 Hook
status: complete
---

# 消息处理插件 Hook

消息处理插件通过 `ToolPkg.registerMessageProcessingPlugin()` 注册。它参与的是回复接管/消息处理链，不是普通的通知型 Hook；宿主先探测是否匹配，再仅对第一个匹配插件建立流式执行。

## 注册契约

```ts
ToolPkg.registerMessageProcessingPlugin({
  id: string,
  function: (event: ToolPkg.MessageProcessingHookEvent) => ToolPkg.MessageProcessingHookReturn
}): void
```

- **API 版本：** `1.0.0` ToolPkg 基线。
- `id` 是包内插件标识，用于宿主执行和日志定位。
- `function` 必须映射到可持久化加载的 ToolPkg 模块导出。
- 同一已启用集合中的插件按容器加载顺序、包名、插件 ID 排序。

## 事件载荷

事件名为 `message_processing`。`event.eventPayload` 的字段如下：

- `chatId?`：当前会话 ID。
- `messageContent?`：当前待处理用户消息。
- `chatHistory?: PromptTurn[]`：结构化历史，字段为 `kind`、`content`、`toolName?`、`metadata?`，不是 `{ role, content }`。
- `workspacePath?`：当前工作区路径。
- `maxTokens?`：当前消息处理使用的 token 上限。
- `tokenUsageThreshold?`：当前 token 使用门槛。
- `probeOnly?`：`true` 表示匹配探测调用，`false` 表示命中后创建执行的正式调用。
- `executionId?`：正式流式执行的取消 ID；探测调用不带该字段。

## 两阶段执行模型

### 阶段一：按顺序探测匹配

宿主按插件顺序逐个调用回调，payload 中 `probeOnly` 为 `true`。以下返回有匹配含义：

- `true`：表示匹配，但不提供消息 chunk。
- 非空字符串：表示匹配，并以该字符串作为探测阶段的候选内容。
- 对象：`matched` 缺省时按 `true`；显式 `false` 时不匹配。
- `false`、`null`、`void`、空字符串或不支持的值：不匹配。

探测阶段的内容不直接作为最终回复发送。宿主遇到第一个 `matched` 结果后停止探测后续插件，并为该插件建立流式执行。

### 阶段二：执行匹配插件并产出流

宿主再次调用同一个插件回调，此时 `probeOnly` 为 `false`，并附带 `executionId`。回调可通过 `sendIntermediateResult(...)` 持续提交中间结果，也可在最终返回值中提供内容。

对于对象，内容提取顺序为：

1. 非空 `chunk` 字段作为一个 chunk。
2. `chunks` 数组中的非空字符串逐项作为 chunk。
3. 非空 `text` 字段作为一个 chunk；只有没有 `text` 时才读取 `content`。

因此同时提供 `text` 和 `content` 时只采用 `text`。如果执行期间没有提交任何中间 chunk，宿主会把最终返回对象提取出的 chunks 作为最终流输出；一旦至少发出一个中间 chunk，最终返回值中的 chunks 不再追加到流中。

## 返回值细节

`MessageProcessingHookObjectResult` 可包含：

- `matched?`：是否接管消息；默认 `true`。
- `text?`：单个文本输出；与 `content` 同时存在时优先。
- `content?`：没有 `text` 时使用的文本输出。
- `chunks?`：多个输出片段。实现还识别对象上的 `chunk` 字段。

仅有 `matched: true` 而没有内容时，插件仍接管该消息，但不会自动生成默认文本。若希望持续输出，应通过 `sendIntermediateResult()` 发送 chunk。

## 失败、取消与耗时

- Hook 执行失败、返回解码失败或最终结果类型不支持时，宿主记录错误并把该插件视为未匹配；探测阶段继续检查后续插件。
- 命中的插件在正式执行阶段失败时，执行流结束，不会回头选择下一插件。
- `message_processing` 明确不使用聊天输入、Prompt 和摘要 Hook 共用的前置 Hook 总预算设置。
- 正式执行创建 `executionId` 并注册取消控制器。宿主取消处理时调用 controller，通知 ToolPkg cancellation registry 中止对应 JS 执行。
- 插件不得把 `probeOnly` 阶段当作一次性副作用入口，因为同一逻辑可能随后再次运行正式阶段。

## 最小示例

```ts
ToolPkg.registerMessageProcessingPlugin({
  id: 'example_command',
  function(event) {
    const payload = event.eventPayload;
    if (payload.probeOnly) {
      return payload.messageContent?.startsWith('/example') === true;
    }
    return {
      matched: true,
      text: '命令已处理'
    };
  }
});
```

该示例在探测阶段只决定是否接管；正式阶段返回实际文本。若正式阶段要流式输出，应在该分支调用中间结果 API。

## 实现依据

- `ToolPkgCommonBridgePlugin.kt`：探测顺序、首次匹配、正式执行、chunk 提取与取消控制器。
- `ToolPkgMessageProcessingCancellationRegistry.kt`：执行取消。
- `ToolPkgHookBridgeSupport.kt`：注册顺序。
- `examples/types/toolpkg.d.ts`：公开事件和返回类型。

相关页面：[ToolPkg 注册 API](../03_runtime/registry.md)、[Hook 总索引](./index.md)。