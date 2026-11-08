---
fork_repository: https://github.com/tuxKOH/Operit
status: completed
---

# World Book Group Merge

将 `com.operit.worldbook_plus` 的分组能力合并回稳定包
`com.operit.worldbook`。

原有世界书只能以平铺列表管理条目，条目可单独绑定一个角色卡。合并后，
用户可将条目整理进分组、复制条目到分组，并在分组界面选择多个角色卡。

包 ID 保持为 `com.operit.worldbook`，版本从 `1.1.0` 升至 `1.2.0`，使已安装
的稳定包能够原地更新并继续使用已有的 `entries.json` 数据。

作用域：

- `examples/worldbook/` 的源码、编译产物与包清单
- 分组的持久化、服务接口、管理界面与中英文文本
- 本 TODO 的变更记录

分组的角色卡选择仅用于组织和展示；条目激活仍由条目自身的
`character_card_id` 控制，不能将该界面字段描述为激活规则。

参见 [1_MergePackageImplementation.md](1_MergePackageImplementation.md)。

[DONE] 稳定包已合并分组实现，并保留 `com.operit.worldbook` 作为升级目标。
