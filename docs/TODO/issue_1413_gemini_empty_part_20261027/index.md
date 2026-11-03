---
title: Issue 1413 Gemini 空 Part 修复
fork: https://github.com/yoruuuchan/Operit
status: complete
---

# Issue 1413 Gemini 空 Part 修复

## 当前状况

Gemini 原生端点按 proto 校验请求体：`Part.data` 是 oneof，正文为空字符串时该 oneof 没有任何已初始化的成员，整条请求被拒绝，返回 HTTP 400 `required oneof field 'data' must have one initialized field`；`Content.parts` 为空数组时返回 `contents.parts must not be empty`。

`GeminiProvider.buildPartsArray()` 的纯文本分支无条件写入 `{"text": text}`。历史里的模型消息在剥离思考正文后可能为空，于是写入 `{"text": ""}`。开启思考档位后，模型只输出思考、不给正文的情况更多，一轮携带这种历史就整段对话报错。

同类空 Part 还有两条路径：图片链接已不在图片池中时，媒体分支得到空 parts；系统提示词为空白时，`systemInstruction` 也得到一个空文本 Part。

## 预期结果

- 空正文不产生 Part，请求里不出现未初始化 `data` 的 Part
- 没有任何 Part 的历史消息不产生 Content 条目，请求里不出现空 `parts`
- 只含系统提示、正文全部为空的请求在本地给出可读错误，不带着空 `contents` 去撞服务端的 400
- 思考元数据、工具调用与工具结果的既有序列化保持不变

## 官方与社区依据

Gemini `Content` 要求至少一个 `Part`，`Part.data` 必须有一个已初始化的成员。同一问题在生态里已有相同结论：CLIProxyAPI 跳过空文本 Part 与空 parts 消息（[issue #1293](https://github.com/router-for-me/CLIProxyAPI/issues/1293)、[PR #1294](https://github.com/router-for-me/CLIProxyAPI/pull/1294)），agno 跳过空 parts 的 Content（[issue #2649](https://github.com/agno-agi/agno/issues/2649)），langchain-google 对空 contents 直接抛错（[PR #1298](https://github.com/langchain-ai/langchain-google/pull/1298)）。

- [Gemini REST Content 参考](https://ai.google.dev/api/generate-content)

## 步骤

1. [Part 与 Content 的序列化约束](./1_gemini_part_and_content_serialization.md) [DONE]

## 不在本任务范围

Issue 备注里提到的思考档位空 `optionId` 抛错，属于配置保存时的档位对齐问题，已由 PR #1391 覆盖，本任务不重复改动。
