---
title: 固定 MNN 稳定依赖
For_Agent: 记录 MNN 原生依赖固定与 PR 合并过程
fork_repository: "git@github.com:Nyashiiro/Operit-follow-up.git"
---

# 固定 MNN 稳定依赖

## 旧实现

- `llm/mnn/CMakeLists.txt` 使用 MNN `master`
- CMake 配置阶段按远端分支当前指向解析提交，导致不同时间的构建输入不同
- 2026-08-13 解析到的提交 `d68305cf2476a7dc319643ba7c62f44e2bc5246b` 在 `:mnn:buildCMakeDebug[arm64-v8a]` 链接阶段缺少 `qwenVideoProcess`

## 修改意图

- 使用官方最新稳定版 MNN `3.6.1`
- 使用发布标签背后的不可变提交 `d407447ed56c4121a11ccbd266dc184ca1ead0c2`
- 将已存在的 `master` 和已知故障提交缓存迁移到稳定提交，避免 Android Studio 复用旧解析结果
- 强制同步 `OPERIT_MNN_GIT_REF` 缓存，避免旧的 `master` 配置继续生效；后续升级只通过审查后的源码提交完成

## 作用域

- `llm/mnn/CMakeLists.txt`
- `ci/README.md`
- `docs/TODO/native_dependency_fetchcontent/index.md`

## 验收

- 默认 CMake 输入不再包含 `master`
- MNN 版本头为 `3.6.1`
- 稳定提交记录在 CI 依赖说明和 TODO 中
- 未执行编译、构建或测试命令

[DONE]
