# 01 核心定向写入与结果类型

子模块仓库：[OperitTerminalCore](https://github.com/AAswordman/OperitTerminalCore)

## 旧实现

```kotlin
// TerminalManager.kt
fun sendInput(input: String) {
    coroutineScope.launch(Dispatchers.IO) {
        val session = sessionManager.getCurrentSession() ?: return@launch
        writeInputToKernel(session, input, "direct-input")
        ...
    }
}

fun sendInterruptSignal() {
    coroutineScope.launch(Dispatchers.IO) {
        val currentSession = sessionManager.getCurrentSession()
        currentSession?.let { writeInputToKernel(it, "\u0003", "interrupt") }
    }
}
```

两个函数都不接收 sessionId：目标只在协程体里从全局状态解析，调用方拿不到结果，无当前会话时静默无操作。

## 新实现

新增 `SessionInputRouter`（`internal object`）承载一次写入的完整语义，`SessionInputResult` 表达四种结果：

| 结果 | 含义 |
| --- | --- |
| `DELIVERED` | 已写入并 flush 到目标会话的 writer |
| `SESSION_NOT_FOUND` | 没有该 id 的会话，什么都没写 |
| `SESSION_NOT_READY` | 会话存在但 writer 尚未挂上，什么都没写 |
| `WRITE_FAILED` | 会话存在但写入抛错，输入没到 pty |

`TerminalManager` 增加两个定向入口：

```kotlin
fun sendInputToSession(sessionId: String, input: String): SessionInputResult
fun sendInterruptSignalToSession(sessionId: String): SessionInputResult
```

两者都只用 `sessionManager.getSession(sessionId)` 解析目标，不出现 `getCurrentSession()`。无参的 `sendInput` / `sendInterruptSignal` 保留，供界面 Ctrl+C 按钮使用，但目标在函数入口处固定：

```kotlin
fun sendInterruptSignal() {
    val sessionId = sessionManager.getCurrentSession()?.id ?: return
    coroutineScope.launch(Dispatchers.IO) { sendInterruptSignalToSession(sessionId) }
}
```

这样即便协程排队期间发生了会话切换，中断仍落在点击时看到的那个会话。

`escapeInputForLog` 一并收敛到 router，`writeInputToKernel` 的日志行为不变。

## 兼容性

- `sendInput` / `sendInterruptSignal` 的调用点（`TerminalEnv`、界面按钮）签名不变。
- `ITerminalService` AIDL 接口未改动：当前主仓库没有绑定方，改动它没有收益。
- 新增的 `SessionInputResult` 是公开类型，主仓库据此区分失败原因。
