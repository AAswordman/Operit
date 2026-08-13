# 迁移与运行时契约

## 版本边界与触发点

已发布版本的数据位于 `speech_services_preferences` DataStore；独立档案版本尚未发布，因此新存储不承担开发中间方案的读时迁移或双写兼容。

迁移只由应用启动的 storage recovery gate 触发。`RecoverablePreferenceDataStores.preflightKnownStores()` 先完成物理损坏处理，随后 `SpeechServiceProfilesPreferences.initializeAndRepair()` 检查新存储格式版本。工厂、Flow 和界面读取不会自行发起迁移。

格式版本尚未建立时：

1. 先规范化旧 `speech_services_preferences` 中可识别的损坏字段。
2. 读取旧 TTS 服务类型、HTTP/VITS 配置、清理规则、语速和音调，以及旧 STT 服务类型和 HTTP 配置。
3. 使用固定迁移档案 ID 建立 TTS/STT 档案，并同时写入当前档案 ID 和格式版本。
4. 缺少已发布配置时使用应用默认值，保证新安装仍有有效的系统 TTS 和本地 Sherpa STT 档案。

格式版本建立后不再读取旧存储，也不把新档案投影或双写回旧存储。

## 新数据形状

新存储使用单独的 `speech_service_profiles` DataStore：

- TTS 档案列表和当前档案 ID
- STT 档案列表和当前档案 ID
- 迁移版本标记

每个档案以 JSON 保存，配置字段继续复用现有 Provider 所理解的结构，避免在迁移中重写供应商协议。

## 原子性与损坏语义

新存储的 TTS/STT 列表、当前 ID 和格式版本在同一次 DataStore 原子更新中提交。正常新安装初始化和一次性迁移即使产生持久化写入，也不属于 corruption：不创建逻辑 quarantine、不记录逻辑修复事件，也不增加损坏修复计数。

实际逻辑损坏的处理遵守以下契约：

- 提交前再次运行规范化，确认结果已经收敛。
- 在替换损坏字段之前，把完整原始 Preferences 状态写入 quarantine。
- 根 JSON 不是合法数组时重建对应域；另一个可解析域和未知 Preferences key 保持不变。
- 根数组合法时逐项救回档案，单个无效元素不会清空其他合法元素。
- 已知字段按当前约束修正，未知档案字段和嵌套配置字段保留。
- 悬空当前 ID 会重新绑定到同类现存档案，确保运行时读取不需要错误分支或隐式默认值。

## 运行时契约

`speech_service_profiles` 是迁移后的唯一运行时事实源。TTS/STT 工厂、Provider 默认语速与音调、设置页、聊天朗读、悬浮窗及软件设置工具都读取当前档案。Provider 不再访问旧 DataStore。

创建档案会复制当前档案并生成 UUID；更新保留 ID 和创建时间；当前档案不能删除。切换或影响服务行为的保存会重建对应服务实例，但不会修改旧存储。

[DONE]
