---
title: 步骤 1：STT 模型来源清单
status: done
document_type: implementation-step
step: 1
---

# 步骤 1：STT 模型来源清单

[返回总计划](./index.md) · [下一步：生成 assets 任务](./2_GeneratedAssetTask.md)

## 旧实现情况

- `models.zip` 提供默认 Sherpa NCNN 模型和 Silero VAD 模型
- 构建输入没有逐文件版本和完整性记录

## 新实现情况

- `app/config/stt-model-assets.properties` 记录八个输入文件
- Sherpa NCNN 文件来自公开的 `csukuangfj/sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13` commit `05945efc40afe4b572542f01104ca5c413a9f6e1`
- Silero VAD 使用与现有 assets 完全相同的公开 Hugging Face 文件
- 每个条目记录目标路径、大小和 SHA-256

## 验收

- 现有 assets 清单与 manifest 的文件大小和 SHA-256 一致
- revision 和来源路径可直接构造不可变下载 URL

## 完成记录

状态：已完成。`app/config/stt-model-assets.properties` 记录默认 STT 的八个 assets、固定来源、大小和 SHA-256。[DONE]
