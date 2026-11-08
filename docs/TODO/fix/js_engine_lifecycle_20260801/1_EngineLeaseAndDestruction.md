---
title: 引擎租约与销毁同步
status: completed
document_type: implementation-plan-step
last_reviewed: 2026-08-01
---

# 引擎租约与销毁同步 [DONE]

## 原状

`ToolPkgManager` 只根据 context key 保存一个 `JsEngine`。Compose XML 渲染和 Compose DSL 屏幕释放时会立刻从表中移除并销毁该实例。异步渲染任务正在完成初始化或任务投递时，`destroy()` 可并发置空 QuickJS，进而把 `JsEngine already destroyed` 抛出到应用错误界面。

## 修改

- 引入 `acquireToolPkgExecutionEngine()`，为会在生命周期结束时释放的 UI 与 widget 调用登记租约。
- 同一 context 的前序租约释放只减少计数；最后一个租约释放才销毁引擎。
- `JsEngine` 用短临界区串行化脚本执行准备与销毁。已销毁实例的执行请求返回脚本错误结果，不再把生命周期异常抛向 Compose。
- 增加 `ToolPkgManagerTest`，覆盖两个持有者依次释放同一 context 的场景。

## 核查

- 已检查所有 `releaseToolPkgExecutionEngine` 调用点均通过显式租约获取实例。
- 已检查常驻 main runtime 继续使用原有 `getToolPkgExecutionEngine`，不会累计 UI 租约。
- 按仓库执行准则未运行编译、构建或测试。
