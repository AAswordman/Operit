---
title: ChatInfo 只读群成员名单
status: completed
repository: https://github.com/yizhen47/Operit
issue: https://github.com/AAswordman/Operit/issues/1442
---

# ChatInfo 只读群成员名单

插件此前只能读取 characterGroupId，无法直接取得群成员。为 ChatInfo 增加 memberCardIds，从当前角色组配置读取，并按群内顺序返回。

作用域包括会话列表、会话查找、结构化结果、TypeScript 声明及聊天模块文档。现有参数、角色绑定和持久化结构保持可用。

实施与验证记录见 [1_api.md](1_api.md)。
