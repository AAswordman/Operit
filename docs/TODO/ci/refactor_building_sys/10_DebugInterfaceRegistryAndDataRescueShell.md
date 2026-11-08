---
title: 步骤 10：调试接口注册与数据救援 Shell
status: draft
document_type: implementation-step
For_Agent: 未经用户明确授权，不执行编译、构建、测试或发布命令
step: 10
depends_on:
  - 9_VariantsSigningAndVersioning.md
fork_repository: "git@github.com:Nyashiiro/Operit-follow-up.git"
last_reviewed: 2026-07-14
---

# 步骤 10：调试接口注册与数据救援 Shell

[上一步：变体、调试边界、签名与版本身份](./9_VariantsSigningAndVersioning.md) · [返回总计划](./index.md) · [下一步：APK 审计与补丁链](./11_ApkAuditAndPatch.md)

## 旧实现情况

- `ScriptExecutionReceiver` 接受代码文本、脚本文件路径和函数参数，外部 ADB 可以通过广播驱动应用执行通用脚本
- ToolPkg 安装、包刷新和 DSL dump 分别使用独立 exported receiver，没有统一的接口描述与注册表
- `tools/adb/execute_js.*`、`run_sandbox_script.*` 和现有 ADB 文档依赖通用执行 receiver
- 应用内部已经具有 `DebuggerShellExecutor`、Shell 工具和终端执行能力
- 数据救援 UI 通过 `RawSnapshotBackupManager` 导出 `files`、external files、`shared_prefs`、`datastore` 和 `databases`
- 数据救援路径、快照导出与 UI 状态耦合，其他内部调试入口无法复用同一组路径定义

## 预期的新实现情况

- QA debug 保留通用代码、脚本文件和调试命令执行 receiver，以支持全自动与探索性调试
- QA debug 另外提供 debug-only 具名调试网关，用于调用注册表中明确标记为 QA debug 的稳定接口
- QA release 与 prod release 都不编译通用执行 receiver 和 QA debug 网关，但包含受限 external support 网关
- 每个调试或支持接口声明稳定 ID、版本、类型参数、输出 schema、适用变体、执行权限、副作用和日志字段
- release support 网关只接受 ADB shell 或 root 身份，不接受普通应用 UID，也不开放网络监听
- 应用内部 ADB/Shell 模式继续使用原有 Shell executor 与应用权限，不受外部 ADB 网关限制
- 数据救援路径与快照导出从 UI 层抽成可复用服务
- 应用内部 Shell 与 release support 网关都提供数据救援路径查看和快照导出
- release support 首批提供日志读取或跟随、数据救援路径和数据快照导出，不开放恢复覆盖、任意 SQL、任意路径或任意命令

## 修改作用域

计划修改：

- `app/src/main/java/com/ai/assistance/operit/core/debug/`
- `app/src/debug/`
- `app/src/main/java/com/ai/assistance/operit/core/tools/system/shell/`
- `app/src/main/java/com/ai/assistance/operit/data/backup/RawSnapshotBackupManager.kt`
- `app/src/main/java/com/ai/assistance/operit/data/backup/OperitBackupDirs.kt`
- `app/src/main/java/com/ai/assistance/operit/ui/recovery/`
- `app/src/main/java/com/ai/assistance/operit/util/AppLogger.kt`
- `app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/logcat/`
- `tools/adb/execute_js.*`、`tools/adb/run_sandbox_script.*` 及迁移后的对应脚本
- 调试接口 schema、注册清单和接口测试

本步骤不修改：

- 应用内部 Shell executor 的命令执行语义
- 数据救援 UI 的恢复、SQL 与文件管理功能
- Raw snapshot 文件格式

## 计划

1. 定义 `DebugCommand`、参数 schema、结果 schema、暴露范围和 `DebugCommandRegistry`。
2. 暴露范围至少区分 `INTERNAL_SHELL`、`EXTERNAL_QA_DEBUG` 与 `EXTERNAL_RELEASE_SUPPORT`，注册时必须显式选择。
3. 创建只存在于 debug source set 的 QA ADB 调试网关，按 command ID 查找注册项并校验类型参数。
4. 将 `ScriptExecutionReceiver`、通用 `EXECUTE_JS` action 和调用脚本限制在 QA debug 构建及其开发工具中，并从全部 release source set 剔除。
5. 将 ToolPkg 调试安装、包刷新、DSL dump 和三项 QA 离线场景改成独立、具名、带类型参数的注册接口。
6. 创建 release support 网关，通过 Manifest 权限与运行时调用者身份校验限定 ADB shell 或 root，并只分派 `EXTERNAL_RELEASE_SUPPORT` command。action 名称本身不作为访问控制。
7. 保持 `DebuggerShellExecutor`、应用内 Shell 工具和终端执行路径独立，不让外部网关改变其能力。
8. 从数据救援实现抽出 `DataRescuePaths`，统一解析 `files`、external files、`shared_prefs`、`datastore`、`databases` 和 raw snapshot 输出目录。
9. 从 `RawSnapshotBackupManager` 抽出可由 UI、内部 Shell 与 release support 共同调用的导出服务，保持现有 snapshot manifest 与 ZIP 结构。
10. 注册 `data_rescue_paths`，向 `INTERNAL_SHELL` 与 `EXTERNAL_RELEASE_SUPPORT` 返回路径 ID、规范路径、是否存在与访问状态。
11. 注册 `data_rescue_export`，向 `INTERNAL_SHELL` 与 `EXTERNAL_RELEASE_SUPPORT` 返回输出路径、大小、hash 与清单信息。
12. 从 `AppLogger.getLogFile()` 与现有 logcat 读取逻辑抽出只读日志源，并对 release 输出执行敏感信息清理。
13. 注册 `support_logs_read` 与 `support_logs_follow`，将经过清理的应用日志输出到调用方 shell，并限制过滤字段、单次读取量和跟随时长。
14. 数据恢复覆盖、任意 SQL、任意文件路径与任意 shell 命令不注册为 `EXTERNAL_RELEASE_SUPPORT`。
15. 为注册表生成机器可读接口清单，供文档、脚本参数检查与 APK 审计使用。

## 验收

- QA debug Manifest 包含通用 `EXECUTE_JS` 外部入口与具名 ADB 调试网关
- QA debug 的具名网关只能执行注册为 `EXTERNAL_QA_DEBUG` 的 command ID，通用入口继续承担自由调试
- 未注册 command、参数类型错误和暴露范围不匹配都会明确终止并记录日志
- release APK 不包含通用执行 receiver 与 QA debug 网关，但包含 external support 网关和审核通过的 support command
- 普通应用 UID 无法调用 release support 网关，ADB shell 与 root 可以调用
- release support 网关同时通过 Manifest 权限和运行时调用者身份检查
- 应用内部 ADB/Shell 模式的既有命令执行能力保持不变
- 数据救援 UI 与内部 Shell 使用同一个 `DataRescuePaths` 和快照导出服务
- `data_rescue_paths` 与 `data_rescue_export` 可从 release 的外部 shell 调用，但不接受任意路径、任意 SQL 或恢复覆盖请求
- `support_logs_read` 与 `support_logs_follow` 将经过清理的日志写向外部 shell，不建立网络服务
- raw snapshot 格式和现有数据救援 UI 行为保持一致

## 完成记录

状态：未开始。完成前需要记录 QA debug 通用执行、具名注册表、release support 调用者限制、日志输出、内部 Shell 保持、数据救援路径与快照导出的验证结果。
