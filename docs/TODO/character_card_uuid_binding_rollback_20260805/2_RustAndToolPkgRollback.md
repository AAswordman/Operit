---
title: Rust 与 ToolPkg 类型回退
status: complete
---

# Rust 与 ToolPkg 类型回退

## 旧实现

assistance2 的 Rust 聊天模型、SQLite `23 -> 24` 迁移、运行时绑定链和 ToolPkg SDK 输出同步加入了单角色卡 `character_card_id`。

## 回退内容

- 删除 Rust 聊天模型、SQLite 和绑定链中的单角色卡 ID
- 删除 ToolPkg 结果及 TypeScript 类型中的该字段
- 恢复按 `character_card_name` 的单角色卡聊天语义

[DONE]
