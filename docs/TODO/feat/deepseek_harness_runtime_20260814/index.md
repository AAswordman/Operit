---
title: DeepSeek Harness Runtime ToolPkg
status: in_progress
---

# DeepSeek Harness Runtime ToolPkg

## 原状

- `sidebar_opencode` 已在 Linux 终端中安装 Node CLI，启动仅监听回环地址的 Web 服务，并在 ToolPkg WebView 中承载其原生 UI。
- Operit 的 ToolPkg 不能直接解析 NPM/Cordis bundle，但终端运行时可以执行原始 Node 与 pnpm 依赖图。
- DeepSeek Harness 以 NPM 包和 Cordis profile bundle 分发；当前接入目标为 `@deepseek-ai/dsh@latest`。

## 意图

- 新增独立的 `sidebar_deepseek_harness` ToolPkg，托管原始 DeepSeek Harness Web Runtime。
- 将 Harness 绑定到 Linux 回环地址，并在主侧边栏 WebView 中显示官方 Web UI。
- 为后续 DSH bundle 安装、离线 tarball 导入和 Runtime 状态管理建立独立运行目录与稳定入口。
- 合并官方协作实现的服务控制能力，提供状态查询、启动、重启和停止入口，同时保留 `node-pty` 校验。

## 兼容约束

- 不修改现有 ToolPkg 导入协议、OpenCode 容器或终端 API。
- DSH 运行在 Linux 终端环境，不能获得 ToolPkg 的 Java/Android Bridge。
- 进入侧边栏先检查本地与上游 `latest` 版本；初始化和更新仅由用户显式操作触发。

## 作用域

- Runtime 目录、pnpm 环境、安装检查、后台进程、健康检查和日志诊断。
- `main_sidebar_plugins` 路由与 WebView 承载页面。
- 示例 ToolPkg 的 manifest、TypeScript 源码和已编译 `dist` 产物。
- ToolPkg 控制子包与服务会话脱离；不暴露 Linux Runtime 中的 DSH 凭据原文。
- ADB 调试工具对 Debug/Release 两个 applicationId 的统一解析与动态路径生成。

## 非目标

- 不把 DSH bundle 接入 Operit 原生包管理器。
- 不翻译 Cordis/React 插件为 Compose DSL。
- 不在此阶段实现 DSH bundle 管理页或离线 tarball 导入。

## 验证

- 静态确认 Runtime 使用 `@deepseek-ai/dsh@latest`、监听 `127.0.0.1`，且 WebView 不暴露原生桥。
- 静态确认示例被 ToolPkg 打包器识别为独立 TypeScript ToolPkg。
- 已在目标手机的 Linux ARM64 容器中确认 Node `v24.19.0`、`node-gyp` 编译链和 `node-pty` 原生模块加载路径。
- 已确认手机文件系统会在 `node-pty` 的 `postinstall` 后留下悬空 `pty.node` 符号链接；运行时已改为跳过该清理钩子并解引用产物。
- 已用修复后的 ToolPkg 启动 DSH Web；实际 PTY 执行返回 `pty-ok`，回环服务返回 HTTP 200，WebView 已进入官方 API Key 配置页。
- 已补齐 ADB JS 执行器、ToolPkg 调试安装和示例包热更新的 Debug/Release 双包选择；详见 `04_dual_app_debugging.md`。
- 已补齐安装失败反馈：仅收到显式成功标记才启动 Web，安装失败会直接展示实际原因；详见 `05_runtime_failure_feedback.md`。
- 正在修复 pnpm 对 DSH 生命周期脚本的构建审批；详见 `06_pnpm_dependency_builds.md`。
- 正在修复 node-pty 本地模块加载路径；详见 `07_node_pty_module_resolution.md`。
- 正在将运行时安装改为显式生命周期，并丰富侧边栏安装反馈；详见
  `10_explicit_runtime_lifecycle.md`。

