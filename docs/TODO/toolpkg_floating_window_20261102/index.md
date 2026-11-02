---
fork: https://github.com/luojiaping/Operit
date: 2026-11-02
source: 用户需求：把 Operit 的插件悬浮窗接口与 Operit2 统一
---

# ToolPkg 插件悬浮窗 API（对齐 Operit2）

## 原本状况

- Operit 与 Operit2 共享同一份 ToolPkg v1 类型契约（`examples/types/toolpkg.d.ts`
  与 `plugins/types-v1/toolpkg.d.ts` 逐字节一致），Registry 为平铺结构
  （`ipc`/`wasm`/各 `register*`），**没有任何插件悬浮窗运行时 API**。
- Operit2 已在 v2 类型（`plugins/types/toolpkg.d.ts`，`@since 2.0.0`）新增
  `Registry.ui.floatingWindows`：capabilities / requestPermission / open /
  update / show / hide / close / list 七个方法；窗口内容为插件注册的
  Compose UI route + 初始 state；行为语义见其
  `RuntimeFloatingWindowController.dart`（属主鉴权、命令串行化、ID 1–128、
  单插件 16 窗上限、state 浅合并、禁用插件审计回收）。
- Operit 侧已有可复用设施：overlay ComposeView 宿主模式
  （`PermissionRequestOverlay`）、ipc 的 callbackId 异步桥接模式、
  聊天内 Compose DSL 渲染链（`XmlRenderPluginRegistry`）、引擎池租约
  （`ToolPkgManager`）。

## 意图

在 Operit 实现**与 Operit2 同形**的 `ToolPkg.ui.floatingWindows`：方法签名、
类型结构、行为语义逐条对齐；窗口内容渲染插件 UiRoute（Compose DSL），
底层 Android WindowManager overlay。桌面小组件接口两边已同构，不改；
`FloatingChatService` 聊天悬浮窗内部接口，不改。

## 版本编号（已与用户确认）

Operit 走本地编号线：新增能力标 `@since ToolPkg API 1.1.0`，
宿主支持集合加入 `1.1.0`。**不跟随 Operit2 的 2.0.0**——v2 接口插件在
Operit 不是全部兼容，版本号分开可让宿主门禁诚实拦截 v2-only 包。

## 作用域（分步文档）

1. [01 类型与版本门禁](01_types_and_version_gate.md)
2. [02 窗口宿主与控制器](02_window_host_and_controller.md)
3. [03 route 渲染接入](03_route_rendering.md)
4. [04 JS 桥接](04_js_bridge.md)
5. [05 权限与 capabilities](05_permissions.md)
6. [06 文档、示例包与单测](06_docs_example_tests.md)

分支：`feat/toolpkg-floating-window-api`（基于 `upstream/dev`）。
