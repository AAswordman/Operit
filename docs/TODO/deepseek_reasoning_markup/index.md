---
fork: https://github.com/CATMIAOZHI/Operit
branch: fix/deepseek-reasoning-markup
status: in-progress
issue: https://github.com/AAswordman/Operit/issues/727
reference_implementation: https://github.com/CATMIAOZHI/Operit/commit/9723b5c5
---

# DeepSeek reasoning markup safety

DeepSeek 的 `reasoning_content` 当前会被直接放入 Operit 内部 `<think>` 标签。模型返回字面量 `</think>` 时，该文本会被误认为内部结构，继而影响流式显示和工具解析。

本任务计划在 Provider 边界引入版本化 XML text codec。canonical 消息、历史、数据库和工具解析保持编码态，只在 DeepSeek 请求字段和已隔离的展示正文中解码。

fork 上已有参考实现，供方案审查和验证使用；该实现不属于此 docs-only PR，上游接受处理边界后再单独提交代码 PR。

## Scope

- DeepSeek 流式与非流式 reasoning 输出
- ToolCall 开启和关闭时的历史 round-trip
- Android、Web Chat、消息编辑器和可读导出
- 新格式测试及旧历史兼容测试

本任务不处理普通正文 XML provenance、#685/#699、其他 Provider 的 reasoning 或 `tool_call_id` 持久化。

## Steps

- [Codec and provider boundary](1_CodecAndProvider.md)
- [Presentation boundaries](2_Presentation.md)
- [Compatibility and verification](3_Verification.md)
