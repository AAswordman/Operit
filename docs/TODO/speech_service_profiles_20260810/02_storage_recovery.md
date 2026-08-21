# 存储恢复与运行时单一事实源

Status: done

## 版本边界

独立语音档案尚未发布，因此已删除开发中间方案的读时迁移和活动档案双写。正式版的 `speech_services_preferences` 只在启动 recovery gate 检测到新格式尚未建立时作为一次性迁移输入；格式版本写入后，运行时只读取和修改 `speech_service_profiles`。

## 已完成实现

- `speech_service_profiles` 已注册为 recoverable Preferences DataStore 的唯一 owner，并纳入已知存储物理预检和 checkpoint。
- 启动协调器先执行 Preferences 物理预检，再执行一次性正式版旧配置迁移和新档案逻辑修复。
- Flow 不再通过 `onStart` 写存储；创建、更新和切换档案不再投影到旧存储。
- TTS/STT 工厂及 Provider 只使用当前档案快照；默认语速和音调通过 Factory 注入，不再运行时读取旧存储。
- 正常初始化与迁移不标记 corruption；实际逻辑损坏才触发 quarantine 和修复事件。
- 已覆盖损坏 JSON、非法档案字段、重复 ID、悬空当前 ID、无效有限数值、HTTP 方法、响应管线及清理正则的规范化。
- 修复保留未知 Preferences key、档案字段和嵌套配置字段，并在提交前验证第二次运行已经收敛。
- 已增加迁移、正常初始化、逻辑收敛、逐项救回、未知字段保留和物理恢复测试。

## 恢复顺序

1. recovery gate 对 `speech_service_profiles` 执行物理预检。protobuf 损坏时先隔离原文件，再验证双恢复槽的 envelope、单调序号和 SHA-256，恢复最新有效槽。
2. 没有有效物理槽时建立可读的空 schema；逻辑层随后建立完整 TTS/STT 域。
3. 格式版本尚未建立时，先修复正式版旧 store，再一次性读取迁移种子；新安装则使用默认种子。
4. 对新 store 的列表、当前 ID 和格式版本统一规范化。正常初始化只提交状态，不报告损坏。
5. 确认存在真实逻辑问题时，先 quarantine 完整的修复前 Preferences，再在 DataStore actor 内原子提交修复结果。

## 逻辑救回边界

- `tts_profiles` 与 `stt_profiles` 分域处理。某个根 JSON 无法解析为数组时只重建该域，不破坏另一个域。
- 根数组合法时逐项解析；合法档案保留，已知坏字段就地规范化，无法救回的单项不阻塞其余档案。
- 当前 ID 必须指向同类现存档案；重复或空 ID 使用稳定规则修正。
- 未知字段在正常保存和修复时合并回原始 JSON，允许未来版本扩展字段穿过旧版本。
- 修复函数幂等并在提交前验证收敛；正常初始化不会生成逻辑损坏 quarantine。

## 验收

- 新安装和正式版升级都生成至少一个有效 TTS 和 STT 档案
- 任意单个损坏档案不会阻止应用启动或破坏其他合法档案
- 当前档案 ID 始终指向同类现存档案
- 语速和音调必须是 `0.5..2.0` 内的有限数值
- HTTP 方法、响应管线和清理正则在写入和恢复时使用同一套规则
- 新档案存储具备物理双槽、quarantine、逻辑修复事件和 raw restore 重绑定
- 新档案更新后不再修改正式版旧存储

[DONE]
