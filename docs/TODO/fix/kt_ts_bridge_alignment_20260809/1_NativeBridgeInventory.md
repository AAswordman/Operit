---
title: Kotlin 桥接接口清点
status: complete
---

# Kotlin 桥接接口清点

## 旧实现

`JsEngine` 通过 `@JavascriptInterface` 向脚本运行时暴露内部桥接方法。它们由 ToolPkg 和运行时包装，不属于 `NativeInterface` 的公开脚本接口。

`JsTools` 向 `Tools.Net` 注入了 `browserTakeScreenshot`，但类型声明没有对应方法。

## 修改意图

公开 `NativeInterface` 类型只描述脚本开发者可直接使用的桥接方法。移除没有 Kotlin 实现的 `setResult` 和 `setError`，不把内部桥接方法复制到 `core.d.ts`。

## 预期结果

静态比较应确认 `core.d.ts` 中每个 `NativeInterface` 公共方法均有对应 Kotlin 实现，并且不含已移除名称。

## 完成记录

`NativeInterface` 已移除 `setResult` 与 `setError`。其余公共声明均映射到 Kotlin 的 `@JavascriptInterface` 实现；ToolPkg 与运行时内部桥接未进入公共声明。
