---
title: 参数映射测试与交付
status: complete
---

# 参数映射测试与交付

## 验证范围

为映射器增加 JVM 单元测试，覆盖全局五档思考程度和思考摘要开关。测试检查序列化前的纯 Kotlin 配置值，避免执行 Android SDK 在本地 JVM 中未实现的 `JSONObject` stub；生产请求仍将相同配置写入 `generationConfig.thinkingConfig`。

## 交付检查

- 检查 Google 和 Gemini Generic provider 共用相同的全局映射器
- 检查思考 Part 的解析和签名历史代码未被改变
- 运行 `:app:testDebugUnitTest --tests com.ai.assistance.operit.api.chat.llmprovider.GeminiThinkingConfigTest`
- 审查 Git diff 与文档完成标记

本任务按仓库执行准则不运行本地测试命令。

[DONE]
