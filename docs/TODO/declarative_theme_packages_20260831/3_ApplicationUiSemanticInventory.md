# 全应用 UI 语义目录

## 目录规则

每个 Operit 控制的界面都必须登记：稳定 ID、宿主表面、主题责任、场景或组件契约、状态、事件、无障碍归属和迁移状态。新增原生路由或独立 Compose 根必须先登记，不能直接新增视觉实现。

主题责任分为：

- `full_scene`：主题包可编排页面级已登记槽位
- `component_skin`：页面结构由宿主保持，主题包提供通用组件皮肤
- `host_shell`：外部或专有内容由宿主拥有，主题包仅绘制容器壳
- `fixed_safety`：始终使用内置恢复和安全 UI
- `platform_owned`：平台或外部应用拥有视觉与交互

## 全局与聊天

- `app.shell`：手机抽屉、平板导航、应用栏、路由内容、公告和导航转场，`full_scene`
- `chat.main`：聊天头、会话条、消息视口、输入器、选择栏和非模态面板，`full_scene`
- `chat.floating`：浮窗、球形入口、全屏与结果展示，`full_scene`
- `chat.offscreen_export`：消息导出与主题预览，`host_shell`
- `chat.permission_overlay`：应用内工具权限提示，`host_shell`

## 业务完整场景

- `memory.graph_library`：记忆库、图谱、文件夹、检索与详情，`full_scene`
- `market.home`、`market.category`、`market.entry_detail`、`market.publisher_console`、`market.artifact_editor`、`market.repo_editor`，`full_scene`
- `packages.manager`：本地插件、MCP、Skill 和包管理，`full_scene`
- `workflow.library`、`workflow.canvas_editor`，`full_scene`；节点几何和命中测试由宿主保持
- `files.browser`：文件浏览、搜索与操作，`full_scene`
- `assistant.profile_and_wake`、`persona.card_studio`、`prompt_tag.market`，`full_scene`

## 通用组件页面

- 设置索引、用户偏好、模型配置、Prompt、上下文、聊天历史、备份、语言、显示、布局、TTS、STT、Token 统计、功能路由和媒体工具
- 帮助、关于、更新说明、市场通知、市场作者资料、GitHub 账户、模型下载和外部 HTTP 配置

这些页面使用 `section`、`form`、`list`、`table`、`status`、`dialog`、`sheet`、`menu`、`button`、`input`、`chart_shell` 等语义组件；主题包通过组件皮肤获得一致视觉，宿主保持信息架构与表单行为。

## 仅宿主外壳

- ToolPkg Compose DSL 与其配置页
- Token 配置 WebView、帮助 WebView、浏览器和用户 HTML
- 终端会话、SQL 查看器、媒体播放器、音频和视频附件
- 记忆图谱、工作流画布、聊天 XML Canvas 内容、虚拟显示与自动化画面
- Glance 小组件和桌面小组件

主题包只获取壳层、标题、加载、错误、边界和静态颜色投影，不能改写内部内容或业务手势。

## 固定恢复与平台所有

- `fixed_safety`：崩溃报告、数据恢复、主题包安装/校验/激活失败、关键权限恢复、插件启动失败与日志
- `platform_owned`：系统权限、输入法、状态栏实际控件、通知、SAF Picker、相机、屏幕捕获、默认助手设置、Shizuku/Root 与其他外部 Activity

## 路由覆盖要求

现有 70 条原生 `Screen` 路由与运行时 ToolPkg 路由均属于本目录。路由类名不应再被用作未来主题 API 的稳定 ID；场景和组件 ID 在主题模式中显式维护。
