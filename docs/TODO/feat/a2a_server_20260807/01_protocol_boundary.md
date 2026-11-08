# 01 协议边界

## 原状

`ExternalChatHttpServer` 直接路由 REST、SSE 和 Web Chat。请求执行由 `ExternalChatRequestExecutor` 负责。

## 修改

- 将 A2A 路由放在同一 HTTP Server 中，但把协议解析和任务管理放到 `integrations/a2a/`。
- `/.well-known/agent-card.json` 从请求 Host 生成本次访问可用的 `/a2a` JSON-RPC URL。
- `/a2a` 除 `OPTIONS` 以外使用现有 Bearer Token 鉴权。
- A2A 1.0 文本 Part 使用 `{ "text": "..." }`，不接受其他 Part 类型。

## 预期结果

既有 REST API 与 A2A JSON-RPC 路由没有实现耦合，除共享 HTTP Server、Token 和聊天执行器外不共享协议对象。

[DONE]
