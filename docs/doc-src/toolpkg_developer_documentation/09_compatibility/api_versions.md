---
title: ToolPkg API 版本与兼容
status: draft
---

# ToolPkg API 版本与兼容

本页描述包声明依赖的宿主 API 版本，以及解析、校验和逐方法门禁。这里的版本不是包自身发布版本。

## 三种版本分别表示什么

- `manifest.version` 是 ToolPkg 作者发布的包版本，用于识别包更新。
- `manifest.api_version` 是包依赖的 ToolPkg 宿主 API 契约版本。解析器将其转为 `ToolPkgApiVersion`，并在包加载前校验宿主支持性。
- `formatVer` 是发布归档/市场条目的格式版本，不决定脚本可以调用哪些宿主方法。市场版本对象里的 `apiVersion` 是 `manifest.api_version` 的展示/传输字段。

不要用提高 `manifest.version` 来声明使用了新宿主 API，也不要把格式迁移当作 API 兼容性升级。

## 当前宿主支持矩阵

兼容策略定义于 `ToolPkgApiCompatibility`：

- ToolPkg API `1.0.0` 是旧版基线。
- ToolPkg API `1.0.1` 从 Operit `1.12.1+4` 起支持。
- 当前实现没有把其他 ToolPkg API 版本列入支持集合。

支持集合按运行中的 `BuildConfig.VERSION_NAME` 计算。Operit 版本比较依次比较 major、minor、patch 和可选 build 数字；ToolPkg API 版本只接受严格的 `major.minor.patch` 三段格式，不接受前缀、后缀或缺段。

## Manifest 解析

`ToolPkgManifest.apiVersion` 对应 JSON/HJSON 字段 `api_version`，缺省值为 `1.0.0`。解析器在建立容器运行时前调用 `ToolPkgApiCompatibility.requireSupported()`：

1. 去除首尾空白；空值按 `1.0.0` 处理。
2. 校验 `major.minor.patch` 格式并解析三段整数。
3. 与当前 Operit 版本可用的 API 集合精确匹配。
4. 不支持或格式错误时拒绝 ToolPkg 解析，不会静默把未知版本当成旧接口继续运行。

开发者应在 `manifest.json` 中显式写出发布目标要求的 `api_version`，避免依赖缺省值而无意中声明为旧 API。

```json
{
  "toolpkg_id": "example.package",
  "version": "1.2.0",
  "api_version": "1.0.1",
  "main": "main.js"
}
```

## 声明版本与运行时门禁

类型声明中的 `@since ToolPkg API x.y.z` 表示该符号最低需要的 ToolPkg API 版本；它本身用于类型/API 文档，并不等价于 JavaScript 引擎自动隐藏该符号。

对于显式使用版本 facade 的方法，运行时从当前 ToolPkg 调用上下文读取已校验的 `apiVersion`，并在调用时执行门禁：

- API 版本上下文缺失或格式无效时抛出错误。
- 当前 `manifest.api_version` 低于方法最早 `since` 版本时抛出错误，错误包含方法名、所需版本和 manifest 声明版本。
- 存在多段实现时，选择 `since <= 当前 API 版本` 的最高版本实现。
- 重复版本、无效版本、非函数实现或没有适用实现会在 facade 构建/调用时抛出错误。

当前代码通过 `JsToolPkgApiRuntime.method().since(version, implementation).build(name)` 实现版本化方法。`ToolPkg.registerChatMessageMenuItem` 和 `ToolPkg.registerChatRuntimeHook` 目前有 `1.0.1` 调用门禁。其他方法应以各自页面的源码版本标注与宿主实现为准；不得仅因类型声明存在 `@since` 就假设运行时也会延迟隐藏成员。

## 给插件作者的兼容策略

- 仅使用 `1.0.0` 成员的包声明 `api_version: "1.0.0"`。
- 使用 `1.0.1` 新成员的包声明 `api_version: "1.0.1"`，并要求用户运行 Operit `1.12.1+4` 或更新版本。
- 不要用运行时探测未声明的新成员来代替版本声明；版本门禁依据 manifest，而不是包版本或市场来源。
- 如果同一份源码需要面向不同 API 版本发布，应构建分别声明兼容版本的产物，并确保每个产物只注册该版本可用的成员。
- 升级 `api_version` 前逐项检查新版本引入的参数、返回结构、Hook 阶段与副作用变化。

## 当前版本标记清单

- ToolPkg 注册 API 的 namespace 基线声明为 `1.0.0`。
- `ChatMessageMenuItemEventName`、`ChatMessageSender`、`ChatRuntimeEventName`、`ChatRuntimeSlotName`、`ChatRuntimeStateName`、消息长按菜单相关 payload/return/registration 类型及其注册方法标记为 `1.0.1`。
- `core.d.ts`、`chat.d.ts`、`compose-dsl.d.ts` 与 `results.d.ts` 也含有 `@since ToolPkg API 1.0.1` 的成员；逐项清单持续维护于[接口覆盖索引](./coverage.md)，模块页面会分别标出。

## 权威实现

- `app/src/main/java/com/ai/assistance/operit/core/tools/packTool/ToolPkgApiVersion.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/packTool/ToolPkgParser.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsEngine.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsToolPkgApiRuntime.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsToolPkgRegistration.kt`

相关页面：[ToolPkg 注册 API](../03_runtime/registry.md)、[接口覆盖索引](./coverage.md)。