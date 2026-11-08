---
title: 聊天输入 Hook
status: complete
---

# 聊天输入 Hook

`ToolPkg.registerChatInputHook()` 注册一个观察输入框变化并可参与提交决策的 Hook。ToolPkg bridge 在主线程之外的 IO dispatcher 执行脚本；输入变化/提交完成属于通知，提交前校验属于串行决策链。

## 注册与顺序

```ts
ToolPkg.registerChatInputHook({
  id: string,
  function: (event: ToolPkg.ChatInputHookEvent) => ToolPkg.ChatInputHookReturn
}): void
```

- **API 版本：** `1.0.0` ToolPkg 基线。
- ToolPkg 容器运行时变更时，宿主重建已启用 Hook 列表。
- 执行次序为 ToolPkg 容器加载顺序，再按包名和 Hook ID 排序。
- 每个事件都提供 `event`、`eventName` 和 `eventPayload`；外层通用元数据还可能含包名、函数名、Hook ID 和 `timestampMs`。

## 事件名与 payload

### `input_changed`

输入内容或选择范围变化时发出通知。payload 字段：

- `chatId?`：当前会话 ID。
- `text?`：当前输入框文本。
- `selectionStart?`、`selectionEnd?`：选择区 UTF-16 字符索引。
- `hasAttachments?`、`attachmentCount?`：附件状态。
- `isProcessing?`：当前会话是否正在处理。
- `inputStyle?`：经典或 Agent 输入样式标记。
- `source?`：输入来源，例如 `classic`、`agent`、`fullscreen`、`queue`。
- `submitSource?`：提交来源，例如 `send`、`button`、`ime_send`、`enter`、`queue`。

### `submit_requested`

用户请求发送前同步派发，ToolPkg Hook 依次观察当前提交文本并可改变决策。后一个 ToolPkg Hook 收到的是前面 `replace` 已更新的文本和光标位置。

### `submitted`

提交已发生后的通知事件。回调返回值不参与提交决策。

`input_changed` 和 `submitted` 通过通知调度异步触发；它们的回调返回值被忽略。执行异常只记录日志，不改变用户输入流程。

## `submit_requested` 返回契约

回调可以返回 `void`、`null`、非空字符串、Promise，或 `ChatInputHookObjectResult`。

### 字符串返回

- 非空字符串等价于 `{ action: 'replace', text: returnedString }`。
- 空白字符串被视为无结果，不清空用户输入。

### 对象字段

- `action?`：`allow`、`block`、`replace` 或 `consume`。
- `text?`：`replace` 使用的新文本；省略时继续使用当前文本。
- `message?`：提交被阻止/消费时供宿主展示的说明文本。
- `clearInput?`：请求宿主在消费提交时清空输入。
- `metadata?`：随 `ChatInputHookResult` 返回的结构化附加信息。

若对象省略 `action`，但含 `text` 字段，则按 `replace` 处理；既无 action 又无 text 时视为无结果。未知 action 被忽略。

### 动作执行

- `allow`：继续调用后续 Hook；不会把对象中的 `text` 隐式写回输入。
- `replace`：用 `text` 替换当前待发送内容，并将选择区起止位置都设为新文本长度。
- `block`：立即终止提交 Hook 链并返回阻止结果；后续 Hook 不执行。
- `consume`：立即终止提交 Hook 链并返回消费结果；后续 Hook 不执行。`clearInput` 由宿主提交调用方处理。
- 全部 Hook 完成且没有 `block`/`consume` 时，宿主以 `allow` 返回最终累积文本。

## 超时与错误

一次 ToolPkg chat-input bridge 分发创建一个共享总预算，而不是为每个 Hook 重置完整时限。预算设置位于 Operit“显示与行为”配置，允许 1 至 60 秒，默认 10 秒。每个 Hook 获得当前剩余毫秒数。

- 总预算耗尽后停止后续 Hook。
- 对 `submit_requested`，超时会允许当前累积文本继续发送，并设置通知信息指出具体包和 Hook；输入变化类通知超时只记录日志。
- 单个 Hook 抛错或返回无法解码的结果时记录日志并继续执行后续 Hook。
- 超时后被中断 Hook 的返回值不参与决策。

## 示例

```ts
ToolPkg.registerChatInputHook({
  id: 'normalize_command',
  function(event) {
    if (event.eventName !== 'submit_requested') return;

    const text = event.eventPayload.text ?? '';
    if (text.startsWith('/upper ')) {
      return {
        action: 'replace',
        text: text.slice('/upper '.length).toUpperCase()
      };
    }
    if (text === '/cancel') {
      return {
        action: 'block',
        message: '该命令不允许发送'
      };
    }
  }
});
```

## 实现依据

- `ToolPkgChatInputHookBridge.kt`：ToolPkg payload、顺序、预算和结果解码。
- `ChatInputHookRegistry.kt`：通知与提交两种调度模式，以及 allow/block/replace/consume 的消费方式。
- `examples/types/toolpkg.d.ts`：事件和返回类型。

相关页面：[ToolPkg 注册 API](../03_runtime/registry.md)、[Hook 总索引](./index.md)。