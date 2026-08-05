---
title: 原生角色卡管理工具
status: complete
---

# 原生角色卡管理工具

## 旧实现

`StandardSoftwareSettingsModifyTools` 已承载软件设置操作，但没有使用 `CharacterCardManager`。角色卡只由设置 UI 和 `StandardChatManagerTool.listCharacterCards` 使用。

## 修改意图

在 `StandardSoftwareSettingsModifyTools` 中直接调用 `CharacterCardManager`，并由 `ToolRegistration` 注册对应原生工具。新增结构化结果类型，完整传递角色卡的配置字段和当前活跃角色卡 ID。

## API 契约

- `list_character_cards_settings` 返回完整角色卡列表和活跃角色卡 ID
- `get_character_card` 返回指定角色卡
- `create_character_card` 只接受可编辑字段，返回新角色卡
- `update_character_card` 合并指定可编辑字段，返回更新后的角色卡
- `delete_character_card` 删除非默认角色卡，返回被删除的 ID
- `set_active_character_card` 和 `clear_active_character_card` 返回当前活跃角色卡 ID
- `import_character_card_from_tavern_json` 与 `export_character_card_to_tavern_json` 使用既有 Tavern 格式转换

角色卡工具使用既有管理器，确保主题、Waifu 设置、自定义表情、聊天绑定和提示词标签的副作用仍由单一实现处理。

[DONE]
