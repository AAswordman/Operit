---
title: 步骤 7：Android 能力契约
status: draft
document_type: implementation-step
For_Agent: 未经用户明确授权，不执行编译、构建、测试或发布命令
step: 7
depends_on:
  - 6_PixiAndAssetPreparation.md
fork_repository: "git@github.com:Nyashiiro/Operit-follow-up.git"
last_reviewed: 2026-07-14
---

# 步骤 7：Android 能力契约

[上一步：pixi 与资产准备入口](./6_PixiAndAssetPreparation.md) · [返回总计划](./index.md) · [下一步：Feature 模块隔离](./8_FeatureModuleIsolation.md)

## 旧实现情况

- `app/src/main` 直接 import FFmpegKit 类型
- 语音实现直接管理 Sherpa 模型路径与 native 初始化
- 聊天和工具箱 UI 直接调用 Android 与 Windows 导出实现
- 基础源码直接读取 `models/` 和 `subpack/` assets
- 功能入口、工具注册、路由和具体 SDK 类型没有稳定边界

## 预期的新实现情况

- 媒体、语音和导出分别具有稳定的 capability contract
- `:app` 中的调用方只依赖契约与状态模型
- 具体 SDK、assets 路径和 native 加载只出现在对应实现边界
- 功能注册表显式声明当前构建包含的实现
- 用户可见操作、数据格式和公开调用契约与旧实现一致

## 修改作用域

计划修改：

- `app/src/main/java/com/ai/assistance/operit/core/`
- `app/src/main/java/com/ai/assistance/operit/ui/`
- `app/src/main/java/com/ai/assistance/operit/util/FFmpegUtil.kt`
- 语音服务、设置、导出流程与工具注册相关源码
- 与能力边界对应的单元测试源码

本步骤不修改：

- Gradle module 列表
- models、subpack 和本地二进制的物理目录
- product flavor、applicationId 和签名

## 计划

1. 定义媒体执行、语音识别与导出能力的输入、输出、状态和错误契约。
2. 建立显式 capability registry，并由当前 `:app` 组合全部既有实现。
3. 将 FFmpegKit import 和执行代码收束到媒体实现包。
4. 将 Sherpa 模型路径、native 初始化、服务与设置入口收束到语音实现包。
5. 将 subpack 访问、导出 UI 与 Android、Windows 导出实现收束到导出实现包。
6. 改造聊天、工具箱、导航和工具注册，使其只依赖契约。
7. 添加说明修改意图的调试注释，记录直接依赖会破坏 lite 编译边界的原因。

## 验收

- 基础调用方不再 import FFmpegKit 或 Sherpa 实现类型
- 基础调用方不再包含 models、subpack 路径和可选 native 加载
- capability registry 与用户可见入口一致
- 旧接口、数据和功能行为保持不变
- 异常路径有明确日志，不隐藏错误

## 完成记录

状态：未开始。完成前需要记录编译与测试结果。
