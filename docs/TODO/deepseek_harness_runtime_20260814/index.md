---
title: DeepSeek Harness Runtime ToolPkg
status: in_progress
---

# DeepSeek Harness Runtime ToolPkg

## 原状

- `sidebar_opencode` 已在 Linux 终端中安装 Node CLI，启动仅监听回环地址的 Web 服务，并在 ToolPkg WebView 中承载其原生 UI。
- Operit 的 ToolPkg 不能直接解析 NPM/Cordis bundle，但终端运行时可以执行原始 Node 与 pnpm 依赖图。
- DeepSeek Harness 以 NPM 包和 Cordis profile bundle 分发；当前接入目标为 `@deepseek-ai/dsh@0.1.0-rc.6`。

## 意图

- 新增独立的 `sidebar_deepseek_harness` ToolPkg，托管原始 DeepSeek Harness Web Runtime。
- 将 Harness 绑定到 Linux 回环地址，并在主侧边栏 WebView 中显示官方 Web UI。
- 为后续 DSH bundle 安装、离线 tarball 导入和 Runtime 状态管理建立独立运行目录与稳定入口。

## 兼容约束

- 不修改现有 ToolPkg 导入协议、OpenCode 容器或终端 API。
- DSH 运行在 Linux 终端环境，不能获得 ToolPkg 的 Java/Android Bridge。
- 固定 DSH 版本；不在启动时自动升级到未知上游版本。

## 作用域

- Runtime 目录、pnpm 环境、安装检查、后台进程、健康检查和日志诊断。
- `main_sidebar_plugins` 路由与 WebView 承载页面。
- 示例 ToolPkg 的 manifest、TypeScript 源码和已编译 `dist` 产物。

## 非目标

- 不把 DSH bundle 接入 Operit 原生包管理器。
- 不翻译 Cordis/React 插件为 Compose DSL。
- 不在此阶段实现 DSH bundle 管理页或离线 tarball 导入。

## 验证

- 静态确认 Runtime 固定 `@deepseek-ai/dsh@0.1.0-rc.6`、监听 `127.0.0.1`，且 WebView 不暴露原生桥。
- 静态确认示例被 ToolPkg 打包器识别为独立 TypeScript ToolPkg。
- 未运行编译、构建或设备测试，遵守本任务的执行约束。

