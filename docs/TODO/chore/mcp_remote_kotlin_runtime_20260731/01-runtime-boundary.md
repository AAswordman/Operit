# 01 Runtime Boundary

旧实现让 `MCPBridgeClient` 同时承担远程连接、本地进程生命周期和工具调用，导致远程启动依赖 terminal/pnpm。新实现通过 `McpRuntimeSession` 暴露连接、工具发现和工具调用能力；bridge session 只包装本地服务，remote session 只持有 Kotlin SDK client。

运行时 session 的主键是 `pluginId`。本地 bridge 的 service name 仅存在于 bridge adapter 内部，远程 display name 不参与寻址。

[DONE]
