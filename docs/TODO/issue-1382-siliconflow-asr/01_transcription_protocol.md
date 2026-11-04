# 转写协议客户端与端点判定

## 旧实现

- `OpenAIProvider.buildInputAudioPayload()` 把音频编码为 `{"data": ..., "format": ...}`，通过聊天
  `input_audio` content part 发送；只适用于原生支持音频输入的聊天模型。
- `OpenAISttProvider.transcribeWavFile()` 自己拼 `MultipartBody`，并额外发送 `language` 与
  `response_format`；硅基流动官方 OpenAPI 未声明这两个字段。
- 没有共享的“这是转写端点吗”判定，也没有可复用的转写请求/解析实现。

## 新实现

- 新增 `AudioTranscriptionProtocol`：
  - `isTranscriptionEndpoint(endpoint)`：按 URL path 判断（`/audio/transcriptions` 结尾，忽略大小写与
    查询参数），供测试、诊断与音频识别共用；
  - `buildRequestBody(file, model, language)`：`multipart/form-data`，必填 `file`、`model`；
    `language` 仅在调用方显式提供时发送（OpenAI 等支持，硅基流动调用方不传）；
  - `parseResponseBody(body)`：优先读取 JSON 的 `text`，纯文本响应按原样返回；
  - `extractErrorMessage(body)`：把硅基流动的 `{"code":...,"message":"..."}`、OpenAI 风格的
    `{"error":{"message":...}}` 与纯文本错误体归一为可读信息。
- 协议对象不持有 Android Context，可直接用 JVM 单元测试覆盖。

## 约束

- 不对非转写端点做任何协议猜测；只有路径明确为 `/audio/transcriptions` 时才走转写。
- 不发送调用方未要求的字段，避免上游按“未知参数”拒绝。
