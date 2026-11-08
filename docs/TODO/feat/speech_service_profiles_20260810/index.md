---
feature: speech-service-profiles
branch: feat/speech-service-profiles
status: done
---

# 语音服务独立配置档案

## 原本状况

已发布版本把 TTS 和 STT 配置分别保存为一个当前服务类型和一份共享 HTTP 配置。切换供应商会复用同一份字段，无法同时保存多组模型、端点、密钥和音色。

## 目标

引入独立的 TTS、STT 配置档案，每个档案拥有稳定 ID、显示名称、服务参数、创建时间和更新时间；运行时只读取当前选中的档案。首次读取时把旧偏好转换为档案，并保留旧偏好接口作为迁移与已存在 Provider 的兼容投影。

## 迁移契约

- 旧 TTS 当前配置生成一个 TTS 档案，保留服务类型、HTTP/VITS 参数、清理规则、语速和音调。
- 旧 STT 当前配置生成一个 STT 档案，保留服务类型、端点、密钥和模型。
- 迁移使用固定档案 ID 和版本标记，只执行一次；已有档案不重复创建。
- 当前档案不能删除；创建、更新、切换后同步旧偏好投影，确保已有 Provider 在迁移期间继续得到相同参数。

## 作用域

- `SpeechServiceProfilesPreferences.kt`：模型、存储、迁移和档案管理。
- TTS/STT 工厂：按档案读取并维护旧 Provider 所需的活动投影。
- `SpeechServicesSettingsScreen.kt`：独立档案管理卡片、选择/创建/重命名/删除和现有参数编辑。
- 相关开发文档和静态检查说明。

## 步骤

1. [迁移契约与运行时接入 [DONE]](./01_migration_contract.md)
2. 设置页档案管理与模型配置页交互对齐 [DONE]

[DONE]
