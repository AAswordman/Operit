# 应用壳、聊天与组件宿主

## App shell

`AppShellSceneHost` 替代 `AppContent` 直接绘制的 Scaffold/TopAppBar。主题 scene 使用实际测量的 navigation、app bar、route content、announcement 和 overlay slots；宿主保留 router、drawer 状态、back guard、转场和 Insets。

## Chat

`ChatMainSceneHost` 的 `header`、`transcript`、`composer`、`settings rail`、`overlay` 和 `configuration` 都必须承载真实内容。输入器、消息、历史/选择器及其内部 controls 经 theme components 输出；不得再以绝对页面百分比给 composer 装饰框定位。

## Components

引入受 V2 skin 驱动的 `OperitThemeComponents`。所有新建或迁移的 Operit UI 只能使用这些组件或明确的 scene slot，不得直接选择默认 Material 视觉。Material/Compose 继续提供输入、焦点、语义和触控行为。

## Detached hosts

悬浮聊天、应用内权限 overlay、browser/WebView host、WebChat、offscreen export 共用 active linked runtime。插件内容置于独立 compatibility theme scope，防止 active theme package 改写插件内部 UI。
