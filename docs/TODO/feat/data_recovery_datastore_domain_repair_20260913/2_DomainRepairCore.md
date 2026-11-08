```md
# DataStore 领域修复核心

## 核心函数

`repairPreferenceState` — 原子提交修复结果，记录损坏 key，quarantine 原件。

## 校验器

- `mergeNormalizedJsonFields` — 保留未知未来字段，数组形状不一致时用归一化数组
- `normalizeSafBookmarks` — 保留有效 SAF bookmark，丢弃 JSON 损坏的
- `getValidModelIndex` — 处理模型名称逗号分隔越界索引
- `CharacterCardToolAccessConfig.normalized` — 去重和 trim
- `CharacterCardMemoryProfileBindingMode.normalize` — 非法模式降级

## 校验顺序

1. `model_configs` — 模型 ID 存在性、modelIndex 越界、customParameters JSON 解析
2. `user_preferences` — 记忆空间列表、active ID 有效性、memory_space_xxx 字段
3. `functional_configs` — 功能映射回 default
4. `character_cards` — 角色卡列表有效性、modelIndex 有效性、toolAccessConfig JSON
5. `character_groups` — 群组列表、成员有效性、orderIndex 排序
6. `api_settings` — SAF bookmark JSON 合法性、featureToggle JSON 合法性、tool prompt order

## 保全与退出

校验失败时不写入，保留原件 ZIP，修复计划记录失败 key。

[DONE]
```