# 02 应用侧调用与失败上报

## 旧实现

```kotlin
// Terminal.kt
fun sendInput(sessionId: String, input: String) {
    terminalManager.switchToSession(sessionId)
    terminalManager.sendInput(input)
}

fun sendInterruptSignal(sessionId: String) {
    terminalManager.switchToSession(sessionId)
    terminalManager.sendInterruptSignal()
}
```

三处问题叠在一起：切换全局前台会话（可见终端跳走）、切换与写入之间隔着协程启动（目标可能被改写）、丢弃 `switchToSession` 的布尔结果（未知 id 落到当前会话）。

## 新实现

`Terminal` 直接调用核心的定向入口，并返回结果：

```kotlin
fun sendInput(sessionId: String, input: String): SessionInputResult =
    terminalManager.sendInputToSession(sessionId, input)

fun sendInterruptSignal(sessionId: String): SessionInputResult =
    terminalManager.sendInterruptSignalToSession(sessionId)
```

`switchToSession` 的显式调用于是从这两个函数里消失，但 `/terminal.switch` 工具、SetupScreen 和界面切换逻辑保持不变。

### 超时取消

`StandardTerminalCommandExecutor.cancelTimedOutCommand` 原本无条件中断后立即等待 settle。目标会话不存在时，中断发不出去，等待必然超时，日志里留下一条 `cancellation did not settle`，把"没发出去"伪装成"发出去但没停"。

现在先看返回结果：未 `DELIVERED` 时记录目标与原因并直接返回，不再进入 settle 等待。

### ctrl+c 输入

`input` 工具的 `control=ctrl` + `input=c` 走同一条中断路径。`applyTerminalInput`、`applyModifierControl`、`applyCtrlCombination` 里的每处写入都经 `requireInputDelivered` 校验，失败时抛 `IllegalStateException`，由 `inputInSession` 的 `Exception` 分支转成工具失败结果，避免工具报告"已向会话写入"而实际什么都没发生。

### 工作区命令取消

`ChatViewModel.cancelWorkspaceCommandExecution` 原本发完中断就不管了。现在把发送挪到 `viewModelScope` 的 IO 上下文执行，失败时回滚 `isCancelling` 并提示用户，避免界面永远停在"正在取消命令..."。

新增文案 `chat_execute_command_cancel_failed`，提供默认语言与已有 7 个语言（`en`、`ko`、`es`、`ms`、`id`、`pt-rBR`、`ro`）；`ja` 目前只覆盖了少量 key，不新增。
