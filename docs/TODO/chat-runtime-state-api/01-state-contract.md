# Chat Runtime State Contract / 聊天运行状态契约

本文件是 `index.md` 的实现约束摘要，用于代码评审和兼容性核对。完整字段、枚举、示例和真机记录见同目录的 `index.md`。

This file is the implementation-constraint summary for code review and compatibility checks. See `index.md` in the same directory for complete fields, enums, examples, and device verification.

## 1. 范围 / Scope

- 公开会话标识是 `chatId`；内部 `MAIN` / `FLOATING` 槽位不公开。
  The public conversation identity is `chatId`; internal `MAIN` / `FLOATING` slots are not exposed.
- 查询接口和 ToolPkg Hook 必须使用相同的字段名、枚举值及 `error` / `retry` 嵌套结构。
  Query APIs and ToolPkg hooks must use the same field names, enum values, and nested `error` / `retry` shape.
- Runtime State 描述生命周期，不是模型配置快照，因此不公开顶层 `provider` / `model`。
  Runtime State describes lifecycle, not model configuration, so top-level `provider` / `model` are excluded.

## 2. 稳定枚举 / Stable enums

### `aiBehavior`

```text
idle
requesting
thinking
processing_tool_result
executing_plan
calling_tool
waiting_tool_result
waiting_tool_confirmation
generating_response
summarizing
retrying
cancelled
error
```

### 其他枚举 / Other enums

```text
userState: typing | waiting_for_ai | null
applicationState: foreground | background
globalActivity: idle | active
error.source: ai | tool | api | system
event: state_snapshot | state_changed
scope: global | session
```

`thinking` 只表示宿主通用处理，不证明模型 reasoning 已开启。`cancelled` 不活动；当前 `error` 阶段仍视为活动，以便在全局状态中保留待处理诊断。

`thinking` means generic host processing and does not prove model reasoning is enabled. `cancelled` is inactive. The current contract treats `error` as active so unresolved diagnostics remain visible globally.

## 3. 错误与重试 / Error and retry

```json
{
  "error": {
    "source": "api",
    "code": "rate_limited",
    "message": "Too many requests",
    "recoverable": true,
    "appCode": null,
    "providerCode": "rate_limit_error",
    "httpStatusCode": 429
  },
  "retry": {
    "attempt": 1,
    "maxAttempts": 5,
    "retryAfterMs": 1000
  }
}
```

约束：

Constraints:

1. `error.code` 是可扩展字符串，不是封闭枚举。
   `error.code` is extensible, not a closed enum.
2. `error.recoverable` 是可恢复性的权威字段。
   `error.recoverable` is authoritative for recoverability.
3. `retrying` 同时携带触发原因 `error` 和进度 `retry`。
   `retrying` carries both triggering `error` and retry progress.
4. 不可恢复错误直接进入 `error`，不伪造 `retry`。
   Non-recoverable failures enter `error` without fabricated retry metadata.
5. 重试成功、取消、完成和空闲清除旧错误/重试；重试耗尽可在终止错误上保留最近 `retry`。
   Recovery, cancellation, completion, and idle clear stale error/retry; exhausted retries may retain the latest retry context on terminal error.
6. `error.message` 必须经过脱敏并限制到 300 字符，不得公开 prompt、reasoning、工具参数或凭据。
   `error.message` must be sanitized and limited to 300 characters; prompts, reasoning, tool arguments, and credentials must not be exposed.

## 4. 工具权限分类 / Tool permission classification

| code | 触发条件 / Trigger |
|---|---|
| `permission_denied` | 用户明确拒绝，或策略禁止 / Explicit user denial or policy forbid |
| `overlay_permission_required` | 没有悬浮窗权限，确认 UI 无法显示 / Overlay permission missing, confirmation UI cannot be shown |
| `permission_confirmation_timeout` | 确认等待超过 60 秒 / Confirmation wait exceeds 60 seconds |
| `invalid_arguments` | 工具参数校验失败 / Tool argument validation failed |
| `tool_execution_failed` | 工具开始执行后失败，或未知工具失败 / Tool failed after execution began, or unknown tool failure |

缺少悬浮窗权限时，宿主打开 Android 设置页并立即结束本次确认；不会等待返回后自动继续同一次工具调用。

When overlay permission is missing, the host opens Android settings and immediately ends the current confirmation; it does not wait for return and resume the same invocation.

## 5. Hook 投递 / Hook delivery

- 注册回放使用 `state_snapshot`；普通变化使用 `state_changed`。
  Registration replay uses `state_snapshot`; normal changes use `state_changed`.
- Hook 队列有界，慢或失败 Hook 不得阻塞其他 Hook。
  Hook queues are bounded; slow or failed hooks must not block others.
- 溢出或失败后，应以最新快照重新同步失败 Hook。
  After overflow or failure, failed hooks should be resynchronized with the latest snapshot.
- Hook 事件不是永久日志；权威当前值来自查询接口或 Store 快照。
  Hook events are not a permanent log; authoritative current state comes from query APIs or store snapshots.

## 6. 清理与保留 / Cleanup and retention

- `idle` / `completed`：清除 `error`、`retry` 和工具上下文。
  `idle` / `completed`: clear `error`, `retry`, and tool context.
- `cancelled`：发布取消过渡并清除错误/重试；后续 `idle`/`completed` 会将其从活动视图清除，底层记录再按 TTL 或数量上限清理。
  `cancelled`: publish cancellation and clear error/retry; later `idle`/`completed` removes it from active views, while the backing record is pruned by TTL or capacity limits.
- `error`：保留结构化诊断，当前仍出现在 `activeChatIds`。
  `error`: retain structured diagnostics and currently remain in `activeChatIds`.
- 删除会话：必须移除 Store 记录和全局活动 ID。
  Conversation deletion must remove the store record and global active ID.
- 非活动记录：受数量上限和 TTL 清理约束。
  Inactive records are bounded by record count and TTL pruning.

## 7. 兼容性 / Compatibility

当前接口仍未正式发布，不保留旧平铺别名：

The API is still unpublished and does not retain old flattened aliases:

```text
errorCode
errorSource
errorMessage
errorRecoverable
retryAttempt
errorRetryAfterMs
```

任何公开结构变更都必须同步更新：

Every public shape change must update:

- Kotlin Runtime Snapshot
- 查询工具 DTO 与映射 / query DTO and mapping
- ToolPkg Hook payload
- `examples/types/results.d.ts`
- `examples/types/toolpkg.d.ts`
- 示例插件与 TODO 文档 / examples and TODO documentation
- 对应序列化与 Store 测试 / serialization and store tests
