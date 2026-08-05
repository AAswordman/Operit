---
title: Kotlin 聊天绑定与数据库回退
status: complete
---

# Kotlin 聊天绑定与数据库回退

## 旧实现

Android 聊天实体、Room `20 -> 21` 迁移、DAO 查询、聊天创建与更新、HTTP 桥和 Compose 界面均使用 `characterCardId` 识别单角色卡聊天。

## 回退内容

- 删除聊天模型与 Room 中的 `characterCardId`
- 删除 `20 -> 21` 及本次新增的数据迁移代码
- 将单角色卡聊天筛选、创建、更新、分支、导入导出恢复为 `characterCardName`
- 从聊天 ToolPkg 结果、JavaScript 桥和界面状态删除该字段
- 保留 `characterGroupId` 和角色卡内部配置 ID

[DONE]
