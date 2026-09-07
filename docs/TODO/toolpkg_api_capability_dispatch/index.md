---
title: ToolPkg API 本地声明与版本变体
status: in-progress
---

# ToolPkg API 本地声明与版本变体

## 原本状况

ToolPkg API 版本判断散在 `Tools.Chat.call`、ToolPkg 注册桥和运行时参数传递里。上一版集中 catalog 又把 API 名称、tool 名称、registration 名称塞进同一张表，维护成本仍然高。

## 修改意图

Kotlin 只校验 manifest 的 `api_version` 是否受当前宿主支持，并把该值放入执行上下文。具体公开 API 在自己的 JS facade 属性旁通过 `ToolPkgApi.namespace("Tools.Chat", { call: ToolPkgApi.method().since("1.0.1", implementation) })` 声明。runtime 从属性名形成公开 API 名称并在调用时选择实现；新增接口只改接口附近代码，同名 API 的新版本实现只需继续追加 `.since(version, implementation)`。

## 作用域

- 精简 `ToolPkgApiCompatibility` 为版本解析和支持版本判断
- `manifest.requires` 作为包依赖和加载顺序元数据解析
- `ToolPkg.registerChatMessageMenuItem` / `registerChatRuntimeHook` 在注册桥本地声明能力限制
- `Tools.Chat.call` 在 `JsTools.kt` 的 JS facade 中本地声明版本变体
- 移除 tool/registration 到公开 API 的集中映射表

## 分步记录

- [ ] 1_capability_registry.md
- [ ] 2_shared_js_runtime.md
- [ ] 3_native_dispatch.md
- [ ] 4_tests_and_docs.md
