---
status: DRAFT
---

# 终端托管任务接口

命令由 Android 应用进程内的任务作用域持有。启动返回 `runId`，查询时继续使用相同 ID。单次等待结束、取消命令、关闭整个会话是三个独立操作。

## 脚本与工具入口

已有 `Tools.System.terminal.exec`、`execStreaming` 和对应旧结果格式继续可用。它们的请求超时会按自己的命令 ID 取消；排队中的命令仅移除自己，运行中的命令无法恢复时关闭会话。工具创建的新本地自动化会话关闭分页和 Git 密码提示，人工创建的会话保持原环境。

新增脚本方法与原生工具对应如下：

- `Tools.System.terminal.start(sessionId, command, { timeoutMs, yieldMs })` 对应 `start_terminal_task`
- `Tools.System.terminal.poll(runId, { yieldMs })` 对应 `poll_terminal_task`
- `Tools.System.terminal.cancel(runId)` 对应 `cancel_terminal_task`

原生参数使用 `session_id`、`command`、`run_id`、`timeout_ms` 和 `yield_ms`。`timeoutMs` 默认 1800000 毫秒，至少 3000 毫秒，从任务开始计时，包含排队时间。`yieldMs` 默认 10000 毫秒，范围 0～30000 毫秒；查询请求被取消或等待时间用尽都不会取消已经拿到 ID 的任务。启动请求在交付 ID 前被取消时，会取消该新任务，避免留下调用方无法查询的命令。

`super_admin` 新增 `terminal_start`、`terminal_poll` 和 `terminal_cancel`。其中 `terminal_start` 接收 `command`，可选 `sessionId`、`timeoutMs`、`yieldMs`；省略会话 ID 时复用当前对话的默认会话。`terminal_poll` 接收 `runId` 与可选 `yieldMs`，`terminal_cancel` 只接收 `runId`。旧 `terminal`、`terminal_wait` 等入口继续保留，旧等待工具仍使用排队标记；托管查询直接读取任务状态，不发送探测命令。

```javascript
const session = await Tools.System.terminal.create("managed-example");
let task = await Tools.System.terminal.start(session.sessionId, "sleep 5; printf 'done\n'", {
    timeoutMs: 60000,
    yieldMs: 1000
});
// 保存同一个 ID，等待下一次查询时不要重跑命令。
const runId = task.runId;
task = await Tools.System.terminal.poll(runId, { yieldMs: 10000 });
```

## 状态和结果

`queued` 表示尚未执行，`running` 表示已开始，`cancelling` 表示正在清理。终态为 `completed`、`failed`、`cancelled` 和 `timed_out`。只有确认命令退出码为 0 且无异常终止原因时才是 `completed`；非零退出、缺失命令退出码和会话退出都不能当作命令成功。工具调用的 `success` 表示任务操作成功，命令成败由 `status` 和 `exitCode` 表达。

`exitCode` 是命令退出码，未知时为空；`sessionExitCode` 单独保存观测到的会话主进程退出码。`terminationReason` 说明非零退出、会话关闭、执行期限或清理失败。主动取消与执行超时的 `cancellationOutcome` 区分 `removed_from_queue`、`interrupted`、`session_closed`、`cleanup_incomplete` 和 `already_finished`；其中 `cleanup_incomplete` 不代表进程已经可靠停止。

`outputMode` 固定为 `tail_snapshot`。`output` 是最多 12000 字符的输出尾部，`outputTruncated` 标记截断；`screen` 是最多 12000 字符的最新动态画面。两者都是替换快照，不能把每次查询结果反复追加成日志。输出尾部不包含完整长日志的持久化保证。

任务记录最多 32 条，容量不足时只淘汰已结束记录；全部仍活动时拒绝新建。应用进程退出后不恢复旧 ID。失效或淘汰的 ID 明确报错，不自动重跑命令。

## 验证

源码回归用例和设备检查见[回归记录](regression.md)，参考适配来源见[来源说明](sources.md)。仓库 Actions 同时执行应用和终端核心 JVM 单测，Debug 构建验证原生库与应用集成。设备执行与原生进程信号仍需在 Android 设备上验证。
