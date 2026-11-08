---
title: 文件工具强化（描述规范化/32MB 上限/编辑引擎重构/工作区防护/大模型审查）
repo: https://github.com/luojiaping/Operit
upstream: https://github.com/AAswordman/Operit
status: impl-complete-pending-compile
---

# 文件工具强化

## 原本状况

静态审计确认的失败机制：提示词与实现不对齐（32KB 截断未文档化、行号前缀污染编辑匹配、environment 支持矩阵全面失实）；edit_file/apply_file 模糊匹配缺陷（无精确快路径、0.9 相似度多候选不拒绝而静默取最优、报错指向不存在的工具名），失败率高导致模型改用终端脚本改文件；read_file_binary 内容到不了模型、read_file_part 无二进制门禁、特殊文件提取失败报成功；工作区零防护（任意绝对路径直接执行，删除无危险路径底线）；无工具审查机制。

## 意图

- A 描述规范化：每个文件工具补充 environment 真实矩阵 / 成功返回格式 / 失败类别与建议动作 / 工具分工；修正误导性文案与报错
- B 读取上限：32MB 硬上限（超出引导 read_file_part 分段），read_file_part 流式化，读取类健壮性修复
- C 编辑引擎：精确匹配快路径 + 多候选拒绝 + 行号前缀剥离 + occurrence 参数 + 匹配行号回传
- D 工作区防护：区内按既有权限模式；区外 ALLOW 强制弹窗、FORBID 直接拒绝；未绑定工作区一律按区外；delete_file 危险路径硬底线；JS 包嵌套调用 AIToolHook 兜底
- E 大模型审查：FunctionType.TOOL_REVIEW + 功能模型配置 + 会话工具菜单自动审查三开关（区内/区外文件 + 高危工具，仅允许模式显示）+ 审查流（合规直接执行 / 不合规弹窗用户决定 / 审查失败弹窗提示错误）
- G 高危工具审查：审查范围从文件工具族泛化为 HighRiskToolCatalog 高危工具调用（核心破坏集 18 个），三开关之「自动审查高危工具」独立管辖
- F 环境矩阵：repo: 环境 clean guard、linux 编辑实装、grep linux 路径映射、SAF 白名单报错、禁用包报错修正

## 期待结果

- 文件工具首次成功率与自纠错率显著提升（描述可自解释、报错可操作）
- 工作区外/未绑定工作区的文件操作全部经用户确认或审查把关，禁止模式直接拒绝
- edit_file 不再需要模型改用终端脚本兜底

## 作用域

- `core/config/SystemToolPrompts.kt`、`core/config/SystemToolPromptsInternal.kt`
- `core/tools/ToolExecutionLimits.kt`、`core/tools/ToolResultDataClasses.kt`、`core/tools/ToolRegistration.kt`、`core/tools/AIToolHandler.kt`
- `core/tools/defaultTool/standard/StandardFileSystemTools.kt`、`SafFileSystemTools.kt`、`LinuxFileSystemTools.kt`、`defaultTool/PathValidator.kt`
- `api/chat/enhance/FileBindingService.kt`、`api/chat/enhance/ToolExecutionManager.kt`、`api/chat/enhance/ConversationMarkupManager.kt`、`api/chat/EnhancedAIService.kt`
- `data/model/FunctionType.kt`、`data/preferences/ApiPreferences.kt`、`services/core/ApiConfigDelegate.kt`
- `ui/features/settings/screens/FunctionalConfigScreen.kt`、`ui/permissions/ToolPermissionSystem.kt`、`ui/permissions/PermissionRequestOverlay.kt`
- `ui/features/chat/components/style/input/agent/AgentChatInputSection.kt`、`.../classic/ClassicChatSettingsBar.kt`、`ui/features/chat/screens/AIChatScreen.kt`
- `assets/packages/extended_file_tools.js`、`examples/types/files.d.ts`
- 新增：`core/tools/WorkspacePathChecker.kt`、`core/tools/WorkspaceFileToolHook`（AIToolHook 实现）、`core/config/FunctionalPrompts.kt` 审查 prompt、FileBindingService 单测、WorkspacePathChecker 单测

## 行为决策（用户确认）

- 审查不合规 → 弹窗展示审查理由，用户决定放行与否
- 审查调用失败 → 弹出原询问窗口并在窗口提示错误信息
- 审查通过 → 直接执行不弹窗（区内区外一致）
- 未绑定工作区 → 所有路径按工作区外对待

## 步骤

- [01-描述规范化.md](01-描述规范化.md)
- [02-读取上限与健壮性.md](02-读取上限与健壮性.md)
- [03-编辑引擎重构.md](03-编辑引擎重构.md)
- [04-工作区防护.md](04-工作区防护.md)
- [05-大模型审查.md](05-大模型审查.md)
- [06-环境矩阵与扩展通道.md](06-环境矩阵与扩展通道.md)
- [07-高危工具审查.md](07-高危工具审查.md)

## 验证

- 用户运行 `:app:compileDebugKotlin` 迭代修复
- 新增 JUnit：FileBindingService 匹配器五组用例、WorkspacePathChecker（含无工作区用例）
- 手工回归矩阵：区内/区外/无工作区 × 允许/询问/禁止 × 审查开/关 × 合规/不合规/失败
- 手工回归矩阵（高危工具）：execute_shell 等核心破坏集 × 允许 × 高危开关开/关 × 合规/不合规/失败；三开关互相独立（仅开高危开关不影响文件工具区外强制弹窗）
- 手工用例：32MB 大文件读取、read_file_part 分页、edit_file 行号粘贴/多候选/occurrence、扩展包 file_exists、禁用包报错、repo: guard
