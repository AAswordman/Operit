---
title: MNN schema 生成与 Android 构建门禁修复
For_Agent: 记录 PR #926 的原生构建修复过程
---

# MNN schema 生成与 Android 构建门禁修复

## 现状

PR #926 的 JVM 单测已经通过，但完整 Android 门禁在 `:mnn:buildCMakeDebug[arm64-v8a]` 失败。云端拉取的 MNN 提交更新了 `schema/default/MNN.fbs` 和 `source/core/OpCommonUtils.cpp`，却携带了未同步的 `schema/current/MNN_generated.h`，因此缺少 `FusedLinearParam` 相关声明。

## 意图与范围

- 在 `llm/mnn/CMakeLists.txt` 加入真实的 schema 生成步骤
- 使用 fetched MNN 自带的 FlatBuffers 源码构建宿主机 `flatc`
- 在 `add_subdirectory(MNN)` 前重新生成 `schema/current/*.h`
- 在 Android 构建指南记录宿主机编译器要求和故障排查入口
- 保留现有 Git 连线及普通 merge 流程，不改动插件市场业务接口

## 预期结果

MNN 源码和 generated headers 来自同一个 fetched revision，干净 CI 工作区能够编译 `:mnn`，并且 `Candidate checks` 全部通过后再合并 PR #926。

## 关联

- 分支：`fix/jvm-json-test-runtime`
- PR：[#926](https://github.com/AAswordman/Operit/pull/926)
- 保留：`origin/fix/withdrawn-entry-republish`
