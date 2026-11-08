# 迁移与运行时契约

## 旧数据来源

旧数据位于 `speech_services_preferences` DataStore。TTS 的服务类型、HTTP 配置、VITS 配置、清理规则、语速和音调分别读取；STT 读取服务类型和 HTTP 配置。缺少旧键时使用现有应用默认值，保证新安装用户仍然拥有系统 TTS 和本地 Sherpa STT 档案。

## 新数据形状

新存储使用单独的 `speech_service_profiles` DataStore：

- TTS 档案列表和当前档案 ID
- STT 档案列表和当前档案 ID
- 迁移版本标记

每个档案以 JSON 保存，配置字段继续复用现有 Provider 所理解的结构，避免在迁移中重写供应商协议。

## 生命周期

读取、创建、更新、删除和切换档案前执行迁移检查。迁移完成后设置版本标记。当前档案不能删除；切换和保存会更新旧偏好投影并重置对应服务实例。

[DONE]
