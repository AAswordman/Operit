# 应用壳、聊天与组件宿主

## App shell

`AppShellSceneHost` 替代 `AppContent` 直接绘制的 Scaffold/TopAppBar。主题 scene 使用实际测量的 navigation、app bar、route content、announcement 和 overlay slots；宿主保留 router、drawer 状态、back guard、转场和 Insets。

## Chat

`ChatMainSceneHost` 的 `header`、`transcript`、`composer`、`settings rail`、`overlay` 和 `configuration` 都必须承载真实内容。输入器、消息、历史/选择器及其内部 controls 经 theme components 输出；不得再以绝对页面百分比给 composer 装饰框定位。

## Components

引入受 V2 skin 驱动的 `OperitThemeComponents`。所有新建或迁移的 Operit UI 只能使用这些组件或明确的 scene slot，不得直接选择默认 Material 视觉。Material/Compose 继续提供输入、焦点、语义和触控行为。

## Detached hosts

悬浮聊天、应用内权限 overlay、browser/WebView host、WebChat、offscreen export 共用 active linked runtime。插件内容置于独立 compatibility theme scope，防止 active theme package 改写插件内部 UI。

## 进展

[DONE] `AppShellSceneHost` 替代 `AppContent` 的原始 Scaffold/TopAppBar；系统栏颜色改为消费 `app_bar` 皮肤容器色（旧 primary 直刷状态栏/顶栏的缺陷已移除，含回归测试）。

[DONE] `chat.main`：`ChatScreenHeader` 迁入真实 `header` 槽位；`transcript` 不再重复绘制角色栏；`configuration_gate` 承载首配屏；composer 由场景 scaffold bottom 区域真实测量，赛博包不再使用百分比绝对定位。

[DONE] 悬浮窗、应用内 overlay、离屏导出与 WebChat 桥统一消费同一激活主题包运行时；Glance 桌面小组件按产品决策保持固定基线。

[TODO] `OperitThemeComponents` 受皮肤驱动的专用组件包装（button/input/dialog/menu/sheet 等当前经 Material 投影着色，专属异形皮肤消费在下一批次接线）。

[TODO] `app.navigation` 抽屉/平板导航、`chat.floating`、`browser.shell` 等仍为 TEMPLATE/HOST_SHELL 级主题化（颜色排版已随包，专属场景待逐批迁移）。
