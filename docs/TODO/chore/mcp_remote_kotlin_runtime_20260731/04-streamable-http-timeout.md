# 04 Streamable HTTP Timeout

`StreamableHttpClientTransport` 在工具调用收到 `202 Accepted` 后，不会为该调用重新建立等待请求。初始化通知建立的 GET/SSE 会话负责接收后续 JSON-RPC 响应。

原有 `HttpClient(OkHttp)` 没有安装 Ktor 的 `HttpTimeout`。SSE 会话因而使用 OkHttp 默认的读超时，在服务端异步处理期间没有数据包时被关闭，调用方收到 `Socket timeout has expired`。远程 runtime 现在统一使用 15 秒连接超时，以及 60 秒的 HTTP 请求和 SSE 空闲窗口。配置模型不增加 timeout 字段。

[DONE]
