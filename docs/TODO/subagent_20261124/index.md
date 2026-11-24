---
title: 子代理（Subagent）移植
status: implemented_and_verified
fork: https://cnb.cool/542474846/Operit
date: 2026-11-24
---

# 子代理（Subagent）移植

[返回总计划](../README.md)

## 原本状况

宿主已有 `EnhancedAIService.sendMessage(isSubTask=true)` 子任务管线、`SubtaskExecutionState` 证据账本与 `ToolExecutionManager.isSubtaskDelegationTarget` 递归闸门，但没有任何"把任务派发给独立子代理"的入口。唯一的同名工具 `subagent:subagent_run` 来自 `examples/subagent` 的被动式 toolpkg：它运行在 JS 沙箱里，预算和递归约束只写在提示词中，终态由脚本自己声明，宿主无法审计。

同时，模型侧并行 `tool_calls` 的流式处理会在 index 切换时立即闭合上一个工具，参数交错时必然产出非法 XML；XML 标签名解析也不接受冒号，`subagent:subagent_run` 这类 `包:函数` 形式在历史回放中会退化成 unknown/text。

## 意图

把子代理做成宿主一等公民：

- 内置工具 `subagent:subagent_run`，五动作（delegate/status/cancel/steer/resume），并发槽位按"子代理自己用的模型配置"分道
- 档案体系（名字/简介/工作内容/模型绑定），名单每轮注入主对话供模型选人，总开关控制暴露
- 终态由宿主终态优先于模型声明，工具账本参与校验，证据归档到 `filesDir/subagent_runs/<jobId>.json`
- `wait=false` 后台委派完成后两段式投递：信封卡片写入父会话 + 隐藏用户消息唤醒父代理
- 执行中实时卡片、完成后信封卡片、统一详情弹窗

## 预期结果

模型可以一次派发多路自包含任务，中途无需轮询；每路结果独立成卡片并各自投递回会话；子代理内的再委派在执行层被拒绝；关闭开关后工具与名单对模型完全不可见。

## 作用域

新增：

- `data/model/SubagentProfile.kt`、`data/model/SubagentRoster.kt`
- `data/preferences/SubagentProfileManager.kt`
- `core/subagent/AgentJobRegistry.kt`、`core/subagent/SubagentDispatchService.kt`
- `ui/features/settings/screens/SubagentConfigScreen.kt`、`SubagentProfileEditorScreen.kt`
- `ui/features/chat/components/part/SubagentRunCard.kt`

修改：`ToolRegistration`、`SystemToolPromptsInternal`、`SystemPromptConfig`、`ConversationService`、`ToolExecutionManager`、`SubtaskExecutionState`、`EnhancedAIService`、`ChatServiceCore`、`MessageCoordinationDelegate`、`OpenAIProvider`、`CustomXmlRenderer`、`OperitScreens`、`SettingsScreen`、4 处 XML 解析器、zh/en 字符串。

移除：`examples/subagent`（注册同名工具的被动式示例，与本功能冲突）。

不涉及：工具权限系统（继续走既有悬浮窗确认链路，本次不改）。

## 步骤

1. [1_config_layer.md](1_config_layer.md) — 数据模型、名单规范化、DataStore、设置页与编辑器
2. [2_dispatch_layer.md](2_dispatch_layer.md) — 任务注册表、调度服务、终态合成、证据归档
3. [3_host_wiring.md](3_host_wiring.md) — 工具注册、提示词、名单注入、并行白名单、配额放宽、steer、完成投递
4. [4_stream_parsing.md](4_stream_parsing.md) — 冒号标签名与并行 tool_calls 缓冲
5. [5_ui_layer.md](5_ui_layer.md) — 运行卡片、信封卡片、详情弹窗、渲染分派、路由
6. [6_strings_and_tests.md](6_strings_and_tests.md) — 字符串资源、既有测试修正、新增单测

## 与参考实现的差异

参考分支 `Operit_xioaShuo` 已实现同功能。本次移植保留其全部协议与调度语义，以下按本仓库实际情况调整：

- 工具权限确认**不改**：参考分支把悬浮窗链路整体换成应用内 `AlertDialog`，本仓库维持现状
- `SubtaskExecutionStateTest` 的预算断言：参考分支把 `require` 放宽为 `>= 1` 却让测试仍断言 `1 through 64`，该测试在参考分支实际不通过；本次一并修正
- 子代理工具配额按无限制语义（`Int.MAX_VALUE`），与参考分支一致

## 实施状态

六个步骤的代码改动均已完成，`examples/subagent` 已移除，编译、单测与真机验证全部通过。

```
:app:compileDebugKotlin            BUILD SUCCESSFUL
:app:assembleDebug                 BUILD SUCCESSFUL（APK 约 422 MB）
adb install -r                     Success
:app:testDebugUnitTest             子代理四个测试类全绿
  SubagentRosterTest               11 用例
  AgentJobRegistryTest             11 用例
  SubtaskExecutionStateTest        19 用例
  SubtaskDeclarationTest            7 用例
```

真机验证：开关关闭时工具与名单对模型不可见；阻塞委派返回信封；`wait=false`
立即返回并结束回合；多路并行各自成卡片、输出无裸露 `<param>`；杀进程重开
名单完好且内置行仍在；运行卡片显示模型/上下文/用量并可停止。

移植后追加修正的三处实际缺陷（参考分支没有这些问题，是本仓库落地才暴露的）：

- **跟随会话的模型绑定取错模型**：只取 CHAT 功能映射的 `configId` 再把
  `modelIndex` 补 0，在逗号分隔多模型配置（如 `gemini,mimo`）里会执行到
  列表第一个模型，而不是会话窗口当前选中的那个。改为用
  `getConfigMappingForFunction` 连 `modelIndex` 一起取
- **完成态详情弹窗模型与用量为空**：归档时没有写入这些字段，且读的是
  `launchRun` 时的旧对象而非注册表实时快照
- **正文 + 裸 JSON 的终局声明被判 no_final_declaration**：任务要求「输出
  某行特定文字」时，子模型会把该行写在声明之前，第三条提取路径缺失；
  已补 `SubtaskDeclaration.findLastJsonObject` 并以 `status` 键做闸门，
  同时强化身份简报「JSON 之外的文字不算交付物」

另有两点已知设计取舍，非缺陷：证据账本里只要有一条失败记录，终态就降级为
`blocked/tool_failure`（即使后续重试成功）；未授予悬浮窗权限的设备上，子代理
工具调用会因权限确认走既有悬浮窗链路而失败（本次按要求未改权限系统）。
