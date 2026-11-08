---
title: STT 模型构建期自动准备
status: done
document_type: implementation-plan
fork_repository: "https://github.com/AAswordman/Operit"
last_reviewed: 2026-07-25
---

# STT 模型构建期自动准备

[模型来源清单](./1_ModelSourceManifest.md) · [生成 assets 任务](./2_GeneratedAssetTask.md) · [CI 与文档迁移](./3_CiAndDocumentationMigration.md)

## 目标

默认 Sherpa NCNN 本地语音识别模型在 Android 构建期自动从 Hugging Face 获取，并以既有的 `models/` assets 路径进入 APK。

## 旧实现情况

- 完整 Android 构建从 Google Drive 下载 `models.zip`
- 模型文件被解压到 `app/src/main/assets/models`
- `SherpaSpeechProvider` 从该 assets 目录复制模型，再通过文件路径创建识别器
- 模型来源、revision 和文件 hash 没有受版本控制的声明

## 预期的新实现情况

- 模型清单固定 Hugging Face 仓库、commit、文件路径、大小和 SHA-256
- Gradle 在 `preBuild` 前准备受管理的生成 assets 目录
- 当前 `models/<模型目录>` 资源路径和运行时加载行为保持不变
- Google Drive 仅保留非模型的 `libs.zip`、`subpack.zip` 和 `jniLibs.zip`

## 修改范围

- `app/config/`
- `app/build.gradle.kts`
- Android 依赖下载与解包脚本
- Android 构建与贡献者文档

## 验收

- 干净工作树执行 Android 打包时不需要 `models.zip`
- 生成 assets 中的八个 STT 文件均通过声明的 SHA-256 与大小校验
- APK 内模型路径仍为 `models/sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13/...`
- CI 不再请求 Google Drive 的 `models.zip`

## 完成记录

状态：已完成。未在本轮执行 Gradle 构建或测试，按仓库执行准则保留给显式验证请求。[DONE]
