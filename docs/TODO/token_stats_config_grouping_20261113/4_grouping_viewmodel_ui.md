# 步骤 4：分组 ViewModel 与 UI 开关

## 旧实现

ViewModel 只为 range 范围内的 identities 查配置名；模型累计排名与筛选下拉
直接使用数据层的按模型名合并结果。统计页无合并开关。

## 意图

展示名"配置名/模型名"在 ViewModel 层拼装，覆盖 lifetime、range、available
三处查询结果；开关切换分组模式并清空模型筛选（displayModelId 语义随分组
变化，保留旧选中集会产生失配）。

## 期待的新实现

- `TokenStatsUiState.modelGrouping`，进入页面时从设置加载。
- 配置名收集扩大到 lifetime + range + available 的全部 identities；
  已删除配置显示既有的"已删除配置"文案，legacy 空 configId 只显示模型名。
- `setModelGrouping(grouping)`：更新状态 → 清空 selectedModels → 持久化 → 重载。
- 开关放在范围分析卡内、模型筛选下拉旁；开启 = 合并相同模型（MERGED_MODEL），
  关闭 = 按配置名/模型（CONFIG_MODEL，默认）。
- strings 多语言目录同步补齐（values / en / es / ko / ro / ms / id / pt-rBR）。

[DONE]

