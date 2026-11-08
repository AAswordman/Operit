---
title: MCP remote Kotlin runtime
status: in-progress
---

# MCP 远程 Kotlin Runtime 重构

## 原状

- 本地 stdio MCP 和远程 HTTP/SSE MCP 都通过 Node bridge 注册。
- `MCPManager` 缓存的是 `MCPBridgeClient`，上层工具调用无法区分本地与远程传输。
- 批量启动无条件初始化 terminal、pnpm 和 bridge，远程服务也会走 bridge 的 spawn/unspawn 流程。
- 远程配置已包含 `endpoint`、`connectionType`、`bearerToken` 和 `headers`，本次保持字段不变。

## 意图

- 仅本地 stdio MCP 继续使用 Node bridge。
- 远程 MCP 使用官方 Kotlin MCP SDK 的 Streamable HTTP/SSE client transport。
- 用统一的 runtime session 隔离上层工具调用与底层传输实现。
- 运行时和工具注册只使用 `pluginId`，显示名称不参与寻址。
- 删除远程 bridge 注册、spawn、unspawn 及缓存转发代码，不增加兼容或回退路径。

## 作用域

- Android 依赖：Kotlin MCP SDK、Ktor client 和版本对齐。
- MCP runtime session、manager、启动器、工具缓存和工具注册。
- `MCPToolExecutor`、`MCPPackage`、`MCPRepository`、MCP 详情页面。
- 远程 Streamable HTTP/SSE 的连接、请求和 SSE 空闲超时。
- 配置模型和配置文件格式不变。

## 验证

- 静态确认远程路径不再引用 `MCPBridgeClient` 或 bridge service registry。
- 静态确认本地路径仍通过 bridge 注册和启动 stdio 服务。
- bridge TypeScript 构建通过，并已同步 Android bridge assets。
- [DONE] 表示对应步骤完成。
