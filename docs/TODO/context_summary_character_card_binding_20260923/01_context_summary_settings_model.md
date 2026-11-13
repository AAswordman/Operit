# 01 总结配置聚合

## 旧实现情况

ModelConfigData 直接持有 summary 相关字段：enableSummary、summaryTokenThreshold、enableSummaryByMessageCount、summaryMessageCountThreshold、summaryCustomRules、summarySectionOverrides、enableSummaryDialogueReview、summaryDialogueReviewTitle，默认值集中在 ModelConfigDefaults 的 DEFAULT_SUMMARY 系列常量。角色卡侧已有 chatModelBindingMode 与 memoryProfileBindingMode 的绑定模式加固定 id 的成熟模式，DataStore 键形如 character_card_{id}_chat_model_binding_mode，复杂配置以 JSON 字符串存储，如 toolAccessConfigKey。

## 意图修正

把散落在 ModelConfigData 上的总结字段收拢成一个可序列化的聚合类型，让同一份配置既能在角色卡上存储，也能作为全局默认单独存储。角色卡侧完全复用已有的绑定模式模式。

## 期待的新实现情况

在 data/model 下新增 ContextSummarySettings，字段与迁移来源如下：

- enableSummary、summaryTokenThreshold、enableSummaryByMessageCount、summaryMessageCountThreshold
- summaryCustomRules
- summarySectionOverrides，类型 List<SummarySectionOverride>，该类型已是 @Serializable
- dialogueReviewEnabled、dialogueReviewTitle

默认值从 ModelConfigDefaults.DEFAULT_SUMMARY 系列常量迁到 ContextSummarySettings.Companion，迁移完成后 ModelConfigDefaults 不再持有总结常量。

CharacterCard 增加：

- summaryBindingMode: String，默认 CharacterCardSummaryBindingMode.FOLLOW_GLOBAL
- summary: ContextSummarySettings，默认 ContextSummarySettings()

新增 CharacterCardSummaryBindingMode 对象，含 FOLLOW_GLOBAL、CUSTOM 两个常量与 normalize 方法，行为与 CharacterCardChatModelBindingMode.normalize 一致，无法识别的值回退为 FOLLOW_GLOBAL。

CharacterCardManager 需要改动的触点：

- setupDefaultCharacterCard 写入默认值与空 JSON 键
- getCharacterCardFromPreferences 读取新键并 normalize
- updateCharacterCard 写回新键，参考 chatModelBindingMode、memoryProfileBindingMode 现有写法
- 若存在按 id 复制绑定关系的 cloneBindingsFromCharacterCard 链路，确认 summary 配置是否需要一并复制，暂定不复制，与新建角色卡使用默认值的语义保持一致

导出与导入：

- OperitCharacterCardPayload 增加 summaryBindingMode 与 summary 字段
- OperitTavernExtension 的导入导出映射补上这两个字段，确保专属总结提示词随卡带走

全局默认配置：

- UserPreferencesManager 新增 globalContextSummaryFlow 与 saveGlobalContextSummary，持久化形式与角色卡一致，使用单份 JSON 字符串键
- 默认值取 ContextSummarySettings()，即与当前 ModelConfigDefaults 行为一致：总结开启、默认阈值、按条数开启、默认条数阈值

CRUD 工具侧检查：

- StandardSoftwareSettingsModifyTools 中按字段读写角色卡的工具需要决定是否暴露 summaryBindingMode 与 summary，本次先不暴露，保持工具面不变

## 细化作用域

- 新增 data/model/ContextSummarySettings.kt
- 修改 data/model/CharacterCard.kt，新增绑定模式对象与两个字段
- 修改 data/preferences/CharacterCardManager.kt 的 setupDefaultCharacterCard、getCharacterCardFromPreferences、updateCharacterCard、导入导出映射
- 修改 data/preferences/UserPreferencesManager.kt，新增全局默认 flow 与保存方法

## 调试注释要求

在 CharacterCard 新字段与 CharacterCardManager 新键处补简短注释，说明该配置替代了原 ModelConfigData.summary 系列字段，便于协作者按 git 历史定位迁移。

## 实际落地记录

- 新增 data/model/ContextSummarySettings.kt，字段与默认值一次到位，默认常量收在 companion
- ModelConfigDefaults 的四个总结常量改为别名指向 ContextSummarySettings，保证 01 到 05 步之间只有一份默认值，05 步删除这些别名
- CharacterCard 新增 summaryBindingMode 与 summary 两个字段，新增 CharacterCardSummaryBindingMode 对象，normalize 只认 CUSTOM
- OperitCharacterCardPayload 增加对应两个字段，酒馆卡导入导出即携带总结配置
- CharacterCardManager 的持久化触点共七处：setupDefaultCharacterCard、getCharacterCardFromPreferences、createCharacterCardLocked、updateCharacterCard、upsertCharacterCardWithIdLocked、deleteCharacterCardLocked 的键清理、备份导入的 CharacterCardImportPayload
- parseSummarySettings 与 writeSummarySettings 复用 toolAccessConfig 的 JSON 键模式，默认配置不落盘，解析失败回退默认值
- UserPreferencesManager 新增 globalContextSummaryFlow 与 saveGlobalContextSummary，键为 global_context_summary_settings，缺失或损坏回退默认值
- 克隆角色卡时不复制总结配置，与新建角色卡使用默认值的语义一致
- 工具面 StandardSoftwareSettingsModifyTools 不暴露总结字段，工具本次不动
- 新增 ContextSummarySettingsTest，覆盖默认值、别名一致性、序列化往返、绑定模式 normalize、默认相等性
- GetDiagnostics 对五个改动文件零告警

[DONE]
