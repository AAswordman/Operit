# 组件目录冻结集

## 修改意图

公开 Style API v1 前，组件目录必须覆盖用户可见的基础控件、菜单与瞬态表面、导航外壳、聊天骨架、富内容和独立 Compose 宿主。每个契约登记可样式化部件、允许属性、状态轴、宿主表面和目录场景；主题不能改变组件结构或交互语义。

## 冻结集

首版冻结 42 个组件：

- 操作与图标：`action.button`、`action.fab`、`action.icon_button`、`data_display.icon_container`
- 输入：`input.text_field`、`input.toggle`、`input.choice_item`、`input.segmented_choice`、`input.slider`
- 结构与反馈：`container.card`、`container.list_item`、`container.section`、`data_display.stat`、`feedback.operation_status`
- 菜单与瞬态面：`overlay.menu`、`overlay.menu_item`、`overlay.popup_panel`、`overlay.dialog`、`overlay.sheet`、`feedback.snackbar`、`overlay.tooltip`
- 标签：`data_display.chip`、`data_display.badge`、`data_display.pill`
- 导航：`navigation.drawer_item`、`navigation.destination_item`、`navigation.tab`、`navigation.app_bar`
- 聊天：`chat.user_message`、`chat.assistant_message`、`chat.system_message`、`chat.thinking`、`chat.tool_call`、`chat.tool_result`、`chat.attachment`、`chat.composer`、`chat.header`
- 富内容与独立宿主：`content.markdown`、`content.code_block`、`floating.window_chrome`、`overlay.permission_prompt`、`offscreen.message_export`

## 批次

1. 重整现有六个试点，接入部件、样式属性、三层解析和状态选择器
2. 迁移基础操作、图标、输入和选择控件，覆盖焦点、错误、只读、拖动和禁用
3. 迁移卡片、列表、标签、菜单、Popup、Dialog、Sheet、Snackbar 和 Tooltip
4. 迁移抽屉、目标项、标签栏和 App Bar，验证手机与平板导航
5. 迁移聊天 Header、消息、思考、工具、附件、Composer、Markdown 和代码块
6. 迁移悬浮窗、权限 Overlay 和离屏消息导出外壳

## 部件与状态

每个组件至少登记 `surface`、`content` 和其实际使用的文字、图标、指示器、分隔线或容器部件。交互组件还需定义可用性、选择和交互状态；异步组件定义活动和校验状态；可展开内容定义展开和内容状态。

目录场景覆盖每个状态轴的所有值及高风险组合，例如 selected + pressed、disabled + selected、loading + error。宿主在目录与生产中强制相同的语义、48dp 触控区和事件约束。

## 表面规则

- `MAIN` 可使用已声明的高级材质
- `FLOATING`、`OVERLAY` 和 `OFFSCREEN` 仅使用各自能力档案声明的属性
- `EDITOR_PREVIEW` 与目标生产表面使用相同链接器和效果宿主
- 菜单、Popup、Dialog、Sheet、Snackbar 和 Tooltip 首版不声明背景采样、液态或水材质
- WebView、Android View、Canvas、Glance 和外部页面内部内容不进入冻结集

## 发布门槛

- 42 个契约均有版本、状态、事件、语义、部件、样式属性和至少一个生产调用点
- `native_v1` 与独立样式包通过同一目录、样式、无障碍和视觉套件
- 未迁页面被表面清单显式标记为 `material_projection_only`、`host_shell_only`、`fixed_diagnostic` 或 `out_of_scope`
- 不存在未分类的 Operit 控制原生 UI 表面
