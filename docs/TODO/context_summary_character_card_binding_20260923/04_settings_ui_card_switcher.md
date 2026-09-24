# 04 设置界面角色卡切换

## 旧实现情况

ContextSummarySettingsScreen 通过全局 FunctionType.CHAT 映射定位 currentConfig: ModelConfigData，全部总结输入框从该 config 派生，自动保存经 ModelConfigManager.updateSummarySettings 与 updateSummaryDialogueReviewSettings 写回模型配置。界面顶部有一张 BoundModelConfigCard 展示当前绑定的模型配置名。ContextSummarySettingsSection 在 ModelConfigScreen 上还有一份入口。

## 意图修正

界面顶部新增角色卡选择器，用户在设置页直接切换不同角色卡并修改该卡的专用总结提示词；全局默认项作为兜底也可编辑。上下文长度相关设置按原有方式继续绑定模型，不在本次改动范围内。ModelConfigScreen 的总结入口整体移除，避免同一配置两个不一致的编辑面。

## 期待的新实现情况

数据结构：

- selectedTarget 表示当前编辑对象，取角色卡 id 或表示全局默认的标记
- 读写统一为 flow：选中角色卡时 collect characterCardManager.getCharacterCardFlow(id) 的 summary 字段，选中全局默认时 collect preferencesManager.globalContextSummaryFlow

角色卡选择器 UI：

- 位置在顶部说明横幅之下、上下文设置区块之上，替代现有 BoundModelConfigCard 的角色，BoundModelConfigCard 保留但只服务于上下文长度设置
- 列表内容为全局默认项加全部角色卡，项上显示名称，当前活跃角色卡做标记
- 切换角色卡时全部总结输入态随新目标重置，沿用现有 remember(target) 的模式

自动保存适配：

- ContextSummaryAutoSaveEffects、ContextSummarySectionsAutoSaveEffect、ContextSummaryDialogueReviewAutoSaveEffect、ContextSummaryCustomRulesAutoSaveEffect 的写入目标改为选中存储
- 角色卡写入走 CharacterCardManager 的更新方法，建议新增 updateCharacterCardSummarySettings(cardId, settings) 一次性写入整个 ContextSummarySettings，避免逐字段更新产生中间态
- 全局默认写入走 saveGlobalContextSettings

文案：

- 新增角色卡选择器标题与说明、全局默认项名称两组字符串资源，默认语言为中文，其余语言补默认回退

ModelConfigScreen：

- 删除 ContextSummarySettingsSection 函数与其调用点，相关 import 一并清理

## 细化作用域

- 修改 ui/features/settings/screens/ContextSummarySettingsScreen.kt
- 修改 data/preferences/CharacterCardManager.kt，新增按卡更新总结配置的方法
- 修改 ui/features/settings/screens/ModelConfigScreen.kt，移除总结区块
- 新增 strings 文案，默认语言 resources values 下增加，其余语言按需补

## 调试注释要求

在角色卡选择器与自动保存写入处写注释，说明写回目标为角色卡或全局默认，不再经过模型配置，与 02 步的解析入口对应。

## 实际落地记录

- 界面新增目标状态：selectedCardId 为空表示全局默认，否则角色卡 id，初始定位到当前会话的角色卡；summaryTarget 经 produceState 加载目标配置，角色卡跟随全局时展示全局配置且 editable 为 false
- SummaryTargetSelectorCard 与 SummaryTargetOptionRow 落地在总结设置标题之下，弹窗列出全局默认与全部角色卡，当前会话的角色卡带标记
- 角色卡目标额外展示"使用专属总结配置"开关，经 CharacterCardManager.updateCharacterCardSummaryBinding 切换绑定模式；关闭时下方编辑区整体禁用，提示跟随全局
- 四个旧 autosave effect 合并为单个 SummarySettingsAutoSaveEffect：任一输入变化后把全部输入组装成完整 ContextSummarySettings，经 onSave 一次性写入当前归属目标；校验规则与原阈值、条数校验一致，关闭总结时保留已存阈值
- 新增 CharacterCardManager.updateCharacterCardSummaryBinding，绑定模式切换不走界面拼键
- 上下文长度设置按原有方式继续绑定模型，BoundModelConfigCard 保留
- ModelConfigScreen 的 ContextSummarySettingsSection 函数与调用点整体移除，上下文长度编辑在上下文和总结设置页仍可进行，功能不丢失
- 文案新增七条默认语言字符串，目标说明与页面横幅同步改写为角色卡归属口径；其余语言走默认回退，i18n 单独处理
- GetDiagnostics 对 ContextSummarySettingsScreen、ModelConfigScreen、CharacterCardManager 零告警；rg 确认 ModelConfigScreen 无 ContextSummary 残留

[DONE]
