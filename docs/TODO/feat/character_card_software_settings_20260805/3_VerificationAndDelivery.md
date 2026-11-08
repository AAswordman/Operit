---
title: 源码复核与交付
status: complete
---

# 源码复核与交付

## 检查项

- 原生工具注册名、软件设置执行器和 JavaScript 桥一一对应
- TypeScript 参数和结果类型与原生字段一致
- `operit_editor.ts` 的工具定义、参数校验和调用覆盖全部角色卡接口
- JavaScript 产物与应用内置副本同步
- 现有 `Tools.Chat.listCharacterCards()` 继续保留

## 执行限制

用户未要求编译、构建或测试。本步骤不执行这些命令。

已确认原生注册名、JavaScript 桥、TypeScript 声明和 editor 的接口数量与名称一致。`examples/operit_editor.js` 与 `app/src/main/assets/packages/operit_editor.js` 的哈希一致，`git diff --check` 未报告空白错误。

[DONE]
