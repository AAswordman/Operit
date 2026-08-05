---
title: Gemini 全局思考配置映射
status: complete
---

# Gemini 全局思考配置映射

## 旧实现

`GeminiProvider` 在全局思考开关开启时创建 `generationConfig.thinkingConfig.includeThoughts`，但没有把全局思考程度传递给 Gemini。

## 修改意图

`GeminiProvider` 直接将全局思考程度转换为 Gemini 的 `thinkingConfig`。该对象在普通模型参数之后写入请求，因此它只由全局思考模式决定。

## 参数契约

- 全局开关 -> `thinkingConfig.includeThoughts = true`
- 全局程度 `1` -> `thinkingConfig.thinkingLevel = MINIMAL`
- 全局程度 `2` -> `thinkingConfig.thinkingLevel = LOW`
- 全局程度 `3` -> `thinkingConfig.thinkingLevel = MEDIUM`
- 全局程度 `4` -> `thinkingConfig.thinkingLevel = HIGH`
- 全局程度 `5` -> `thinkingConfig.thinkingLevel = HIGH`

Gemini 仅接受 `thinkingLevel`、`thinkingBudget` 和 `includeThoughts` 作为 `thinkingConfig` 的成员。响应仍由现有 `part.thought` 和 `thoughtSignature` 处理，因此能进入已实现的折叠思考展示。

[DONE]
