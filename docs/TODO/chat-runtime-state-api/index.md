# Chat Runtime State API / 聊天运行状态接口

本文面向 ToolPkg、JavaScript/TypeScript 集成和 Operit 宿主开发者，定义当前聊天运行状态查询与事件接口。字段名、枚举值和时间单位属于公开契约；集成方不应读取内部 `MAIN` / `FLOATING` 槽位，也不应从显示文案反推状态。

This document defines the current chat runtime-state query and event contract for ToolPkg, JavaScript/TypeScript integrations, and Operit host code. Field names, enum values, and time units are public contract. Consumers must not inspect internal `MAIN` / `FLOATING` slots or infer state from display text.

## 1. 接口概览 / API overview

| 接口 / API | 作用 / Purpose |
|---|---|
| `Tools.Chat.getCurrentChatRuntimeState(chatId?)` | 查询指定对话或当前对话的会话状态 / Query one conversation or the current conversation |
| `Tools.Chat.getGlobalChatRuntimeState()` | 查询全局活动状态 / Query aggregate application activity |
| `get_current_chat_runtime_state` | 与当前会话查询对应的工具名 / Tool name for the session query |
| `get_global_chat_runtime_state` | 与全局查询对应的工具名 / Tool name for the global query |
| `ToolPkg.registerChatRuntimeStateHook({ id, function })` | 注册状态快照与变更事件 / Register snapshot and change events |

`chatId` 是公开会话标识。省略 `chatId` 时，查询接口依次尝试当前主界面会话和当前悬浮会话；若没有当前会话，返回空 `chatId` 与 `idle`。

`chatId` is the public conversation identity. When omitted, the query checks the current main-chat conversation and then the current floating-chat conversation. If neither exists, it returns an empty `chatId` with `idle`.

## 2. 会话查询结果 / Session query result

```ts
interface CurrentChatRuntimeStateResultData {
  chatId: string;
  aiBehavior: ChatRuntimeStateBehavior;
  userState?: "typing" | "waiting_for_ai" | null;
  applicationState: "foreground" | "background";
  toolName?: string | null;
  error?: ChatRuntimeStateError | null;
  retry?: ChatRuntimeStateRetry | null;
  isIdle: boolean;
  isActive: boolean;
}
```

```json
{
  "chatId": "chat-123",
  "aiBehavior": "retrying",
  "userState": "waiting_for_ai",
  "applicationState": "foreground",
  "toolName": null,
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
  },
  "isIdle": false,
  "isActive": true
}
```

### 2.1 字段说明 / Field reference

| 字段 / Field | 类型 / Type | 中文说明 | English description |
|---|---|---|---|
| `chatId` | string | 目标对话 ID；无当前会话时为空字符串 | Target conversation ID; empty when no current conversation exists |
| `aiBehavior` | enum | 宿主当前处理阶段，见第 3 节 | Current host processing phase; see section 3 |
| `userState` | enum? | 用户交互派生状态，与 AI 阶段独立 | Derived user interaction state, independent of the AI phase |
| `applicationState` | enum | 应用前后台状态 | Application foreground/background state |
| `toolName` | string? | 当前阶段关联的工具名 | Tool associated with the current phase |
| `error` | object? | 当前错误或重试触发原因 | Current error or the error that triggered retry |
| `retry` | object? | 当前或最近一次重试上下文 | Current or most recent retry context |
| `isIdle` | boolean | 等价于 `aiBehavior == "idle"` | Equivalent to `aiBehavior == "idle"` |
| `isActive` | boolean | 除 `idle`、`cancelled` 外均为 `true`；当前 `error` 阶段也视为活动，用于保留待处理诊断状态 | `true` for every phase except `idle` and `cancelled`; currently also `true` for `error` so unresolved diagnostics stay visible |

## 3. `aiBehavior` 枚举 / `aiBehavior` enum

`thinking` 表示宿主正在准备请求或处理上下文，不代表模型一定启用了隐藏思考或 reasoning 输出。

`thinking` means the host is preparing a request or processing context. It does not prove that model reasoning or hidden thinking is enabled.

| 值 / Value | 中文语义 | English semantics | 常见后继 / Typical next state |
|---|---|---|---|
| `idle` | 没有正在进行的 AI 工作 | No active AI work | `thinking` |
| `requesting` | Provider 请求正在建立或等待首个响应事件 | Provider request is being established or awaiting its first response event | `generating_response`, `retrying`, `error` |
| `thinking` | 宿主准备输入、上下文、提示词或请求 | Host is preparing input, context, prompts, or a request | `requesting`, `calling_tool`, `error` |
| `processing_tool_result` | 工具已返回，宿主正在读取、规范化和整合结果 | A tool returned and the host is parsing, normalizing, and integrating its result | `requesting`, `generating_response`, `executing_plan` |
| `executing_plan` | 宿主正在安排或执行多步计划 | Host is arranging or executing a multi-step plan | `calling_tool`, `requesting`, `generating_response` |
| `calling_tool` | 工具调用正在进入执行流程 | A tool invocation is entering execution | `waiting_tool_confirmation`, `waiting_tool_result`, `error` |
| `waiting_tool_result` | 工具已经开始，宿主等待返回结果 | Tool execution started and the host is awaiting its result | `processing_tool_result`, `error` |
| `waiting_tool_confirmation` | 等待用户确认工具权限 | Waiting for user confirmation before tool execution | `calling_tool`, `error` |
| `generating_response` | 正在接收模型回复内容 | Model response content is being received | `calling_tool`, `idle`, `retrying`, `error` |
| `summarizing` | 正在执行对话、上下文或记忆总结 | Conversation, context, or memory summarization is running | `idle`, `error` |
| `retrying` | 可恢复错误已被接受并等待或执行下一次尝试 | A recoverable failure was accepted and another attempt is pending or running | `requesting`, `generating_response`, `error`, `cancelled` |
| `cancelled` | 用户取消当前操作；该过渡状态不计为活动 | User cancelled the operation; this transition is not active | `idle` |
| `error` | 处理以错误结束，并保留诊断数据 | Processing ended with an error and diagnostics remain available | 新请求的 `thinking`，或显式清理后的 `idle` / a new request's `thinking`, or `idle` after cleanup |

状态可能变化很快，Hook 消费者不能假设每次都观察到所有中间值。查询接口返回调用时的最新快照。

States can change quickly; hook consumers must not assume that every intermediate value will be observed. Query APIs return the latest snapshot at call time.

## 4. 用户、应用与全局枚举 / User, application, and global enums

### 4.1 `userState`

| 值 / Value | 中文语义 | English semantics |
|---|---|---|
| `typing` | 当前会话存在用户草稿 | The conversation has a user draft |
| `waiting_for_ai` | AI 阶段处于活动状态，用户正在等待处理完成 | The AI phase is active and the user is waiting |
| `null` | 当前没有可报告的用户交互状态 | No reportable user interaction state |

### 4.2 `applicationState`

| 值 / Value | 中文语义 | English semantics |
|---|---|---|
| `foreground` | 应用在前台 | App is in the foreground |
| `background` | 应用在后台 | App is in the background |

### 4.3 `globalActivity`

| 值 / Value | 中文语义 | English semantics |
|---|---|---|
| `idle` | `activeChatIds` 为空 | `activeChatIds` is empty |
| `active` | 至少一个会话处于活动或待处理状态 | At least one conversation is active or retains an actionable state |

全局结果结构：

Global result shape:

```ts
interface GlobalChatRuntimeStateResultData {
  globalActivity: "idle" | "active";
  applicationState: "foreground" | "background";
  activeChatIds: string[];
  updatedAt: number;
}
```

`updatedAt` 使用 Unix epoch 毫秒。`activeChatIds` 已去重并按字符串排序。由于当前 `the `error` phase is active`，终止错误会话会继续出现在 `activeChatIds` 中，直到新请求覆盖、会话被移除或状态被清理。

`updatedAt` is Unix epoch milliseconds. `activeChatIds` is deduplicated and sorted. Because `the `error` phase is active` in the current contract, a terminal error conversation remains in `activeChatIds` until a new request replaces it, the conversation is removed, or state is cleared.

## 5. 错误对象 / Error object

```ts
interface ChatRuntimeStateError {
  source: "ai" | "tool" | "api" | "system";
  code: string;
  message?: string | null;
  recoverable: boolean;
  appCode?: number | null;
  providerCode?: string | null;
  httpStatusCode?: number | null;
}
```

### 5.1 `error.source`

| 值 / Value | 中文语义 | English semantics |
|---|---|---|
| `ai` | 模型输出或 AI 行为异常，例如只有思考内容而没有正文 | Model-output or AI-behavior failure, such as thinking-only output |
| `tool` | 工具授权、参数校验或工具执行边界失败 | Tool permission, validation, or execution-boundary failure |
| `api` | Provider 请求、认证、限流、服务或网络失败 | Provider request, authentication, rate-limit, service, or network failure |
| `system` | 未被更具体分类覆盖的宿主错误 | Host-side failure not covered by a more specific category |

### 5.2 常见 `error.code` / Common `error.code` values

`error.code` 是可扩展稳定字符串，不是封闭枚举。消费者应处理已知值，并为未知新值保留兜底分支。

`error.code` is an extensible stable string, not a closed enum. Consumers should handle known values and retain a fallback for future values.

| code | recoverable | 中文语义 | English semantics |
|---|---:|---|---|
| `authentication_failed` | false | API Key 或认证失败 | API key or authentication failed |
| `permission_denied` | false/场景相关 | Provider 或工具权限被拒绝 | Provider or tool permission denied |
| `overlay_permission_required` | true | 无悬浮窗权限，无法显示工具确认界面 | Overlay permission is required to show tool confirmation |
| `permission_confirmation_timeout` | true | 等待工具确认超过 60 秒 | Tool confirmation exceeded the 60-second timeout |
| `invalid_arguments` | true | 工具参数不合法 | Tool arguments are invalid |
| `tool_execution_failed` | true | 工具开始执行后失败，或未知工具失败 | Tool failed after execution began, or an unknown tool failure occurred |
| `invalid_endpoint` | false | API 地址无效或 404 端点不存在 | API endpoint is invalid or missing |
| `invalid_request` | false | 请求格式、参数或 Provider 请求无效 | Request format, parameters, or provider request is invalid |
| `request_too_large` | false | 请求体过大 | Request body is too large |
| `model_not_found` | false | 模型或部署不存在 | Model or deployment does not exist |
| `insufficient_balance` | false | 余额不足 | Account balance is insufficient |
| `quota_exceeded` | false | 配额已耗尽 | Quota is exhausted |
| `rate_limited` | true | 请求频率受限 | Request was rate limited |
| `content_policy` | false | 内容策略或安全策略拒绝 | Content or safety policy rejected the request |
| `context_window_exceeded` | false | 上下文窗口或 Token 上限超出 | Context window or token limit was exceeded |
| `server_overloaded` | true | Provider 过载 | Provider is overloaded |
| `service_unavailable` | true | Provider 暂时不可用或维护 | Provider is temporarily unavailable or under maintenance |
| `server_error` | true | Provider 5xx 服务端错误 | Provider 5xx server error |
| `gateway_error` | true | 网关错误 | Gateway error |
| `timeout` | true | HTTP 408/504 等 Provider 超时 | Provider timeout such as HTTP 408/504 |
| `dns_resolution_failed` | true | DNS 解析失败 | DNS resolution failed |
| `connection_refused` | true | 连接被拒绝 | Connection was refused |
| `network_unreachable` | true | 网络不可达 | Network is unreachable |
| `connection_reset` | true | 连接被重置 | Connection was reset |
| `connection_closed` | true | 连接意外关闭 | Connection closed unexpectedly |
| `tls_handshake_failed` | true | TLS/证书握手失败 | TLS or certificate handshake failed |
| `connection_timeout` | true | 本地连接或读超时 | Local connect or read timeout |
| `provider_error` | false | 已确认是 Provider/IO 错误，但没有更具体分类 | Provider or I/O error without a more specific classification |
| `pure_thinking_only` | true/配置相关 | 模型只返回思考内容，没有正文 | Model returned thinking content without a normal response body |
| `unknown` | false | 未识别错误 | Unclassified error |

工具错误的 `recoverable` 当前由 `InputProcessingState.ToolError` 默认值决定，通常为 `true`；API 错误则由分类器决定。不要仅凭错误码字符串猜测是否可恢复，应优先读取实际 `error.recoverable`。

Tool-error recoverability currently follows `InputProcessingState.ToolError` defaults and is usually `true`; API-error recoverability is classifier-driven. Do not infer recoverability from the code string alone; read `error.recoverable` first.

### 5.3 Provider 与 Operit 元数据 / Provider and Operit metadata

| 字段 / Field | 中文说明 | English description |
|---|---|---|
| `message` | 面向公开接口的脱敏错误摘要，最长 300 字符 | Sanitized public error summary, limited to 300 characters |
| `appCode` | Operit 本地错误码；网络错误当前使用 `5000` 至 `5007` | Operit-local error code; network failures currently use `5000` through `5007` |
| `providerCode` | Provider 原始 `error.code` / `error.type`，无法提取时为 `null` | Original provider `error.code` / `error.type`, or `null` when unavailable |
| `httpStatusCode` | HTTP 状态码，无法识别时为 `null` | HTTP status code, or `null` when unavailable |

公开状态不会新增顶层 `provider` 或 `model`。该接口描述会话生命周期，不是模型配置快照；同一会话可能因功能模型、临时覆盖、模型索引或后续工具请求而使用不同模型。需要模型归属时，应读取消息记录或模型配置接口。

The public state does not add top-level `provider` or `model` fields. This API describes conversation lifecycle, not a model-configuration snapshot; one conversation may use different models because of functional bindings, per-request overrides, model indexes, or later tool-result requests. Use message records or model-configuration APIs when model attribution is required.

## 6. 重试对象 / Retry object

```ts
interface ChatRuntimeStateRetry {
  attempt?: number | null;
  maxAttempts?: number | null;
  retryAfterMs?: number | null;
}
```

| 字段 / Field | 单位 / Unit | 中文说明 | English description |
|---|---|---|---|
| `attempt` | count | 当前重试次数，从 1 开始 | Current retry number, starting at 1 |
| `maxAttempts` | count | 允许的最大重试次数 | Maximum retry count |
| `retryAfterMs` | ms | 下一次尝试前的等待时间 | Delay before the next attempt |

生命周期规则：

Lifecycle rules:

1. 进入 `retrying` 时，`error` 描述触发原因，`retry` 描述重试进度。
   In `retrying`, `error` describes the cause and `retry` describes progress.
2. 重试成功并恢复普通处理后，旧 `error` 和 `retry` 会被清除。
   Successful recovery clears stale `error` and `retry`.
3. 重试耗尽进入 `error` 时，最近一次 `retry` 可保留用于诊断。
   A terminal `error` may retain the latest retry context.
4. `idle`、`completed`、取消和会话移除会清除错误/重试上下文。
   `idle`, completion, cancellation, and conversation removal clear error/retry context.
5. 不可恢复错误直接进入 `error`，不会为了填充字段而伪造 `retry`。
   Non-recoverable failures enter `error` directly and do not fabricate `retry`.

## 7. Hook 事件 / Hook events

```ts
ToolPkg.registerChatRuntimeStateHook({
  id: "runtime-monitor",
  function: async (event) => {
    if (event.scope === "session") {
      console.log(event.chatId, event.aiBehavior, event.error, event.retry);
    } else {
      console.log(event.globalActivity, event.activeChatIds);
    }
  }
});
```

### 7.1 事件枚举 / Event enum

| `event` | 中文语义 | English semantics |
|---|---|---|
| `state_snapshot` | 注册回放或失败恢复时发送的当前快照 | Current snapshot sent during registration replay or failure recovery |
| `state_changed` | 状态发生变化 | State changed |

### 7.2 Hook payload

```ts
interface ChatRuntimeStateEventPayload {
  scope: "global" | "session";
  event: "state_snapshot" | "state_changed";
  applicationState: "foreground" | "background";
  activeChatIds: string[];
  globalActivity: "idle" | "active";
  updatedAt: number;
  chatId?: string;
  aiBehavior?: ChatRuntimeStateBehavior;
  userState?: "typing" | "waiting_for_ai" | null;
  toolName?: string | null;
  error?: ChatRuntimeStateError | null;
  retry?: ChatRuntimeStateRetry | null;
}
```

`scope == "global"` 时只保证全局字段存在；`scope == "session"` 时附带会话字段。没有 `error` / `retry` 时，Hook payload 通常省略这些键，而查询 DTO 可能序列化为缺失或 `null`，消费者应兼容二者。

For `scope == "global"`, only global fields are guaranteed. `scope == "session"` adds session fields. Hook payloads normally omit `error` / `retry` when absent, while query DTO serialization may omit them or expose `null`; consumers should accept both forms.

Hook 投递使用有界队列。慢 Hook 不会阻塞其他 Hook；队列溢出或执行失败后，宿主会尝试用最新 `state_snapshot` 重新同步失败的 Hook。事件是边沿通知，不是永久日志；需要可靠当前值时应重新调用查询接口。

Hook delivery uses a bounded queue. A slow hook does not block other hooks. After queue overflow or hook failure, the host attempts to resynchronize failed hooks with the latest `state_snapshot`. Events are edge notifications, not a permanent log; query the current state again when an authoritative latest value is required.

## 8. 典型状态序列 / Typical state sequences

### 8.1 普通回复 / Normal response

```text
idle -> thinking -> requesting -> generating_response -> idle
```

### 8.2 允许工具执行 / Allowed tool execution

```text
thinking -> requesting -> generating_response
-> calling_tool -> waiting_tool_confirmation
-> calling_tool -> waiting_tool_result
-> processing_tool_result -> generating_response -> idle
```

### 8.3 缺少悬浮窗权限 / Missing overlay permission

```text
calling_tool -> waiting_tool_confirmation
-> calling_tool -> error(tool/overlay_permission_required)
-> processing_tool_result -> generating_response -> idle
```

进入 Android 悬浮窗设置页后，本次工具确认会立即返回 `overlay_permission_required`；当前实现不会等待用户从设置页返回并自动恢复同一次工具调用。

After opening Android overlay settings, the current confirmation immediately returns `overlay_permission_required`; the implementation does not wait for the user to return and automatically resume the same tool invocation.

### 8.4 不可恢复 API 错误 / Non-recoverable API error

```text
thinking -> requesting -> error(api/authentication_failed)
```

此时 `retry` 缺失，`error.recoverable == false`。错误状态保留供查询和 UI 诊断。

`retry` is absent and `error.recoverable == false`. The error state remains available for queries and UI diagnostics.

### 8.5 可恢复错误 / Recoverable failure

```text
requesting -> retrying(error + retry) -> requesting/generating_response
```

重试耗尽时：

When retries are exhausted:

```text
requesting -> retrying -> error(error + latest retry)
```

## 9. 数据保护与兼容性 / Data protection and compatibility

- `error.message` 会遮蔽常见 token、API key、prompt、reasoning 等敏感片段，并限制长度。
  `error.message` redacts common tokens, API keys, prompts, reasoning fields, and limits length.
- 不公开工具参数、完整请求体、完整响应体、chain-of-thought 或凭据。
  Tool arguments, full request/response bodies, chain-of-thought, and credentials are not exposed.
- `error.code` 可扩展；新增代码不要求升级整个接口版本。
  `error.code` is extensible; adding a code does not require a full API version bump.
- 当前未发布版本不保留旧平铺字段别名，例如 `errorCode`、`retryAttempt`、`errorRetryAfterMs`。
  This unpublished version does not retain legacy flattened aliases such as `errorCode`, `retryAttempt`, or `errorRetryAfterMs`.
- 时间戳和延迟均为毫秒。
  Timestamps and delays are expressed in milliseconds.

## 10. 当前验证记录 / Current verification record

2026-08-29 真机记录已观察到：

Observed on a physical device on 2026-08-29:

| 场景 / Scenario | 关键结果 / Key result |
|---|---|
| 未授予悬浮窗权限 / Overlay permission missing | `error.source = "tool"`, `error.code = "overlay_permission_required"`, `recoverable = true` |
| 允许工具执行 / Tool allowed | 出现 `waiting_tool_confirmation`, `waiting_tool_result`, `processing_tool_result`，最终 `idle` / phases observed and returned to `idle` |
| 无效 API Key / Invalid API key | `error.source = "api"`, `error.code = "authentication_failed"`, `recoverable = false`, HTTP `401` |

尚未通过真机观察真实 `retrying`；该路径需要临时网络故障、429 或临时 5xx。不可恢复 401 不应生成 `retry`。

A real `retrying` path has not yet been observed on-device; it requires a transient network failure, 429, or temporary 5xx. A non-recoverable 401 must not fabricate `retry`.

## 11. 实现位置 / Implementation map

- `app/src/main/java/com/ai/assistance/operit/api/chat/ChatRuntimeState.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardChatManagerTool.kt`
- `app/src/main/java/com/ai/assistance/operit/plugins/toolpkg/ToolPkgChatRuntimeStateBridge.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/ToolResultDataClasses.kt`
- `examples/types/results.d.ts`
- `examples/types/toolpkg.d.ts`
- `examples/chat_runtime_state_monitor.js`

## English quick summary

The API exposes conversation lifecycle, user interaction, application visibility, tool context, structured error metadata, and retry progress. Query APIs return authoritative current snapshots; ToolPkg hooks provide bounded asynchronous change delivery with snapshot resynchronization. Error codes are extensible, `error` and `retry` are separate nested objects, provider/model attribution remains outside this lifecycle contract, and non-recoverable failures do not fabricate retry metadata.
