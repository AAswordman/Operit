---
title: Remote MCP Session Recovery
status: in-progress
fork: https://github.com/yoruuuchan/Operit
issue: https://github.com/AAswordman/Operit/issues/1312
---

# Remote MCP Session Recovery

修复远程 Streamable HTTP MCP 的 session 失效后，首次工具调用失败、需要模型再次调用才能恢复的问题。

## 原状

- [RemoteMcpRuntimeSession](../../../app/src/main/java/com/ai/assistance/operit/data/mcp/plugins/RemoteMcpRuntimeSession.kt) 在 `callTool` 抛异常时，只把 `connected` 置为 false 再原样抛出。
- 远程 Streamable HTTP 服务端在 idle 超时或重启后丢弃 session，携带旧 `Mcp-Session-Id` 的请求会被服务端以 HTTP 404 或 `Session not found` 拒绝。
- 首次工具调用因此失败；模型收到错误后再次调用，`MCPManager.getOrCreateSession` 才重建 session 成功，于是每次 session 失效都白白浪费一次工具调用。

## MCP 规范依据

[MCP 规范 2025-06-18 · Transports · Session Management](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports#session-management) 第 3、4 条：

> 3. The server **MAY** terminate the session at any time, after which it **MUST** respond to requests containing that session ID with HTTP 404 Not Found.
>
> 4. When a client receives HTTP 404 in response to a request containing an `Mcp-Session-Id`, it **MUST** start a new session by sending a new `InitializeRequest` without a session ID attached.

所以「收到 404 就重新 initialize」不是启发式猜测，而是规范里的 MUST。而且服务端是在 HTTP 层拒绝这个未知 session 的，请求没有进入 tool handler，工具确定没执行——这正是安全重放的前提。

## MCP Kotlin SDK 0.10.0 的失效信号

从 SDK 源码确认失效是如何冒泡到应用层的：

- `StreamableHttpClientTransport.performSend` 在 POST 收到非成功状态码时抛 `StreamableHttpError(code = status, body)`，其中 `code` 就是 HTTP 状态码。
- `AbstractClientTransport.send` 捕获非 `McpException` 后，包成 `McpException(code = INTERNAL_ERROR, message = "Error while sending message: ...", cause = StreamableHttpError(404))`。
- 关键安全事实：服务端在 **HTTP 层** 就拒绝了这个未知 session，请求根本没有被派发到任何 tool handler，所以工具 **确定没有执行**。

其它失败走不同路径，执行状态未知，一律不能重试：

- 请求超时：`Protocol.request` 抛 `TimeoutCancellationException`，或 `McpException(code = REQUEST_TIMEOUT)`。
- 连接中断：`McpException(code = CONNECTION_CLOSED, message = "Connection closed")`。
- 普通 IO 异常：`SocketTimeoutException`、连接重置等，被包成 `McpException(INTERNAL_ERROR, cause = <io>)`。

## 意图

- 只在**明确判定旧 session 已失效且请求未执行**时（HTTP 404 / `Session not found`），由客户端重新初始化并完成能力协商。
- 重新握手成功后，把原请求**恰好重放一次**。
- 超时、断线、以及任何执行状态未知的失败，原样抛出，绝不重放。
- 恢复失败时抛出携带诊断信息的 `RemoteMcpSessionRecoveryException`，交给模型。
- 并发命中同一个死 session 的请求共享一次重连。

## 作用域

新增一个可注入的传输 seam，把 SDK / Ktor 细节关进工厂，让恢复状态机可以在 JVM 单测里跑：

- `RemoteMcpClient` / `RemoteMcpClientFactory`：传输客户端的窄接口；工厂负责完整 initialize 握手并返回已连接的客户端。
- `SdkRemoteMcpClientFactory`：生产实现，持有共享 `HttpClient`，每次 `create()` 造一个全新的 SDK `Client` + transport，`dispose()` 关闭共享 HTTP 客户端。
- `RemoteMcpSessionRecovery`：纯函数分类器，遍历 cause 链判断是否为「已确认的 session 失效且请求未执行」。
- `RemoteMcpSessionRecoveryException`：恢复失败或重放仍失败时抛出，message 带诊断。

`RemoteMcpRuntimeSession` 重写为：

- `connectionMutex` 串行化 connect / close / recovery；`sessionEpoch`（AtomicLong）标识 session 代次，让等锁的请求复用别人已经重建好的 session。
- `withSessionRecovery` 包住 `listTools` / `callTool`：先跑一次，捕获异常后用分类器判定；确认失效才 `recoverSession` + 重放一次；否则标记断连并原样抛出。
- build-then-swap：先 `factory.create()` 拿到新客户端，再替换引用、最后关闭旧客户端，保证并发读者在切换过程中永远看不到 null 客户端。
- `tornDown` 守卫：`close()` 之后仍在等锁的请求不会把一个已被 owner 拆除、工厂资源已释放的 session 复活。

`MCPManager` 与 `McpRuntimeSession` 接口、本地 bridge 传输、SSE 传输路径都不改。

## 验证

单元测试（`RemoteMcpSessionRecoveryTest` + `RemoteMcpRuntimeSessionRecoveryTest`）覆盖：

- 分类器：404 / 包裹的 404 / `Session not found`（含大小写与子串）判为可恢复；REQUEST_TIMEOUT、SocketTimeout、CONNECTION_CLOSED、500、401、无状态码的 StreamableHttpError、裸 IO、工具级错误判为不可恢复；自引用 cause 不死循环；深层 cause 链仍能识别。
- session：正常 session 只调一次且不重连；确认失效后重连并重放恰好一次；包裹的 session-not-found 同样触发恢复；listTools 同样恢复；超时原样抛出且不重连不重放；重连失败给出诊断；重放仍失败给出诊断且只重放一次（重试边界）；8 个并发请求命中同一死 session 共享一次重连；close 释放工厂 HTTP 客户端。
