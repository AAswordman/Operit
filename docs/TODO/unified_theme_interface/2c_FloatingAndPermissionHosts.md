# FLOATING 与权限 Overlay 宿主

## 兼容边界

本单元以 `upstream/main@f323d6c50fa661837fad06d4618462861779b562` 为唯一兼容基线。

- 保留 `FloatingChatService` 的服务生命周期、窗口模式、位置尺寸、IME、状态指示器和关闭语义
- 保留外部聊天接口的 `show_floating`、`initial_mode`、`auto_exit_after_ms` 与 Widget 直接启动浮窗行为
- 保留权限 Overlay 的悬浮窗权限检查、窗口 flags、Allow、Deny、Always Allow 和超时决策语义
- 保留当前主题作用域、动态色、自定义颜色、字体、角色卡与群组目标切换行为
- `COLOR_SCHEME`、`TYPOGRAPHY` Intent extra 和 `floating_chat_prefs` 的颜色字体 JSON 未出现在上游外部接口文档，属于内部重复主题通道

## 旧实现

浮窗聊天和状态指示器通过 `FloatingWindowTheme` 消费主界面序列化进 Service Intent 的 `ColorScheme` 与 `Typography`。直接由 Widget 或外部入口启动时，Service 再读取 `floating_chat_prefs` 中的 JSON。该通道只复制部分视觉结果，无法表达主题目标、解析环境、完整字体来源或实时主题切换。

权限请求 Overlay 单独由主聊天页面写入 `ColorScheme`，也没有主题快照或解析主题 Local。浮窗聊天内部还会再次观察主题快照，导致一个 Compose 根有两条主题输入路径。

## 修改意图

- 将 OFFSCREEN 的动态配色、纯解析、Typography 和 CompositionLocal 注入提取为参数化独立 Compose 宿主
- 以 `FLOATING` 环境包装浮窗聊天和状态指示器两个 WindowManager Compose 根
- 以 `OVERLAY` 环境包装权限请求 WindowManager Compose 根
- 将复用旧序列化主题通道的 WebSession 浏览器和最小化指示根改为 `FLOATING` 宿主
- 删除 Service Intent、SharedPreferences、回调参数和 WebSession 包装器中的颜色字体序列化通道
- 保持独立宿主不应用主 Activity 系统栏副作用或应用背景媒体

## 不在本单元

- 操作反馈、自动化进度、虚拟显示和 UI 调试器等其余 WindowManager Overlay
- Glance 受限静态投影与 Widget 刷新
- 崩溃、恢复和启动诊断宿主
- 浮窗聊天内部组件契约、布局重构或行为动作变更

## 验收标准

- 浮窗聊天、状态指示器和权限 Overlay 都提供 `LocalThemePreferenceSnapshot`、`LocalResolvedNativeThemeV1`、自定义 Typography 和解析后的 `MaterialTheme`
- 独立 Compose 根在活动角色卡或群组及其主题字段更新时重组为对应的 FLOATING 或 OVERLAY 解析结果
- `FloatingChatWindow` 不再重复观察或提供主题快照
- 不再存在浮窗颜色字体 Intent extra、JSON 偏好键、序列化模型调用、`FloatingWindowTheme` 或无调用 WebSession 浮窗主题包装器
- 外部启动参数、WindowManager 行为和权限请求结果接口不变
- 纯测试覆盖 FLOATING 与 OVERLAY 的宿主类型、模式、自定义颜色、字体和背景规格

## 实施状态

- `resolveNativeThemeForDetachedComposeHost` 以显式宿主类型集中动态配色和纯主题解析，OFFSCREEN 调用保持原有入口。
- `NativeThemeFloatingHost` 和 `NativeThemeOverlayHost` 共同提供活动快照、解析主题、Typography、`MaterialTheme` 与两个主题 Local。
- 浮窗聊天、状态指示器、WebSession 浏览器和最小化指示根均改为 FLOATING 宿主；`FloatingChatWindow` 不再重复观察主题快照。
- 权限请求 Overlay 改为 OVERLAY 宿主，并在设置 Compose 内容前建立生命周期树。
- 删除颜色字体 Intent extra、JSON 偏好、序列化模型、`FloatingWindowTheme` 和 `WebSessionFloatingTheme`；窗口模式、外部 Intent 和权限结果接口保持不变。
- `NativeThemeRuntimeTest` 覆盖 FLOATING、OVERLAY 的解析环境与视觉字段；`NativeThemeDetachedHostAndroidTest` 覆盖主题 Local、Material 配色和字体缩放注入。
- 静态检查与 release 构建结果记录在验证文档；本单元不单独执行单元测试或 Android Compose 测试。

[DONE]
