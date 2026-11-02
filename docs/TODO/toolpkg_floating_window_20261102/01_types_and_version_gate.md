# 01 类型与版本门禁

## 旧实现

- `examples/types/toolpkg.d.ts`：Registry 平铺（`ipc`/`wasm` 直挂），无 `ui`
  命名空间，无任何悬浮窗类型。`@since` 最高到 `1.0.1`。
- `core/tools/packTool/ToolPkgApiVersion.kt`：`supportedApiVersions()` 只含
  `1.0.0` / `1.0.1`，`requireSupported` 拒绝其他版本。

## 意图修正

把 Operit2 v2 的五个悬浮窗接口定义（FloatingWindowOptions /
FloatingWindowOpenOptions / FloatingWindowCapabilities / FloatingWindowInfo /
FloatingWindowApi + UiApi 容器）以**同形**方式引入 Operit 的 d.ts，挂到
`Registry.ui.floatingWindows`；版本标 `@since ToolPkg API 1.1.0`（本地编号线，
不跟 2.0.0）。

## 期待的新实现

- `examples/types/toolpkg.d.ts`：
  - 新增 `ToolPkg.FloatingWindowOptions`（x/y/width/height/alwaysOnTop/
    clickThrough/focusable/draggable，注释与 Operit2 逐字对齐）
  - 新增 `ToolPkg.FloatingWindowOpenOptions`（id/route/state/options）
  - 新增 `ToolPkg.FloatingWindowCapabilities`（scope/systemOverlay/
    applicationOverlay/supported/permissionGranted/positioning/alwaysOnTop/
    clickThrough/focusable）
  - 新增 `ToolPkg.FloatingWindowInfo`（id/route/visible/options）
  - 新增 `ToolPkg.FloatingWindowApi`（capabilities/requestPermission/open/
    update/show/hide/close/list）
  - 新增 `ToolPkg.UiApi { floatingWindows }`，`Registry` 增加 `ui: UiApi`
  - 全部标 `/** @since ToolPkg API 1.1.0 */`
- `ToolPkgApiVersion.kt`：新增 `API_VERSION_1_1_0 = "1.1.0"` 常量与
  introduced-in 记录，`supportedApiVersions()` 加入 `1.1.0`。



[DONE]
