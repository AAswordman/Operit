---
title: Kotlin 与 TypeScript 桥接接口对齐
fork: https://github.com/tuxKOH/Operit
status: complete
---

# Kotlin 与 TypeScript 桥接接口对齐

## 当前状况

`examples/types/network.d.ts` 缺少 Kotlin 已注入的 `Tools.Net.browserTakeScreenshot`。

`examples/types/core.d.ts` 保留了没有 Kotlin 实现的 `setResult` 与 `setError` 声明。`JsEngine` 的其他未声明 `@JavascriptInterface` 方法是 ToolPkg 或运行时内部桥接，不应加入公共 `NativeInterface` 类型。

Compose DSL 也缺少已确认需要公开的 `AiChat` 和 `AdaptiveSidePanel` 节点。

## 预期结果

- `Tools.Net.browserTakeScreenshot` 在运行时和 TypeScript 中具有相同的参数与返回值
- `NativeInterface` 只保留当前公开且由 Kotlin 实现的方法
- 移除没有 Kotlin 实现的 `setResult` 与 `setError` 类型声明
- `AiChat` 与 `AdaptiveSidePanel` 在 Compose DSL 的 TypeScript 声明和 Kotlin 渲染器中同时可用
- 面向脚本开发者的文档按实际公开接口更新

## 步骤

1. [DONE: 公开接口清点](./1_NativeBridgeInventory.md)
2. [DONE: TypeScript、Compose DSL 与开发文档](./2_TypeDeclarationsAndDocs.md)
3. [DONE: 静态双向核对](./3_StaticVerification.md)

## 执行约束

本任务不运行编译、构建或测试命令。验证采用公开接口映射、Compose DSL 节点集合比较和 Git diff 审查。

## 完成记录

- 移除了没有 Kotlin 实现的 `NativeInterface.setResult` 与 `NativeInterface.setError`
- 补齐 `Tools.Net.browserTakeScreenshot`、`AiChat` 与 `AdaptiveSidePanel`
- 核对通过：公开 `NativeInterface` 声明均有 Kotlin 实现，Compose DSL 类型和渲染节点集合一致
