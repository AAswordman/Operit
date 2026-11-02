# 05 权限与 capabilities

## 旧实现

- overlay 权限检查/跳设置页的现成模式（多处一致）：
  `Settings.canDrawOverlays(context)` 检查；`requestOverlayPermission()`
  用 `Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
  Uri.parse("package:..."))` + `FLAG_ACTIVITY_NEW_TASK`（
  `PermissionRequestOverlay.kt` 349-379、`ChatViewModel.kt`、
  `AIForegroundService.kt` 等）。

## 意图修正

capabilities 反映 Android 真实能力；requestPermission 行为对齐 Operit2
（"打开宿主的 overlay 权限设置页，之后复查 capabilities"）。

## 期待的新实现

- `capabilities()`（无副作用、不弹权限提示）：
  - `scope` = `canDrawOverlays` 时 `"system"`，否则 `"unsupported"`
  - `systemOverlay` = `permissionGranted` = `canDrawOverlays`
  - `applicationOverlay` = `false`（Android 侧无"仅应用内"overlay 通道）
  - `supported` = `canDrawOverlays`
  - `positioning` / `alwaysOnTop` / `clickThrough` / `focusable` = `true`
- `requestPermission()`：已授权直接返回 `true`；未授权跳系统设置页，
  返回 `true` 表示已发起跳转（插件应在设置返回后复查 capabilities，
  与 Operit2 语义一致）
- `open` 在未授权时抛错（信息含权限指引），与 Operit2 的
  `permissionGranted` 语义对齐



注：capabilities 与 requestPermission 实现位于 ToolPkgFloatingWindowRuntime（随 03 的装配门面落地）。

[DONE]
