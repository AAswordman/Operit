# 05 清理测试与交付

## 旧实现情况

ModelConfigData 持有八个总结字段，ModelConfigDefaults 持有 DEFAULT_ENABLE_SUMMARY、DEFAULT_ENABLE_SUMMARY_BY_MESSAGE_COUNT、DEFAULT_SUMMARY_MESSAGE_COUNT_THRESHOLD 等常量，ModelConfigManager 提供 updateSummarySettings 与 updateSummaryDialogueReviewSettings。分段覆盖已有 FunctionalPromptsSummaryTest，但覆盖的是理想往返，未覆盖规范化与全量持久化。

## 意图修正

项目未发布，按彻底迁移处理：删除模型配置上的总结字段与相关常量、方法，不留兼容层。删除后全仓不得再出现对 ModelConfigData.summary 系列的引用。

## 期待的新实现情况

删除清单：

- ModelConfigData 的 enableSummary、summaryTokenThreshold、enableSummaryByMessageCount、summaryMessageCountThreshold、summaryCustomRules、summarySectionOverrides、enableSummaryDialogueReview、summaryDialogueReviewTitle
- ModelConfigDefaults 中 DEFAULT_ENABLE_SUMMARY、DEFAULT_SUMMARY_TOKEN_THRESHOLD、DEFAULT_ENABLE_SUMMARY_BY_MESSAGE_COUNT、DEFAULT_SUMMARY_MESSAGE_COUNT_THRESHOLD 及任何仅服务于总结的默认值
- ModelConfigManager 的 updateSummarySettings 与 updateSummaryDialogueReviewSettings
- ChatContextSettings 若在 02 步后仍残留模型配置读总结字段的分支，一并清整

保留项：

- contextLength、maxContextLength、enableMaxContextMode 留在模型配置，属于上下文容量，不属于总结配置
- ConversationSummaryConfig 与 FunctionType.SUMMARY 的模型绑定完全不动

全仓清理方式：

- 用 rg 检索 summaryCustomRules、summarySectionOverrides、enableSummaryDialogueReview、summaryDialogueReviewTitle、updateSummarySettings、updateSummaryDialogueReviewSettings、DEFAULT_SUMMARY，逐个确认无残留后删除

测试：

- test/core/config/FunctionalPromptsSummaryTest.kt 扩充 03 步用例，固定默认模板逐字一致的回归
- 新增 ContextSummarySettings 模型测试：默认值、序列化往返、绑定模式 normalize 回退
- 新增解析测试：角色卡 CUSTOM、角色卡 FOLLOW_GLOBAL、CharacterGroup、卡片字段损坏回退全局默认，四条路径
- 若 resolveSummarySettings 依赖 Android Context，测试放 app 模块依赖 DataStore 内存实现，或把解析核心抽成无 Context 依赖的纯函数后单测

文档：

- docs/doc-src 中描述上下文总结设置的章节改为角色卡绑定口径，说明全局默认的作用
- before_docing.md 的口径遵守，不用 Markdown 图表，目录用制表符树视图
- 设置项文案的 i18n 单独一个 pr，不混入代码 pr

静态自查：

- 用 rg 确认 settings_screens、services/core、api/chat/enhance、core/config 四个目录无旧字段引用
- GetDiagnostics 对改动文件过一遍，确认无未使用 import 与类型错误

## 细化作用域

- 修改 data/model/ModelConfigData.kt
- 修改 data/model/ModelConfigDefaults
- 修改 data/preferences/ModelConfigManager.kt
- 新增与修改测试文件
- 更新 docs/doc-src 相关章节

## 调试注释要求

删除字段处不留哨兵注释，删除即彻底，协作者通过 git 历史与本文档追溯。

## 实际落地记录

- ModelConfigData 删除全部八个 summary 字段，注释改为说明总结配置已迁移
- ModelConfigDefaults 删除 DEFAULT_SUMMARY 系列四个常量；模型配置 JSON 反序列化本就忽略未知键，旧数据残留键不造成问题
- ModelConfigManager 删除 updateSummarySettings 与 updateSummaryDialogueReviewSettings
- ApiConfigDelegate 删除镜像模型配置的四个旧 StateFlow 及 updateStateFromConfig 中的对应赋值
- ToolResultDataClasses 的 ModelConfigResultItem 删除四个 summary 字段，StandardSoftwareSettingsModifyTools 的填充点同步移除
- 解析核心抽为无 Context 依赖的纯函数 SummarySettingsResolver，含 usesCardSettings 谓词与 resolve；ApiConfigDelegate 的流实现与命令式入口、快速设置栏写入全部改调该谓词，判定只有一处定义
- 新增 SummarySettingsResolverTest 五个用例：CUSTOM 用卡内、FOLLOW_GLOBAL 用全局、未知绑定模式回退、群聊即使用卡也不生效、卡读取失败回退
- ContextSummarySettingsTest 随别名删除移除对应的别名一致性用例，保留默认值、序列化往返、绑定模式与默认相等性用例
- 新增 docs/doc-src/architecture/context_summary_character_card_binding.md，说明迁移原因、配置归属、解析链路、界面用法与相关代码位置
- FunctionalPromptsSummaryTest 在 03 步已扩充到 12 个用例；rg 全仓确认无 ModelConfigData 总结字段、无 updateSummarySettings 残留；GetDiagnostics 对全部改动文件零告警

[DONE]
