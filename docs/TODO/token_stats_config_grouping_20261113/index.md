---
working_branch: feat/token-stats-grouping
---

# Token 统计按模型配置分组 + 流式 usage 落库修复

## 背景

应用内排查报告（GLM-5.2 未统计）确认了一条根因链：OPENAI_GENERIC 流式请求不携带
`stream_options.include_usage`，严格端点不回 usage 分块，`applyUsageToCounters`
静默 early-return，`RequestTracker.finish()` 拿不到成功 attempt 的快照返回 null，
落库被静默跳过且无任何日志。标题/总结等非流式辅助请求走固定模型索引，导致只承担
主对话流式的模型（如 glm-5.2）永远没有记录，而常驻功能位的模型持续积累记录，
形成"部分模型统计不到"的观感。

同时，统计页所有模型维度展示按模型名合并（`displayModelIdFor`），不同渠道
（模型配置）的同名模型 token 混在一起。

## 意图

- 让 OPENAI_GENERIC / OPENAI_LOCAL 流式请求显式索要 usage，使严格端点的成功请求
  正常落库。
- 消除落库跳过路径的静默性：每个跳过分支记录原因，下次排查看日志即可定位。
- 统计页默认按"模型配置名/模型名"划分模型维度，提供"合并相同模型"开关，
  打开后恢复按模型名合并的旧视图。

## 范围

- `TokenTrackingAIService`：finish/persist 跳过分支加日志。
- `OpenAIProvider`：usage 解析失败加 debug 日志。
- `AIServiceFactory`：OPENAI_GENERIC / OPENAI_LOCAL 开启 includeUsageInStream。
- `TokenStatsQueryModels` / `TokenStatsQueryService`：分组参数化。
- `TokenStatsPreferences` / `TokenStatsSettingsStore`：持久化分组模式。
- `TokenUsageStatisticsViewModel`：分组状态、配置名解析、开关行为。
- `TokenStatsFilterBar` + strings 多语言：合并开关 UI。
- 单测：分组键、配置分组聚合、fake store 适配。

不改动：Room 表结构、`token_usage_records` 的 provider-confirmed 语义、
取消请求不落库的既有约定、配置详情卡（已是 configId 粒度）。

## Steps

1. [落库可观测性](1_usage_persist_observability.md)
2. [流式 usage 请求](2_generic_stream_include_usage.md)
3. [分组数据层与持久化](3_grouping_data_layer.md)
4. [分组 ViewModel 与 UI 开关](4_grouping_viewmodel_ui.md)
5. [测试与文档](5_tests_and_docs.md)
