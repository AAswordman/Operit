# 阻止损坏的 MCP 参数

## 旧实现情况

`MCPToolExecutor.validateParameters` 只验证工具名格式。任何参数都会继续到
`RemoteMcpRuntimeSession.callTool`，包含 `U+FFFD` 的长文本也会被发送到远程 MCP 服务。

## 修正意图

替换字符表示 UTF-8 解码时已有字节丢失，应用无法推断原始内容。重试会再次触发写入，
不能作为数据修复手段。应在连接远程服务前拒绝该调用，并提供无敏感内容的可定位错误。

## 新实现情况

- 逐个检查 MCP 工具参数中的 `U+FFFD`
- 记录首个损坏位置的字符偏移和 UTF-8 字节偏移
- 验证失败时不创建或调用远程 MCP 会话

## 验证

未运行构建或测试，遵循仓库执行准则。已通过代码路径审阅确认参数验证发生在
`getOrCreateSession` 与 `callTool` 之前。

[DONE]
