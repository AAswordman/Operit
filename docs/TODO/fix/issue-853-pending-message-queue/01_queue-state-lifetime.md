---
fork: https://github.com/AAswordman/Operit.git
issue: https://github.com/AAswordman/Operit/issues/853
---

# 队列状态归属

## 旧实现

输入屏幕的 `pendingQueueMessages` 以当前会话 ID 作为 `remember` key。会话切换时，旧列表
离开组合树且没有任何 ViewModel 或存储层持有其内容。队列发送协程也从当前选择会话读取 ID，
协程等待期间切换会话可能将消息发送到错误的会话。

## 修正

新增独立的队列状态存储器，由 `ChatViewModel` 持有并按会话 ID 保存消息、展开状态和自动
出队控制状态。输入屏幕只订阅当前会话的状态并调用 ViewModel 操作。发送时捕获队列所属
会话 ID，并通过现有的后台发送接口提交该 ID。

## 预期结果

切换至其他会话再返回时，队列面板保留原有消息。原会话完成输出后，队列消息仍会发送到
原会话，而不会依赖界面当前显示的其他会话。

## 验证

- 已新增 `PendingMessageQueueStoreTest`，覆盖跨会话保留与取消后的自动出队控制
- 已执行 `git diff --check`
- 未执行 Gradle 测试，遵循仓库默认执行约束

[DONE]
