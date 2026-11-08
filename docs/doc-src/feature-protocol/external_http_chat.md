# External HTTP Chat API

本文档描述 Operit 新增的局域网 HTTP 聊天接口。它与现有 `EXTERNAL_CHAT` Intent 接口语义一致，只是入口从广播改成了 HTTP。

## 1. 启用方式

在 Operit 自带终端执行：

```bash
web-chat start-server --port 8094
web-chat start-server --port 8094 --login
```

普通启动输出当前端口、网页地址和 Bearer Token；`--login` 进一步请求默认浏览器打开自动登录链接。设置中的旧“外部 HTTP 调用”页面已移除，原端口、Token 与启用状态继续保留。

`web-chat status` 查看状态，`web-chat stop-server` 停止服务，`web-chat examples` 显示四类接入示例。完整命令、登录和升级说明见 [web-chat 命令](web_chat_cli.md)。

默认端口为 `8094`。HTTP Chat、Web Chat 与 A2A 共用该服务。

## 2. 鉴权

除 `OPTIONS` 外，所有请求都必须带：

```http
Authorization: Bearer YOUR_TOKEN
```

Bearer Token 在首次启用时自动生成。使用 `web-chat token show` 查看，`web-chat token reset` 重置；普通启动沿用已有 Token。

## 3. 接口

### 3.1 `GET /api/health`

用于检查服务联通性与鉴权是否正常。

示例：

```bash
curl -H "Authorization: Bearer YOUR_TOKEN" "http://DEVICE_IP:8094/api/health"
```

返回示例：

```json
{
  "status": "ok",
  "enabled": true,
  "service_running": true,
  "port": 8094,
  "version_name": "1.10.0+1"
}
```

### 3.2 `POST /api/external-chat`

请求体为 JSON，字段复用现有 Intent 接口：

- `request_id`
- `message`
- `group`
- `create_new_chat`
- `chat_id`
- `create_if_none`
- `show_floating`
- `return_tool_status`
- `initial_mode`
- `auto_exit_after_ms`
- `stop_after`

HTTP 新增字段：

- `stream`: `true` 时改为按 SSE 分块返回
- `response_mode`: `sync` 或 `async_callback`
- `callback_url`: `response_mode=async_callback` 时必填

补充说明：

- `return_tool_status` 默认为 `true`
- `return_tool_status=false` 时，会从外部返回中移除 `<tool*>`、`<tool_result*>`、`<status>` 这类辅助内容，减少传输体积
- `initial_mode` 仅在 `show_floating=true` 时有意义
- `initial_mode` 可选值：`WINDOW`、`BALL`、`VOICE_BALL`、`FULLSCREEN`、`RESULT_DISPLAY`、`SCREEN_OCR`
- 如果 `show_floating=true` 且未传 `initial_mode`，则沿用当前/上次保存的浮窗模式；首次默认 `WINDOW`
- `stream=true` 与 `response_mode=async_callback` 不能同时使用

## 4. 同步调用

示例：

```bash
curl -X POST "http://DEVICE_IP:8094/api/external-chat" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json; charset=utf-8" \
  -d '{
    "message": "你好，帮我总结今天的待办",
    "response_mode": "sync",
    "show_floating": true,
    "return_tool_status": false,
    "initial_mode": "WINDOW"
  }'
```

返回示例：

```json
{
  "request_id": "f0fdde0c-3f68-43c1-ae43-9d7736d6fd7d",
  "success": true,
  "chat_id": "1742558116153",
  "ai_response": "这是今天的待办总结……"
}
```

如果请求本身格式正确，但聊天执行失败，也会返回同结构 JSON，只是：

- `success = false`
- `error` 带失败原因

## 5. SSE 流式返回

当 `stream=true` 时，接口返回 `text/event-stream`，每个分块都是标准 SSE 格式。

示例：

```bash
curl -N -X POST "http://DEVICE_IP:8094/api/external-chat" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Accept: text/event-stream" \
  -H "Content-Type: application/json; charset=utf-8" \
  -d '{
    "message": "请一步步解释这个问题",
    "stream": true,
    "show_floating": true,
    "return_tool_status": false,
    "initial_mode": "WINDOW"
  }'
```

返回事件：

- `start`: 已接受请求并拿到 `chat_id`
- `delta`: 本次增量文本
- `done`: 全部完成，`ai_response` 为完整结果
- `error`: 处理失败

返回示例：

```text
event: start
data: {"event":"start","request_id":"req-001","chat_id":"1742558116153"}

event: delta
data: {"event":"delta","request_id":"req-001","chat_id":"1742558116153","delta":"你好，"}

event: delta
data: {"event":"delta","request_id":"req-001","chat_id":"1742558116153","delta":"下面我来解释。"}

event: done
data: {"event":"done","request_id":"req-001","chat_id":"1742558116153","success":true,"ai_response":"你好，下面我来解释。"}
```

错误示例：

```text
event: error
data: {"event":"error","request_id":"req-001","success":false,"error":"Invalid parameter: stream=true is not compatible with async_callback"}
```

注意：

- SSE 模式下建议显式发送 `Accept: text/event-stream`
- 连接关闭后，服务端会尝试取消这次 AI 响应

## 6. 异步回调

示例：

```bash
curl -X POST "http://DEVICE_IP:8094/api/external-chat" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json; charset=utf-8" \
  -d '{
    "message": "继续刚才的话题",
    "response_mode": "async_callback",
    "callback_url": "http://YOUR_PC:8080/callback"
  }'
```

立即返回：

```json
{
  "request_id": "dca1a2e0-8f7e-4bf8-9523-a4b7bdf2fd13",
  "accepted": true,
  "status": "accepted"
}
```

AI 完成后，Operit 会向 `callback_url` 发送一次 `POST application/json` 回调，请求体仍然是：

```json
{
  "request_id": "dca1a2e0-8f7e-4bf8-9523-a4b7bdf2fd13",
  "success": true,
  "chat_id": "1742558116153",
  "ai_response": "……"
}
```

注意：

- JSON 请求体默认按 UTF-8 处理，建议显式发送 `Content-Type: application/json; charset=utf-8`
- v1 不做重试
- callback 非 2xx 或网络失败只记日志，不自动补发

## 7. 行为说明

- `show_floating=true` 时，会尝试启动 `FloatingChatService`
- `show_floating=true` 且传入 `initial_mode` 时，会按该模式启动浮窗
- `show_floating=true` 但未传 `initial_mode` 时，会沿用当前/已保存模式；首次默认 `WINDOW`
- `create_new_chat=true` 时，会先创建新对话再发送
- `chat_id` 仅在 `create_new_chat=false` 时生效
- `create_if_none=false` 且当前没有对话时，会返回失败
- `stop_after=true` 时，请求结束后会尝试停止聊天服务
- `return_tool_status=false` 时，会过滤工具状态相关 XML，减少 `ai_response` / SSE `delta` 的体积
- `stream=true` 时，响应改为 SSE；不再返回单个固定 JSON 响应体
- `stream=true` 与 `response_mode=async_callback` 同时出现时，会返回 `400 Bad Request`

这些语义与现有 `EXTERNAL_CHAT` Intent 接口保持一致。
