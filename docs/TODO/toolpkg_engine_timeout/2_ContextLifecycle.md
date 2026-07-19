---
title: ToolPkg Context 生命周期
status: completed
document_type: implementation-plan-step
last_reviewed: 2026-07-16
---

# ToolPkg Context 生命周期 [DONE]

## 旧实现

ToolPkg 引擎仅按字符串 key 存入无上限 map。容器清理只识别 main 和 provider key，XML Compose DSL 渲染创建引擎后没有在组合销毁时释放。

## 修改

- 引擎条目显式保存 container package name，不依赖 key 格式推断所有者
- 所有创建点传入容器名，容器停用、替换或删除时清理其全部 context
- XML Compose DSL 渲染使用 `DisposableEffect` 释放 context

## 期待结果

main、provider、Compose DSL、XML render 和 widget context 均可追溯到容器。短生命周期 UI context 随宿主销毁，容器级操作不会遗留相关 QuickJS 线程。

状态：完成。释放操作同时校验引擎实例，旧宿主的延迟 dispose 不会误删同 key 的新 context。
