---
title: 步骤 8：Feature 模块隔离
status: draft
document_type: implementation-step
For_Agent: 未经用户明确授权，不执行编译、构建、测试或发布命令
step: 8
depends_on:
  - 7_AndroidCapabilityContracts.md
fork_repository: "git@github.com:Nyashiiro/Operit-follow-up.git"
last_reviewed: 2026-07-14
---

# 步骤 8：Feature 模块隔离

[上一步：Android 能力契约](./7_AndroidCapabilityContracts.md) · [返回总计划](./index.md) · [下一步：变体、签名与版本身份](./9_VariantsSigningAndVersioning.md)

## 旧实现情况

- `:app` 同时拥有应用壳、能力实现、assets 和 Sherpa native 构建
- FFmpeg、models、subpack 与功能代码没有独立 Gradle 所有者
- 所有能力依赖都进入同一个 application module
- 现有模块图无法生成不含可选能力的 APK
- feature 之间缺少可由 Gradle 强制执行的依赖方向

## 预期的新实现情况

- `:app` 保持为唯一 application 壳
- `:core:capability-api` 只保存稳定契约和注册模型
- `:feature:media` 拥有 FFmpeg 实现、依赖、资源和许可证
- `:feature:speech` 拥有语音实现、models、Sherpa native 构建和许可证
- `:feature:export` 拥有导出实现、subpack、资源和许可证
- feature 只依赖 capability API，feature 之间没有直接依赖

## 修改作用域

计划修改：

- `settings.gradle.kts`
- `core/capability-api/`
- `feature/media/`
- `feature/speech/`
- `feature/export/`
- `app/build.gradle.kts`
- `app/src/main/`

本步骤不修改：

- prod 与 QA 安装身份
- full 与 lite product flavor
- APK 发布和补丁脚本

## 计划

1. 创建 capability API 与三个普通 Android library module。
2. 将步骤 6 的契约移动到 capability API，并保持调用接口不变。
3. 将媒体实现、FFmpeg 二进制和相关资源移动到 media module。
4. 将语音实现、models、CMake 配置和 Sherpa native 源码边界移动到 speech module。
5. 将导出实现、subpack 和相关 UI 资源移动到 export module。
6. 将 consumer rules、Manifest 项、依赖和许可证说明移动到各自所有者。
7. 在本步骤结束时让 `:app` 继续组合全部三个 feature，保持原有能力完整。
8. 静态检查模块依赖方向和重复资源所有者。

## 验收

- 每个 feature 能独立解析自己的依赖与资源
- `:app` 主源集不拥有可选制品
- feature 之间不存在直接依赖或循环依赖
- 每个 asset、AAR、JAR 和 native 目标只有一个 module 所有者
- 全量组合的用户能力与步骤 6 前一致

## 完成记录

状态：未开始。完成前需要记录模块解析、编译和资源检查结果。
