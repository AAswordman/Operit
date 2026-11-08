---
title: 静态双向核对
status: complete
---

# 静态双向核对

## 核对范围

- 检查 `core.d.ts` 的 `NativeInterface` 不包含没有 Kotlin 实现的旧成员
- 比较 `JsTools.kt` 注入的浏览器截图方法与 `network.d.ts` 的 `Net` 成员
- 比较 `compose-dsl.d.ts` 的新增节点与 Kotlin Compose DSL 节点分发
- 审查文档和类型文件的差异

## 完成标准

`browserTakeScreenshot` 的参数字段与 Kotlin 参数归一化逻辑一致，`AiChat` 与 `AdaptiveSidePanel` 均有 TS 类型和 Kotlin 渲染路径，并且过时名称不再出现在公开类型声明或开发者文档中。

## 完成记录

- `core.d.ts` 的每个 `NativeInterface` 成员均能在 Kotlin `@JavascriptInterface` 中找到实现
- `setResult` 与 `setError` 未出现在公开类型或开发文档
- `browserTakeScreenshot` 的四个参数与 Kotlin 归一化逻辑一致
- Compose DSL 类型工厂与 Kotlin 渲染节点各 88 个，双向集合无差异
- `git diff --check` 未发现空白错误
