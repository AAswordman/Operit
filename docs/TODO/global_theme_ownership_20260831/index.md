---
title: 全应用主题所有权
fork: https://github.com/luojiaping/Operit.git
branch: feat/plugin-interface
status: implementation_in_progress
---

# 全应用主题所有权

## 目标

主题包是 Operit 日常自有界面的唯一视觉权威，而不是聊天页背景或局部组件皮肤。

主题包控制：主应用壳、状态栏与导航栏的可控外观、手机抽屉、平板导航、所有原生路由、聊天、输入器、弹窗、菜单、sheet、Snackbar、加载/空态/错误态、悬浮聊天、应用内权限壳、浏览器与 WebView 的 Operit 壳，以及 Operit WebChat。

宿主控制：数据、路由、业务事件、滚动、IME、焦点、无障碍语义、触控尺寸、平台生命周期和系统 API 调用。

插件自行输出的 Compose DSL/WebView/Canvas 内容不受主题包强制重绘；其 Operit 外层标题、加载、错误和容器必须被主题化。首次启动、崩溃报告、数据修复和桌面小组件维持固定界面。Android 系统权限框、SAF、输入法和状态栏图标内容由平台拥有。

## 未发布清理

旧 `.otheme` V1 格式、`operit.default@1.0.2`、`operit.cyber_grid@1.0.2`、局部 `chat.main` 叠加式渲染和旧 Release 均不形成兼容边界。

[DONE] `operit-theme-default` 与 `operit-theme-cyber-grid` 的 `v1.0.0`、`v1.0.1`、`v1.0.2` GitHub Release、资产与标签已删除。

## 目录

1. [V2 包与运行时契约](./1_V2PackageAndRuntime.md)
2. [全应用 Surface 目录](./2_SurfaceOwnershipCatalog.md)
3. [应用壳、聊天与组件宿主](./3_AppShellChatAndComponents.md)
4. [主题仓库与发布](./4_ThemeRepositoriesAndRelease.md)
5. [验收与覆盖门槛](./5_AcceptanceAndCoverage.md)
