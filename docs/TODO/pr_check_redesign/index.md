---
title: Pull Request 技术预审重构
repo: https://github.com/luojiaping/Operit
upstream: https://github.com/AAswordman/Operit
status: completed
---

# Pull Request 技术预审重构

## 原本状况

现有 PR workflow 同时使用目标分支 tip、贡献者 head 和 GitHub merge ref，却没有为三者定义不同职责。过时 fork 会把上游新增内容算入贡献者差异，实际构建又只检出 head，导致路径误判、旧问题错误归责和未验证最终候选树。

门禁还将技术检查、PR 模板策略、文档提示和完整 Android 构建拆成多个阻断状态。一次改动可能得到多个重复红灯，而任意 `skipped` 又会被聚合器视为成功。

## 修改意图

- 所有技术检查只运行在 GitHub 生成的 merge candidate 上
- 使用 candidate 第一父提交到 candidate 的净差异分类和归责
- 每个 PR 只产生一个技术检查状态
- PR 标题、正文、Issue 和 checklist 不参与自动阻断
- 按资源、JVM 和完整构建三个层级运行 Android 检查
- fork PR 不接收 secret，也不上传 APK 或 AAB

## 作用域

- `.github/workflows/pr-check.yml`
- `.github/workflows/android-build.yml`
- `gradle/wrapper/gradle-wrapper.properties`
- `ci/script/` 与 `ci/test/`
- `examples/toolpkg_wasm_demo/package-lock.json`
- PR 模板、CI 文档和贡献指南

Issue 自动整理、发布签名、业务代码和仓库 ruleset 不在本次修改范围内。

## 期待结果

类似 PR #770 的翻译改动只看到一个技术状态。候选摘要只报告该 PR 在当前 `main` 上实际引入的问题，不再报告其他语言的历史债务、上游文档变化或 PR 模板格式错误。

## 步骤

1. [候选树与作用域契约](./1_CandidateAndScopes.md)
2. [差异归责与检查器](./2_AttributionAndChecks.md)
3. [工作流与构建分层](./3_WorkflowAndBuildLanes.md)

## 完成情况

- 旧 `PR Required` workflow、模板策略 job 和聚合器已删除
- 新 workflow 只生成一个 `Candidate checks` 技术状态
- 快速检查和 Android 分层均使用 candidate 第一父差异
- PR 不读取 secret，也不上传 APK/AAB
- 已添加门禁、归责、Markdown 和 ZIP 安全测试
- Gradle 8.13 distribution 使用官方 SHA-256 校验
- WASM ToolPkg 使用独立锁文件执行真实编译与打包
- 官方 Actions 已固定到 Node.js 24 运行时版本
- ToolPkg 运行时文件、GitHub 示例生成结果和可信构建输入均显式校验
- 首次线上完整 lane 已通过 assemble 和 JVM 单测，并补齐 Android lint 检出的四个 WASM 模块数翻译
- 按仓库执行约束，本次未运行测试或构建命令

[DONE]
