---
fork_repository: https://github.com/AAswordman/Operit.git
---

# 记忆提取附加规则

长期记忆的自动与手动 AI 整理只使用内置知识图谱提示词，用户无法限定入库范围、分类偏好或文本写法。

本次为每个记忆空间增加独立的记忆提取附加规则，并在记忆库设置的自动保存分组中提供编辑入口。规则由自动保存调度器和手动更新共用，内置的长期价值筛选与 JSON 输出协议保持有效。

作用域：

- `MemorySearchSettingsPreferences` 中的记忆空间配置
- 记忆库设置弹窗与 ViewModel 保存链路
- `MemoryLibrary` 的知识图谱提取提示词

步骤：

- [01 设置与提示词接入](01_settings_and_prompt_integration.md)
