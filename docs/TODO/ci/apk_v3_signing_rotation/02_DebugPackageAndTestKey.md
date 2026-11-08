---
title: 步骤 2：Debug 独立包名与测试密钥
status: draft
document_type: implementation-step
plan_scope: apk-v3-signing-rotation
step: 2
depends_on:
  - 01_NightlyAndReleaseDualSigning.md
fork_repository: "https://github.com/AAswordman/Operit.git"
last_reviewed: 2026-08-07
---

# 步骤 2：Debug 独立包名与测试密钥

## 旧实现情况

- Debug 使用正式 `applicationId`
- 配置到本地正式密钥时，Debug 使用该密钥签名
- Debug 与正式版不能并行安装
- 动态快捷方式固定指向正式包名

## 意图修正

旧 Debug APK 已发布，但发布规范不完整。协作者直接安装正式版覆盖旧 Debug，不保留旧 Debug 的更新或数据迁移路径。

## 预期的新实现情况

- Debug 的 application ID 为 `com.ai.assistance.operit.debug`
- Debug 使用 Android 默认 debug keystore
- Debug 启动器名称为 `Operit Debug`
- Debug 快捷方式只打开 Debug 包
- 正式版、Nightly 与 Debug 可以并行安装

## 修改作用域

已修改：

- `app/build.gradle.kts`
- `app/src/debug/res/xml/shortcuts.xml`

不修改：

- 正式版与 Nightly 的 application ID
- 正式版与 Nightly 的轮换签名
- 旧 Debug APK 的更新链和用户数据

## 验收

- Debug APK package 为 `com.ai.assistance.operit.debug`
- Debug 签名证书不同于正式发布证书
- Debug 与正式版可同时安装
- Debug 快捷方式指向 `com.ai.assistance.operit.debug`

## 完成记录

状态：[DONE]。
