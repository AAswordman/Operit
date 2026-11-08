---
title: Issue 842 Gemini 思考参数传递
fork: https://github.com/tuxKOH/Operit
status: complete
---

# Issue 842 Gemini 思考参数传递

## 当前状况

Google provider 只根据全局思考开关写入 `includeThoughts`，没有读取全局思考强度。Gemini GenerateContent API 要求思考配置置于 `generationConfig.thinkingConfig`，因此当前全局思考程度没有影响 Gemini 3 的实际思考等级。

现有响应解析已将带有 `thought: true` 的 Part 转为 `<think>` 内容，并保留后续请求所需的 `thoughtSignature`。Gemini 的 `thinkingConfig` 在普通模型参数之后由全局思考模式写入。

## 预期结果

- 全局思考开关开启时发送 `thinkingConfig.includeThoughts = true`
- 全局思考程度 `1` 至 `5` 映射为 `MINIMAL`、`LOW`、`MEDIUM`、`HIGH`、`HIGH`
- 带有 `thought: true` 的响应继续复用现有折叠思考展示

## 官方参数依据

Gemini GenerateContent REST 参考定义 `generationConfig.thinkingConfig` 的 `includeThoughts`、`thinkingBudget` 和 `thinkingLevel` 字段。Gemini 3 与 2.5 的思考等级范围因模型而异；Gemini 3 的全量等级为 `MINIMAL`、`LOW`、`MEDIUM`、`HIGH`。

- [Gemini thinking](https://ai.google.dev/gemini-api/docs/thinking)
- [Gemini 3 guide](https://ai.google.dev/gemini-api/docs/gemini-3)
- [GenerateContent REST reference](https://ai.google.dev/api/generate-content)

## 步骤

1. [思考参数映射](./1_GeminiThinkingParameterMapping.md) [DONE]
2. [参数映射测试与交付](./2_VerificationAndDelivery.md) [DONE]

## 执行约束

本任务不在本地运行编译、构建或测试命令。验证限于源码检查、测试源码审查和 Git diff 审查。

## 完成记录

已完成 Gemini 思考参数的请求映射与 JVM 测试覆盖。`part.thought` 的折叠展示和 `thoughtSignature` 历史传递复用既有实现。CI 暴露的 Android `JSONObject` JVM stub 问题已通过测试纯 Kotlin 配置值解决，并运行定向 JVM 测试验证。
