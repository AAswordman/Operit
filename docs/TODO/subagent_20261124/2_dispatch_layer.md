# 步骤 2：调度层

[返回总计划](index.md)

## 旧实现情况

无。子任务管线（`EnhancedAIService.sendMessage(isSubTask=true)`）已存在，但没有"注册任务、排队、并发限流、终态合成、投递回父会话"这一层。

## 意图

一个纯逻辑注册表 + 一个唯一执行体，把子任务管线的产物包装成模型可治理的后台任务。

## 新实现

### `core/subagent/AgentJobRegistry.kt`

状态机与句柄：

- `Status`：`QUEUED` / `RUNNING` / `DONE` / `CANCELLED` / `FAILED` / `TIMEOUT` / `INTERRUPTED`
- `SubagentJob`：`jobId`、`parentChatId`、`profileId`、`profileName`（名字快照）、`toolTitle`、`snapshot`、`status`、`createdAt`/`startedAt`/`finishedAt`、`outcomePath`、`childSessionId`、`queuedBehind`、`summary`、`lastToolName`、`lastActivityAt`、`executedToolCalls`、`toolRecords`、`notifyParentOnFinish`、`modelConfigId`、`modelIndex`
- `RunningHandle(deferred, steerChannel, job)`：排队与运行**共用同一句柄**，阻塞委派即使一进来就排队也能直接 `await` 同一个 `deferred`，出队时不必重建

并发槽位按 `modelConfigId` 分道：

```kotlin
const val DEFAULT_CONCURRENT = 1
fun normalizeConcurrency(configured: Int): Int =
    if (configured <= 0) DEFAULT_CONCURRENT else configured
```

配置里 `maxConcurrentRequests = 0` 的语义是"不限制"，但对子代理意味着"同一模型不并发打请求"，避免打爆用户配额。因此不存在无限并行。

出队允许跨配置跳过：队首一个满额配置不能把后面属于其它配置的任务一起堵死。用 `running.size < maxConcurrent` 取队首会犯这个错。

`persistenceHook` 让持有者写盘，注册表本身不依赖 `Context`。重启后非终态任务标记 `INTERRUPTED`，这是 `resume` 动作的唯一合法入口——不做"重启后假装还能 cancel"。

### `core/subagent/SubagentDispatchService.kt`

`handleToolCall` 分发五动作。未知参数显式拒绝并指出正确写法（模型偶尔把 `wait=false` 写成 `background=true`，静默忽略会让"后台"委派实际阻塞到结束）。

`delegate`：

1. 解析名单选档案，未命中报 `unknown_agent` 并列出有效名
2. `resolveBindingForProfile` 解析出 `(configId, modelIndex)`，据此设置该配置的并发门径
3. `register` 入表，`notifyParentOnFinish = !wait`
4. `wait=true` 挂起等 `deferred`，信封即工具结果；`wait=false` 立即返回 `job_id` 并要求结束回合

`runSubtask`：`customSystemPromptTemplate = SUBTASK_AGENT_PROMPT_TEMPLATE`，`chatHistory` 第一条放身份简报（你看不到父对话、不得提问、不得再委派、最后一轮只输出一个 JSON）。语言由 `LocaleUtils.usesChineseContent(context)` 决定，不按任务文本猜。模型绑定直接复用任务上已解析的值，不重新解析——否则排队期间配置被改会出现"按配置 A 限流、实际用配置 B 执行"。

`launchRun` 三个必守点：

- 正常、取消、异常三条路径都 `complete(handle.deferred)`，缺一 `wait=true` 就永久挂起
- 协程以 `handle.job` 为父，`cancel` 才能穿透到挂起的 `await`
- 出队任务用 `queuedJob.notifyParentOnFinish`，不能统一传 `true`，否则阻塞委派会在工具结果之外多投递一次

`resolveBindingForProfile` 与 `runSubtask` 共用同一解析函数，杜绝限流口径与真实调用口径漂移。

### 终态合成（宿主优先）

```kotlin
val hostDriven: Pair<String, String>? = when (reason) {
    "model_finished" -> null                                    // 交给模型声明
    "tool_budget_exhausted", "token_limit" -> "blocked" to reason
    "delegation_denied", "permission_denied", "cancelled", "execution_error" -> "failed" to reason
    else -> "failed" to "execution_error"
}
```

模型声明 `completed` 还要过两道账本校验：账本有 `executed && !success` 记录降为 `blocked/tool_failure`；执行过工具时 `evidence_ids` 必须引用至少一个本次成功记录，否则 `blocked/no_valid_evidence`。

### 终局声明解析

必须先剥 think 块再判定。推理模型会输出 `<think>...</think>` + 裸 JSON，而 `stripJsonFence` 只认 `{` 开头——直接用原文会让合法声明被判 `no_final_declaration`，卡片显示"受限"且正文退化成原始 JSON。

正文 + 末尾 json 围栏（deepseek 系常见）取最后一个围栏解析。

### 信封

```
<subagent><final run="..." success="..." tool_count="..." status="..."
                 reason="..." summary_truncated="..." evidence_path="...">
摘要正文（≤4000 字）
[The summary above is the subtask's final report. Use it directly; do NOT read the evidence archive unless the user explicitly asks to verify raw evidence.]
</final></subagent>
```

末行软指令必要：实测父代理看到 `evidence_path` 会立刻 `read_file` 读归档，多一轮调用与长结果显示。

`archiveEvidence` 把完整 Outcome + 账本写到 `filesDir/subagent_runs/<jobId>.json`。归档失败时信封**不带** `evidence_path`，不假装有证据。

### 防轮询

三层约束，缺一层实测就会复现"会话一直卡在处理中"：

| 层 | 位置 | 行为 |
| --- | --- | --- |
| 工具返回文本 | `delegate` | `wait=false` / `queued` 直接 `END YOUR TURN NOW`，明确禁止 sleep/轮询 |
| 提示词 | `SystemToolPromptsInternal` details | 说明轮询会阻断投递 |
| 执行层闸门 | `statusWithPollGuard` | 按状态指纹判进展，连续 3 次无进展仍有未结束任务就返回失败 |

`delegate` 成功后清空这两个计数器。

### `internal object SubtaskDeclaration`

`stripJsonFence` / `findLastJsonFence` / `stripThinkBlocks`。宿主没有这三个工具函数，需在本文件内自建。

## 依赖

- `EnhancedAIService.sendMessage` + `SendMessageCallbacks.onSubtaskToolResult` / `onSubtaskCompleted`
- `SubtaskExecutionState` 的 `toolSessionScopeId`、`takeOutcomeJson`、`isFinalDeclarationCandidate`
- `ModelConfigManager.getModelConfigFlow`、`FunctionalConfigManager.getConfigIdForFunction`
- `ActivePromptManager.activePromptFlow` + `CharacterCardManager.getCharacterCardFlow`（跟随会话解析）
- `ChatServiceCore.appendMessageToChat` / `wakeChatAfterTurn`（步骤 3 新增）

## 验收

- 阻塞委派返回信封；`wait=false` 立即返回 `job_id`
- 子任务内再委派得到 `delegation_denied`
- 证据 JSON 落盘；归档失败时信封无 `evidence_path`
- 同一配置并发满时排队，不同配置互不挤占
- 取消运行中任务，阻塞委派立即拿到 `cancelled` 终态

[DONE] 实现已完成，编译、单测与真机验证均通过（详见 [index.md](index.md) 的实施状态）。
