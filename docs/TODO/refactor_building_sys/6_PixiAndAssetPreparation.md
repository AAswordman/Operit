---
title: 步骤 6：pixi 与资产准备入口
status: draft
document_type: implementation-step
For_Agent: 未经用户明确授权，不执行编译、构建、测试或发布命令
step: 6
depends_on:
  - 5_ToolingDirectoryMigration.md
fork_repository: "git@github.com:Nyashiiro/Operit-follow-up.git"
last_reviewed: 2026-07-14
---

# 步骤 6：pixi 与资产准备入口

[上一步：工具目录迁移](./5_ToolingDirectoryMigration.md) · [返回总计划](./index.md) · [下一步：Android 能力契约](./7_AndroidCapabilityContracts.md)

## 旧实现情况

- 仓库没有 `pixi.toml`
- `ci/README.md` 只有 `ci/script/` 的简短目录说明
- web-chat、toolpkg 和 MCP bridge 没有统一的 assets 生产入口
- JS assets 没有路径、大小、SHA-256 和生产任务清单
- 开发文档要求全局安装工具并修改持久化环境配置

## 预期的新实现情况

- pixi 固定 Node.js、pnpm、JDK、Python 和可稳定管理的宿主工具
- `ci/script/` 提供环境报告、冻结安装、JS assets 生产和制品校验入口
- Android 构建启动前必须通过 JS assets 清单与外部制品清单校验
- 脚本不修改 shell profile、注册表或系统级环境变量
- 外部 CI 只调用与本机相同的 pixi task
- 每个 pixi task 保持单一职责，并提供稳定命令、退出码和结构化报告

## 修改作用域

计划修改：

- `pixi.toml` 及对应锁定文件
- `ci/script/`
- `ci/README.md`
- `package.json`
- `web-chat/scripts/`
- `tools/sync_example_packages.py`
- `tools_built-in/mcp_bridge/`

本步骤不修改：

- Android feature module
- Gradle 变体身份
- APK 发布与补丁逻辑

## 计划

1. 由 pixi 提供步骤 1 已确认的宿主工具版本。
2. 实现 `env-report`、`js-install`、`js-webchat`、`js-toolpkg`、`js-bridge` 和 `artifact-verify` 入口。
3. 建立 `js-assets.json`，记录生产任务、相对路径、大小和 SHA-256。
4. 增加 `js-assets-check`，报告生成结果与 Git 工作树、assets 清单的差异。
5. 为 Android 编排定义前置顺序，先校验 JS assets 与外部制品，再调用 Gradle。
6. 为每个脚本提供 `--help`、明确参数、输出和退出条件。
7. 为后续流水线契约保留稳定 task 名称，不把多个独立阶段隐藏在一个不透明命令中。

## 验收

- 本机和外部 CI 使用同一 pixi task
- JS assets 都能追溯到源码、lockfile 和生产任务
- Gradle 文件不安装 Node 或 Python 依赖，也不直接调用 npm、pnpm 或制品下载
- 脚本结束后不会改变宿主机持久化配置
- 输入或 hash 不一致时任务明确终止

## 完成记录

状态：未开始。完成前需要记录依赖安装与 assets 构建结果。
