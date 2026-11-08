---
title: PR Check Android 构建与单测拆分
repo: https://github.com/luojiaping/Operit
upstream: https://github.com/AAswordman/Operit
status: verified
---

# PR Check Android 构建与单测拆分

## 原本状况

PR Check 的完整 Android lane 在同一个 job 中执行 `assembleDebug` 和
`:app:testDebugUnitTest`。Android Build workflow 也在编译后继续执行 JVM 单测，
导致单测问题和构建问题共享失败边界、runner 时间和产物上传条件。

## 修改意图

- Android build job 只负责准备打包输入、执行编译和上传 Android 产物
- Android JVM test job 独立执行单测及必要的 AndroidTest 编译
- PR 的构建和单测结果分别可见，单测失败不阻断 APK 编译
- 保留现有 `PR Check / Candidate checks` 聚合状态名称
- 保留 merge candidate 校验、权限限制和 fork 安全边界

## 作用域

- `.github/workflows/pr-check.yml`
- `.github/workflows/android-build.yml`
- `.github/workflows/android-tests.yml`
- `ci/script/pr_check.py` 与相关测试
- `ci/README.md`、开发者 CI 文档和本 TODO 目录

## 步骤

1. [Job 边界与状态契约](./1_JobBoundaries.md)
2. [Workflow 实现](./2_WorkflowImplementation.md)
3. [验证与 fork 测试](./3_Verification.md)

## 当前状态

- [x] 完成 PR workflow 的 build/test job 拆分
- [x] 完成可信 Android workflow 的纯构建化
- [x] 补齐独立测试 workflow 和文档
- [x] 通过 YAML、actionlint 和 CI 门禁单元测试
- [x] 在 fork 的 PR/Actions 上验证 build/test job 独立执行
- [x] 修复 fork 验证暴露的 Android JVM 测试源码与测试夹具错误
- [x] 完成 cancellation 断言修正后的 fork Actions 验证
