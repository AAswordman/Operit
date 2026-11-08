# UI 重排细化

## 已发布行为约束

- 不改变 `SpeechServiceProfilesPreferences` 的 JSON、档案 ID、当前档案选择和删除约束。
- 不改变旧 `SpeechServicesPreferences` 投影，现有 Provider 继续读取相同字段。
- 不增加第二套保存机制；页面继续使用现有自动保存流程。
- 不删除任何已存在的 Provider 配置字段。

## 页面结构

1. 页面内 Tab：`TTS` 和 `STT`。
2. 当前 Tab 的配置档案紧凑管理栏，保留新建、切换、重命名和删除非当前档案。
3. 当前 Provider 和播放参数集中展示。
4. 清理正则和 HTTP 请求参数作为可展开区域展示。
5. TTS Tab 保留测试 TTS，STT Tab 保留对应引擎参数和说明。

## 完成标准

- 切换 Tab 不丢失编辑状态。
- 切换、创建、重命名、删除档案行为不变。
- 每种已存在 TTS Provider 仍可访问原有字段。
- 页面不再同时渲染 TTS 和 STT 两个完整长表单。
