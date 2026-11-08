# 请求错误编号与证据字段

本目录记录当前实现，编号描述一个可直接观测的故障条件。相同系统异常仍可能由不同组件触发，编号不能代替网络责任调查，也不证明已经计费或退费。

通用错误位于 `api/chat/error/`，不再归属密钥池包。应用自身检查在发现位置产生带类型异常；网络库异常在请求边界按异常类、系统 errno 与真实阶段归类。只有具体 SocketException 没有暴露 errno 时，才识别完整的标准系统错误文字；不扫描普通 IOException、服务端正文或任意包装消息来猜 DNS、TLS、超时或连接中止。

## 编号目录

- **5000 `host_resolution_failed`**：UnknownHostException：系统无法解析 API 主机。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5003 `connection_aborted`**：系统报告 ECONNABORTED，或具体 SocketException 的标准连接中止短语；不判定发起者。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5004 `first_token_timeout`**：客户端首字看门狗在 600000ms 内没有认可的首个有效输出，主动取消。错误域为 `client`，此条件的默认重试许可为 `true`。
- **5010 `connect_timeout`**：SocketTimeoutException，观测阶段为建立连接。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5011 `request_headers_write_timeout`**：SocketTimeoutException，观测阶段为写请求头。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5012 `response_headers_timeout`**：SocketTimeoutException，观测阶段为等待响应头。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5013 `response_read_timeout`**：SocketTimeoutException，观测阶段为读取响应体。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5014 `socket_timeout_unlocated`**：SocketTimeoutException，但没有观测到请求阶段。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5015 `tls_timeout`**：SocketTimeoutException，观测阶段为 TLS 协商。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5016 `request_body_write_timeout`**：SocketTimeoutException，观测阶段为上传请求体。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5017 `dns_timeout`**：SocketTimeoutException，观测阶段为 DNS 解析。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5020 `connection_refused`**：系统报告 ECONNREFUSED，连接被拒绝。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5021 `host_unreachable`**：系统报告 EHOSTUNREACH，或 NoRouteToHostException，目标主机不可达。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5022 `network_unreachable`**：系统报告 ENETUNREACH，网络不可达。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5023 `port_unreachable`**：PortUnreachableException，端口不可达。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5030 `connection_reset`**：系统报告 ECONNRESET，连接被重置；不判定重置发起者。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5031 `socket_closed`**：具体 SocketException 报告 socket 已关闭；不判定关闭发起者。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5032 `broken_pipe`**：系统报告 EPIPE，写入管道已断开。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5033 `socket_not_connected`**：系统报告 ENOTCONN，socket 没有连接。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5040 `tls_peer_unverified`**：SSLPeerUnverifiedException，TLS 对端身份验证失败。错误域为 `transport`，此条件的默认重试许可为 `false`。
- **5041 `certificate_expired`**：CertificateExpiredException，证书已过期。错误域为 `transport`，此条件的默认重试许可为 `false`。
- **5042 `certificate_not_yet_valid`**：CertificateNotYetValidException，证书尚未生效。错误域为 `transport`，此条件的默认重试许可为 `false`。
- **5043 `certificate_path_invalid`**：CertPathValidatorException，证书链校验失败。错误域为 `transport`，此条件的默认重试许可为 `false`。
- **5044 `certificate_invalid`**：CertificateException，证书处理异常且没有更具体的证书子类证据。错误域为 `transport`，此条件的默认重试许可为 `false`。
- **5045 `tls_handshake_failed`**：SSLHandshakeException，TLS 握手异常且没有更具体的证书原因。错误域为 `transport`，此条件的默认重试许可为 `false`。
- **5046 `tls_protocol_error`**：SSLProtocolException，TLS 协议处理异常。错误域为 `transport`，此条件的默认重试许可为 `false`。
- **5050 `http2_cancel`**：StreamResetException 的 HTTP/2 错误原因为 CANCEL；直接读取库的错误字段，不匹配错误句子。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5051 `http2_refused_stream`**：StreamResetException 的 HTTP/2 错误原因为 REFUSED_STREAM；直接读取库的错误字段，不匹配错误句子。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5052 `http2_protocol_error`**：StreamResetException 的 HTTP/2 错误原因为 PROTOCOL_ERROR；直接读取库的错误字段，不匹配错误句子。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5053 `http2_internal_error`**：StreamResetException 的 HTTP/2 错误原因为 INTERNAL_ERROR；直接读取库的错误字段，不匹配错误句子。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5054 `http2_flow_control_error`**：StreamResetException 的 HTTP/2 错误原因为 FLOW_CONTROL_ERROR；直接读取库的错误字段，不匹配错误句子。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5055 `http2_enhance_your_calm`**：StreamResetException 的 HTTP/2 错误原因为 ENHANCE_YOUR_CALM；直接读取库的错误字段，不匹配错误句子。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5056 `http2_inadequate_security`**：StreamResetException 的 HTTP/2 错误原因为 INADEQUATE_SECURITY；直接读取库的错误字段，不匹配错误句子。错误域为 `transport`，此条件的默认重试许可为 `false`。
- **5057 `http2_http_1_1_required`**：StreamResetException 的 HTTP/2 错误原因为 HTTP_1_1_REQUIRED；直接读取库的错误字段，不匹配错误句子。错误域为 `transport`，此条件的默认重试许可为 `false`。
- **5058 `http2_stream_closed`**：StreamResetException 的 HTTP/2 错误原因为 STREAM_CLOSED；直接读取库的错误字段，不匹配错误句子。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5059 `http2_connect_error`**：StreamResetException 的 HTTP/2 错误原因为 CONNECT_ERROR；直接读取库的错误字段，不匹配错误句子。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5060 `http2_settings_timeout`**：StreamResetException 的 HTTP/2 错误原因为 SETTINGS_TIMEOUT；直接读取库的错误字段，不匹配错误句子。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5061 `http2_compression_error`**：StreamResetException 的 HTTP/2 错误原因为 COMPRESSION_ERROR；直接读取库的错误字段，不匹配错误句子。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5062 `http2_no_error_reset`**：StreamResetException 的 HTTP/2 错误原因为 NO_ERROR；直接读取库的错误字段，不匹配错误句子。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5063 `http2_connection_shutdown`**：OkHttp ConnectionShutdownException，HTTP/2 连接关闭。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5064 `http2_frame_size_error`**：StreamResetException 的 HTTP/2 错误原因为 FRAME_SIZE_ERROR；直接读取库的错误字段，不匹配错误句子。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5070 `http_protocol_error`**：ProtocolException，HTTP 协议格式错误。错误域为 `protocol`，此条件的默认重试许可为 `false`。
- **5071 `response_headers_eof`**：EOFException，读取响应头时提前结束。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5072 `response_body_eof`**：EOFException，读取响应体时提前结束。错误域为 `transport`，此条件的默认重试许可为 `true`。
- **5080 `stream_json_invalid`**：在流式 JSON 数据解码位置检测到 JSON 语法错误。错误域为 `protocol`，此条件的默认重试许可为 `false`。
- **5081 `response_json_invalid`**：在非流式响应解码位置检测到 JSON 语法错误。错误域为 `protocol`，此条件的默认重试许可为 `false`。
- **5082 `response_body_missing`**：成功 HTTP 响应缺少响应体对象。错误域为 `protocol`，此条件的默认重试许可为 `true`。
- **5083 `stream_completion_missing`**：响应结束，但没有提供方要求的完成标记；不据此断言物理网络故障。错误域为 `protocol`，此条件的默认重试许可为 `true`。
- **5084 `response_shape_invalid`**：JSON 已解码，但所需字段或数据结构不符合协议。错误域为 `protocol`，此条件的默认重试许可为 `false`。

## 兼容性

5000、5003、5004 保留原先明确的观察含义。宽泛的 5001、5002、5005、5006 停止产生，不分配给新的含义，历史消息和日志不重写。5004 等待上限改为十分钟；收到被认可的正文或思考输出仍立即解除计时。

错误 JSON 保留 `error` 外层与 `type/message/retryable/attempt/cause/detail` 等原字段，新增 `schemaVersion=2`、错误域、异常全名，以及可观测到的请求 ID、阶段、耗时、上传完成记录、HTTP 状态和提供方请求 ID。`detail` 最多 2000 字符，截断时标记 `detailTruncated=true`，异常链继续写入原有日志。

无法识别的异常使用 `code=null` 与 `domain=unclassified`，保留原始异常和阶段，不分配一个涵盖所有 IOException 的网络编号。未知异常在密钥统计中不再默认标为 NETWORK；原有请求预算与重试策略继续适用。普通包装的历史鉴权、额度提示仍按原密钥策略处理，这不参与新增编号匹配。

非成功 HTTP 状态继续显示真实 HTTP 错误。HTTP 200 正文中的服务端错误使用 `domain=provider`，保留 `providerCode/providerType`，不制造本地 5000 段编号；这些字段来自服务端原始结构，不根据诊断文字猜测业务类别。

## 格式示例

以下仅展示格式，不是真实请求或执行结果：

```json
{
  "error": {
    "schemaVersion": 2,
    "code": 5030,
    "type": "connection_reset",
    "domain": "transport",
    "cause": "SocketException",
    "detail": "Connection reset",
    "requestId": "example-request",
    "phase": "response_body",
    "httpStatus": 200,
    "requestBodySent": true,
    "elapsedMs": 12000,
    "attempt": 2,
    "retryable": true
  }
}
```

`requestBodySent=true` 只说明客户端观察到上传完成，HTTP 200 只说明收到该 HTTP 状态；两者都不是提供方计费或完整生成的凭证。

## 后台提示

请求错误在当前进程的提示队列中保留；没有可见窗口时不丢弃，Activity 或悬浮窗口恢复后按顺序展示。宿主切换时保留尚未显示完的请求错误，普通短时操作提示仍清理，普通提示的五条容量不会挤掉请求错误。窗口 token 失效时，请求错误继续留待可用宿主展示。

该队列不跨进程持久化；本次没有增加错误历史数据库，也没有改变 Android 后台服务或进程保活策略。

## 实现边界

本次不修改正文与回滚的双通道架构，不新增 Responses refusal 的正文渲染，不调整模型计费配置，不实施全项目包路径重构。错误提示更具体不等于已经解决底层连接故障。

[完整回归清单](regression.md) · [结果填写表](results.md)
