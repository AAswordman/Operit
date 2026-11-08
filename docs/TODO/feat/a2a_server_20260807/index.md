---
title: A2A Server
status: completed
---

# A2A Server

## 原状

- 外部 HTTP 服务已经对外发布并由第三方使用，提供 `/api/health`、`/api/external-chat` 和 Web Chat。
- 服务端已有 Bearer Token 鉴权、聊天执行器和 SSE 基础设施。
- 当前 HTTP 路由没有 A2A Agent Card、JSON-RPC 方法或 A2A 任务资源。

## 意图

- 让 Operit 作为 A2A 1.0 Server，被其他 Agent 通过局域网外部 HTTP 服务发现和调用。
- 在同一个监听端口新增标准 Agent Card 和 A2A JSON-RPC 入口。
- 复用既有聊天执行器，但将每个 A2A 请求隔离为独立聊天与独立任务。
- A2A 路由沿用已有 Bearer Token；Agent Card 不包含凭据并可被客户端发现。

## 兼容约束

- 不修改 `/api/health`、`/api/external-chat`、`/api/web/*` 或 `/` 的路径、请求字段、响应字段和鉴权行为。
- 不修改外部 HTTP 服务的端口、启停逻辑、监听地址或 Token 存储。
- A2A 仅新增 `/.well-known/agent-card.json` 和 `/a2a`。

## 作用域

- A2A 1.0 Agent Card，声明 JSON-RPC、Bearer 鉴权、文本输入输出和流式能力。
- `SendMessage`、`SendStreamingMessage`、`GetTask`、`ListTasks`、`CancelTask`、`SubscribeToTask`。
- 文本 Message/Artifact 与 A2A 任务状态到既有流式聊天执行器的映射。
- 设置页中的 A2A 地址展示与稳定协议文档。

## 非目标

- 不提供 A2A Client。
- 不支持文件、结构化数据、推送通知、扩展协商或远程任务的持久化。
- 不将 A2A 路由并入 MCP。

## 验证

- 静态确认既有外部 HTTP 路由和文档保持不变。
- 静态确认 A2A Agent Card 使用 1.0 的 `supportedInterfaces` 和 JSON-RPC 声明。
- 静态确认 A2A 消息仅接受 1.0 文本 Part，任务状态和 SSE 事件使用 1.0 对象形态。
- [DONE] 01 协议边界、02 任务生命周期、03 文档与验证。
