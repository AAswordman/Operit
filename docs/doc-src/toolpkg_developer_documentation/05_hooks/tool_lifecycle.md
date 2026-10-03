---
title: 工具调用生命周期 Hook
status: draft
---

# 工具调用生命周期 Hook

`ToolPkg.registerToolLifecycleHook()` 订阅工具请求从进入宿主到结束的生命周期。它分成一个同步前置拦截点和多个异步通知点；两类返回值与失败语义不同。

## 注册

```ts
ToolPkg.registerToolLifecycleHook({
  id: string,
  function: (event: ToolPkg.ToolLifecycleHookEvent) => ToolPkg.ToolLifecycleHookReturn
}): void
```

- **API 版本：** `1.0.0` ToolPkg 基线。
- 多个 Hook 按容器加载顺序、包名和 Hook ID 排序。
- 每个 payload 至少包括 `toolName`、参数名到字符串值的 `parameters` 映射和工具 `description`。

## 阶段与调度

### `tool_call_requested`

请求收到后发出异步通知。返回值不阻止工具调用。

### `tool_call_intercept`

在权限检查与 executor 激活之前同步执行。各 ToolPkg Hook 按顺序串行调用：

- `void`、`null`、`{ action: 'allow' }` 或不含可识别 action 的结果允许继续检查后续 Hook。
- `{ action: 'block', reason: nonEmptyString }` 立即阻断请求，后续拦截 Hook 不执行。
- `block` 缺少字符串 reason 或 reason 去空白后为空时，宿主也会阻断，并以接口错误作为阻断原因。
- 脚本执行异常、结果解码错误会 fail closed：宿主阻断工具调用并记录错误，而不是继续放行。

当前声明文件的 `ToolLifecycleEventName` 已包含 `tool_call_intercept`；本页按运行时 bridge 记录其同步拦截行为、返回值解释和失败路径。

### `tool_permission_checked`

权限检查完成后异步通知。payload 在基础字段上增加：

- `granted`：权限是否通过。
- `reason?`：宿主提供的权限判断原因。

### `tool_execution_started`

工具 executor 即将开始时发出异步通知，只含基础工具信息。

### `tool_execution_result`

产生 `ToolResult` 后发出异步通知，增加：

- `success`：工具结果成功标志。
- `errorMessage?`：结果中的错误字段。
- `resultText?`：`ToolResult.result.toString()`。
- `resultJson?`：当结果 JSON 可解析为对象或数组时提供解析后的值；标量或空值不提供 JSON 结果。

### `tool_execution_error`

执行抛异常时发出异步通知：`success` 固定为 `false`，`errorMessage` 使用异常消息；异常没有消息时使用异常类名。

### `tool_execution_finished`

请求生命周期结束时发出异步通知，只含基础工具信息。

## 顺序与可靠性边界

- `tool_call_requested`、权限、开始、结果/错误、结束事件通过单一无界队列串行投递；它们是观察通知，不等待插件返回值改变宿主状态。
- `tool_call_intercept` 不进入该异步通知队列，宿主同步等待每个回调的放行/阻断决定。
- 拦截链按 Hook 次序执行；第一个 block 立即返回。
- 普通通知 Hook 的执行失败只写日志并继续投递其他 Hook。
- 无界队列用于保持通知顺序，不代表宿主保证进程被杀或应用崩溃后可恢复未处理事件。

## 载荷字段清单

`ToolLifecycleEventPayload` 的可选字段包括 `granted`、`reason`、`success`、`errorMessage`、`resultText`、`resultJson`。哪些字段存在取决于 `eventName`；未出现的字段不要当成 `false` 或空字符串。

`tool_call_intercept` 的返回类型在运行时接受 `action: 'block'` 且要求非空 `reason`；公开类型 `ToolLifecycleHookObjectResult` 还声明 `{ action: 'allow' }`。其他 action 不会改变宿主拦截决定。

## 示例

```ts
ToolPkg.registerToolLifecycleHook({
  id: 'deny_destructive_tool',
  function(event) {
    if (event.eventName !== 'tool_call_intercept') return;

    const name = event.eventPayload.toolName;
    if (name === 'delete_file') {
      return {
        action: 'block',
        reason: '该 ToolPkg 禁止直接删除文件'
      };
    }
    return { action: 'allow' };
  }
});
```

此示例使用的 `'tool_call_intercept'` 已包含在当前声明文件的事件名联合类型中；运行时仍需按本页的同步拦截规则处理返回值。

## 实现依据

- `plugins/toolpkg/ToolPkgToolLifecycleBridge.kt`：事件构造、队列投递、拦截返回解析和 fail-closed 行为。
- `core/tools/AIToolHook.kt`、`AIToolHandler.kt`：拦截发生在权限检查与 executor 激活前。
- `examples/types/toolpkg.d.ts`：当前声明形状。

相关页面：[ToolPkg 注册 API](../03_runtime/registry.md)、[Hook 总索引](./index.md)、[接口覆盖索引](../09_compatibility/coverage.md)。
