---
title: ToolPkg 引擎超时与生命周期修复
status: completed
document_type: implementation-plan-index
repository: https://github.com/AAswordman/Operit.git
last_reviewed: 2026-07-16
---

# ToolPkg 引擎超时与生命周期修复

普通 JavaScript 插件包在扫描 `.toolpkg` 时共用注册引擎。同步脚本占满 QuickJS 线程后，现有超时取消只能排到同一线程等待，无法终止正在运行的脚本。ToolPkg UI context 同时存在所有权不明确和 XML 渲染未释放的问题，导致引擎及其线程持续累积。

本计划仅处理普通插件包和 `.toolpkg`，不涉及 MCP。

## 目标

- 每个 `.toolpkg` 在独立的临时注册引擎中解析，失败不会污染后续包
- 超时和销毁能够直接通知 native QuickJS 中断同步执行
- ToolPkg context 记录所属容器，容器停用或删除时清理全部相关引擎
- XML Compose DSL 渲染离开组合时释放对应引擎

## 步骤

1. [注册隔离与强制中断 [DONE]](./1_RegistrationIsolationAndInterrupt.md)：修复同线程取消和共享注册引擎
2. [Context 生命周期 [DONE]](./2_ContextLifecycle.md)：补齐容器所有权和 XML 渲染释放
3. [验证与记录 [DONE]](./3_Verification.md)：增加定向回归测试并完成静态核对

## 作用域

代码修改限于 QuickJS 执行封装、普通插件包加载器、ToolPkg 引擎管理与 XML 插件渲染。现有插件脚本格式、注册函数和工具调用接口保持不变。

## 完成记录

普通插件包注册已改为每包临时引擎；超时和关闭会直接触发 native interrupt；运行 context 具备容器与实例所有权；XML 渲染已补齐释放。已通过调用面检索和 `git diff --check`，按仓库执行准则未运行编译、构建或测试。
