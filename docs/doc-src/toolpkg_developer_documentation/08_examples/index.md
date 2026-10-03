---
title: 插件示例
status: draft
---

# 插件示例

本节收集从声明 API 到真实宿主行为的完整示例；示例必须能对应仓库声明和实现，不用泛化 JavaScript 教程代替插件契约。

## 入口与 UI

- [最小 ToolPkg 快速开始](../01_getting_started/quick_start.md)
- [Compose DSL 组件与状态管理](../06_ui_and_compose/compose_dsl.md)
- [Material 3 组件调用](../06_ui_and_compose/material3_components.md)

## Hook 与模块调用

- [消息处理两阶段接管](../05_hooks/message_processing.md)
- [聊天输入提交决策](../05_hooks/chat_input.md)
- [Prompt 与摘要流水线](../05_hooks/prompt_pipeline.md)
- [AI Provider 流式返回](../05_hooks/ai_provider.md)
- [工具模块 API](../04_modules/index.md)

完整示例尚需逐项验证输入、返回值和异常分支；未验证的代码不会标为可直接发布。