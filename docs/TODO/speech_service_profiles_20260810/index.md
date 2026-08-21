---
feature: speech-service-profiles
branch: feat/speech-service-profiles
status: completed
---

# 语音服务独立配置档案

## 原本状况

已发布版本把 TTS 和 STT 配置分别保存为一个当前服务类型和一份共享 HTTP 配置。切换供应商会复用同一份字段，无法同时保存多组模型、端点、密钥和音色。

## 目标

引入独立的 TTS、STT 配置档案，每个档案拥有稳定 ID、显示名称、服务参数、创建时间和更新时间。启动 storage recovery gate 在物理预检后完成一次性正式版旧配置迁移和逻辑修复；此后运行时只读取当前选中的新档案，`speech_service_profiles` 是唯一事实源。

## 迁移契约

- 旧 TTS 当前配置生成一个 TTS 档案，保留服务类型、HTTP/VITS 参数、清理规则、语速和音调。
- 旧 STT 当前配置生成一个 STT 档案，保留服务类型、端点、密钥和模型。
- 迁移使用固定档案 ID 和版本标记，只执行一次；已有档案不重复创建。
- 旧存储只作为格式版本尚未建立时的一次性输入；新档案不会反向写回旧存储。
- 正常初始化和迁移不报告 corruption；只有真实逻辑损坏才隔离原始状态并记录修复。

## 作用域

- `SpeechServiceProfilesPreferences.kt`：模型、启动迁移、逻辑自愈和档案管理。
- Preferences recovery：新存储唯一 owner、物理双槽、quarantine 和启动恢复顺序。
- TTS/STT 工厂与 Provider：只使用同一个当前档案快照，不读取旧存储中的运行时配置。
- `SpeechServicesSettingsScreen.kt`：独立档案管理卡片、选择/创建/重命名/删除和现有参数编辑。
- 迁移、逐项救回、未知字段保留、逻辑收敛和物理恢复测试。

## 步骤

1. [迁移契约与运行时接入 [DONE]](./01_migration_contract.md)
2. 设置页档案管理与模型配置页交互对齐 [DONE]
3. [存储恢复与运行时单一事实源 [DONE]](./02_storage_recovery.md)

[DONE]
