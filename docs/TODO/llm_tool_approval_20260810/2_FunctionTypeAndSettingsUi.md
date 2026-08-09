# 功能模型与设置界面

## 旧实现

FunctionType 无审批类条目；工具权限设置页只渲染 允许 与 禁止 两个分组卡片；聊天输入栏的权限标签 when 仅覆盖三档。

## 修正意图

FunctionType 增加 TOOL_APPROVAL，FunctionalConfigManager 的默认映射与回退逻辑自动覆盖新条目，功能模型配置页补充显示名称与描述。工具权限设置页新增 智能审批 分组卡片；审批提示词随 FunctionalPrompts 统一维护，不在设置中开放编辑。ClassicChatSettingsBar 与 AgentChatInputSection 的权限标签补齐 LLM 分支。

## 新实现结果

用户可在功能模型配置为审批绑定独立模型；在工具权限设置中把任意工具划入智能审批分组；聊天界面权限菜单正确显示第四档标签。新增文案提供中文与英文资源。

[DONE]
