# 02 窗口宿主与控制器

## 旧实现

- 仓库有三个独立 overlay ComposeView 宿主实现，无通用抽象：
  - `services/floating/FloatingWindowManager.kt`（聊天专用）
  - `ui/permissions/PermissionRequestOverlay.kt`（权限弹层，最干净的
    独立宿主样板：`TYPE_APPLICATION_OVERLAY` + `ComposeView` +
    `ServiceLifecycleOwner` 生命周期驱动 + `removeView`）
  - `services/floating/UIDebuggerWindowManager.kt`（调试球）
- 无任何插件侧窗口注册表/审计设施。

## 意图修正

新增插件悬浮窗的宿主与控制器，行为契约逐条对齐 Operit2
`RuntimeFloatingWindowController`。

## 期待的新实现

新增 `core/tools/packTool/floating/ToolPkgFloatingWindowController.kt`：

- 命令入口 `command(ownerPackage, requestJson): resultJson`，op 分发
  `capabilities/requestPermission/open/update/show/hide/close/list`
- 命令串行化：单一 `HandlerThread`/主线程消息队列顺序执行
- 窗口注册表 `key = owner + '\u0000' + id`；ID 校验 trim 后 1–128 字符；
  重复 open 抛错；单 owner 上限 16
- `update`：options 补丁合并 + state 浅合并（不重建 UI 会话）
- `show/hide`：切 visible 保留会话；`close`：释放引擎 + removeView + 注销
- 审计：插件禁用/卸载时强制关窗（订阅包目录变更 + 周期对账）；
  窗口被外部移除时修剪注册表
- 窗口操作抽象成接口（`ToolPkgFloatingWindowSurface`），控制器纯逻辑
  可 JVM 单测，不依赖 WindowManager

新增 `core/tools/packTool/floating/ToolPkgFloatingWindowHost.kt`：

- 照 `PermissionRequestOverlay` 模式：`ComposeView` +
  `ServiceLifecycleOwner`（ON_CREATE/START/RESUME ↔ PAUSE/STOP/DESTROY）+
  `WindowManager.LayoutParams(TYPE_APPLICATION_OVERLAY, TRANSLUCENT,
  TOP|START, x, y, w, h)`
- flags 映射：`clickThrough` → `FLAG_NOT_TOUCHABLE`；`focusable=false` →
  `FLAG_NOT_FOCUSABLE`；尺寸校验 16–4096 逻辑像素
- `update` 走 `updateViewLayout`；可见性走 add/removeView（hide 保留组合
  树与引擎会话时用 `View.GONE` + 不移除窗口的轻量方案或 removeView+保留
  引擎，实现时按 Android 约束取其一并记录在此）



[DONE]
