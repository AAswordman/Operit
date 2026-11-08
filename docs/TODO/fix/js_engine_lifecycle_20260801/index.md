---
title: QuickJS 执行上下文生命周期
status: completed
document_type: implementation-plan-index
repository: https://github.com/AAswordman/Operit.git
last_reviewed: 2026-08-01
---

# QuickJS 执行上下文生命周期

Compose XML 渲染在重组或离开组合时会释放 ToolPkg QuickJS context。已有实现按 context key 立即销毁引擎，多个界面持有同一 context 时，其中一个界面离开即可使另一个界面继续使用已销毁实例。

## 目标

- 有释放义务的 UI 与 widget 调用获得显式引擎租约
- 每个 context 的最后一个租约释放后才关闭 QuickJS
- 销毁与脚本执行初始化、调用登记和任务投递不会交错
- ToolPkg 脚本接口保持不变

## 步骤

1. [引擎租约与销毁同步 [DONE]](./1_EngineLeaseAndDestruction.md)

## 作用域

- `ToolPkgManager`
- `PackageManager`
- Compose DSL 与 XML 渲染调用点
- `JsEngine`
- 定向单元测试
