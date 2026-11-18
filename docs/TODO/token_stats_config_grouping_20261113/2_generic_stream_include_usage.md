# 步骤 2：OPENAI_GENERIC / OPENAI_LOCAL 流式请求 usage

## 旧实现

`OpenAIProvider.includeUsageInStream` 默认 false，工厂仅给 `ApiProviderType.OPENAI`
分支传 true，XaiProvider 与 Kimi / DeepSeek 子类自行开启。OPENAI_GENERIC 与
OPENAI_LOCAL（中转渠道与本地部署最常用类型）流式请求不带
`stream_options.include_usage`，严格遵循 OpenAI 规范的端点不回 usage 分块，
成功请求无 usage 上报，不落库。

## 意图

与官方 OPENAI 类型对齐：流式请求显式索要 usage。Kimi / DeepSeek 的长期实践
与 Ollama 等本地端点的支持情况表明该参数已被主流兼容端点接受。

## 期待的新实现

工厂的 OPENAI_GENERIC 与 OPENAI_LOCAL 分支显式传 `includeUsageInStream = true`。
不做按端点试探或失败后降级的逻辑；若个别端点拒绝未知参数报 400，属于端点
不符合 OpenAI 规范，按错误正常暴露。

[DONE]
