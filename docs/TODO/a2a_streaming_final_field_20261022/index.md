---
fork_repository: https://github.com/yoruuuchan/Operit.git
branch: fix/issue-1307-a2a-streaming-final
issue: https://github.com/AAswordman/Operit/issues/1307
---

# A2A 流式事件去掉 schema 外的 final 字段

## 原状

`A2aHttpHandler.statusUpdateEvent()` 在 `TaskStatusUpdateEvent` 里写了一个 `final` 布尔位，用来告诉流式客户端「这是最后一帧」，SSE 循环也靠它退出。A2A 1.0 的 `lf.a2a.v1.TaskStatusUpdateEvent` 只有 `taskId`、`contextId`、`status` 和 `metadata`。官方 `a2a-sdk` 用 `json_format.ParseDict` 严格解析，读到 `final` 就抛 `ParseError`，于是 `SendStreamingMessage` 和 `SubscribeToTask` 在官方 SDK 客户端上完全不可用。非流式路径不经过这个函数，所以 `SendMessage`、`GetTask`、`ListTasks` 一直正常，缺陷只在流式暴露。

`docs/doc-src/feature-protocol/external_a2a_server.md` 当时把这行为写成了契约的一部分，实现与文档一致，但两者一起偏离了规范。

## 意图

终态由 `status.state` 表达，流在终态状态帧之后关闭；帧里的成员集合严格等于 A2A 1.0 定义，官方 SDK 可以逐帧解析到流结束。

非流式响应保持不变：`Task` 的成员集合、`artifacts` 的产生条件、失败状态里的 `status.message` 都不动。

## 作用域

- `integrations/a2a/A2aJsonSerialization.kt`：新增协议序列化单元，集中 `Task`、`TaskStatusUpdateEvent`、`TaskArtifactUpdateEvent` 与 JSON-RPC envelope，并在生成帧时给出「此帧是否结束流」。
- `integrations/a2a/A2aHttpHandler.kt`：流式循环按帧的结束标记退出；序列化调用改走新单元，删除 `statusUpdateEvent()`、`taskToJson()` 及重复的 envelope 构造。
- `integrations/a2a/A2aTaskManager.kt`：`A2aTaskEvent.Status` 去掉 `final` 字段，事件本身只描述状态变化。
- `docs/doc-src/feature-protocol/external_a2a_server.md`：事件示例与结束条件改为按 `status.state` 判断。
- `app/src/test/.../integrations/a2a/A2aJsonSerializationTest.kt`：回归测试。

## 验证

- 单元测试 `A2aJsonSerializationTest` 断言 `statusUpdate`、`artifactUpdate`、`Task` 的键集合等于 1.0 定义，递归断言任何帧都不含 `final`，并断言四种终态结束流、四种非终态不结束。`:app:testDebugUnitTest` 全量通过。
- 把修复后的序列化输出交给官方 `a2a-sdk` 1.1.5 解析：`lf.a2a.v1.TaskStatusUpdateEvent` 的字段集经 `DESCRIPTOR.fields` 核实为 `task_id / context_id / status / metadata`；20 条覆盖八种任务状态、失败状态消息、artifact 分片和非流式 `Task` 的样本全部经 `json_format.ParseDict` 通过，`WhichOneof("payload")` 均落到正确成员。
- 同一条检查对旧帧（带 `final: true`）复现出 issue 里的原报错 `ParseError: Message type "lf.a2a.v1.TaskStatusUpdateEvent" has no field named "final"`，确认被修掉的正是这个失败点。

[DONE]
