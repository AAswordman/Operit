---
fork: https://github.com/3316891527/Operit
status: 已实现，等待仓库工作流和设备验收
---

# 角色卡操作菜单

本轮仅优化 `CharacterCardItem` 的三点弹出菜单，角色卡主体重构留待下一轮。独立 `components/character/CharacterCardActionsMenu` 使用圆角、细描边、紧凑图标底色和分隔线，删除或重置采用强调色，选择操作后先关闭菜单，再调用既有回调。

编辑、复制和导出保持原行为；非当前卡仍显示设为当前，默认卡显示重置，普通卡显示删除。原删除、重置确认继续由调用方处理，没有增加或删除角色卡业务操作。

实现 [DONE]。完整默认卡、普通卡、当前状态及关闭手势检查已并入 [完整验收清单](../data-and-backup/verification.md)，Android 视觉和操作结果待确认。
