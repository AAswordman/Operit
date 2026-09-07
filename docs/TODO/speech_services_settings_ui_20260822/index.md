---
feature: speech-services-settings-ui
status: in-progress
---

# 语音服务设置页 UI 重排

## 原本状况

已发布版本的语音服务设置页把 TTS、STT、配置档案、供应商参数、清理规则和说明内容连续堆叠在同一条长列表中。TTS 供应商切换后，低频 JSON 参数和常用播放参数没有清晰层级。

## 目标

在不改变已发布配置数据、路由、自动保存和 Provider 参数契约的前提下，将页面压缩为 TTS/STT 分页、单行配置档案管理、紧凑播放参数和可展开的低频设置。

## 作用域

- `SpeechServicesSettingsScreen.kt`：只调整 Compose 页面结构和档案入口展示。
- `SpeechServicesSettingsPreferences.kt` 及 Provider：不修改数据模型和运行时契约。
- 保留所有现有 TTS/STT 字段、档案操作和测试入口。

## 结果

- TTS 和 STT 通过页面内 Tab 切换，避免同屏滚动两个完整配置区。
- 当前 Tab 只显示对应的配置档案入口。
- 语速和音调合并到同一播放参数区域。
- TTS 清理规则和 HTTP 低频参数支持折叠。
