---
fork: https://github.com/yoruuuchan/Operit.git
issue: https://github.com/AAswordman/Operit/issues/1180
---

# 调用链与阻塞点 [DONE]

## 调用链

1. `Tools.System.terminal.hiddenExec` 把参数映射为 `execute_hidden_terminal_command`。
2. ToolPkg runtime 创建 Promise；只有 `JsNativeInterfaceDelegates.callToolAsync` 的 native 线程
   调用 `sendToolResult` 后，Promise 才会 resolve 或 reject。
3. native 线程同步等待 `StandardTerminalCommandExecutor.executeHiddenCommand` 中的
   `runBlocking`，再进入 `TerminalManager` 和当前 `TerminalProvider`。
4. 本地 provider 复用 `executorKey` 对应的后台 login shell，通过 marker 区分每条命令；
   SSH provider 使用独立 exec channel。

因此 Promise 本身不是阻塞源。只要 provider 或 manager 不返回，native callback 就不会发生，
上层 Promise 会永久 pending。

## 真实阻塞点

最新 `dev` 的本地 hidden executor 有三类 timeout 覆盖缺口：

- `getOrCreateHiddenExecShell` 在 timeout 之外执行，包含全局创建 mutex、
  `ProcessBuilder.start()` 和固定 30 秒 READY 等待。
- 命令写入虽然位于 `withTimeout` 中，但使用结构化 `withContext(Dispatchers.IO)`；
  native pipe write/flush 不响应协程取消时，父协程必须继续等待这个 IO 子任务，timeout 无法返回。
- shell 已退出但后代仍持有 stdout 时，reader 可能永远等不到 EOF，结果 marker 也不会回来。

SSH 的 channel 建立也没有消耗同一 timeout 预算。上述任一路径阻塞，都会让 native callback
缺席，而不是在 QuickJS 前或 Promise 解析阶段卡住。

## 基线复现

在独立临时工作树中固定 `upstream/dev@5948dff9`，只加入进程注入测试缝，不加入修复。
以下三个 wall-clock 测试都在测试自身 deadline 到达后失败，证明旧实现没有完成调用：

- READY marker 永不出现；
- 另一个 key 等待全局 executor 创建 mutex；
- shell stdin write 永久阻塞。

测试直接约束 `LocalTerminalProvider` 通用层，没有改动 QQbot。

## 定位结论

已确认阻塞发生在 Kotlin/native provider 的 executor 准备、调度和 IO 生命周期。旧实现不是某条
异常分支漏调 `complete`，而是 timeout 作用域外或结构化取消等待中的 native IO 永远没有返回，
导致上层 callback 和 Promise 完成路径都无法到达。

## 修复位置

修复放在 `TerminalManager` 与 `TerminalProvider` 通用执行链，QQbot 仍使用原有 API 和参数。
