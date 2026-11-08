# 03 文档与验证

## 修改

- 扩充外部 HTTP 设置页，展示 Agent Card URL 和 JSON-RPC URL。
- 在 `docs/doc-src/feature-protocol/` 新增 A2A Server 使用说明和 JSON-RPC 示例。
- 静态核对既有 HTTP 路由与协议文档没有改动。

## 验证记录

- 未执行编译、构建或测试命令，遵循仓库的执行准则。
- 静态核对 `ExternalChatHttpServer` 的既有 `/api/health`、`/api/external-chat`、`/api/web/*` 与 Web Chat 路由未改变。
- 静态核对 A2A Agent Card、JSON-RPC 方法名、错误码和 SSE envelope 符合 A2A 1.0 JSON-RPC binding。
- 静态核对设置页使用的 A2A 文案键已同时存在于默认中文和英文资源。

[DONE]
