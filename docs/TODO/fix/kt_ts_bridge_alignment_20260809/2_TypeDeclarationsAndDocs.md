---
title: TypeScript 声明与开发文档
status: complete
---

# TypeScript 声明与开发文档

## 旧实现

网络浏览器截图接口不能获得 TypeScript 提示，Compose DSL 缺少嵌入聊天与自适应侧栏节点。`core.d.ts` 还保留了两个无运行时实现的旧接口。

## 修改意图

更新 `examples/types/core.d.ts`、`examples/types/network.d.ts` 与 `examples/types/compose-dsl.d.ts`，实现 Compose DSL 渲染节点，并同步开发文档。

## 预期结果

脚本作者能够从 `examples/types/index.d.ts` 获得浏览器截图、嵌入聊天和自适应侧栏的类型提示，不会看到没有 Kotlin 实现的旧桥接方法。

## 完成记录

已添加 `browserTakeScreenshot`、`AiChat` 与 `AdaptiveSidePanel` 的公开类型和开发文档。`AdaptiveSidePanel` 的 Kotlin 实现支持宽屏拖拽调宽与窄屏遮罩关闭。
