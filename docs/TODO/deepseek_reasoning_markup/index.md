---
title: DeepSeek reasoning markup safety
status: draft
document_type: bugfix-plan-index
step: 0
depends_on: []
fork_repository: https://github.com/CATMIAOZHI/Operit
branch: fix/deepseek-reasoning-markup
issue: https://github.com/AAswordman/Operit/issues/727
reference_implementation: https://github.com/CATMIAOZHI/Operit/commit/9723b5c5
last_reviewed: 2026-07-18
---

# DeepSeek reasoning markup safety

DeepSeek 的 `reasoning_content` 会被直接放入 Operit 内部 `<think>` 标签。历史或未来后端若返回字面量 `</think>`，该文本会被误认为内部结构，继而影响流式显示和工具解析；当前 deepseek-v4-flash 实测未复现该注入，因此 codec 同时承担后端回退时的防御性兼容保护。

本任务计划在 Provider 边界引入版本化 XML text codec。canonical 消息、历史、数据库和工具解析保持编码态，只在共享历史投影、支持 reasoning 的 Provider 请求字段和已隔离的展示正文中解码。

fork 上已有参考实现，供方案审查和验证使用；它早于本次跨 Provider 契约修订，尚未实现共享历史投影，也不属于此 docs-only PR。上游接受处理边界后再修订并单独提交代码 PR。

## Scope

- DeepSeek 流式与非流式 reasoning 输出
- 对话切换到 Kimi、通用 OpenAI 或不支持 reasoning 的 Provider 时的历史投影
- thinking、history preservation 和 ToolCall 三类正交控制组合下的历史 round-trip
- DeepSeek 原生 ToolCall 后续请求所需的协议状态
- Kimi/MiMo thinking 开关与独立的请求级历史投影能力
- v1 reasoning segment 的来源标识及普通正文同形 XML
- Android、Web Chat、消息编辑器和可读导出
- 新格式测试及旧历史兼容测试

本任务不处理通用正文 XML provenance、#685/#699、`tool_call_id` 持久化，也不改变其他 Provider 自身 reasoning 响应的 canonical 编码方式。为避免把普通正文中的精确 v1 示例误删，本任务只为内部生成的 v1 reasoning segment 保留最小来源标识；该标识不扩展到其他正文 XML。跨 Provider 部分只处理 DeepSeek v1 历史向目标 Provider 请求格式的安全投影。

## Steps

- [Codec and provider boundary](1_CodecAndProvider.md)
- [Presentation boundaries](2_Presentation.md)
- [Compatibility and verification](3_Verification.md)

## Completion

本计划尚未完成。代码、文档和授权验证全部完成后，在各步骤文档的一级标题末尾添加 `[DONE]`。
