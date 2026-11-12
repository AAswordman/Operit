# 步骤 5：测试与文档

## 旧实现

`TokenStatsDisplayUnitTest` 只覆盖格式化纯函数；实现 `TokenStatsSettingsStore`
的测试 fake 若存在需要适配新接口方法。分组逻辑无测试。

## 期待的新实现

- 分组单测：CONFIG_MODEL 下同模型不同配置拆分为多条、legacy 空 configId
  独立成组；MERGED_MODEL 维持合并。
- fake settings store 适配 model_grouping 读写。
- 按 docs/doc-src/before_docing.md 维护相关文档。

实现记录：分组单测落在
`app/src/test/java/com/ai/assistance/operit/data/stats/TokenStatsDisplayGroupingTest.kt`，
覆盖配置拆分、legacy 空 configId、合并口径（含模型名大小写归一）与同配置不同模型
不合并四种情形；测试未发现实现 `TokenStatsSettingsStore` 的 fake，无需适配。
doc-src 下无 token 统计功能文档，本 TODO 文件夹即本次变更文档。

[DONE]

