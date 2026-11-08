---
fork_repository: local main
issue: https://github.com/AAswordman/Operit/issues/858
---

# Issue 858 MCP 工具参数完整性

## 现状

远程 MCP 写入工具收到的长文本参数会在约 7.8 KB 的 UTF-8 字节偏移出现
`U+FFFD`。调用方已在 MCP 服务入口确认损坏数据在服务端业务代码之前到达。

`RemoteMcpRuntimeSession` 将结构化参数直接交给 Kotlin MCP SDK，SDK 以完整 JSON
字符串交给 Ktor 请求体。项目中唯一的 8 KB 缓冲用于 MCP ZIP 下载，与工具调用无关。
模型流式工具参数在此之前已被转换为字符串，因此原始字符一旦变成替换字符便无法恢复。

## 目标

在 MCP 调用离开应用前拒绝包含 `U+FFFD` 的参数，避免远程写入工具静默持久化损坏文本。
错误与日志只报告参数名、替换字符数量和偏移，不记录原始参数内容。

## 作用域

- `app/src/main/java/com/ai/assistance/operit/core/tools/mcp/MCPToolExecutor.kt`
- 本目录中的排查与完成记录

## PR

待创建
