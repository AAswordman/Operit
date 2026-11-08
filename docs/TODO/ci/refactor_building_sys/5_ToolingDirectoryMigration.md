---
title: 步骤 5：工具目录迁移
status: draft
document_type: implementation-step
For_Agent: 未经用户明确授权，不执行编译、构建、测试或发布命令
step: 5
depends_on:
  - 4_GradleInputNormalization.md
fork_repository: "git@github.com:Nyashiiro/Operit-follow-up.git"
last_reviewed: 2026-07-14
---

# 步骤 5：工具目录迁移

[上一步：Gradle 输入规范化](./4_GradleInputNormalization.md) · [返回总计划](./index.md) · [下一步：pixi 与资产准备入口](./6_PixiAndAssetPreparation.md)

## 旧实现情况

- `tools/` 同时存放开发脚本、调试入口、构建脚本、独立工程、宿主辅助工具和应用内置工具
- Python 脚本分散在 hotbuild、Compose DSL、GitHub、memory、string 与目录根部
- desktop、Shower、FFmpeg、native ripgrep 和 shell identity launcher 等辅助工程与脚本混放
- MCP bridge 等会生成 APK 内置 assets 的工具仍位于通用 `tools/`
- 文档和脚本通过旧路径直接引用这些内容，目录职责无法由路径判断

## 预期的新实现情况

- 可执行工作流脚本进入 `ci/script/<意图>/`
- 仓库自有 Python 入口各自位于有名称的 `ci/script/` 子目录
- CI 与开发流程使用的宿主辅助工程、二进制和工具进入 `ci/tools/<名称>/`
- 随应用交付或专门生成应用内置内容的工具进入 `tools_built-in/<名称>/`
- `tools/` 不再承担多种相互冲突的目录职责
- 每个迁移项都有旧路径、新路径、调用方和产物去向记录

## 修改作用域

计划修改：

- `tools/`
- `ci/script/`
- `ci/tools/`
- `tools_built-in/`
- `settings.gradle.kts` 与辅助工程设置文件
- package manifest、构建脚本和路径调用方
- 与工具路径直接相关的开发文档

本步骤不修改：

- 工具的业务算法与输入输出协议
- APK 用户可见能力与数据格式
- Android feature module 和变体设计

## 计划

1. 为 `tools/` 每个一级条目建立消费者清单，记录调用命令、生成物、运行环境和是否随 APK 交付。
2. 根据运行职责分类，不按扩展名或目录名批量移动。
3. 将 Python 工作流入口移动到有名称的 `ci/script/` 子目录，并将同一工作流的 shell、batch、配置和说明放在相同目录。
4. 将宿主辅助工程与工具移动到 `ci/tools/`，同步更新 Gradle settings、脚本和文档路径。
5. 将 MCP bridge 等应用内置工具移动到 `tools_built-in/`，同步更新 pnpm workspace 和 assets 生产路径。
6. 每次只迁移一个功能组，并在同一次修改中更新全部调用方。
7. 比较迁移前后的输入、输出文件名和内容清单，确保目录变化不改变工具行为。
8. 所有条目完成分类后删除空的旧目录与旧路径说明。

## 验收

- `ci/script/` 只保存可直接调用的命名工作流
- `ci/tools/` 只保存这些工作流和开发过程使用的宿主工具
- `tools_built-in/` 只保存随应用交付或生产应用内置内容的工具
- 旧路径没有活动调用方
- 每个迁移项的输入、输出和行为与迁移前一致
- 迁移记录没有未确认的路径归类

## 完成记录

状态：未开始。完成前需要记录工具构建、脚本执行和产物比较结果。
