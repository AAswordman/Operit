---
title: 聊天主题可见性修复
fork: https://github.com/luojiaping/Operit.git
branch: feat/plugin-interface
status: implementation_in_progress
---

# 聊天主题可见性修复

## 现状

`chat.main` 的 composer 宿主以 `fillMaxSize()` 参与 scene scaffold 的 bottom 测量，可能占用 transcript 的全部高度；赛博背景因而直接暴露在消息区域。与此同时，V2 包已声明的 `composer`、`input`、`message_user` 和 `message_assistant` 皮肤没有被聊天原生组件消费，Agent 输入器仍使用固定 Material 容器，Cursor AI 消息则渲染在透明背景上。

## 目标

- transcript 始终获得除 header/composer 外的可用高度。
- Agent 与 Classic 输入器的 composer、编辑器、主要交互控件消费 V2 skin。
- Cursor 与 Bubble 的用户/AI 消息拥有皮肤定义的可读容器、内容色和轮廓，背景图片不能吞没消息。
- 保持发送、队列、附件、输入法、流式回复、消息操作和无障碍语义的现有行为。

## 范围

1. [布局修复](./1_ComposerMeasurement.md)
2. [输入器与消息皮肤](./2_ComponentSkinConsumption.md)
3. [验证与设备验收](./3_Validation.md)

## 主题包边界

当前 V2 主题 manifest 的 skin 声明已满足本次修复所需契约。本次只修复 APK 内的消费端；除非设备视觉验收发现 manifest 本身的设计错误，否则不重发主题包。
