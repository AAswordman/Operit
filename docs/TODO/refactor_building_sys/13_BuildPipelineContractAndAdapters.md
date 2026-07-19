---
title: 步骤 13：构建流水线契约与平台适配
status: draft
document_type: implementation-step
For_Agent: 未经用户明确授权，不执行编译、构建、测试或发布命令
step: 13
depends_on:
  - 12_QaAdbOfflineSimulation.md
fork_repository: "git@github.com:Nyashiiro/Operit-follow-up.git"
last_reviewed: 2026-07-14
---

# 步骤 13：构建流水线契约与平台适配

[上一步：QA ADB 离线模拟](./12_QaAdbOfflineSimulation.md) · [返回总计划](./index.md) · [下一步：文档同步与旧路径清理](./14_DocumentationAndCleanup.md)

## 旧实现情况

- `ci/README.md` 已记录当前 PR 技术预审和 Android 构建入口
- 仓库已有 GitHub Actions workflow，但仍没有平台无关的完整流水线定义
- pixi 与 `ci/script/` 的计划只列出任务名，没有统一的阶段 ID 和依赖关系
- 输入、输出、缓存、权限、secret、网络访问、设备要求和 QA 场景没有结构化描述
- 可视化 CI 或 GitHub Actions 接入时需要重新解释脚本顺序和产物交接

## 预期的新实现情况

- `ci/pipeline/` 保存平台无关的阶段清单与构建 profile
- `config/build-system/schema/` 保存唯一的流水线 schema
- 每个阶段具有稳定 ID、所属段落、显示名称、单一职责、明确依赖和一个 pixi 命令入口
- 阶段清单声明输入、输出、产物、缓存、权限、secret、网络、设备和成功报告
- 可视化 CI 直接读取阶段清单生成任务视图
- GitHub Actions job 只负责准备 runner、调用阶段命令和上传声明产物
- 平台适配层不复制 Gradle、pnpm、测试、审计或发布逻辑
- 流水线 profile 保留完整阶段，不读取 `For_Agent` 元数据决定是否执行编译、构建或测试
- `qa_verify` 显式包含 market、patch 和 announcement 三个离线场景 ID
- pipeline 静态检查包含 QA debug command、release support command 与 APK 外部入口审计

## 修改作用域

计划修改：

- `ci/pipeline/`
- `ci/script/pipeline/`
- `config/build-system/schema/`
- `pixi.toml`
- `ci/README.md`
- `.github/workflows/`
- 构建清单与阶段报告 schema

本步骤不修改：

- 各阶段内部的业务实现
- Android 功能、模块和变体语义
- 外部制品与补丁格式

## 计划

1. 在 `config/build-system/schema/` 定义阶段 schema，字段包括 `id`、`section`、`display_name`、`description`、`needs`、`command`、`workdir`、`inputs`、`outputs`、`artifacts`、`caches`、`permissions`、`secrets`、`network`、`device`、`timeout` 和 `report`。
2. 为 source validate、environment prepare、dependency prepare、asset prepare、artifact verify、static verify、test execute、Android assemble、package audit、QA verify、patch prepare 和 publish 建立稳定阶段 ID。
3. 定义 `verify`、`build`、`qa` 和 `release` profile。每个 profile 显式列出阶段与批准变体，不在运行时推断路径。
4. 让每个阶段调用一个 pixi task，并输出结构化阶段报告和明确退出码。
5. 定义阶段间产物名称与 hash，后续阶段只消费清单声明的上游产物。
6. 为 secret、网络、设备与发布权限设置最小权限，阶段日志不输出 secret 值。
7. 实现本机流水线入口，按 profile 读取同一阶段清单。
8. 为未来可视化 CI 定义读取接口，使其能够展示阶段、依赖、状态、日志和产物。
9. 添加精简的 GitHub Actions 适配，每个 job 映射一个阶段 ID，不包含业务构建命令。
10. 增加静态一致性检查，确保平台适配引用的阶段 ID、依赖和产物与清单一致。
11. 验证 `verify`、`build`、`qa` 与 `release` profile 覆盖各自需要的完整阶段，不受 Agent 会话权限影响。
12. 让 `qa_verify` 分别输出 market、patch 和 announcement 场景状态，避免用一个总状态隐藏单项失败。
13. 在 `package_audit` 分别报告 QA debug allowlist、release support allowlist、通用执行入口的变体断言和 internal Shell 非回归结果。

## 验收

- 每个构建阶段都有稳定 ID、所属段落、明确依赖和单一 pixi 命令
- `verify`、`build`、`qa` 和 `release` profile 的阶段组成固定且可审计
- 可视化 CI 无需解析 shell 脚本就能生成阶段视图
- GitHub Actions workflow 不复制业务构建逻辑
- 本机、可视化 CI 和 GitHub Actions 使用相同阶段输入与产物契约
- publish 阶段需要明确授权，并且权限不扩散到其他阶段
- 阶段失败会明确终止依赖它的后续阶段

## 完成记录

状态：未开始。完成前需要记录本机流水线、构建、测试与平台适配验证结果。
