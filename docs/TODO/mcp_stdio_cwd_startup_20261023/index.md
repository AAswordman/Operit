---
title: MCP stdio cwd and startup diagnostics
status: implemented-verification-pending
fork: https://github.com/yoruuuchan/Operit.git
---

# MCP stdio cwd 与启动诊断

## 原状

- `mcpServers.<id>` 的模型只有 `command`、`args`、`env`、`autoApprove`、`disabled`，标准 MCP 的 `cwd` 会在读取、导入、部署和启动各环节被丢弃。
- 本地插件是否“已安装”按 Android 侧 `Download/Operit/mcp_plugins/<pluginId>` 目录判定。用户已在 Linux 终端维护好的插件目录即使写进 `mcpServers`，也会被判定为未安装，无法进入启动列表。
- 启动本地插件时 `MCPStarter` 固定把 `cwd` 设为 `~/mcp_plugins/<shortName>`，配置中声明的绝对命令、绝对入口或工作目录不会生效。
- `initBridge()` 对所有本地插件无条件检查 `pnpm`，即使插件只使用 `node` 或 `sh`。
- 连接失败时 `MCPManager` 把 bridge 的具体失败原因覆盖成 `MCP runtime connection failed`，加载页只显示「验证失败」；`server_status.json` 的 `lastStartTime` 不会在成功启动后写入。
- `MCPBridge.sendCommand()` 会把完整 `params`（包含 `env` 值）写入应用日志。

## 意图

- 让 `cwd` 作为一等配置字段贯穿读取、清理、导入、部署和启动。
- 引入 `McpStdioConfigResolver` 统一判定“应用托管目录”和“自维护目录”：显式外部 `cwd`、第一个非选项参数是外部绝对路径、或绝对命令且无相对入口时，视为自包含配置，不复制、不要求 Android 侧目录。
- 启动时使用配置的 `cwd`，未配置时才使用 `~/mcp_plugins/<shortName>`。
- `pnpm` 只在 `npx` 或仍需部署的插件上强校验。
- bridge 在 helper 退出、握手失败、spawn 超时时立即返回具体失败原因；该原因传递到加载页和 `server_status.json`。
- 日志只记录 env 键名，不记录 env 值。
- 不改变远程 MCP 路径和已有本地插件的默认行为。

## 作用域

- `MCPLocalServer` 配置模型、清理、导入、启停状态和运行目录判定。
- `McpConfigImportParser`、`MCPRepository`、`MCPDeployer`、`MCPStarter`。
- `McpRuntimeSession` 失败详情、`BridgeMcpRuntimeSession`、`RemoteMcpRuntimeSession`、`MCPManager`。
- `PluginLoadingScreen` 失败文案与 `MCPConfigScreen` 失败提示。
- `MCPBridge` 日志脱敏。
- `tools/mcp_bridge` 的启动失败诊断和场景测试。
- `examples/operit_editor.ts`、`examples/operit_editor.js`、`app/src/main/assets/packages/operit_editor.js` 的配置手册。

## 验证

- 正确配置的本地 stdio MCP 能启动并通过验证，`lastStartTime` 被写入。
- 缺少 command、进程异常退出、握手失败、握手超时都能在管理页看到具体原因。
- 日志中 `env` 值被替换为 `***`，只保留键名。
- 远程 MCP 与现有本地插件路径保持原有行为。
- bridge 场景测试与 Android JVM 单元测试通过。
