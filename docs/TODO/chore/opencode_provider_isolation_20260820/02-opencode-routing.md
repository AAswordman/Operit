# OpenCode 路由隔离

OpenCodeProvider 负责根据模型选择协议，并把请求交给专用 provider 实现。专用实现可以复用通用 provider 的消息、工具和流处理，但通过受控扩展点覆盖请求体和请求 URL，不把 OpenCode 条件写回公共 provider。

## 请求边界

- OpenAI Chat Completions：显式传入 `reasoning_effort`
- OpenAI Responses：显式传入 `reasoning`，不触发公共自动注入
- Anthropic Messages：显式传入 `thinking` / `output_config`
- Gemini：使用模型 URL、`x-goog-api-key` 和流式 `?alt=sse`
