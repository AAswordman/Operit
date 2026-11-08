---
title: 脚本接口与 Operit Editor
status: complete
---

# 脚本接口与 Operit Editor

## 旧实现

`Tools.SoftwareSettings` 的 JavaScript 桥和 `examples/types/software_settings.d.ts` 没有角色卡接口。`examples/operit_editor.ts` 及其 JavaScript 同步产物也没有角色卡工具。

## 修改意图

将原生角色卡工具映射为 `Tools.SoftwareSettings` 方法，为输入和输出提供严格的 TypeScript 类型，并在 `operit_editor` 提供可由包调用的角色卡管理工具。

## 同步范围

- `examples/operit_editor.ts` 是编辑器源文件
- `examples/operit_editor.js` 是其 JavaScript 产物
- `app/src/main/assets/packages/operit_editor.js` 是内置包副本

这三个文件的角色卡工具契约必须一致。

已新增 `Tools.SoftwareSettings` 的角色卡方法和结果类型，并在 editor 注册九个角色卡管理工具。数组参数支持 string array 及其 JSON 字符串表示，editor 元数据统一使用 JSON 字符串数组。

[DONE]
