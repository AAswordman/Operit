---
title: Operit 构建系统重构总计划
status: draft
document_type: refactoring-plan-index
For_Agent: 未经用户明确授权，不执行编译、构建、测试或发布命令
fork_repository: "git@github.com:Nyashiiro/Operit-follow-up.git"
last_reviewed: 2026-07-14
---

# Operit 构建系统重构总计划

本目录是构建系统重构的工作草稿，不代表已经发布或最终确定的实现。每个编号文件对应一个可独立审查和提交的最小步骤，后一步只依赖已经完成的前一步。

## 目标

仓库需要为 JavaScript assets、外部二进制、Android 模块、APK 变体和补丁发布建立唯一且可审计的构建输入。

用户可见能力、数据和对外接口保持稳定。内部构建入口完成迁移后，旧 npm 命令、旧 Gradle build type、目录扫描和过期说明会被删除。

## 步骤

1. [基线与构建契约](./1_BaselineAndContracts.md)：记录现状，确定清单格式、工具版本和用户接口不变量
2. [pnpm workspace 与锁文件](./2_JsWorkspaceAndLockfile.md)：统一仓库自有 JavaScript 包的依赖输入
3. [外部制品清单](./3_ExternalArtifactManifest.md)：登记 AAR、JAR、JNI、models 和 subpack 的来源与所有者
4. [Gradle 输入规范化](./4_GradleInputNormalization.md)：移除宽泛依赖扫描与不明确的 `.so` 选择规则
5. [工具目录迁移](./5_ToolingDirectoryMigration.md)：将脚本、宿主工具和应用内置工具迁入职责明确的目录
6. [pixi 与资产准备入口](./6_PixiAndAssetPreparation.md)：统一 JS assets、制品校验和环境报告入口
7. [Android 能力契约](./7_AndroidCapabilityContracts.md)：在现有 `:app` 内收束媒体、语音和导出边界
8. [Feature 模块隔离](./8_FeatureModuleIsolation.md)：将能力实现与制品移动到唯一 Android library 所有者
9. [变体、调试边界、签名与版本身份](./9_VariantsSigningAndVersioning.md)：建立受控组合并隔离 debug 与 release 能力
10. [调试接口注册与数据救援 Shell](./10_DebugInterfaceRegistryAndDataRescueShell.md)：移除外部通用执行并补充可扩展的内部调试入口
11. [APK 审计与补丁链](./11_ApkAuditAndPatch.md)：建立 APK 身份审计和补丁发布硬约束
12. [QA ADB 离线模拟](./12_QaAdbOfflineSimulation.md)：验证插件市场、差量更新和应用内弹窗通知推送
13. [构建流水线契约与平台适配](./13_BuildPipelineContractAndAdapters.md)：为可视化 CI 和 GitHub Actions 提供稳定阶段边界
14. [文档同步与旧路径清理](./14_DocumentationAndCleanup.md)：更新正式开发文档并删除迁移期入口

## 构建流水线段落

构建由以下稳定阶段组成。平台可以隐藏未被 profile 选中的阶段，但不能改变阶段职责或依赖。

### 输入校验

1. `source_validate`：检查源码状态、配置 schema、批准变体和路径引用

### 环境与依赖准备

2. `environment_prepare`：准备固定工具链并输出环境报告
3. `dependency_prepare`：执行 pnpm 冻结安装并确认 Gradle 输入
4. `asset_prepare`：生成 web-chat、toolpkg、bridge assets 和清单
5. `artifact_verify`：校验 AAR、JAR、JNI、models 与 subpack

### 构建

6. `static_verify`：执行 profile 明确要求的静态检查
7. `test_execute`：执行 profile 明确要求的单元测试或集成测试
8. `android_assemble`：构建指定且已批准的 APK 或 AAB 变体

### 产物验证

9. `package_audit`：检查 APK 身份、签名、ABI、Manifest、assets 与 native 库
10. `qa_verify`：执行插件市场、差量更新、应用内弹窗通知推送的 ADB 离线模拟与 official-test 验证

### 补丁与发布

11. `patch_prepare`：校验基础与目标 APK，并生成、重建和核对补丁
12. `publish`：在明确授权下发布完整包、补丁、元数据和审计报告

`verify`、`build`、`qa` 与 `release` profile 显式选择上述阶段。每个阶段只调用一个 pixi task，并通过结构化报告交付状态与产物。

这些 profile 必须保留完整的编译、构建、测试、审计、补丁和发布能力。YAML 中的 `For_Agent` 只限制 Agent 在当前会话中执行命令，不参与 profile 选择，也不改变构建脚本行为。

## QA debug 与 release 边界

- `qaFullDebug` 与 `qaLiteDebug` 是全量调试测试包，允许外部 ADB 提交代码、脚本路径和调试命令，以支持探索性与全自动调试
- QA debug 同时提供具名接口，用于稳定执行 ToolPkg 调试、包刷新、DSL dump 和 QA 场景
- `qaFullRelease` 保留 QA applicationId、QA release 签名、固定 official-test 服务配置、应用内部 Shell 和 release support 接口，但不包含外部 ADB 通用执行或任意 endpoint
- `prodFullRelease` 包含正式身份、正式服务配置、应用内部 Shell 和同一组 release support 接口
- 通用执行 receiver 与具名调试网关都只通过 debug source set、debug Manifest 和 debug 专属依赖引入
- 应用内部 ADB/Shell 模式继续使用内部 Shell executor，不受外部调试入口变化限制
- release 只保留日志输出、数据救援等明确注册的 external support command，并拒绝普通应用 UID 调用
- release 限制通过不编译 QA debug 通用执行实现、不合并 debug Manifest、不打包 debug assets、不解析 debug 依赖和不接受本机明文 endpoint 实现
- APK 审计必须检查 release 的 Manifest、DEX、action 字符串、assets、依赖图和网络配置，不能只检查 `BuildConfig.DEBUG`

## 执行约束

- 同一类输入只能有一个受版本控制的事实来源
- 每个二进制、asset 和 native 库只能有一个消费模块
- 文件缺失、hash 不符、签名不符、版本不递增或变体未批准时直接终止
- Gradle 不安装 JS 或 Python 依赖，不下载业务制品，也不发布网络资产
- QA debug 明确包含面向外部 ADB 的通用代码、脚本和调试命令执行入口
- 所有 release APK 不包含通用执行 receiver、QA debug 网关、debug assets 或 client secret
- 所有 release APK 都包含受限 external support 网关，并至少提供日志输出、数据救援路径和数据快照导出
- prod release 不包含 QA endpoint 与测试 OAuth 配置

## 目标模块目录

```text
/
	app/
	ci/
		script/
		tools/
	core/
		capability-api/
	feature/
		media/
		speech/
		export/
	tools_built-in/
```

`:app` 保持为 application 壳。capability API 提供稳定契约，三个 feature 目录分别提供普通 Android library。

## 完成标记

每个步骤完成代码修改、文档同步和该步骤要求的验证后，在对应文件的一级标题末尾添加 `[DONE]`。只有十四个步骤都完成，才能归档本目录。
