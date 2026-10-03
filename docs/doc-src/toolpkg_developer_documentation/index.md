---
title: ToolPkg 开发者文档
status: draft
---

# ToolPkg 开发者文档

这套文档面向已有 JavaScript 或 TypeScript 基础、需要开发 Operit ToolPkg 的开发者。正文聚焦 Operit 宿主接口和 ToolPkg 运行契约，不重复讲解通用语言语法。

当前文档以仓库 `dev` 分支中的类型声明与 Android 运行时实现为依据。声明文件给出可调用形状；宿主实现用于核实执行时序、副作用、错误和版本门禁。若两者不一致，会在对应页面单独指出，不以类型声明替代运行时契约。

## 阅读路径

- 第一次开发：阅读[快速开始](./01_getting_started/quick_start.md)，再阅读[包结构与入口](./02_package_model/package_structure.md)。
- 查询某个运行时方法：从[运行时全局 API](./03_runtime/global_api.md)或[模块 API 索引](./04_modules/index.md)进入。
- 注册 UI、Hook 或 AI Provider：阅读[ToolPkg 注册 API](./03_runtime/registry.md)以及[Hook 参考](./05_hooks/index.md)。
- 排查旧版宿主兼容：阅读[API 版本与兼容规则](./09_compatibility/api_versions.md)。
- 查询返回字段：阅读[公共结果类型](./04_modules/results.md)。

## 目录结构

```text
toolpkg_developer_documentation/
	index.md
	01_getting_started/
		quick_start.md
	02_package_model/
		package_structure.md
		manifest.md
	03_runtime/
		global_api.md
		registry.md
		quickjs_runtime.md
	04_modules/
		index.md
		chat.md
		files.md
		network.md
		system.md
		software_settings.md
		memory.md
		workflow.md
		tasker.md
		android.md
	05_hooks/
		index.md
		chat_input.md
		prompt_pipeline.md
		lifecycle_and_chat.md
		tool_lifecycle.md
	06_ui_and_compose/
		index.md
		compose_dsl.md
		material3_components.md
		material_icons.md
	07_types_and_libraries/
		index.md
		java_bridge.md
		libraries.md
	08_examples/
		index.md
	09_compatibility/
		api_versions.md
		coverage.md
```

目录按盘点结果继续增补；没有覆盖完成的页面会保留草稿状态，不会在索引中伪装为完整。

## API 版本规则

ToolPkg API 版本表示包所依赖的宿主接口契约，独立于包自身的 `version` 和归档 `formatVer`。仓库当前实现支持 API `1.0.0`；API `1.0.1` 要求 Operit `1.12.1+4` 或更新版本。所有带版本门槛的字段、方法和 Hook 都会在其参考条目中标注 `@since ToolPkg API x.y.z`，并在[兼容规则](./09_compatibility/api_versions.md)集中列出宿主最低版本。

## 文档权威来源

- 全局声明与导出入口：[`examples/types/index.d.ts`](../../../examples/types/index.d.ts)
- ToolPkg 注册、Hook、IPC 和 WASM 类型：[`examples/types/toolpkg.d.ts`](../../../examples/types/toolpkg.d.ts)
- 各模块声明：[`examples/types/`](../../../examples/types/)
- 包 Manifest 与解析行为：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/ToolPkgParser.kt`
- API 版本支持策略：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/ToolPkgApiVersion.kt`
- JS 侧版本门禁与 ToolPkg facade：`app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsToolPkgApiRuntime.kt`、`JsToolPkgRegistration.kt`
- Hook 触发及结果消费：`app/src/main/java/com/ai/assistance/operit/plugins/toolpkg/`

## 条目约定

每个方法条目应尽量覆盖调用签名、版本要求、上下文与前置条件、参数字段约束、异步/超时语义、返回字段、错误条件、副作用及可运行示例。类型中存在的 `?`、`null`、联合类型和默认值会分别说明，不会互相替代。

Hook 页面还会说明宿主触发时机、阶段名称、事件 payload、跨 Hook 的数据变更方式、返回值解释、注册顺序、异常处理、共享超时预算和取消行为。尚未从实现核实的内容会明确标成待核实项。