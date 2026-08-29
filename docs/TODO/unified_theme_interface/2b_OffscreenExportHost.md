# OFFSCREEN 消息图片导出宿主

## 兼容边界

本单元以 `upstream/main@f323d6c50fa661837fad06d4618462861779b562` 为唯一兼容基线。

- 保留 `MessageImageGenerator.generateMessageImage` 的参数、默认值、异常、PNG 缓存文件和调用方 `Uri` 结果
- 保留活动主题快照的单次捕获、软件 Bitmap 图片加载、不可交互消息渲染、任意高度捕获和资源释放语义
- 保留 `includeBackground` 的透明外层与卡片内背景规则，背景视频继续立即播放且素材错误仅记录日志
- 不为功能分支内已替换的独立系统配色路径保留并行实现

## 旧实现

消息图片导出直接按系统配置选择 `darkColorScheme` 或 `lightColorScheme`，只提供原始主题快照。它没有提供解析后的主题、字体或 `OFFSCREEN` 宿主类型，因此导出结果不会完整反映当前主题模式、自定义颜色和字体。

`ScrollView` 附加到 Activity 根视图后，测量、等待和捕获不在同一个释放范围中。协程取消或测量异常可能使临时视图和离屏播放器继续附着。

## 修改意图

- 新增内部 `NativeThemeOffscreenHost`，集中 `OFFSCREEN` 环境解析、动态配色、字体、主题 Local 和 `MaterialTheme` 投影
- 编辑器预览继续创建自身的预览快照，再委托给该宿主；消息导出传入活动目标的实际快照
- 消息导出直接消费 `ResolvedNativeThemeV1` 的明暗状态、Material 配色、字体和背景规格
- 将临时 `ScrollView` 从附加起到捕获结束置于同一释放范围，确保失败和取消都移除视图

## 不在本单元

- 消息组件参数、圆角、气泡素材和聊天细节的完整运行时契约迁移
- 悬浮窗、权限 Overlay、Glance、Canvas 和 WebView 宿主
- 主题字段、DataStore 前缀、编辑器存储模型或分享文件 API 变更

## 验收标准

- 导出和编辑器预览通过同一 OFFSCREEN 主题宿主提供 `LocalThemePreferenceSnapshot`、`LocalResolvedNativeThemeV1` 和自定义 Typography
- 导出不再自行选择系统浅深色 `ColorScheme`
- 导出背景从解析结果读取，保留立即视频播放、裁剪透明度和仅记录错误的行为
- 临时捕获视图在测量、等待、绘制和取消路径后均从根视图移除
- 纯测试覆盖 OFFSCREEN 宿主环境下的显式模式、自定义颜色、字体和背景解析
- 更新原生主题契约进度与验证记录

## 实施状态

- `resolveNativeThemeOffscreen` 集中 Android 动态基底配色和纯 `OFFSCREEN` 主题解析；后者可由 JVM 测试注入哨兵配色。
- `NativeThemeOffscreenHost` 统一提供原始快照、解析结果、自定义 Typography 和 `MaterialTheme`。
- 编辑器预览保留自身的 `theme_editor_preview` 快照身份，并委托至 OFFSCREEN 宿主。
- 消息图片导出捕获活动目标快照一次，使用其解析后的配色、字体、明暗状态和背景规格；原始导出函数签名不变。
- `AppBackgroundLayer` 保留上游公开函数签名，新增解析主题输入的内部入口；媒体渲染、立即播放、透明度裁剪和日志语义共用一处实现。
- 临时 `ScrollView` 的附加、测量、等待和捕获放入单一释放范围，取消和异常都会移除根视图中的临时内容。
- `NativeThemeRuntimeTest` 覆盖 OFFSCREEN 显式模式、自定义颜色、字体、背景和注入基底配色选择。
- 静态检查和 release 构建结果记录在验证文档；本单元不单独执行单元测试或 Android Compose 测试。

[DONE]
