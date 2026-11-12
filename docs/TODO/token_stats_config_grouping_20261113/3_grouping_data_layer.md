# 步骤 3：分组数据层与持久化

## 旧实现

`TokenStatsQueryService.buildDisplayModels` 用 `displayModelIdFor(row.model)`
把聚合行按模型名合并，不同模型配置（渠道）的同名模型 token 混在一起。
`TokenStatsQueryParams` 无分组参数；`TokenStatsPreferences` 无分组持久化。

## 意图

把分组键做成查询参数，默认按"模型配置 + 模型"划分，保留按模型名合并的
旧行为作为可切换选项。配置名的拼装留在 ViewModel 层，数据层只负责分组。

## 期待的新实现

- `TokenStatsGrouping` 枚举：CONFIG_MODEL（默认）/ MERGED_MODEL。
- `TokenStatsQueryParams.grouping` 透传到 lifetimeOverview 与 rangeData。
- CONFIG_MODEL 键 = `config:{configId}|model:{model小写}`，组内 identities
  共享同一 configId，breakdown 暴露该 configId；legacy 导入记录 configId
  为空字符串，同样成立。
- MERGED_MODEL 维持现有键与行为。
- `buildDisplayModels` 提为 internal，入参均为纯数据类，可直接单测。
- `TokenStatsPreferences` 增加 model_grouping 键，SettingsStore 接口同步。

[DONE]

