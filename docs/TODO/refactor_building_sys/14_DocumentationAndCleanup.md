---
title: 步骤 14：文档同步与旧路径清理
status: draft
document_type: implementation-step
For_Agent: 未经用户明确授权，不执行编译、构建、测试或发布命令
step: 14
depends_on:
  - 13_BuildPipelineContractAndAdapters.md
fork_repository: "git@github.com:Nyashiiro/Operit-follow-up.git"
last_reviewed: 2026-07-14
---

# 步骤 14：文档同步与旧路径清理

[上一步：构建流水线契约与平台适配](./13_BuildPipelineContractAndAdapters.md) · [返回总计划](./index.md)

## 旧实现情况

- `BUILDING.md` 仍要求 npm、全局 pnpm 和持久化环境变量
- `ci/README.md` 已记录当前 PR 检查参数、输入和构建层级，但尚未覆盖计划中的完整流水线 profile
- 构建文档没有按流水线阶段说明依赖、权限和产物交接
- MCP bridge 文档与脚本错误提示仍指向 npm 命令
- 仓库没有一份从目录选择、依赖声明、锁文件、构建任务到 CI 接入的新增包与工具指南
- 现有 ADB 文档主要介绍通用 `EXECUTE_JS` receiver，没有说明具名调试接口的注册规范
- 数据救援路径与内部 Shell 调试命令缺少正式说明
- 构建文档仍包含 `clone`、`nightly`、`assembleDebugClone` 和旧 APK 路径
- `Repo_Arch_Basic.md` 仍将开发、调试、构建和应用内置工具统一描述为 `tools/`
- TODO 计划与正式开发文档没有明确的归档边界

## 预期的新实现情况

- `BUILDING.md` 只描述当前 pixi、pnpm、制品准备和批准变体入口
- `ci/README.md` 记录全部脚本职责、参数、输出、权限和退出条件
- CI 文档按稳定阶段 ID 描述本机、可视化 CI 与 GitHub Actions 的映射关系
- QA 文档分别说明 debug 全量调试、release 调试能力剔除与三项 ADB 离线场景
- bridge、web-chat、example 错误提示与真实命令一致
- 旧 npm 与 Gradle 构建入口、旧产物路径和迁移说明被删除
- 架构文档分别说明 `ci/script/`、`ci/tools/` 与 `tools_built-in/` 的职责
- 新增包与工具指南提供可复制的最小示例、目录决策、依赖声明、资产清单和 CI 接入步骤
- 调试接口指南说明 QA debug、release support 与 internal Shell 的边界、注册 schema、权限、测试和审计方法
- 数据救援 Shell 文档说明路径查看、快照导出、输出格式与不对外暴露的操作
- release support 文档说明日志读取、日志跟随、数据救援和 shell-only 调用约束
- 完成记录、稳定接口、实验结果和验证证据进入正式文档

## 修改作用域

计划修改：

- `docs/doc-src/dev-core/BUILDING.md`
- `ci/README.md`
- `tools_built-in/mcp_bridge/README.md`
- `docs/doc-src/dev-core/ADDING_PACKAGES_AND_TOOLS.md`
- `docs/doc-src/dev-core/DEBUG_INTERFACES.md`
- `docs/doc-src/dev-core/DATA_RESCUE_SHELL.md`
- web-chat 与 example 的构建错误提示
- 补丁发布相关文档
- `Repo_Arch_Basic.md`
- `docs/TODO/refactor_building_sys/`

本步骤不修改：

- 已稳定的业务实现
- 已验收的 module 与变体模型
- 构建和补丁算法

## 计划

1. 从实际 pixi task 和脚本 `--help` 生成任务清单，不复制过期命令。
2. 更新 BUILDING，说明环境准备、JS assets、外部制品、变体、签名输入和产物位置。
3. 更新 CI README，说明本机与外部 CI 的统一调用边界。
4. 更新 MCP bridge、web-chat 和 example 的命令与错误提示。
5. 删除 npm 混用入口、旧 build type、旧任务别名和旧 APK 路径说明。
6. 更新仓库架构说明，分别记录脚本、宿主工具和应用内置工具目录。
7. 记录构建 profile、阶段顺序、产物交接、权限边界和平台适配方式。
8. 记录 QA debug 的完整调试入口、release APK 的 support allowlist、被剔除的通用执行入口和三项 ADB 离线模拟命令。
9. 编写新增包与工具指南，说明以下完整流程：
   - 仓库自有 JS 包加入 pnpm workspace、声明依赖、更新唯一 lockfile、生成 assets 清单并接入 pipeline stage
   - CI 工作流脚本放入 `ci/script/<意图>/`，宿主工具放入 `ci/tools/<名称>/`，应用内置工具放入 `tools_built-in/<名称>/`
   - 本地 AAR、JAR、JNI、models 与 subpack 先登记制品 manifest、hash、许可证和唯一消费者，再写入精确 Gradle 依赖
   - 新工具声明负责人、输入输出、权限、构建任务、测试任务、产物清单和文档入口
10. 编写调试接口指南，说明 command ID 命名、版本、类型参数、结果 schema、暴露范围、注册代码、调用脚本、日志、测试与 APK 审计。
11. 在调试接口指南中明确 QA debug 允许 external ADB 通用执行，release 只保留 `EXTERNAL_RELEASE_SUPPORT` command，internal Shell 不受两者影响。
12. 编写数据救援 Shell 指南，记录 `data_rescue_paths`、`data_rescue_export`、共享路径定义、快照格式、输出位置和 release ADB shell 调用方法。
13. 记录 `support_logs_read` 与 `support_logs_follow` 的过滤、敏感信息清理、读取上限、跟随时长和 shell 输出格式。
14. 将旧 `execute_js` 与 sandbox 外部广播文档改写为 QA debug 专用说明，并补充具名接口与 release support 的适用场景。
15. 为十四个步骤补充实际 PR、验证记录和稳定接口链接。
16. 全部步骤完成后，在标题添加 `[DONE]`，再按 TODO 规范单独归档本目录。

## 验收

- 新协作者只阅读 BUILDING 与 CI README 就能定位全部当前入口
- 文档中的任务名、参数和产物路径与实现一致
- 仓库自有文档不再引导旧 npm 或旧 Gradle 路径
- 新协作者能按指南正确添加 workspace package、CI script、宿主工具、应用内置工具和外部制品
- 新调试接口具有从实现、注册、调用、测试到 APK 审计的完整示例
- 文档明确 QA debug 通用执行、具名 command registry、release support 与应用内部 Shell 的权限边界
- release 用户可以按文档从外部 ADB shell 读取日志和执行数据救援，不需要安装 QA 包
- 数据救援 Shell 文档与 `DataRescuePaths`、快照导出服务的实际接口一致
- 十四个步骤都具有完成证据和相应文档
- 本目录仍保持草稿状态，直到用户明确确认归档

## 完成记录

状态：未开始。归档动作需要用户明确确认。
