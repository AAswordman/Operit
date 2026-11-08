---
title: 步骤 9：变体、调试边界、签名与版本身份
status: draft
document_type: implementation-step
For_Agent: 未经用户明确授权，不执行编译、构建、测试或发布命令
step: 9
depends_on:
  - 8_FeatureModuleIsolation.md
fork_repository: "git@github.com:Nyashiiro/Operit-follow-up.git"
last_reviewed: 2026-07-14
---

# 步骤 9：变体、调试边界、签名与版本身份

[上一步：Feature 模块隔离](./8_FeatureModuleIsolation.md) · [返回总计划](./index.md) · [下一步：调试接口注册与数据救援 Shell](./10_DebugInterfaceRegistryAndDataRescueShell.md)

## 旧实现情况

- `clone` 和 `nightly` 同时承担调试、共存安装、签名和更新通道职责
- `nightly` 最终使用 debug 签名
- `versionCode` 与 `versionName` 固定在 `app/build.gradle.kts`
- APK 重命名依赖 AGP 内部类型 `BaseVariantOutputImpl`
- prod、QA、full、lite 没有独立的 Gradle 维度
- `ScriptExecutionReceiver`、ToolPkg 调试安装、刷新和 DSL dump receiver 位于主 Manifest，并且 `exported=true`
- 调试 receiver 的实现类和 action 字符串位于 `src/main`，release APK 也会包含这些代码与入口
- `GITHUB_CLIENT_SECRET` 被写入 BuildConfig

## 预期的新实现情况

- identity 维度包含 `prod` 与 `qa`
- capability 维度包含 `full` 与 `lite`
- build type 只包含 `debug` 与 `release`
- 初始只启用 `qaFullDebug`、`qaLiteDebug`、`qaFullRelease` 和 `prodFullRelease`
- QA debug 测试包保留完整的应用内调试与 Shell 能力，并允许外部 ADB 驱动通用代码、脚本文件和调试命令执行
- QA debug 使用独立 QA applicationId 与固定 QA debug 签名，并通过具名注册接口提供稳定的 ToolPkg 调试、包刷新、Compose DSL dump 和 QA 场景触发
- QA release 与 prod release 不包含由外部 ADB 驱动的任意代码、脚本路径、命令、endpoint 切换或 ToolPkg 调试入口
- QA release 与 prod release 包含相同的受限 external support 网关，用于日志和数据救援
- 应用内部 Shell executor 在 debug 与 release 中保持原有能力
- QA release 只保留编译期固定的 QA 服务配置，用于接近正式环境的 official-test 验证
- prod release 只包含正式服务配置
- versionName 与单调递增的 versionCode 来自步骤 1 的唯一版本元数据

## 修改作用域

计划修改：

- `app/build.gradle.kts`
- 根 `build.gradle.kts`
- `app/src/debug/`
- `app/src/release/`
- `app/src/qa/`
- `app/src/prod/`
- full 与 lite 组合源码
- 调试 receiver、调试 assets 与调试专属依赖
- debug 与 release 的 Network Security Config
- `config/build-system/version.*`
- 使用 Variant 与 Artifacts API 的 build logic

本步骤不修改：

- feature 内部业务实现
- QA 离线 fixture 的业务数据
- 补丁生成算法

## 计划

1. 按 identity、capability 的顺序声明两个 flavor dimension。
2. 使用 Android Components API 只启用四个批准变体。
3. full 显式依赖三个 feature，lite 不解析这些 module，也不注册其入口。
4. 为 QA debug、QA release 和 prod release 建立独立且固定的签名映射。
5. 将调试实现、Manifest、action 资源和专属依赖移出 main source set，并将通用 `ScriptExecutionReceiver` 放入 debug source set。
6. QA debug 保留应用内部与外部 ADB 驱动的代码、脚本和 Shell 调试能力；具名接口用于可重复的自动化场景。
7. release source set 不声明 QA debug 组件，也不编译通用执行 receiver 与 QA debug 网关。release support 网关只分派明确登记的支持命令。
8. 将 QA 身份和固定 official-test 配置放入 qa source set，将 ADB 离线 endpoint 与调试执行入口放入 debug source set。
9. debug 网络配置只为 ADB reverse 使用的本机 fixture 开放明确端口；release 网络配置不接受本机明文 endpoint。
10. 删除 client secret 的 APK 注入，保密 OAuth 交换留在受控服务端。
11. 使用唯一版本元数据提供 versionName 与 versionCode。
12. 使用公开 Artifacts API 定位产物，删除 `clone`、`nightly`、`assembleDebugClone` 和内部产物 API。

## 验收

- Gradle 只生成四个批准变体
- `qaFullDebug` 与 `qaLiteDebug` 保持完整的应用内调试与 Shell 能力，并包含通用 `EXECUTE_JS` 与具名调试接口
- `qaFullRelease` 和 `prodFullRelease` 的合并 Manifest 都不含通用 `EXECUTE_JS` receiver，但包含受限 external support 网关
- `qaFullRelease` 和 `prodFullRelease` 的合并 Manifest 不含调试 receiver 与调试 action
- release DEX、assets 和依赖图不含外部 ADB 通用执行 receiver、QA debug 网关、debug 组件类名或 debug action 字符串
- release support command 清单只包含审查通过的日志、数据救援和后续受限支持接口
- release APK 设置 `debuggable=false`，不通过外部 ADB 接受任意 endpoint、脚本路径或命令
- release 中的应用内部 Shell executor 与现有工具调用路径保持可用
- QA release 只包含固定 QA 服务配置，prod release 不含 QA endpoint 和 client secret
- 变体名可以推导 applicationId、签名、能力档和服务配置
- lite 不解析三个 feature，也不包含其入口、assets 或 native 库

## 完成记录

状态：未开始。完成前需要记录四个变体的解析、assemble、Manifest、DEX、assets、依赖与网络配置审计结果。
