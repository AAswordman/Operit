# 02 任务生命周期

## 修改

- 为每个 A2A 请求生成服务端 task ID，并保存上下文 ID、内部聊天 ID、状态、输出和活动流会话。
- 每个新的 A2A 上下文创建独立 Operit 聊天，后续关联到同一上下文的新请求复用对应聊天。
- `SendMessage` 按 A2A 1.0 的 `returnImmediately` 语义返回任务，后台将流式聊天执行映射为工作、完成、失败或取消状态。
- `SendStreamingMessage` 和 `SubscribeToTask` 用 SSE 发送带 JSON-RPC envelope 的 A2A 1.0 Stream Response。
- `GetTask`、`ListTasks` 与 `CancelTask` 分别提供快照、分页查询和取消。

## 预期结果

A2A 调用不会共享或篡改用户正在进行的普通聊天，客户端可以查询和取消服务仍存活期间的任务。

[DONE]
