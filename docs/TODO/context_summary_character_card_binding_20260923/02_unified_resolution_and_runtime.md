# 02 统一解析与运行时接线

## 旧实现情况

总结配置在运行时被拆成两条链路读取：

- readSummaryConfig 取全局 FunctionType.CHAT 映射的模型配置，构造 ConversationSummaryConfig，供 summarizeMemory 生成提示词使用
- resolveChatContextSettings 经 effectiveChatConfigId 取模型配置，buildChatContextSettings 从中挑出 enableSummary、summaryTokenThreshold、enableSummaryByMessageCount、summaryMessageCountThreshold，供 shouldGenerateSummary 判断阈值

effectiveChatConfigId 由 effectiveChatConfigTarget 决定，角色卡固定对话模型时切到角色卡绑定的配置。两条链路的 configId 可能不同，这就是界面改动不生效、角色卡不生效的结构性原因。

## 意图修正

新增唯一的解析入口 resolveSummarySettings，输入当前活跃角色卡，输出 ContextSummarySettings。所有运行时链路与设置界面写回都经过这一入口，不同源问题从结构上消失。

## 期待的新实现情况

解析入口放在 ApiConfigDelegate，签名 suspend fun resolveSummarySettings(): ContextSummarySettings，内部逻辑：

1. 取 activePromptManager.activePromptFlow.first()
2. 若为 ActivePrompt.CharacterCard，读取该卡的 summaryBindingMode
3. CUSTOM 返回卡内 summary 字段
4. FOLLOW_GLOBAL、CharacterGroup 或卡片读取失败时返回全局默认 globalContextSummarySettings

运行时接线：

- MessageCoordinationDelegate.readSummaryConfig 改为调用 resolveSummarySettings，把结果转成 ConversationSummaryConfig，全局规则取 summaryCustomRules，分段与对话回顾直接透传
- buildChatContextSettings 与 resolveChatContextSettings 增加解析结果入参，ChatContextSettings 中的四个总结字段改由 resolveSummarySettings 提供，不再从 ModelConfigData 读取
- ApiConfigDelegate 内已有 effectiveChatConfig 的 StateFlow 链路，注意 resolveChatContextSettings 包含 configIdOverride 分支，总结配置解析与 configIdOverride 解耦，始终按活跃角色卡解析

调用方检查：

- MessageCoordinationDelegate 中 shouldGenerateSummary 与 summarizeHistory、异步发送触发总结、群聊每轮后总结三条路径都改走新的 resolveSummarySettings 结果
- ChatViewModel 与 AIChatScreen 中凡读取模型配置总结字段用于展示或判断的调用点，改为新的解析入口

## 细化作用域

- 修改 services/core/ApiConfigDelegate.kt，新增 resolveSummarySettings 与 ChatContextSettings 构造改造
- 修改 services/core/MessageCoordinationDelegate.kt 的 readSummaryConfig 与三个触发路径
- 全仓检索 enableSummary、summaryTokenThreshold、summaryMessageCountThreshold、summaryCustomRules、summarySectionOverrides、enableSummaryDialogueReview、summaryDialogueReviewTitle 的 ModelConfigData 引用点，逐一接到新链路

## 调试注释要求

在 readSummaryConfig 与 buildChatContextSettings 处写注释，说明总结配置不再来自模型配置，来源统一为角色卡或全局默认，以及为什么必须同源，避免后续协作者再把字段读回模型配置。

## 实际落地记录

- ApiConfigDelegate 新增 resolveSummarySettings 作为唯一命令式入口，内部经 summarySettingsFlowForPrompt 实现
- 新增私有 summarySettingsFlowForPrompt，按活跃目标返回总结配置流；命令式与响应式两条解析路径共用同一实现，不会漂移
- 新增公开 effectiveSummarySettings StateFlow，effectiveSummaryTokenThreshold、effectiveEnableSummary、effectiveEnableSummaryByMessageCount、effectiveSummaryMessageCountThreshold 四个原有展示流改为从其派生，聊天快速设置栏展示值随角色卡切换即时更新
- buildChatContextSettings 增加 summarySettings 入参，ChatContextSettings 的四个总结字段改由其提供；contextIdOverride 语义不变，仍只决定对话模型
- readSummaryConfig 改走 resolveSummarySettings，MessageCoordinationDelegate 中 ModelConfigManager、FunctionalConfigManager、FunctionConfigMapping 三个 import 随之移除
- 聊天快速设置栏的四个写入方法 updateSummaryTokenThreshold、toggleEnableSummary、toggleEnableSummaryByMessageCount、updateSummaryMessageCountThreshold 收拢到 updateActiveSummarySettings：CUSTOM 卡写卡内，其余写全局默认，与解析入口互为逆操作
- CharacterCardManager 新增 updateCharacterCardSummarySettings，供上述写入与 04 步的设置界面共用
- ChatViewModel 的手动总结路径原本就调用 readSummaryConfig，自动接上新链路；ChatServiceCore 的两个展示代理本来就是 effective 变体，自动接上新链路
- 三个触发路径 shouldGenerateSummary 全部经 chatContextSettings 取值，随本次改造自动同源
- GetDiagnostics 对 ApiConfigDelegate、MessageCoordinationDelegate、CharacterCardManager、ChatViewModel 零告警；rg 确认 updateSummarySettings 仅剩两个设置界面在调用，属于 04 步范围

[DONE]
