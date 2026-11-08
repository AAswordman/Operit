---
title: 步骤 11：APK 审计与补丁链
status: draft
document_type: implementation-step
plan_scope: apk-audit-and-patch
For_Agent: 未经用户明确授权，不执行编译、构建、测试或发布命令
step: 11
depends_on:
  - 10_DebugInterfaceRegistryAndDataRescueShell.md
fork_repository: "git@github.com:Nyashiiro/Operit-follow-up.git"
last_reviewed: 2026-07-14
---

# 步骤 11：APK 审计与补丁链

[上一步：调试接口注册与数据救援 Shell](./10_DebugInterfaceRegistryAndDataRescueShell.md) · [返回总计划](./index.md) · [下一步：QA ADB 离线模拟](./12_QaAdbOfflineSimulation.md)

## 旧实现情况

- `nightly_auto.py` 自行选择 `assembleRelease` 或 `assembleNightly` 并推断 APK 文件名
- 补丁元数据包含 APK hash，但没有统一关联 JS、制品、工具链和变体清单
- `nightly` 的 debug 签名不能与正式 release 构成同一更新链
- release APK 缺少针对任意执行代码、调试 action 和调试 assets 的实包审计
- 正式包与 QA 包的身份和配置隔离缺少实包审计

## 预期的新实现情况

- 每个 APK 具有构建清单和内容审计报告
- prod、QA 与不同能力档分别建立补丁链
- 补丁发布前检查 applicationId、签名、versionCode、ABI、能力档和基础 APK hash
- APK 审计能证明 QA debug 同时具有通用执行与具名接口，并证明 release 只包含审核通过的 support command
- 外部 CI 只调用 pixi 管理的 Android、审计和补丁任务

## 修改作用域

计划修改：

- `ci/script/android/`
- `ci/script/patch/`
- `ci/script/hotbuild/`
- 构建清单、APK 审计报告和补丁元数据 schema

本步骤不修改：

- capability API 的业务契约
- feature module 所有权
- 正式用户数据格式

## 计划

1. 建立 `android-assemble` 与 `apk-audit` 任务，先校验 JS 和外部制品清单。
2. 构建清单记录 Git revision、工具版本、输入清单 hash、变体、versionCode、签名摘要和 APK hash。
3. APK 审计检查 applicationId、签名、ABI、assets、native 库与 Manifest 组件。
4. 为 QA debug APK 建立调试入口 allowlist，确认通用 `EXECUTE_JS` 与具名接口按计划存在。
5. 为 release APK 建立 exported component 与 support command allowlist，并对通用执行 DEX 类、QA debug action、debug assets、debug 依赖和本机 endpoint 建立明确的不存在断言。
6. 验证普通应用 UID 调用 release support 网关会被拒绝，ADB shell 可以读取日志并执行数据救援导出。
7. 改造 hotbuild，使其读取构建清单与公开产物位置，不推断旧 build type 或文件名。
8. 补丁生成后重建目标 APK，并核对目标 SHA-256。

## 验收

- 构建清单、APK 审计报告和补丁元数据能够互相追溯
- 基础与目标 APK 的 applicationId 和签名相同
- 目标 versionCode 严格大于基础 versionCode
- 能力档、ABI 与内置制品组合完全一致
- QA 与 prod 更新链隔离
- QA debug APK 的通用执行入口、具名 ADB 接口与 allowlist 一致，应用内部 Shell 能力不参与该限制
- QA release 与 prod release 不包含外部 ADB 通用执行 receiver、QA debug 网关、debug action 或 debug assets
- QA release 与 prod release 包含一致的 support command allowlist，日志和数据救援可以从 ADB shell 调用
- 普通应用 UID 无法调用 release support command
- QA release 与 prod release 的应用内部 Shell executor 保持可用
- prod release 不包含 QA 测试配置

## 完成记录

状态：未开始。完成前需要记录 APK 构建、设备测试、补丁重建和发布验证结果。
