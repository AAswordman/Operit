# Chat Runtime State 手工测试 / Manual Test Guide

本文用于验证 Chat Runtime State 查询接口和 ToolPkg Hook。先安装目标 Build 的 Debug APK，并启用测试 Hook。中间状态可能很短暂；没有观察到某个中间值不一定表示失败，应同时检查最终快照。

This guide verifies the Chat Runtime State query APIs and ToolPkg hook. Install the target Debug APK and enable the test hook first. Intermediate states may be brief; a missed intermediate event is not automatically a failure, so always inspect the final snapshot too.

## 当前真机结论 / Current device results

2026-08-29 已完成三轮核心测试：

Three core scenarios were verified on-device on 2026-08-29:

| 测试 / Test | 关键状态 / Key state | 结果 / Result |
|---|---|---|
| 缺少悬浮窗权限 / Missing overlay permission | `tool / overlay_permission_required`, `recoverable = true` | 通过 / Pass |
| 允许工具执行 / Allow tool execution | `waiting_tool_confirmation -> waiting_tool_result -> processing_tool_result -> idle` | 通过 / Pass |
| 无效 API Key / Invalid API key | `api / authentication_failed`, HTTP `401`, `recoverable = false` | 通过 / Pass |

前两轮会话与全局状态最终回到 `idle`。第三轮保留 `error`，全局状态继续包含该 `chatId`，符合当前 `the `error` phase is active` 的契约。真实 `retrying` 仍未通过真机观察。

The first two session/global traces returned to `idle`. The third retained `error` and kept the `chatId` in global state, matching the current `the `error` phase is active` contract. A real `retrying` transition has not yet been observed on-device.

## 一、普通发送消息 / Normal message


### 操作

1. 打开一个普通聊天。
2. 发送一条简单消息，例如：`你好，请介绍一下你自己`。
3. 观察 Runtime State Hook 输出的状态。

### 正常结果

状态可能会依次出现：

```text
thinking
requesting
生成回复
idle
```

其中有些状态出现得很快，Hook 可能看不到每一个，这属于正常情况。

重点只看最后结果：

- 最后应该回到 `idle`。
- 不应该长时间停在 `thinking`、`requesting` 或生成回复状态。
- `thinking` 在这里表示应用正在准备内容，不代表模型开启了思考模式。

## 二、快速取消

### 操作

1. 发送一条需要较长时间生成的消息。
2. AI 开始处理后，尽快点击取消。
3. 继续观察状态几秒钟。

### 正常结果

应该能看到：

```text
cancelled
idle
```

重点确认：

- 能出现 `cancelled`。
- `cancelled` 不应该被当成仍在工作。
- 后面应该回到 `idle`。
- 不需要重新发送消息，旧的 `cancelled` 也不应该一直残留。

## 三、工具确认和工具执行

### 操作

1. 把工具权限设置为“询问”。
2. 发送一条会使用工具的消息，例如让 AI 读取一个文件。
3. 当出现工具确认时，点击允许。
4. 观察整个过程中的 Runtime State。

### 正常结果

可能看到以下几个状态：

```text
waiting_tool_confirmation
calling_tool
waiting_tool_result
processing_tool_result
requesting
或
executing_plan
generating_response
idle
```

实际顺序可能因工具和模型不同而有变化，不要求每个状态都出现。

重点确认：

- 等待你确认时是 `waiting_tool_confirmation`。
- 工具真正运行时是 `calling_tool`。
- 等待工具返回时是 `waiting_tool_result`。
- 工具已经返回、应用正在读取和整理结果时是 `processing_tool_result`。
- `processing_tool_result` 不应该显示成 `thinking`。
- 最后应该回到 `idle`。

## 四、执行计划

### 说明

`executing_plan` 不是每次聊天都会出现。只有当 AI 或应用需要安排多个步骤时，才可能出现这个状态。

例如：

- 需要连续使用多个工具；
- 需要先完成一个步骤，再决定下一步；
- AI 正在安排工具的执行顺序。

### 操作

1. 发送一条需要多步完成的任务。
2. 例如让 AI 先读取文件，再根据文件内容完成下一步操作。
3. 观察 Runtime State。

### 正常结果

如果这个流程实际生成了执行计划，应该出现：

```text
executing_plan
```

不要把“没有出现这个状态”直接判断为失败，因为简单任务本来就可能不需要执行计划。

如果出现这个状态，重点确认：

- 输出值是 `executing_plan`。
- 不是 `thinking`。
- 后面可以继续进入工具调用、下一轮请求或生成回复。

## 五、错误与权限信息检查 / Error and permission checks

普通 `error` 对象可以使用无效 API Key、无效模型名称或无效 API 配置验证。工具权限场景应区分用户拒绝、缺少悬浮窗权限和确认超时。要验证 `retry` 对象，需要一次可恢复错误，例如请求期间短暂断网后恢复，或服务商返回 429/临时 5xx；无效 API Key 和无效模型通常是不可重试错误，不会产生 `retry`。

A normal `error` object can be tested with an invalid API key, model, or endpoint. Tool permission tests should distinguish explicit denial, missing overlay permission, and confirmation timeout. Testing `retry` requires a recoverable failure such as a temporary network interruption, 429, or transient 5xx. Invalid credentials and invalid models are normally non-recoverable and must not fabricate `retry`.

### 正常结果

Runtime State 使用两个独立对象，不再返回旧的平铺错误和重试字段：

```json
{
  "error": {
    "source": "api",
    "code": "rate_limited",
    "message": "Too many requests",
    "recoverable": true,
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

重点确认：

- 普通处理状态没有 `error` 和 `retry`。
- `retrying` 状态同时包含触发重试的 `error` 和当前 `retry`。
- `retry.attempt` 随重试递增，且不超过 `retry.maxAttempts`。
- 重试成功并恢复生成后，`error` 和 `retry` 都被清空。
- 重试耗尽进入 `error` 时，最后一次 `retry` 上下文仍保留，便于诊断。
- 取消请求后不再继续重试，并清空 retry 上下文。
- 查询接口与 Runtime State Hook 返回相同的嵌套结构。
- 顶层不再出现 `errorCode`、`retryAttempt`、`errorRetryAfterMs` 等旧字段。

错误消息可能会被缩短或隐藏敏感内容，这是正常的保护行为。

### 工具权限错误 / Tool permission errors

| 场景 | `error.code` | 预期 |
|---|---|---|
| 用户在确认界面明确拒绝 | `permission_denied` | 不执行工具 |
| 未授予悬浮窗权限 | `overlay_permission_required` | 打开系统设置，本次工具调用失败 |
| 确认界面等待超过 60 秒 | `permission_confirmation_timeout` | 本次工具调用失败 |
| 工具参数错误 | `invalid_arguments` | 不进入真实工具执行 |
| 工具开始后失败 | `tool_execution_failed` | 保留工具名和错误摘要 |

### 不可恢复 API 错误 / Non-recoverable API errors

预期示例：

```json
{
  "aiBehavior": "error",
  "error": {
    "source": "api",
    "code": "authentication_failed",
    "recoverable": false,
    "providerCode": "invalid_request_error",
    "httpStatusCode": 401
  }
}
```

此类错误不应包含 `retry`。当前契约将 `error` 视为待处理活动状态，因此全局 `activeChatIds` 可以继续包含该会话，直到新请求、显式清理或删除会话。

These errors must not contain `retry`. The current contract treats `error` as an actionable active state, so global `activeChatIds` may retain the conversation until a new request, explicit cleanup, or deletion.

## 六、Hook 并发和失败恢复

这个测试如果不方便做，可以暂时跳过，前面的状态测试更重要。

### 操作

如果可以准备两个 Runtime State Hook：

1. 让一个 Hook 正常返回。
2. 让另一个 Hook 故意延迟或报错。
3. 发送一条普通消息。
4. 观察两个 Hook 的输出。

### 正常结果

- 出错或变慢的 Hook 不应该让正常 Hook 一起卡住。
- 正常 Hook 仍然可以收到状态。
- 失败的 Hook 后面应该收到一份最新的 `state_snapshot`。
- 不同 Hook 的状态不能互相混淆。

## 七、聊天删除后的状态

### 操作

1. 打开或创建一个聊天。
2. 让它产生一次 Runtime State 状态。
3. 删除这个聊天。
4. 再通过 Hook 或查询工具查看这个聊天的状态。

### 正常结果

- 删除后不应该继续保留旧的活动状态。
- 查询结果应该是空闲状态，或者不再包含这个聊天。
- 全局状态中的 `activeChatIds` 不应该继续包含已删除的聊天。

## 八、示例插件字段检查

运行示例插件 `chat_runtime_state_monitor`，查看生成的 JSONL 内容。

正常字段应该使用：

```json
{
  "chat_id": "...",
  "aiBehavior": "...",
  "userState": "...",
  "applicationState": "...",
  "toolName": "...",
  "isIdle": false,
  "isActive": true
}
```

不应该再出现这些旧字段：

```text
action
user_state
app_state
tool_name
is_idle
is_active
```

## 九、输出路径检查

示例插件默认会写入：

```text
/sdcard/Download/chat_runtime_state.jsonl
```

这个路径应该可以正常使用。

如果手动填写输出路径，可以使用 Download 目录下的路径，例如：

```text
/sdcard/Download/my_runtime_state.jsonl
```

以下路径应该被拒绝：

```text
/tmp/runtime.jsonl
../runtime.jsonl
```

## 建议测试顺序

最推荐的顺序是：

```text
1. 普通消息
2. 快速取消
3. 工具确认和工具执行
4. 需要多步操作的任务
5. 错误状态
6. 删除聊天
7. 示例插件和输出路径
```

如果时间有限，优先测试前四项。尤其关注这三个新状态：

```text
thinking
processing_tool_result
executing_plan
```

它们的区别是：

```text
thinking                   通用的准备和内部处理
processing_tool_result     正在处理工具已经返回的结果
executing_plan             正在安排或执行多步计划
```

测试汇报时，把看到的状态按顺序发回来即可。例如：

```text
测试三：
waiting_tool_confirmation
calling_tool
waiting_tool_result
processing_tool_result
requesting
generating_response
idle

结果：通过 / 异常
异常说明：...
```

如果某个状态没有看到，也请注明“没有看到”，不要自行补全。这样更容易判断是状态没有触发，还是触发得太快没有被 Hook 捕捉到。
