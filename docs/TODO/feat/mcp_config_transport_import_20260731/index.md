---
title: MCP standard config transport import
status: implemented-user-verified
fork: https://github.com/AAswordman/Operit.git
---

# MCP 标准配置传输导入

本次补录：用户确认目前 Bug 均已修复，本页待填写的验收说明已按用户确认同步。验证来源及现有 CI 记录见[验证结果补录](../../chore/distribution_integration/06-验证结果补录.md)。

## 原状

- 已发布的 `mcp_config.json` 将本地 stdio 配置存放在 `mcpServers`。
- 已发布的远程配置存放在 `pluginMetadata`，由 Kotlin MCP runtime 读取。
- 标准 MCP 配置把远程项写成 `type` 与 `url`，现有 `ServerConfig` 只表示 `command`。
- 页面导入和 MCP 市场安装分别判断 `command`，远程配置会被误判并清理。

## 意图

- 保持已发布的内部配置结构和远程字段不变。
- 增加唯一的标准配置解析边界，显式区分 stdio、Streamable HTTP 和 SSE。
- 本地项写入 `mcpServers`，远程项写入 `pluginMetadata`，不把远程 URL 塞入本地命令模型。
- 页面导入与市场安装复用同一解析结果。
- 同一 ID 发生传输类型替换时移除另一类记录并清理工具缓存。
- 日志不再输出配置正文、URL 凭据或认证令牌。

## 作用域

- 标准 MCP 配置解析器及结构化校验。
- `MCPLocalServer.mergeConfigFromJson` 和远程/本地写入边界。
- `MCPRepository.checkConfigNeedsPhysicalInstallation` 与市场安装流程。
- 远程配置敏感日志和远程详情配置入口。

## 验证

- 标准 stdio 配置仍能导入并部署。
- `type=streamable_http` 与 `type=sse` 配置能导入为远程元数据。
- 混合配置按传输类型分别保存，同名替换不残留另一类记录。
- 市场远程配置不再进入仓库安装分支。

本轮已完成静态核对和解析器单元测试编写。按照工作区执行准则，未主动执行编译或测试命令。
