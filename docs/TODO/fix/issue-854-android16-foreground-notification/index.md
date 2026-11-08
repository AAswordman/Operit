---
fork_repository: local main
issue: https://github.com/AAswordman/Operit/issues/854
---

# Issue 854 Android 16 前台通知

## 现状

在 Android 16 设备上，发送第二条对话消息时，`AIForegroundService` 的通知可能触发
`BadForegroundServiceNotificationException`。日志显示系统无法膨胀通知内容视图，并尝试读取一个不存在的字符串数组资源。

当前通知通过 `androidx.core:core-ktx` 1.12.0 创建。该版本发布时仅面向 Android 14，应用本身则已使用 Android 16 SDK。

## 目标

将通知兼容层升级到支持 Android 16 的稳定版本，不改变前台通知的频道、操作或可见文本。

## 作用域

- `gradle/libs.versions.toml`
- 本目录中的排查与验证记录

## PR

待创建
