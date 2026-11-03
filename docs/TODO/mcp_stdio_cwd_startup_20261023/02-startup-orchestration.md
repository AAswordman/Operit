# 02 启动编排与依赖检查

`MCPRepository.loadPluginsFromMCPLocalServer` 只把 Android 侧存在插件目录的本地服务算作已安装，手工在 Linux 侧部署并写入 `mcpServers` 的服务不会进入 `installedPluginIds`，加载页因此直接跳过，桥接器和插件日志为空。

本步骤让“已写入 `mcpServers` 的本地服务”直接视为已配置；`MCPRepository.isPluginInstalled` 采用同一判定，管理页详情不再把这类服务当成未安装。是否需要复制目录由 `MCPLocalServer.isPluginRuntimeReady` 在启动时判断：显式外部 `cwd` 或外部绝对入口直接就绪；`npx`/`uvx`/`uv` 允许空目录；其余应用托管插件要求目录存在且非空。

`MCPStarter.startPluginInternal` 和 `registerPlugin` 改用 `McpStdioConfigResolver.resolveWorkingDirectory`，把配置的 `cwd` 传给 bridge 的 `register` 请求。

`initBridge` 原先对所有本地插件强制检查 `pnpm`。bridge 本身是打包好的 JS，只需要 `node`；只有 `npx`（spawn helper 中改写为 `pnpm dlx`）和仍需执行 `pnpm install` 的应用托管插件依赖 `pnpm`。本步骤把检查条件收敛为 `requiresPnpmForPlugins`，并在 `startAllDeployedPlugins` 初始化失败时按 `TERMINAL_SERVICE_UNAVAILABLE`、`NODEJS_MISSING`、`BRIDGE_FAILED` 返回具体状态，而不是继续执行后逐个报「验证失败」。

预期结果：纯 `node`/`sh` 插件在没有 `pnpm` 的终端中也能启动；手工维护的 Linux 插件无需伪造 Android 侧目录。

[DONE]
