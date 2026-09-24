---
fork: https://github.com/AAswordman/Operit
---

# 上下文总结绑定角色卡

## 原状

上下文总结配置挂在 ModelConfigData 上，包括总结总开关、token 阈值、按消息条数阈值、自定义总结规则、分段覆盖、对话回顾开关与标题。这些配置的读取存在两条不同链路：设置界面与 readSummaryConfig 固定取全局 FunctionType.CHAT 映射的模型配置；触发阈值 resolveChatContextSettings 则取 effectiveChatConfigId，角色卡固定对话模型时会切到角色卡绑定的配置。两条链路不同源，界面改写的配置运行时不一定生效，切换角色卡也不会改变总结行为。

另有一个持久化层面的问题。buildSummarySectionOverrides 只持久化与默认值不同的字段，且 buildSummarySystemPrompt 仅在 overrides 非空时才应用覆盖。默认指令文本由 block.substringAfter('\n').trimEnd() 提取，设置界面回填文本与之比较时，任何空白差异都会让用户改过的指令被判定为与默认相同而丢弃；关闭全部分段后 overrides 为空，覆盖同样不被应用。

## 意图

让角色卡成为上下文总结配置的权威来源。每张角色卡独立持有总结开关、阈值、自定义规则、分段覆盖和对话回顾配置，另设一份全局默认配置兜底没有角色卡或角色卡跟随全局的场景。设置界面、运行时提示词读取、触发判断三条链路解析同一份配置，从结构上消除不同源导致的失效。分段覆盖改为规范化后的全量持久化，消除空白差异吞掉用户修改的问题。

总结使用的模型不在本次范围内，仍走全局 FunctionType.SUMMARY 映射。

## 作用域

- 新增 ContextSummarySettings 聚合，承载全部总结配置字段
- CharacterCard 增加 summaryBindingMode 与 summary 字段，DataStore 键与导出 payload 同步携带
- 全局默认 ContextSummarySettings 落到 UserPreferencesManager，提供 flow 与保存入口
- 新增统一的 resolveSummarySettings 解析，运行时提示词读取、触发阈值、设置界面写回共用同一来源
- FunctionalPrompts 分段覆盖改为规范化全量持久化，修复开关失效与修改不生效
- ContextSummarySettingsScreen 顶部增加角色卡选择器，写回对应存储
- 删除 ModelConfigData 与 ModelConfigManager 中的总结相关字段与方法，移除 ModelConfigScreen 的入口
- 单元测试、文案与文档

## 验收

- 每张角色卡可以独立配置总结开关、token 阈值、按消息条数阈值、总结提示词，互不影响
- 角色卡为 CUSTOM 时用卡内配置，FOLLOW_GLOBAL 与无角色卡会话用全局默认配置
- 关闭某一段总结提示词后，生成的总结提示词不再包含该段
- 只修改某一段的标题或指令，生成的总结提示词反映修改，其余段落保持默认
- 关闭全部分段或禁用对话回顾时，生成的提示词中对应内容消失
- 切换角色卡后，本轮会话的总结行为按新角色卡配置生效
- 导出再导入角色卡后，专属总结配置保留
- 无角色卡场景按全局默认配置触发总结，行为与当前默认一致
- ModelConfigData 中不再存在总结相关字段，全仓无残留引用

## Pull Request

Target branch: dev

步骤：

- [01 总结配置聚合](01_context_summary_settings_model.md)
- [02 统一解析与运行时接线](02_unified_resolution_and_runtime.md)
- [03 分段提示词持久化修复](03_prompt_persistence_bugfix.md)
- [04 设置界面角色卡切换](04_settings_ui_card_switcher.md)
- [05 清理测试与交付](05_cleanup_tests_and_docs.md)
- [06 全部分段关闭时的自由格式提示词](06_all_sections_disabled_freeform_prompt.md)
- [07 插入总结与自动总结同路径](07_insert_summary_shares_auto_summary_path.md)
- [08 插入总结保留 previousSummary](08_insert_summary_preserves_previous_summary.md)
