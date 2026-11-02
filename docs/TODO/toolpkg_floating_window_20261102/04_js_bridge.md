# 04 JS 桥接

## 旧实现

- `JsToolPkgRegistration.kt` 的 toolpkg-bridge 胶水：`toolPkgApi.namespace
  ('ToolPkg', {...})` 暴露 register*/readResource/getConfigDir；版本化方法用
  `toolPkgApi.method().since('1.0.1', ...)`。
- ipc 的异步回程（最佳参照）：JS 侧 callbackId + `root[callbackId]` Promise
  resolve/reject（`JsExecutionScriptBuilder.kt` 925-991）→ Kotlin
  `invokeToolPkgIpcAsync`（`JsEngine.kt` 1744-1779，线程执行）→
  `sendToolPkgIpcResult` 生成回调脚本经 `launchQuickJsEvaluation` 回填。
- 属主身份：`resolveCurrentToolPkgTarget()`（params 注入链
  `__operit_ui_package_name` → `toolPkgId` → ...）。

## 意图修正

`ToolPkg.ui.floatingWindows` 七方法在 bridge 胶水里以 `@since 1.1.0` 版本化
暴露，异步命令统一走 callbackId 回程。

## 期待的新实现

- bridge 脚本（`buildToolPkgRegistrationBridgeScript`）新增 `ui` 成员：
  `floatingWindows: { capabilities, requestPermission, open, update, show,
  hide, close, list }`，每个方法 `toolPkgApi.method().since('1.1.0', ...)`
- 每方法：生成 `__operit_toolpkg_float_<ts>_<rand>` callbackId，挂
  `root[callbackId]`，调 `NativeInterface.invokeToolPkgFloatingWindowCommand(
  callbackId, ownerPackage, op, argsJson)`；owner 由
  `resolveCurrentToolPkgTarget()` 解析，解析失败直接 reject
- `JsEngine.JsToolCallInterface` 新增 `@JavascriptInterface
  invokeToolPkgFloatingWindowCommand`：线程内调用控制器，结果经
  `sendToolPkgFloatingWindowResult`（复用
  `buildStringResultCallbackScript` 模式）回填 Promise
- op 编组与控制器对齐：`open` 传完整 options 对象；`update` 传
  `{id, options, state?}`；单 id 方法传 `{id}`



[DONE]
