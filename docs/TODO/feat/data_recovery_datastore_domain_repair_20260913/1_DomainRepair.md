# DataStore 领域修复

## 旧实现

`PreferencesHealthManager` 只处理 `CorruptionException`。逻辑损坏不会进入修复计划。

## 修改意图

- 可读副本上运行幂等领域校验
- 修复顺序：`model_configs` → `user_preferences` → `functional_configs` → `character_cards` → `character_groups`，`api_settings` 独立
- 校验不收敛时停止写入并保留原件 ZIP
- 未知未来 JSON 字段在记录仍可用时保留

## 期待结果

用户在数据救援里对可读但逻辑损坏的 DataStore 执行与文件重置同一入口的修复，且不进入应用启动链路。
