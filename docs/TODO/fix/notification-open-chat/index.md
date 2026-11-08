---
repo: https://github.com/3316891527/Operit
status: IMPLEMENTED_CI_PENDING
---

# 点击通知打开来源会话

现有回复通知和工具通知只启动应用，没有携带来源会话。点击通知时如果应用停留在其他聊天，会看到错误的上下文。

本次引入上游 [PR #1427](https://github.com/AAswordman/Operit/pull/1427)，来源提交为 [66a42c6](https://github.com/AAswordman/Operit/commit/66a42c6de16656247f0d568b5438541e5f7e6148) 和 [4d88738](https://github.com/AAswordman/Operit/commit/4d887380d81c54f8b457e5765c6f7bbdd8015666)。系统操作工具适配个人版本已有的 `core/tools/builtin/system` 包；导航入口沿用现有应用结构。

## 实施范围

1. `AIForegroundService` 在回复通知中携带来源 chatId，以 chatId 派生 PendingIntent 请求码，避免多个会话复用同一个通知点击目标。
2. `StandardSystemOperationTools.sendNotification` 接收可选 `chat_id`，与中英文工具定义同步。未传时继续只启动应用。
3. `MainActivity` 在冷启动和新 Intent 到达时接收会话请求，以单调递增编号区分连续点击。
4. `OperitApp` 先检查会话存在，再设置当前会话并导航；目标已删除或切换失败时清除待处理请求，不切换到失效会话。

本次不更改通知权限、数据库和持久化格式。已存在且不带会话 ID 的通知维持原行为；工具通知只有显式提供 `chat_id` 才定位会话。

## 验证

本地只执行差异、仓库卫生和文档链接静态检查，不运行 Android 构建或 JVM。工作分支派发 Debug 与 Android Tests，PR 的 Candidate checks 负责验证合并候选；执行结果与设备结果分别记录，不把派发受理写成测试通过。

完整行为检查见[回归清单](regression.md)。

[DONE]
