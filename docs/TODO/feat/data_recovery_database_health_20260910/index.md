---
Repository: https://github.com/AAswordman/Operit
Branch: feat/data-recovery-database-health
Status: completed
---

# 数据救援配置和数据库检测

## 原有状况

已发布的数据救援界面提供原始快照导入导出和 SQL 执行器，但普通用户无法判断配置文件与 Room 数据库是否完整，也无法区分能够确定处理的问题与必须人工救援的数据损坏。

PR #1006 尝试在应用启动期间统一恢复 Preferences、Room 和 ObjectBox，同时混入备份格式、业务配置修正和语音配置改造。该范围无法安全合并，现改为从当前 `dev` 提取用户手动触发的 Preferences 文件与 Room 检测修复能力。

## 意图

- 保持现有数据救援入口和 SQL、原始快照接口兼容
- 在 SQL 执行器下方增加用户主动触发的配置和数据库检测
- 在同一入口检测 Preferences 配置文件是否能够正常读取
- 检测数据库文件、SQLite 完整性、版本和外键状态
- 仅对能够确定处理的问题提供修复操作
- 修改前分别保全原始配置文件以及 DB、WAL、SHM 和 journal 文件
- 不接入应用启动流程，不修改 Preferences 或 ObjectBox 生命周期

## 作用域

- `DataRecoveryActivity` 和 `DataRecoveryViewModel`
- 新增 Room 健康检查与修复管理器
- 新增 Preferences 配置文件健康检查与修复管理器
- 中文、英文和日文界面文案
- 与该功能直接相关的开发文档

## 非目标

- 启动时主动扫描或恢复数据库
- Raw Snapshot 格式升级
- Preferences 业务字段、ObjectBox 或业务配置引用自动修正
- 自动替换无法验证的数据库内容

## 步骤

1. [Room 健康检查](./1_RoomHealthCheck.md)
2. [明确修复与原件保全](./2_RepairAndPreservation.md)
3. [兼容性与核对](./3_CompatibilityAndVerification.md)
4. [Preferences 配置文件检测与修复](./4_PreferencesHealth.md)

[DONE]
