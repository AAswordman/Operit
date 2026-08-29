# 主界面宿主适配器

## 修改意图

将 `OperitTheme` 中依赖 Android 窗口、Compose 生命周期和媒体资源状态的工作移到明确的主界面宿主适配器。纯 `native_v1` 解析器继续只产生 `ResolvedNativeThemeV1`，不持有 `Window`、`ExoPlayer` 或偏好写入能力。

## 范围

- 提取主界面的窗口系统栏应用逻辑，保留沉浸式布局、状态栏隐藏、颜色优先级、图标明暗和导航栏对比度行为
- 提取背景图片和视频媒体渲染、播放器创建释放、生命周期控制与资源失败事件
- 让消息图片导出的 `AppBackgroundLayer` 复用同一媒体渲染器，只记录加载失败，不修改主题偏好
- 让主界面继续通过 `ActivePromptManager` 将匹配当前 URI 和媒体类型的资源失败写回当前主题目标

## 不在本单元

- 悬浮窗、Overlay、Glance 和诊断面的主题接入
- `ThemeDefinition`、`ThemeInstance` 注册表和独立主题包
- 已发布偏好字段、主题作用域、解析公式和编辑器行为变更

## 验收标准

- `Theme.kt` 不再直接操作窗口系统栏或创建、释放 `ExoPlayer`
- 主界面和离屏导出共用图片、视频、透明度、模糊和失败事件实现
- 隐藏状态栏、背景透明系统栏、自定义状态栏色和普通主题色的窗口决策可由纯单元测试覆盖
- 背景资源在主界面加载失败时仅禁用仍处于活动状态且仍引用该资源的目标背景；离屏导出仅记录错误
- 本单元完成后更新原生主题契约进度和验证记录

## 实施状态

- `NativeThemeMainWindowChromeHostAdapter` 从主根提取窗口系统栏副作用，并以纯窗口状态冻结颜色与图标决策。
- `NativeThemeBackgroundMediaHostAdapter` 集中图片、视频、播放器生命周期和素材失败事件；主根使用生命周期播放策略，消息图片导出使用立即播放策略。
- `AppBackgroundLayer` 保留离屏导出 API，只作为背景媒体适配器的日志宿主。
- `NativeThemeHostAdaptersTest` 覆盖隐藏状态栏、背景透明、自定义状态栏色、默认主题色、失败资源匹配与离屏透明度裁剪。
- 静态检查使用 `git diff --check`；按仓库执行准则，本单元未运行单元测试、Android Compose 测试或构建。

[DONE]
