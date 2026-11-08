---
title: 步骤 12：QA ADB 离线模拟
status: draft
document_type: implementation-step
For_Agent: 未经用户明确授权，不执行编译、构建、测试或发布命令
step: 12
depends_on:
  - 11_ApkAuditAndPatch.md
fork_repository: "git@github.com:Nyashiiro/Operit-follow-up.git"
last_reviewed: 2026-07-14
---

# 步骤 12：QA ADB 离线模拟

[上一步：APK 审计与补丁链](./11_ApkAuditAndPatch.md) · [返回总计划](./index.md) · [下一步：构建流水线契约与平台适配](./13_BuildPipelineContractAndAdapters.md)

## 旧实现情况

- 插件市场直接使用 `MarketStatsApiService` 中的正式 API 与静态资源地址
- 差量更新通过 `UpdateManager` 读取 GitHub Releases，并由 `PatchUpdateInstaller` 下载、重建和校验 APK
- 应用内弹窗通知推送通过 `RemoteAnnouncementRepository` 拉取 pointer 与 payload，再由 `RemoteAnnouncementDialog` 展示
- 三条在线链路没有统一的 QA debug endpoint 配置、ADB 触发协议和离线 fixture
- 当前计划曾将应用内弹窗通知推送与 Android `NotificationListenerService` 混为同一测试目标
- 现有调试 receiver 位于主 Manifest，尚未形成 QA debug 专属边界

## 预期的新实现情况

- `qaFullDebug` 是三项 ADB 离线模拟的主测试包，`qaLiteDebug` 可验证基础能力在 lite 组合中的行为
- 三项固定模拟通过具名注册接口触发；QA debug 仍保留外部 ADB 通用执行能力，用于扩展和探索自动化场景
- QA debug 通过 `adb reverse` 访问宿主机 fixture，并复用生产 HTTP、解析、缓存、状态管理、下载和 UI 实现
- `qa-market-offline` 验证插件市场
- `qa-patch-offline` 验证差量更新
- `qa-announcement-offline` 验证应用内弹窗通知推送
- 每个场景具有固定 fixture、明确 ADB 步骤、结构化结果和独立诊断日志
- `qaFullRelease` 不包含 ADB 任意执行与离线 endpoint 切换，只使用固定 official-test 配置进行接近正式环境的验证
- 应用内弹窗通知推送测试只覆盖远程公告拉取与弹窗展示，不覆盖系统通知监听服务

## 修改作用域

计划修改：

- `ci/script/qa/`
- `ci/tools/qa_fixture/`
- `app/src/debug/`
- `app/src/qa/`
- 插件市场 endpoint 配置与 `MarketStatsApiService`
- 更新 endpoint 配置、`UpdateManager` 与 `PatchUpdateInstaller`
- 公告 endpoint 配置、`RemoteAnnouncementRepository`、`RemoteAnnouncementPreferences` 与 `RemoteAnnouncementDialog`
- QA 场景报告 schema 与固定 fixture 数据

本步骤不修改：

- prod release 的正式 endpoint
- `NotificationListenerService` 的系统通知监听逻辑
- 插件市场、差量更新和公告弹窗的生产数据模型

## 计划

### ADB 与 fixture 公共入口

1. 建立宿主机 fixture server，固定监听地址、端口范围、数据目录和访问日志格式。
2. 为 QA debug 建立 endpoint 配置接口，三项功能继续使用各自的生产客户端与解析模型。
3. 使用 `adb reverse` 将设备端固定端口映射到 fixture server。
4. 通过步骤 10 的 debug-only ADB 网关注册三个场景 command ID，用类型参数执行状态清理、触发动作和结果导出。
5. 保留应用内部 Shell 与 QA debug 外部 ADB 的完整脚本、命令和 ToolPkg 调试能力；三个固定场景使用具名接口保证结果可重复。
6. 每个场景生成包含请求、响应、状态变化、UI 结果和错误日志的结构化报告。

### 插件市场离线模拟

1. fixture 提供与正式市场 API 和静态资源路径一致的列表、分类、详情、搜索、排序与插件资产响应。
2. `qa-market-offline` 启动 fixture、建立 ADB reverse、清理市场缓存并触发真实市场刷新。
3. 场景验证列表与详情解析、搜索和排序、资源下载、安装结果与本地状态变化。
4. 断言所有请求都到达 fixture，测试期间不访问正式市场服务。

### 差量更新离线模拟

1. fixture 提供 GitHub Releases 兼容响应、补丁元数据、补丁 ZIP 和目标 APK 信息。
2. 使用相同 QA applicationId 与固定 QA debug 签名准备基线 APK 和目标 APK，目标 versionCode 严格递增。
3. `qa-patch-offline` 安装基线 APK、建立 ADB reverse、触发真实更新检查和补丁下载。
4. 场景验证基础 APK hash、补丁 hash、目标 APK 重建 hash、安装请求与安装后版本。
5. 结果报告记录基础与目标构建清单、补丁元数据和设备安装状态。

### 应用内弹窗通知推送离线模拟

1. fixture 提供 `latest.json` pointer 与公告 payload，字段格式与 `RemoteAnnouncementRepository` 当前模型一致。
2. `qa-announcement-offline` 清理公告已读状态、建立 ADB reverse 并触发真实公告拉取。
3. 场景验证 versionCode、渠道、语言、时间范围、启用状态和倒计时筛选。
4. 场景断言 `RemoteAnnouncementDialog` 展示的标题、正文、确认文本与倒计时，并验证确认后的已读持久化。
5. 结果报告明确标记该场景为应用内远程公告，不采集 `NotificationListenerService` 结果。

## 验收

- 三个 pixi 任务可分别执行，不共享未声明状态
- `qaFullDebug` 能通过 ADB 完成插件市场、差量更新和应用内弹窗通知推送的离线端到端验证
- 三个场景复用生产客户端、解析模型、状态管理与 UI，不建立第二套业务实现
- QA debug 可通过通用入口进行自由调试，也可通过三个具名场景获得稳定、可审计的端到端结果
- fixture 请求日志证明离线场景未访问正式服务
- 差量更新重建出的 APK hash 与目标 APK 完全一致，并记录安装后 versionCode
- 公告场景验证 `RemoteAnnouncementDialog` 与已读状态，不将系统通知监听视为通过条件
- `qaFullRelease` 与 `prodFullRelease` 不包含 debug-only 场景入口、ADB 任意执行或离线 endpoint

## 完成记录

状态：未开始。完成前需要记录三项离线场景、QA release 限制和 official-test 的设备验证结果。
