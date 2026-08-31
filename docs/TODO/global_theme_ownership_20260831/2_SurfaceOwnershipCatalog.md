# 全应用 Surface 目录

## Required daily surfaces

`app.shell`、`chat.main`、`chat.floating`、`chat.permission_overlay`、`browser.shell`、`web_chat.main`、`memory.graph_library`、`market.*`、`packages.manager`、`workflow.*`、`files.browser`、`assistant.*`、`persona.card_studio`、`prompt_tag.market`、`settings.*`、`toolbox.*`、`terminal.shell`、`media.shell`、`plugin.host_shell`。

每个原生 `Screen` 以显式 surface ID 登记；不得把 Kotlin 类名当作主题 API。通用设置/详情/表单页使用 page template 与 component skins，复杂业务页使用独立 scene。Dialog、sheet、menu、Snackbar、toast、loading、empty 和 error 是覆盖清单中的通用 surface，不允许独立硬编码视觉实现。

## Plugin boundary

ToolPkg Compose DSL、插件 WebView DOM、Canvas、插件桌面 widget DSL 和插件自定义布局维持插件 own scope。Operit 仍主题化外层 route、app bar、加载、错误、容器、picker 和未配置状态。内嵌 `UI.AiChat()` 仍是 Operit surface，必须使用 active theme。

## Fixed and platform surfaces

首次启动、崩溃报告、数据修复、桌面小组件保持固定。系统权限、SAF、输入法和 Android 负责的 status/navigation icon glyphs 不属于主题渲染范围。
