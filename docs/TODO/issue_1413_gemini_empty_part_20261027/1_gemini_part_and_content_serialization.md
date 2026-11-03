---
title: Gemini Part 与 Content 的序列化约束
status: complete
---

# Gemini Part 与 Content 的序列化约束

## 旧实现

`GeminiProvider.buildPartsArray()` 的纯文本分支无条件写入 `{"text": text}`。历史消息正文为空时得到 `{"text": ""}`，`Part.data` oneof 没有任何成员被初始化，端点返回 400。

`buildContentsAndCountTokens()` 的每个 turn 分支都把 parts 直接写进 `contentsArray`，不检查长度。空正文因此变成一条空 parts 的 Content，命中另一条服务端校验 `contents.parts must not be empty`。

两条同源的空 Part：

- 媒体分支在图片链接已不在图片池时得到空 parts，仍然被写入 Content
- `systemInstruction` 无条件写入 `{"text": systemContent}`，系统提示为空白时同样是空 Part

## 新实现

Parts 层：纯文本分支只在正文非空白时写入 `text`，与媒体分支「先 trim 再判断」的规则一致。

Content 层：新增 `putContent(role, parts)`，parts 为空时整条 Content 不写入请求，并记一条 debug 日志。所有发出 Content 的分支统一走这个入口，包括工具调用的 `emitQueuedFunctionCallsIfNeeded()`、`flushOpenFunctionCallsAsUnmatched()`、ASSISTANT、TOOL_CALL、USER、SUMMARY、TOOL_RESULT 以及关闭工具调用时的兜底分支。

系统提示层：先剔除空白系统消息再拼接，全部空白时不产生 `systemInstruction`。

请求层：`createRequestBody()` 在写入 `contents` 前检查历史是否还剩可用 Content。一条都不剩时抛 `EmptyContentsException`，不发送必然被拒的请求。该异常与 `PromptBlockedException` 一样在 `handleRetryableError()` 里直接上抛，不参与重试，因为同样的历史重试只会得到同样的结果。

## 保持不变的部分

- `thoughtSignature` 仍挂在 functionCall Part 上，由 `queueFunctionCalls()` 与 `buildGeminiFunctionCallPart()` 传递
- `preserveThinkInHistory = true` 的调用方继续把 `<think>` 原文作为文本 Part 发送
- functionCall 与 functionResponse 的配对由既有的 open tool call 队列保证，被丢弃的只是没有 Part 可发的消息
- token 计数走独立的 `sanitizedHistoryForTokenCount`，不受 parts 过滤影响

## 验证

- 新增 `GeminiContentsSerializationTest`，用不变量断言遍历每个 Part：必须带一个 `data` 成员，`text` 不得为空串，`parts` 不得为空数组
- 覆盖纯思考回复、普通文本、保留思考、工具调用与结果、失效图片链接、空白系统提示、空 contents 本地报错
- 红绿对照：只回退 parts 与 content 两处守卫再跑同一测试类，13 条用例 6 条失败，报错分别是 `contents[1].parts[0] has empty text`、空 parts content 计数 `expected:<0> but was:<1>`、以及空 contents 未被拦截，与 issue 报告的三种形态一致；恢复守卫后 13 条全部通过
- 全量 `:app:testDebugUnitTest`：1356 条通过，0 失败，6 跳过
- 门禁 `check_repo_hygiene.py`、`check_markdown_links.py`、`check_localizations.py`：errors=0 warnings=0

本地运行单测时排除了 `:app:syncSttModelAssets` 与 `:app:syncMainAssets`，因为 STT 模型资产在本机下载校验不过，且与序列化改动无关；native 库与 FFmpegKit AAR 复用本机已有产物。CI 的 `Android JVM tests` job 会自行构建这两项前置。

[DONE]
