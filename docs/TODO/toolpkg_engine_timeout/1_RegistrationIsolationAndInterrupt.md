---
title: ToolPkg 注册隔离与强制中断
status: completed
document_type: implementation-plan-step
last_reviewed: 2026-07-16
---

# ToolPkg 注册隔离与强制中断 [DONE]

## 旧实现

所有 `.toolpkg` 的 `registerToolPkg()` 共用 `PackageManager` 的 JavaScript 引擎。等待超时后，取消代码通过被占用的 QuickJS executor 排队；同步死循环不会让出线程，后续包注册和引擎销毁因而继续阻塞。

## 修改

- 在 Java 等待超时或线程被中断时，直接调用 QuickJS native interrupt
- 销毁引擎前先触发 interrupt，销毁期间不再向已关闭调度器追加 JavaScript 取消任务
- 外部与内置 `.toolpkg` 每次加载均创建临时注册引擎，并在解析结束后确定性销毁

## 期待结果

坏包在时限到达后退出注册，错误归属当前包，扫描可以继续处理后续包。注册引擎不保存跨包状态，也不在扫描结束后保留线程。

状态：完成。
