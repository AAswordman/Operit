# 路由与请求头

## [DONE] 模型列表端点

Zen 使用 `https://opencode.ai/zen/v1/models`，Go 使用 `https://opencode.ai/zen/go/v1/models`。端点由现有 `OpenCodeRouting.normalizedBase` 统一补全版本段。

## [DONE] 协议路由

- GPT、Grok、Muse Spark 使用 OpenAI Responses。
- Claude、Qwen 使用 Anthropic Messages。
- Gemini 使用 Google Generative Language。
- DeepSeek、GLM、Kimi、MiMo、MiniMax Zen 以及 Go 的其他开放模型使用 OpenAI-compatible Chat Completions。
- MiniMax Go 使用 Anthropic Messages；MiniMax Zen 的免费兼容模型仍按目录中的 Anthropic 协议处理。

## [DONE] Go 客户端身份

- `User-Agent` 使用 `Operit/<version>`。
- `x-opencode-session` 使用模型配置 ID 生成稳定值，使同一配置创建的请求保持一致。

当前 `AIService` 接口没有向 provider 传递聊天记录所属会话 ID，因此这里使用稳定的模型配置标识；后续若公共聊天接口暴露会话 ID，应将该值替换为真实聊天会话标识。

