---
title: ToolPkg 包结构与运行入口
status: draft
---

# ToolPkg 包结构与运行入口

ToolPkg 是带 manifest 的插件包。宿主不是把其中任意 JS 文件直接当作普通脚本执行，而是先解析包格式，再执行主入口的注册函数，校验并安装注册结果。

## 建议目录

```text
my_toolpkg/
	manifest.json
	main.ts
	dist/
		main.js
	src/
		ui/
			panel.ts
	resources/
		logo.svg
		config.json
	modules/
		core.wasm
```

- `manifest.json` 或 `manifest.hjson`：包身份、API 版本、入口、依赖、子包、资源和模板声明。
- `main`：manifest 指定的宿主执行入口；通常指向编译后的 `dist/main.js`。
- 主入口：导出 `registerToolPkg()`，只注册模块与 Hook，不承担常驻运行逻辑。
- `resources/`：通过 manifest 的 `resources[]` 声明，再由资源 API 按 key 读取。
- `src/`、`dist/`、`modules/` 等目录名称由包作者组织；宿主按 manifest 路径解析，不要求固定源码布局。

## 宿主加载过程

1. 定位并读取 manifest。
2. 解析 `toolpkg_id`、`version`、`api_version`、`main` 等字段；当前 API 版本不受宿主支持时包加载失败。
3. 检查依赖及资源、子包和可选模块声明。
4. 在注册会话中执行 main 模块的 `registerToolPkg()`。
5. 将 UI、Hook、Provider 等注册定义编码并解析为运行时对象。
6. 只有有效注册结果才进入 ToolPkg runtime；后续触发的 Hook 在对应宿主 runtime context 中执行。

主入口注册过程不是持久 JavaScript session。主入口求值时建立的全局变量不会作为后续工具调用或 UI Hook 的跨调用状态；需要持久数据时使用明确的资源、配置目录或宿主状态接口。

## 子包函数引用

注册对象中包含的 `function` 字段必须能成为稳定的包内函数引用。运行时优先查找当前模块导出名；对于从其他模块导入的函数，会根据 ToolPkg 编译/加载时记录的模块路径和导出名建立引用。未导出、且缺少稳定模块路径标记的匿名/临时函数会使注册失败。

Compose screen 也必须能被宿主序列化为包内模块路径。不要传递闭包捕获的局部状态作为持久 screen/function 引用；后续调用会在新的执行上下文中重新加载模块函数。

## 入口与普通脚本包的区别

- 普通脚本包由某个工具调用 `exports` 函数执行。
- ToolPkg 主入口先注册宿主扩展点；工具子包脚本仍可通过普通工具运行时被调用。
- ToolPkg manifest 的 `api_version` 控制宿主接口契约；普通脚本中的语言语法或 npm 构建目标不改变该版本。

## 字段与格式详解

- [Manifest 字段索引](./manifest.md)
- [ToolPkg 格式、资源、子包、WASM、UI 与调试安装完整说明](./package_format.md)
- [ToolPkg API 版本与兼容](../09_compatibility/api_versions.md)

实现依据：`ToolPkgParser.kt`、`ToolPkgMainRegistrationScriptParser.kt`、`JsToolPkgRegistration.kt`。