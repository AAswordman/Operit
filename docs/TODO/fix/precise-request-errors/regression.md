# 完整回归清单

以下覆盖新编号与原有请求行为，不表示已经执行。可通过受控异常、JVM 用例与工作分支 APK 验证，不需要调用真实付费模型来制造失败。

| 编号 | 做什么 | 预期行为 |
| --- | --- | --- |
| E01 | UnknownHostException：系统无法解析 API 主机 | 产生 5000 / host_resolution_failed，保留异常类型与原始细节 |
| E02 | 系统报告 ECONNABORTED，或具体 SocketException 的标准连接中止短语；不判定发起者 | 产生 5003 / connection_aborted，保留异常类型与原始细节 |
| E03 | 客户端首字看门狗在 600000ms 内没有认可的首个有效输出，主动取消 | 产生 5004 / first_token_timeout，保留异常类型与原始细节 |
| E04 | SocketTimeoutException，观测阶段为建立连接 | 产生 5010 / connect_timeout，保留异常类型与原始细节 |
| E05 | SocketTimeoutException，观测阶段为写请求头 | 产生 5011 / request_headers_write_timeout，保留异常类型与原始细节 |
| E06 | SocketTimeoutException，观测阶段为等待响应头 | 产生 5012 / response_headers_timeout，保留异常类型与原始细节 |
| E07 | SocketTimeoutException，观测阶段为读取响应体 | 产生 5013 / response_read_timeout，保留异常类型与原始细节 |
| E08 | SocketTimeoutException，但没有观测到请求阶段 | 产生 5014 / socket_timeout_unlocated，保留异常类型与原始细节 |
| E09 | SocketTimeoutException，观测阶段为 TLS 协商 | 产生 5015 / tls_timeout，保留异常类型与原始细节 |
| E10 | SocketTimeoutException，观测阶段为上传请求体 | 产生 5016 / request_body_write_timeout，保留异常类型与原始细节 |
| E11 | SocketTimeoutException，观测阶段为 DNS 解析 | 产生 5017 / dns_timeout，保留异常类型与原始细节 |
| E12 | 系统报告 ECONNREFUSED，连接被拒绝 | 产生 5020 / connection_refused，保留异常类型与原始细节 |
| E13 | 系统报告 EHOSTUNREACH，或 NoRouteToHostException，目标主机不可达 | 产生 5021 / host_unreachable，保留异常类型与原始细节 |
| E14 | 系统报告 ENETUNREACH，网络不可达 | 产生 5022 / network_unreachable，保留异常类型与原始细节 |
| E15 | PortUnreachableException，端口不可达 | 产生 5023 / port_unreachable，保留异常类型与原始细节 |
| E16 | 系统报告 ECONNRESET，连接被重置；不判定重置发起者 | 产生 5030 / connection_reset，保留异常类型与原始细节 |
| E17 | 具体 SocketException 报告 socket 已关闭；不判定关闭发起者 | 产生 5031 / socket_closed，保留异常类型与原始细节 |
| E18 | 系统报告 EPIPE，写入管道已断开 | 产生 5032 / broken_pipe，保留异常类型与原始细节 |
| E19 | 系统报告 ENOTCONN，socket 没有连接 | 产生 5033 / socket_not_connected，保留异常类型与原始细节 |
| E20 | SSLPeerUnverifiedException，TLS 对端身份验证失败 | 产生 5040 / tls_peer_unverified，保留异常类型与原始细节 |
| E21 | CertificateExpiredException，证书已过期 | 产生 5041 / certificate_expired，保留异常类型与原始细节 |
| E22 | CertificateNotYetValidException，证书尚未生效 | 产生 5042 / certificate_not_yet_valid，保留异常类型与原始细节 |
| E23 | CertPathValidatorException，证书链校验失败 | 产生 5043 / certificate_path_invalid，保留异常类型与原始细节 |
| E24 | CertificateException，证书处理异常且没有更具体的证书子类证据 | 产生 5044 / certificate_invalid，保留异常类型与原始细节 |
| E25 | SSLHandshakeException，TLS 握手异常且没有更具体的证书原因 | 产生 5045 / tls_handshake_failed，保留异常类型与原始细节 |
| E26 | SSLProtocolException，TLS 协议处理异常 | 产生 5046 / tls_protocol_error，保留异常类型与原始细节 |
| E27 | StreamResetException 的 HTTP/2 错误原因为 CANCEL；直接读取库的错误字段，不匹配错误句子 | 产生 5050 / http2_cancel，保留异常类型与原始细节 |
| E28 | StreamResetException 的 HTTP/2 错误原因为 REFUSED_STREAM；直接读取库的错误字段，不匹配错误句子 | 产生 5051 / http2_refused_stream，保留异常类型与原始细节 |
| E29 | StreamResetException 的 HTTP/2 错误原因为 PROTOCOL_ERROR；直接读取库的错误字段，不匹配错误句子 | 产生 5052 / http2_protocol_error，保留异常类型与原始细节 |
| E30 | StreamResetException 的 HTTP/2 错误原因为 INTERNAL_ERROR；直接读取库的错误字段，不匹配错误句子 | 产生 5053 / http2_internal_error，保留异常类型与原始细节 |
| E31 | StreamResetException 的 HTTP/2 错误原因为 FLOW_CONTROL_ERROR；直接读取库的错误字段，不匹配错误句子 | 产生 5054 / http2_flow_control_error，保留异常类型与原始细节 |
| E32 | StreamResetException 的 HTTP/2 错误原因为 ENHANCE_YOUR_CALM；直接读取库的错误字段，不匹配错误句子 | 产生 5055 / http2_enhance_your_calm，保留异常类型与原始细节 |
| E33 | StreamResetException 的 HTTP/2 错误原因为 INADEQUATE_SECURITY；直接读取库的错误字段，不匹配错误句子 | 产生 5056 / http2_inadequate_security，保留异常类型与原始细节 |
| E34 | StreamResetException 的 HTTP/2 错误原因为 HTTP_1_1_REQUIRED；直接读取库的错误字段，不匹配错误句子 | 产生 5057 / http2_http_1_1_required，保留异常类型与原始细节 |
| E35 | StreamResetException 的 HTTP/2 错误原因为 STREAM_CLOSED；直接读取库的错误字段，不匹配错误句子 | 产生 5058 / http2_stream_closed，保留异常类型与原始细节 |
| E36 | StreamResetException 的 HTTP/2 错误原因为 CONNECT_ERROR；直接读取库的错误字段，不匹配错误句子 | 产生 5059 / http2_connect_error，保留异常类型与原始细节 |
| E37 | StreamResetException 的 HTTP/2 错误原因为 SETTINGS_TIMEOUT；直接读取库的错误字段，不匹配错误句子 | 产生 5060 / http2_settings_timeout，保留异常类型与原始细节 |
| E38 | StreamResetException 的 HTTP/2 错误原因为 COMPRESSION_ERROR；直接读取库的错误字段，不匹配错误句子 | 产生 5061 / http2_compression_error，保留异常类型与原始细节 |
| E39 | StreamResetException 的 HTTP/2 错误原因为 NO_ERROR；直接读取库的错误字段，不匹配错误句子 | 产生 5062 / http2_no_error_reset，保留异常类型与原始细节 |
| E40 | OkHttp ConnectionShutdownException，HTTP/2 连接关闭 | 产生 5063 / http2_connection_shutdown，保留异常类型与原始细节 |
| E41 | StreamResetException 的 HTTP/2 错误原因为 FRAME_SIZE_ERROR；直接读取库的错误字段，不匹配错误句子 | 产生 5064 / http2_frame_size_error，保留异常类型与原始细节 |
| E42 | ProtocolException，HTTP 协议格式错误 | 产生 5070 / http_protocol_error，保留异常类型与原始细节 |
| E43 | EOFException，读取响应头时提前结束 | 产生 5071 / response_headers_eof，保留异常类型与原始细节 |
| E44 | EOFException，读取响应体时提前结束 | 产生 5072 / response_body_eof，保留异常类型与原始细节 |
| E45 | 在流式 JSON 数据解码位置检测到 JSON 语法错误 | 产生 5080 / stream_json_invalid，保留异常类型与原始细节 |
| E46 | 在非流式响应解码位置检测到 JSON 语法错误 | 产生 5081 / response_json_invalid，保留异常类型与原始细节 |
| E47 | 成功 HTTP 响应缺少响应体对象 | 产生 5082 / response_body_missing，保留异常类型与原始细节 |
| E48 | 响应结束，但没有提供方要求的完成标记；不据此断言物理网络故障 | 产生 5083 / stream_completion_missing，保留异常类型与原始细节 |
| E49 | JSON 已解码，但所需字段或数据结构不符合协议 | 产生 5084 / response_shape_invalid，保留异常类型与原始细节 |
| R01 | 普通 IOException 分别包含 timeout、certificate、stream reset、connection abort 等旧关键词 | 不生成本地网络编号，保留原异常 |
| R02 | 服务端错误正文含网络关键词，但结构字段为 content_filter、rate_limit_exceeded 或鉴权错误 | 保留服务端业务编号，按结构字段决定是否重试或换钥 |
| R03 | 请求收到 HTTP 200 后出现读取超时或 socket reset | 保留 200、response_body、请求 ID 与上传完成信息 |
| R04 | 两个并发请求分别处于连接与读取阶段，重试在创建 Call 前失败 | 错误阶段和请求 ID 互不污染；未创建 Call 的失败不携带前次请求的 200 或 ID |
| R05 | 测试 HTTP 400/401/402/403/404/429/502/503 及嵌套状态异常 | 真实 HTTP 状态保留；参数错误停止，鉴权或模型错误按候选换钥，临时错误保留策略 |
| R06 | 重跑已有余额、限流、参数、密钥和模型访问回归 | 不因错误文字细分而删除原有用例，单密钥与密钥池预算保持 |
| R07 | 单密钥连续可重试失败六次，以及密钥池候选耗尽 | 最多首次加五次重试；池模式受候选预算及二十次上限约束 |
| R08 | 接受重试并发出提示 | 先发出原有回滚，再提示具体失败和下一次尝试信息 |
| R09 | 用户取消、协程取消和正常停止 | 不包装成 5000 段故障，不再自动重试 |
| R10 | 首字等待达到五分钟、599999ms、600000ms | 五分钟和599999ms不取消，600000ms看门狗取消并报告5004 |
| R11 | 正文、思考或认可的工具内容到达后继续等待 | 解除首字计时；纯协议标签不冒充有效首输出 |
| R12 | 重启下一次尝试或显式结束请求 | 旧看门狗不再取消旧 Call，新尝试独立计时 |
| R13 | OpenAI/Responses/Claude/Gemini 正常流式和非流式请求 | 正文、思考、工具调用、完成事件和用量仍能正常收尾 |
| R14 | SSE 注释、空 data 事件以及正常完成标记 | 空事件不触发 JSON 错误，正常完成不产生缺失结束标记错误 |
| R15 | 后台无可见宿主时产生多个请求错误，再回到 Activity | 错误按顺序展示，普通短时提示不补播 |
| R16 | 请求错误显示期间切换页面或悬浮窗口 | 未显示完的错误保留到可见宿主，不被清空或重复入队 |
| R17 | 大量普通操作提示与请求错误交错 | 普通提示容量仍为五条，不挤掉请求错误 |
| R18 | 窗口 token 失效、窗口尚无可用布局后再次恢复宿主 | 不丢请求错误，在宿主可用后展示 |
| R19 | 检查九语言首字等待文案以及复制错误 JSON | 说明十分钟与客户端取消，字段可复制且占位符合法 |
| R20 | 检查旧消息与日志 | 旧编号保留，不进行历史数据重写 |
