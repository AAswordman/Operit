---
fork: https://github.com/yoruuuchan/Operit
branch: fix/http-api-message-tool-truncation
status: implementation-complete
issue: https://github.com/AAswordman/Operit/issues/1375
pr: https://github.com/AAswordman/Operit/pull/1414
---

# HTTP 入口消息与工具调用截断修复

## 原本状况

Issue #1375 报告了两个现象，且两者都只在特定条件下出现：

1. 入口侧：MacroDroid 通过 `POST /api/external-chat` 发送触发文本，服务端返回 200，但模型收到的是被削掉首段的残片（`<tool`、`TEST`、`TESTLINE-完整` 等）。
2. 出口侧：模型每轮生成工具调用时，会在 `<tool_xxx` 标签开头处随机截断，整个调用丢失（残片 `<tool_L6Gi`、`<`、`<tool_4rx2`、`<tool_Z9qK`、`<tool_Z9q`）。

手动输入路径不受影响，非流式模式同样复现，换供应商（DeepSeek / 智谱）同样复现。

## 根因

两个现象来自同一个缺陷：`SAVEPOINT` / `ROLLBACK` 事件没有携带生产者记录的权威偏移量。

- Provider 在请求开始时调用 `emitSavepoint(id)`，此时 `receivedContent` 为空，recorded offset 为 0。
- 但 `TextStreamEvent` 只带 `(eventType, id)`，偏移量由各消费者用自己的 `contentBuffer.length` 推导。
- 内容 chunk 与事件走不同的协程和可重放旁路，消费者读到 `SAVEPOINT` 时往往已经把被中止尝试的尾部 chunk 追加进自己的缓冲区，于是推导出的偏移量大于 0。
- 重试被接受时 `ROLLBACK` 只把文本截断到该非零偏移，留下被中止尝试的一段前缀。这段前缀随后被 `MessageProcessingDelegate.resolveFinalContent` 优先采用（事件通道 replay 非空时以 `aiMessage.content` 为准），进入历史与 UI。
- 残留前缀的长度取决于调度时序，因此表现为「固定长度首段被削掉」和「在 `<tool_` 开头处随机截断」。HTTP 入口多经过 NanoHTTPD worker → `runBlocking` → `serviceScope` → `shareRevisable` 几跳，chunk 与事件的入队间隔更大，所以更容易命中。
- `detectAndRepairTruncatedToolRound` 只能修复带 `name="` 的残块，裸 `<tool_XXXX` 无法在下游补救，所以必须在回滚时就用正确偏移量。

## 修改意图

把「回滚到哪个位置」变成生产者记录、消费者直接使用的数据，并且用「原始 chunk 序列里的区间」而不是「可见文本长度」来描述：

- `TextStreamEvent(eventType, id, contentLength, discardedLength)`：
  - `contentLength` 是该次尝试在**原始 chunk 序列**中的起始偏移；
  - `discardedLength` 是该次尝试产出的字符数，回滚删除 `[contentLength, contentLength + discardedLength)`。
- `TextStreamRevisionTracker` 同时保留 raw 与 visible 两份缓冲：`append` 累积 raw，`rollback` 记录要删除的 raw 区间后重算 visible。这样 chunk 与事件谁先到都能得到同一结果；消费者不再从自己的缓冲区推导偏移。
- Provider 侧记录 `discardedTotal`（已被回滚删除的字符总数）。`receivedContent` 在回滚时会被截断，所以 `receivedContent.length + discardedTotal` 才是原始序列偏移；`emitRollback` 用 `receivedContent.length - retainedLength` 得到本次尝试长度。
- 一次执行可能包含多轮请求（工具调用后继续请求），而每个 Provider 的偏移都从自己那轮开始。`EnhancedAIService` 在把事件上抛到整条响应流的事件通道时，用本轮开始前已发射的字符数做平移（`TextStreamEvent.shiftedBy`），保证 `MessageProcessingDelegate`、`resolveFinalContent`、`resolved` 等以整条响应为单位的下游拿到正确偏移。
- 所有以「拼接 chunk」方式消费响应的地方改用 `collectRevisionAppliedText()` / `applyRevisionEvents()`：`StandardChatManagerTool`（HTTP 与工具入口）、`ExternalChatHttpServer` SSE 终态、`A2aTaskManager` 终态、`PhoneAgent`。
- `shareRevisable` 直接复用生产者的 `eventChannel`，不再额外包一层 `share()`。额外包一层会让事件由另一个协程转发，最后一条 `ROLLBACK` 可能在文本流结束后才到达，重放时丢失。
- `MessageProcessingDelegate.resolveFinalContent` 统一改为用完整 chunk replay + 完整事件 replay 计算最终文本，不再优先采用可能落后的 `aiMessage.content` 快照。
- 入口链路诊断：HTTP 读取、请求准备、会话消息构建、`getMemoryFromMessages`、Provider 请求历史、工具调用解析都输出长度 + SHA-256 摘要 + 首尾预览，便于线上核对「同一段文本在各阶段是否一致」，且不把完整私密内容写进日志。
- `ExternalChatHttpServer.readRequestBody` 抽为 `ExternalChatRequestBodyReader`，严格按 `Content-Length` 读满字节，避免依赖 NanoHTTPD 未调用的 `parseBody`。

## 作用域

- `util/stream/RevisableTextStream.kt`、`TextStreamRevisionTracker.kt`、`RevisionAppliedText.kt` 的事件协议、回滚语义与收集工具
- `api/chat/EnhancedAIService.kt`、`services/core/MessageProcessingDelegate.kt`、`ui/features/chat/components/RevisableTextStreamRemember.kt` 三处消费者
- `api/chat/llmprovider/{OpenAI,Claude,Gemini}Provider.kt` 三处生产者
- `core/tools/defaultTool/standard/StandardChatManagerTool.kt`、`core/tools/agent/PhoneAgent.kt`、`integrations/a2a/A2aTaskManager.kt` 的响应收集
- `integrations/http/ExternalChatHttpServer.kt` 请求体读取与 SSE 终态
- `util/ChatTrace.kt` 诊断工具
- `api/chat/enhance/ToolExecutionManager.kt`、`core/chat/AIMessageManager.kt` 诊断
- 回归测试：`ToolTagStreamParsingTest`、`ExternalChatRequestBodyReaderTest`、`ExternalChatHttpIngressTest`、`TextStreamRevisionTrackerTest`、`RevisionAppliedTextTest`、`ProviderRetryRollbackTest`

## 期待结果

- HTTP 发送的任意长度文本，无论 TCP 如何分片、JSON 中是否含多字节字符，都原样进入会话与模型请求
- 工具调用在任意 chunk 边界下都能被完整解析，不再因回滚偏移错误丢失
- 正常文本、非流式模式、手动输入路径行为不变
- 重试撤回只丢弃被中止尝试的输出，savepoint 之前的内容保持
- 多轮工具调用中，后续轮次的回滚不会误删前面轮次的文本

## 验证记录

- `:app:testDebugUnitTest` 全量通过（1367 tests, 0 failures）
  - `TextStreamRevisionTrackerTest` 覆盖 chunk 先到/事件先到两种时序
  - `RevisionAppliedTextTest` 覆盖收集、转发、多轮请求的偏移平移
  - `ToolTagStreamParsingTest` 覆盖每个二分切点、逐字符切分与多种固定块长
  - `ExternalChatRequestBodyReaderTest` 覆盖逐字节、多字节字符跨块、大于 NanoHTTPD 8192 缓冲区
  - `ExternalChatHttpIngressTest` 使用真实 NanoHTTPD + 真实 TCP socket，按 13 字节/1 字节分段写入并比对长度与 SHA-256
  - `ProviderRetryRollbackTest` 使用真实 `OpenAIProvider` + OkHttp 拦截器：第一次尝试输出 `<tool_L6Gi` 后断开，第二次返回完整工具调用；另有连续两次尝试都被中止的用例
- `:app:assembleDebug` 通过（`llm/mnn` 的 NDK flatc 宿主编译问题与本次改动无关，构建时排除 `:mnn` 的 external native 任务并使用共享树预编译产物）
- 端到端：安装到 AVD（`operit35`，Android 16 x86_64），外部 HTTP 服务监听 8094，mock OpenAI SSE 服务经 `adb reverse` 接入
  - 入口侧：981 字符 / 2881 字节消息，`len/bytes/sha256` 在 `readRequestBody` → `startMessageToAIStream` → `buildUserMessageContent` → `getMemoryFromMessages` → `sendMessage requestHistory` 全链路一致（`head=HEAD-MARKER-012345678901`、`tail=TAIL-MARKER-987654321098`）
  - 出口侧：第一次尝试输出 `<tool_L6Gi` 后断开，重试返回完整 `<tool_AB12 name="device_info">...</tool_AB12>`；回滚日志 `targetLength=0 resultingLength=0`，`ai_response` 中不含被中止尝试的前缀，工具调用被解析（`Found 1 tool invocations`）并执行
  - 多轮：第二轮请求第一次尝试输出 `PREFIX-LEAK-` 后断开，回滚日志 `targetLength=176 resultingLength=166`，只删除第二轮的中止片段，第一轮输出保留
  - SSE：`delta` 中仍会看到已发出的中止片段（已发送的 delta 无法撤回），`done` payload 由 revision history 重建，只含完整工具调用
  - 非流式与纯文本路径在同一构建上验证通过
