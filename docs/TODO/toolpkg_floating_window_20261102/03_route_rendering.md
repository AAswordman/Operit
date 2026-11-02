# 03 route 渲染接入

## 旧实现

插件 Compose DSL 渲染有两套现成链路：

- 应用内：`ui/common/composedsl/ToolPkgComposeDslScreen.kt`（工具箱/导航
  route 渲染，`keepAlive` 由 `stableScreenKey` 维持）
- 聊天内：`ui/common/markdown/XmlRenderPluginRegistry.kt` 的
  `RenderComposeDslScreen`——acquire/release 专用引擎 + 节点级复用
  `RenderToolPkgComposeDslNode`
- 引擎池：`ToolPkgManager`，contextKey 前缀区分 runtime 种类
  （`toolpkg_main:` / `toolpkg_widget:` / `toolpkg_xml_render:` 等），
  lease 归零销毁

## 意图修正

浮窗内容 = 插件注册的 UiRoute（Compose DSL），走专用引擎上下文，open 时
建立会话、update 时浅合并 state 后重渲染、close/hide 会话保留策略与
控制器一致。

## 期待的新实现

- contextKey：`toolpkg_floating:<owner>:<windowId>`，走
  `acquireToolPkgExecutionEngine` / `releaseToolPkgExecutionEngine` 租约
- route 脚本经 `PackageManager.getToolPkgComposeDslScript(owner, moduleId)`
  获取；render 用 `executeComposeDslScript`（`__operit_render_compose_dsl`），
  state 更新用 `rerenderComposeDslTree`，动作回调用
  `dispatchComposeDslActionAsync`（对齐 `XmlRenderPluginRegistry` 用法）
- 初始 state 来自 `open` 请求；`update` 浅合并后作为重渲染输入
- 渲染参数注入对齐 `ToolPkgComposeDslScreen.buildActionRuntimeOptions()`：
  `__operit_package_name` / `containerPackageName` / `toolPkgId` /
  `__operit_ui_package_name` / `__operit_compose_execution_context_key`
- route 不存在（插件未注册该 id）→ open 报错，不建会话



[DONE]
