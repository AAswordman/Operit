# 步骤 3：宿主接入

[返回总计划](index.md)

## 旧实现情况

| 位置 | 现状 |
| --- | --- |
| `ToolRegistration` | 无子代理工具 |
| `SystemToolPromptsInternal` | 无子代理分类 |
| `SystemPromptConfig` | 无名单提示 |
| `ConversationService` | 无开关控制与名单注入 |
| `ToolExecutionManager` 并行白名单 | 不含委派工具，判定用原始工具名 |
| `SubtaskExecutionState` | `require(maxToolCalls in 1..64)` |
| `EnhancedAIService` | `require(subtaskMaxToolCalls in 1..64)`，无 `subtaskSteerProvider` |
| `ChatServiceCore` | 无 `appendMessageToChat` / `wakeChatAfterTurn` |

## 意图

把调度层接到既有管线上。工具名必须是 `subagent:subagent_run`（`包名:函数名`）——`ToolExecutionManager.isSubtaskDelegationTarget` 按这个形式匹配，改名会让"子任务禁止再委派"的闸门失效，子代理可以无限嵌套。

## 新实现

### 工具注册

`ToolRegistration.kt` 末尾：

```kotlin
val subagentDispatch = SubagentDispatchService.getInstance(context)
handler.registerTool(
        name = "subagent:subagent_run",
        descriptionGenerator = { ... },
        executor = { tool ->
            runBlocking(Dispatchers.IO) { subagentDispatch.handleToolCall(tool) }
        }
)
```

### 提示词分类

`SystemToolPromptsInternal` 中英各加一个分类：`Subagent Tools` / `子代理工具`。参数 schema 覆盖 `tool_title`/`action`/`task`/`agent`/`context`/`wait`/`progress_report`/`job_id`/`message`。

`details` 必须写明：`wait=false` 后要结束回合、不要 sleep/轮询、只有用户明确要进度时才用 `status`。

### 名单提示

`SystemPromptConfig.buildSubagentRosterHint(useEnglish, profiles)`：名字 + 简介 + 模型绑定标签，再附调用约定（内置工具不要 `use_package`、工具列表没暴露时走 `package_proxy`、`tool_title` 必填、`wait` 默认 true）。

`getSystemPromptWithCustomPrompts` 加 `subagentRosterHint: String = ""` 参数，在 `composedPrompt` 之后、compose hooks 之前拼接。

### 开关与可见性

`ConversationService`：

```kotlin
val isSubtaskSession = customSystemPromptTemplate == SystemPromptConfig.SUBTASK_AGENT_PROMPT_TEMPLATE
val subagentsEnabled = !isSubtaskSession && subagentProfileManager.subagentsEnabledFlow.first()
val subagentRosterHint = if (subagentsEnabled) buildSubagentRosterHint(...) else ""
val effectiveToolVisibility =
    if (subagentsEnabled) roleCardToolAccess.effectiveBuiltinToolVisibility
    else roleCardToolAccess.effectiveBuiltinToolVisibility + ("subagent:subagent_run" to false)
```

子任务自身不注入名单：子任务内再委派会被拒绝，注入只会诱导子模型发起必然失败的调用。

### 并行白名单

`ToolExecutionManager`：

```kotlin
val parallelizableToolNames = setOf(..., "subagent:subagent_run")
val (parallelInvocations, serialInvocations) = injectedInvocations.partition {
    parallelizableToolNames.contains(resolveToolTarget(it.value.tool).tool.name)
}
```

判定必须用解析后的真实目标名。模型常把委派包进 `package_proxy`，用原始名会让所有代理调用落进串行队列，8 路并行退化成串行。

### 工具配额

`SubtaskExecutionState`：`require(maxToolCalls >= 1)`；`tryStartTool` 的 `executedToolCount == maxToolCalls` 在 `Int.MAX_VALUE` 下永不成立。

`EnhancedAIService`：`require(subtaskMaxToolCalls >= 1)`。

子代理传 `Int.MAX_VALUE`，保留独立会话范围与账本。

### steer 注入

`SendMessageOptions` 加 `subtaskSteerProvider: (suspend () -> String?)? = null`，贯穿 `handleToolInvocation` 与 `processToolResults` 两个签名及其 4 处调用。

注入点在 `TOOL_RESULT` 之后、`normalizeConversationHistoryForModel` 之前：

```kotlin
if (context.subtaskExecution != null && subtaskSteerProvider != null) {
    val steerMessage = subtaskSteerProvider.invoke()
    if (!steerMessage.isNullOrBlank()) {
        context.conversationHistory.add(PromptTurn(kind = PromptTurnKind.USER, content = "[steer] $steerMessage"))
    }
}
```

### 完成投递（本仓库需自建）

`ChatServiceCore` 新增两个入口：

```kotlin
suspend fun appendMessageToChat(chatId: String, message: ChatMessage) {
    chatHistoryDelegate.addMessageToChat(message, chatId)
}

fun wakeChatAfterTurn(chatId: String, messageText: String, turnOptions: ChatTurnOptions, ...) {
    messageCoordinationDelegate.wakeChatAfterTurn(...)
}
```

`MessageCoordinationDelegate.wakeChatAfterTurn` 三个必须避开的坑：

1. **不能只调 `ChatHistoryManager.addMessage`**——只落库，不进内存 `_chatHistory`，当前打开该会话时卡片要切换会话或重启才出现
2. **不能用 `sendUserMessage` 唤醒**——它在 `isLoading=true` 时静默丢弃，父代理还在输出时子代理完成就会丢唤醒
3. **不能"先判 isLoading 再读 turnCompleteCounter"**——两步之间若回合结束，会读到已递增的计数并白等一整个超时窗口

正确写法：直接等会话离开 `activeStreamingChatIds`，这正是各会话 `isLoading` 的派生值，无时序竞态：

```kotlin
pendingWakeupByChatId.remove(chatId)?.waitJob?.cancel()   // 新唤醒替换旧唤醒
if (!messageProcessingDelegate.isChatLoading(chatId)) { dispatch(); return }
val request = PendingWakeupRequest(chatId, turnOptions)
pendingWakeupByChatId[chatId] = request
request.waitJob = coroutineScope.launch {
    withTimeoutOrNull(WAKEUP_MAX_WAIT_MS) {          // 30 分钟
        messageProcessingDelegate.activeStreamingChatIds.first { chatId !in it }
    } ?: AppLogger.w(TAG, "等待会话空闲超时，仍将继续派发唤醒: chatId=$chatId")  // 超时仍派发
    if (pendingWakeupByChatId[chatId] !== request) return@launch
    pendingWakeupByChatId.remove(chatId)
    dispatch()
}
```

同一会话只保留最后一次唤醒：8 路并行完成时逐条排队会让父代理对同一批结果重复播报 8 次。

`PendingWakeupRequest` 与既有的 `PendingAutoContinuationRequest` 并列声明，互不干扰。

## 验收

- 开关关闭：工具不出现在提示词与工具列表，模型看不到名单
- 阻塞委派返回信封；`wait=false` 立即返回且父代理结束回合
- 8 路并行同时启动（看日志时间戳），不退化成串行
- 子任务内再委派被拒绝
- 后台委派完成后会话自动唤醒一次，不重复播报

[DONE] 实现已完成，编译、单测与真机验证均通过（详见 [index.md](index.md) 的实施状态）。
