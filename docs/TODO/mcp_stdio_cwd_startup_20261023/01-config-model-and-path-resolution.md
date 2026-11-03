# 01 配置模型与路径判定

`MCPConfig.ServerConfig` 只描述 `command`，`MCPLocalServer.sanitizeServerConfig`、`mergeConfigFromJson`、`addOrUpdateMCPServer` 都会重建对象，因此新增字段必须在每一层显式传递，否则读入后立即丢失。

本步骤给 `ServerConfig` 增加标准 MCP 的 `cwd`，并在清理、导入合并、启用状态切换、部署配置保存处保留该字段。`hasLocalRuntimeConfigurationChanged` 也纳入 `cwd`，避免运行中的进程继续使用旧工作目录。

新增 `McpStdioConfigResolver` 承担纯规则判断：

- `commandName`：取命令最后一段并转小写。
- `isTerminalPath`：识别 `/...`、`~`、`~/...`、`$HOME/...`。
- `usesAppManagedRuntimeDirectory`：没有 `cwd` 且第一个非选项参数不是外部绝对路径时，服务运行在 `~/mcp_plugins/<id>`；`cwd` 指回 `~/mcp_plugins` 时仍由应用托管。
- `requiresAppManagedInstallation`：在上一条基础上排除 `npx`、`uvx`、`uv`。
- `resolveWorkingDirectory`：显式 `cwd` 优先，其次外部绝对入口所在目录，最后 `~/mcp_plugins/<shortName>`。

预期结果：用户写 `"cwd": "/root/project"`、`"args": ["/root/project/dist/index.js"]` 或只有绝对 command 时，Operit 不再要求 Android 侧插件目录，也不会把工作目录改回 `~/mcp_plugins`；`"command": "/usr/bin/node", "args": ["dist/index.js"]` 仍会部署，因为相对入口必须落在托管目录里。

[DONE]
