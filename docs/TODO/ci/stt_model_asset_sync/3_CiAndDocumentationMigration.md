---
title: 步骤 3：CI 与文档迁移
status: done
document_type: implementation-step
step: 3
depends_on:
  - 2_GeneratedAssetTask.md
---

# 步骤 3：CI 与文档迁移

[上一步：生成 assets 任务](./2_GeneratedAssetTask.md) · [返回总计划](./index.md)

## 旧实现情况

- CI 的完整 Android profile 下载和解包 `models.zip`
- README、编译指南和贡献指南要求开发者手动下载四个 Google Drive 归档

## 新实现情况

- 完整 Android profile 仅下载三个非模型归档
- CI 的 Android 构建通过 Gradle 自动准备 STT assets
- 文档说明首次构建需要访问固定的 Hugging Face 模型来源，但不要求手动处理模型归档

## 验收

- 依赖脚本和 Python 解包器不再声明 `models.zip`
- 中英文 README、编译指南、贡献指南和 CI 文档保持一致

## 完成记录

状态：已完成。CI 仅处理三个 Drive 归档，并按 manifest 哈希缓存生成 STT assets。[DONE]
