# 03 失败诊断与日志脱敏

现状有两个问题：bridge 侧在 helper 进程退出后不立刻返回失败，`spawn` 请求会挂到超时；返回时 `lastError` 常为空，只剩 `MCP error -32000: Connection closed`。应用侧 `MCPManager.getOrCreateSession` 又把这个细节覆盖成 `MCP runtime connection failed`，`PluginLoadingScreen` 最终只显示「验证失败」。

本步骤在 bridge 两处补全失败路径：

- `spawn-helper.ts` 在连接失败时带上 `command`、`args`、`cwd` 和 `envKeys`（只列键名），让「找不到入口」「工作目录不存在」「缺 env」可以直接区分。
- `index.ts` 在 helper 发送 `closed` 或 `exit` 而 spawn 仍挂起时立即 `rejectPendingSpawn`，带上 `mcpErrors` 或最近的 helper 输出。

应用侧：

- `McpRuntimeSession` 增加 `getLastFailureDetail()`，`BridgeMcpRuntimeSession` 暴露 `MCPBridgeClient.lastConnectionFailureDetail`，`RemoteMcpRuntimeSession` 记录连接异常文本。
- `MCPManager.getOrCreateSession` 保留该详情，不再覆盖。
- `MCPStarter.registerPlugin` 返回 `PluginRegistration(serviceName, failureDetail)`；`onPluginStarted` 新增 `failureDetail` 参数，加载页优先显示具体原因。
- `MCPStarter.startPluginInternal` 的单插件启动同样写 `lastStartTime`/`lastStopTime`；`verifyAllMcpPlugins` 刷新成功与失败状态。
- `processPlugin` 成功时写 `lastStartTime`，失败时把原因写入 `server_status.json.errorMessage`；管理页插件行展示该错误。

`MCPBridge.sendCommand` 的日志原本直接打印完整 `params`。本步骤在写日志前递归复制 JSON，把 `env` 的值、以及键名命中 `token|secret|password|api_key|authorization|bearer|credential` 的值替换成 `***`，响应日志同样处理。

预期结果：握手失败、进程崩溃、缺 env、错误 `cwd` 都能在加载页看到原因；导出的日志不包含 env 值。

[DONE]
