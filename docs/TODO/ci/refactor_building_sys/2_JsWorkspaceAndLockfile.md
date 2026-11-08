---
title: 步骤 2：pnpm workspace 与锁文件
status: draft
document_type: implementation-step
For_Agent: 未经用户明确授权，不执行编译、构建、测试或发布命令
step: 2
depends_on:
  - 1_BaselineAndContracts.md
fork_repository: "git@github.com:Nyashiiro/Operit-follow-up.git"
last_reviewed: 2026-07-14
---

# 步骤 2：pnpm workspace 与锁文件

[上一步：基线与构建契约](./1_BaselineAndContracts.md) · [返回总计划](./index.md) · [下一步：外部制品清单](./3_ExternalArtifactManifest.md)

## 旧实现情况

- 根项目和 `web-chat` 的构建入口使用 npm
- `tools/example_packages/sync_example_packages.py` 已直接调用 pnpm，仓库同时存在两套包管理入口
- `pnpm-workspace.yaml` 没有 packages 列表
- 原仓库忽略 `pnpm-lock.yaml` 和 `package-lock.json`；PR 门禁重构已暂时提交三个 npm lockfile 供 `npm ci` 使用
- `tools/mcp_bridge` 使用 npm 串联自己的 build、bundle 和 copy 脚本

## 预期的新实现情况

- 根 `package.json` 通过 `packageManager` 固定 pnpm 精确版本
- 根 `pnpm-lock.yaml` 是仓库自有 JavaScript 包的唯一锁文件
- workspace 只包含 `web-chat`、`tools/mcp_bridge` 和 `examples/github`
- 受管理包只通过 pnpm 安装和调用脚本
- 第三方源码、子模块、用户模板和独立 examples 不受根 workspace 管理

## 修改作用域

计划修改：

- `package.json`
- `pnpm-workspace.yaml`
- `pnpm-lock.yaml`
- `.gitignore`
- `web-chat/package.json`
- `tools/mcp_bridge/package.json`
- `examples/github/package.json`
- `tools/example_packages/sync_example_packages.py`

本步骤不修改：

- 第三方与 Git 子模块中的 package manifest 和 lockfile
- Android Gradle 依赖
- JS assets 的 pixi 编排

## 计划

1. 确认 Node.js LTS 精确版本和 pnpm 精确版本，并写入步骤 1 的工具链配置。
2. 为 workspace 添加三个显式 package 路径，不使用覆盖整个仓库的通配符。
3. 生成并提交唯一根 lockfile。
4. 删除受管理范围的 npm 脚本调用，并保持运行时协议字段的原有语义。
5. 检查每个 import 的真实依赖，将缺失声明加入实际使用方。
6. 增加静态检查，阻止受管理目录出现其他包管理器 lockfile。

## 验收

- workspace 范围与计划完全一致
- 冻结安装不会修改 lockfile
- 受管理包不存在隐式可见依赖
- 受管理构建入口不调用 npm 或 yarn
- 第三方、模板和子模块未被 workspace 接管

## 完成记录

状态：部分准备。PR 门禁已使用 npm lockfile 冻结当前安装；迁移到唯一 pnpm workspace lockfile 尚未开始，完成前仍需记录冻结安装与包构建验证结果。
