---
title: 聊天角色卡 UUID 绑定回退
fork: https://github.com/tuxKOH/Operit
status: complete
---

# 聊天角色卡 UUID 绑定回退

## 当前状况

未发布的 Issue 864 将聊天与单角色卡的绑定从 `characterCardName` 改为 `characterCardId`，并同步改动了 Android/Kotlin 与 assistance2 Rust 实现。该方案破坏了既有聊天绑定。

## 修改意图

彻底移除本轮单角色卡 UUID 聊天绑定，恢复聊天仅通过 `characterCardName` 绑定角色卡。删除相关数据库迁移、持久化字段、传递链路和 ToolPkg 类型字段，不保留兼容层。

角色卡自身的内部 ID、角色群组 `characterGroupId` 与 SoftwareSettings 角色卡管理接口不在回退范围。

## 步骤

1. [Kotlin 聊天绑定与数据库回退](./1_KotlinChatBindingRollback.md) [DONE]
2. [Rust 与 ToolPkg 类型回退](./2_RustAndToolPkgRollback.md) [DONE]
3. [源码复核与交付](./3_VerificationAndDelivery.md) [DONE]

## 执行约束

本任务不执行编译、构建或测试命令。仅执行源码检索、差异审查和空白检查。

[DONE]
