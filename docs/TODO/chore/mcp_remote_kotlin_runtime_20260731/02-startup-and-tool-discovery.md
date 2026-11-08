# 02 Startup And Tool Discovery

`MCPStarter` 过去把远程服务注册到 Node bridge，并把远程连接、工具缓存和验证都建立在 bridge service name 上。批量启动也会在没有本地插件时初始化 terminal、pnpm 和 bridge。

本步骤将启动编排改为以 `pluginId` 管理 runtime session。远程插件在 Kotlin SDK session 中完成连接、验证和工具发现；本地插件继续由 bridge 注册和启动 stdio 服务。工具缓存直接来自 session，不再写回 bridge。

预期结果：远程插件不访问 terminal、pnpm、bridge registry、spawn 或 unspawn；本地插件的 stdio 生命周期保持在 bridge 内。

MCP 配置页面通过 `MCPRepository` 读取远程工具名。repository 从已持久化的远程元数据注册 runtime，并用 Kotlin SDK session 发现和缓存工具；页面本身不再向 bridge 查询远程服务。没有本地插件时，页面也不会初始化 bridge 查询。

远程工具调用的返回在 `MCPToolExecutor` 中统一解析。标准 `content` 直接呈现；只有 `structuredContent` 时，执行器解析其中嵌套的 JSON 结果，再输出文本内容，避免 Kotlin SDK 的序列化转义进入工具执行界面。

[DONE]
