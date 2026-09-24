# 上下文总结按角色卡绑定

## 为什么改

上下文总结的开关、阈值、总结提示词过去挂在模型配置上。这些字段有三条读取链路：设置页面固定读全局 CHAT 功能映射的模型配置，提示词生成同样固定读全局映射，而触发阈值走的是 effectiveChatConfigId，角色卡固定对话模型时会切到另一份配置。三条链路不同源，用户在设置页的修改可能落在运行时根本不读的那份配置上，切换角色卡也不会改变总结行为。

现在总结配置改由角色卡持有，另设一份全局默认兜底。同一份配置只有一种解析路径，读写不会错位。

## 配置长在哪里

总结配置收敛为 ContextSummarySettings 一个聚合，字段包括总开关、token 阈值、按消息条数阈值、自定义总结规则、分段覆盖、对话回顾开关与标题。

每张角色卡持有两个字段：summaryBindingMode 与 summary。绑定模式只有两种取值，CUSTOM 表示该卡使用专属配置，FOLLOW_GLOBAL 表示使用全局默认。全局默认存在 UserPreferencesManager 的单份配置里，供没有角色卡的会话与跟随全局的角色卡使用。角色卡的总结配置随 OperitCharacterCardPayload 导入导出，换设备不会丢失。

迁移按彻底处理：ModelConfigData 上的 summary 系列字段、ModelConfigDefaults 的对应默认常量、ModelConfigManager 的 updateSummarySettings 与 updateSummaryDialogueReviewSettings、ApiConfigDelegate 中镜像模型配置的旧 StateFlow、模型配置页里的总结入口全部删除，不留兼容层。模型配置 JSON 反序列化本身忽略未知键，旧数据里的残留键不会造成问题。

## 怎么解析

判定收在 SummarySettingsResolver 纯函数里：角色卡 CUSTOM 用卡内 summary，FOLLOW_GLOBAL、群聊会话与角色卡读取失败用全局默认。ApiConfigDelegate 的 resolveSummarySettings 命令式入口与 effectiveSummarySettings 响应式 StateFlow 共用同一个流实现和同一个判定函数，界面展示与运行时读取不可能再拆成两个来源。

聊天快速设置栏的阈值编辑经 updateActiveSummarySettings 写回当前生效的那份配置，与解析入口互为逆操作。

生成链路为：readSummaryConfig 取解析结果转成 ConversationSummaryConfig，FunctionalPrompts.buildSummarySystemPrompt 拼出最终提示词。分段覆盖按规范化后的全量持久化：enabled 恒写入，title 与 instruction 去首尾空白后与默认一致写 null，用户清空的指令以空串原样持久化。渲染侧对 title 与 instruction 全为 null 的分段直接拼接模板原文，因此未编辑过的提示词与默认模板逐字一致。

## 界面怎么用

上下文和总结设置页顶部是总结配置归属选择器，列出全局默认与全部角色卡，当前会话的角色卡带标记，初始定位到当前会话的角色卡。选中角色卡后出现专属总结配置开关：开启才可编辑，关闭时编辑区整体禁用并展示全局配置，避免误以为在改卡内配置。任一输入变化后界面把全部输入组装成完整的 ContextSummarySettings 一次性写入目标存储，不再按字段分片读写，也就消除了多个自动保存相互覆盖的问题。

上下文长度设置仍绑定模型，不在本次范围内。

## 相关代码

- 解析核心 `data/model/SummarySettingsResolver.kt`
- 运行时入口 `services/core/ApiConfigDelegate.kt` 的 resolveSummarySettings 与 effectiveSummarySettings
- 提示词拼装 `core/config/FunctionalPrompts.kt` 的 buildSummarySystemPrompt
- 提示词读取 `services/core/MessageCoordinationDelegate.kt` 的 readSummaryConfig
- 设置界面 `ui/features/settings/screens/ContextSummarySettingsScreen.kt`
- 角色卡持久化 `data/preferences/CharacterCardManager.kt`
