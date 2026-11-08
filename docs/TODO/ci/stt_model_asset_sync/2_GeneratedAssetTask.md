---
title: 步骤 2：生成 STT assets 任务
status: done
document_type: implementation-step
step: 2
depends_on:
  - 1_ModelSourceManifest.md
---

# 步骤 2：生成 STT assets 任务

[上一步：模型来源清单](./1_ModelSourceManifest.md) · [返回总计划](./index.md) · [下一步：CI 与文档迁移](./3_CiAndDocumentationMigration.md)

## 旧实现情况

- Gradle 只读取预先放入 `src/main/assets/models` 的文件
- 模型缺失时，构建只能等待开发者手动准备 Google Drive 归档

## 新实现情况

- `syncSttModelAssets` 使用受版本控制的 manifest 下载文件到 `app/build/generated/stt-model-assets`
- 下载后立即校验大小和 SHA-256，校验失败使构建失败
- 生成目录作为 main source set 的额外 assets 目录
- `preBuild` 依赖同步任务，所有 Android 打包入口使用相同输入

## 验收

- 任务输出仅包含 manifest 中定义的 assets
- 模型下载失败或内容不匹配会中止构建
- 运行时 assets 路径无需改动

## 完成记录

状态：已完成。`syncSttModelAssets` 会在 `preBuild` 和 assets merge 前准备生成目录，并按 manifest 校验内容。[DONE]
