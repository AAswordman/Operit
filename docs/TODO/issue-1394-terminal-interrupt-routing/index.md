---
fork_repository: https://github.com/yoruuuchan/Operit.git
---

# 终端中断按会话定向

关联 Issue：[AAswordman/Operit#1394](https://github.com/AAswordman/Operit/issues/1394)

## 原状

`Terminal.sendInterruptSignal(sessionId)` 写的不是"向这个会话发中断"，而是先把全局当前会话切换成该 id，再对"当前会话"发中断：

```kotlin
fun sendInterruptSignal(sessionId: String) {
    terminalManager.switchToSession(sessionId)
    terminalManager.sendInterruptSignal()
}
```

`TerminalManager.sendInterruptSignal()` 随后在 `Dispatchers.IO` 上启动协程，在协程体里才 `getCurrentSession()` 并写入 `\u0003`。目标因此是在挂起之后才从全局状态解析的，`switchToSession` 与写入之间的任何一次切换都会改写接收者。`sendInput(sessionId, input)` 是同一种写法。

由此产生的可观测后果：

- 后台会话或 hidden exec 超时取消时，可见终端会被切到那个会话。
- 中断可能落到别的 pty：`switchToSession` 与写入之间隔一次协程启动。
- 未知 id 静默失败：`SessionManager.switchToSession` 只记一条 `Session not found` 并返回 `false`，调用方丢弃返回值，中断落到当前会话。
- 没有当前会话时是空操作：`currentSession?.let { ... }` 什么都不发也不报错，而 `cancelTimedOutCommand` 仍在等该会话 settle。

字节编码本身没有问题：`\u0003` 经 writer 写入是单个 `0x03`，pty 的 `stty -a` 为 `isig icanon`，行规程会把它转成 SIGINT。问题只在路由。

## 意图

让"点名会话的输入/中断"只作用于该会话，并且返回明确结果：

- 不读也不写全局当前会话，前台终端在后台取消时保持不动。
- 无效 sessionId 得到 `SESSION_NOT_FOUND`，未就绪得到 `SESSION_NOT_READY`，写入失败得到 `WRITE_FAILED`。
- 未点名会话的入口（界面 Ctrl+C 按钮）仍作用于调用时的当前会话，但目标在调用时就固定。

## 作用域

- `terminal` 子模块（OperitTerminalCore）：新增 `SessionInputRouter` 与 `SessionInputResult`；`TerminalManager` 新增 `sendInputToSession` / `sendInterruptSignalToSession`，无参版本在调用时固定目标。
- `app`：`Terminal.sendInput/sendInterruptSignal(sessionId)` 改为直接调用定向入口并返回结果；`StandardTerminalCommandExecutor` 的超时取消与 `control=ctrl+c` 处理该结果；`ChatViewModel` 取消失败时给出提示。
- 新增失败文案资源（默认语言与已有 7 个语言）。
- CI：把 `:terminal:testDebugUnitTest` 加进两个 workflow 的 JVM 测试任务，让这组回归测试真正执行。

不在范围内：pty 编码、`switchToSession` 语义、AIDL `ITerminalService` 接口（当前无调用方）。

## 验收

- Ctrl+C 只写入指定会话，前台当前会话 id 不变。
- 后台命令超时取消只中断目标会话，未收到中断时不谎报 settle。
- `sendInterruptSignal("不存在")` 返回 `SESSION_NOT_FOUND`，不写入任何会话。
- 界面 Ctrl+C、普通输入、会话切换保持可用。
- `:terminal:testDebugUnitTest` 与 `:app:compileDebugKotlin` 通过。

步骤：

- [01 核心定向写入与结果类型](01_core_session_input_router.md)
- [02 应用侧调用与失败上报](02_app_call_sites.md)
- [03 回归测试与构建验证](03_tests_and_build.md)
