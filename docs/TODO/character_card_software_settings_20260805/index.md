---
title: SoftwareSettings 角色卡管理接口
fork: https://github.com/tuxKOH/Operit
status: complete
---

# SoftwareSettings 角色卡管理接口

## 当前状况

`CharacterCardManager` 已支持角色卡的读取、创建、更新、删除、激活以及酒馆 JSON 导入和导出。脚本侧只有 `Tools.Chat.listCharacterCards()`，其返回内容仅用于会话选卡，缺少完整字段和管理操作。

`Tools.SoftwareSettings` 与 `operit_editor` 均不能管理角色卡。

## 预期结果

- 在 `Tools.SoftwareSettings` 增加角色卡的列表、详情、创建、更新、删除、激活与清除活跃状态接口
- 提供单张角色卡的酒馆 JSON 导入和导出接口
- 通过 JavaScript 桥、TypeScript 声明和 `operit_editor` 暴露全部接口
- 继续保留 `Tools.Chat.listCharacterCards()`，让会话工具保持原有调用方式

## 接口边界

`SoftwareSettings` 负责角色卡配置本身。`Chat` 只在创建会话或发送消息时引用角色卡。

写入接口只接受可编辑字段。角色卡 ID、创建时间和默认角色卡属性由角色卡管理器维护，调用方不能通过更新接口改变它们。

## 步骤

1. [原生角色卡管理工具](./1_NativeCharacterCardTools.md) [DONE]
2. [脚本接口与编辑器](./2_ScriptApiAndOperitEditor.md) [DONE]
3. [源码复核与交付](./3_VerificationAndDelivery.md) [DONE]

## 执行约束

本任务不在本地运行编译、构建或测试命令。验证使用源码检查、接口映射审查、产物同步检查和 Git diff 审查。

## 完成记录

已新增角色卡的完整配置管理接口，保留现有 `Tools.Chat.listCharacterCards()`。`examples/operit_editor.ts` 与两份 JavaScript 包产物已同步，源码复核确认工具注册、JavaScript 桥、类型声明和 editor 入口一致。
