# 原生主题契约

## 旧实现

`OperitTheme` 同时负责目标快照观察、颜色与字体解析、窗口系统栏、背景媒体、玻璃效果和 Compose 根注入。部分功能又独立解析相同字段，导致主题来源和视觉结果分散。

## 修改意图

引入四个内部核心对象：

- `ThemeDefinition`：主题身份、版本、能力、参数模式和组件实现声明
- `ThemeInstance`：角色卡或群组选择的主题及其参数
- `ThemeEnvironment`：宿主表面、窗口、明暗模式、字体缩放和动态效果策略
- `ResolvedTheme`：供渲染器直接消费的完整运行时结果

建立纯解析器，把持久化数据、主题定义和运行环境解析为 `ResolvedTheme`。窗口、媒体和服务生命周期由宿主适配器处理，不进入纯主题解析。

## 预期结果

- `native_v1` 精确表达当前主题行为
- 运行时解析结果不成为新的持久化副本
- 主界面、悬浮窗、导出、Glance 和诊断面使用显式宿主类型
- 业务组件不直接读取 DataStore 或字符串主题键
- `MaterialTheme` 成为统一主题结果的一种原生投影

## 兼容要求

- 保留角色卡、群组和默认角色的现有作用域语义
- 保留旧资源 URI 的读取能力
- 保留现有系统主题、自定义颜色、字体、背景和系统栏结果
- ToolPkg Compose DSL 已发布的 Material 颜色令牌继续由当前 Android `MaterialTheme` 提供

## 实施进度

- [DONE] 新增 `ResolvedNativeThemeV1`，集中表达宿主环境、实际明暗模式、Material 配色、背景、字体和系统栏规格
- [DONE] 主 `OperitTheme` 已消费统一解析结果，动态色仍由 Android 宿主注入
- [DONE] 原有明暗配色与自定义颜色公式已从 `Theme.kt` 移入纯解析器
- [DONE] 主 Compose 根同时提供已发布偏好快照和解析后的 V1 主题上下文
- [DONE] 纯单元测试覆盖系统与固定明暗模式、自定义颜色、背景、字体和系统栏规格
- [DONE] 主界面窗口系统栏副作用由 `NativeThemeMainWindowChromeHostAdapter` 应用
- [DONE] 背景播放器、图片和视频资源失败事件由 `NativeThemeBackgroundMediaHostAdapter` 承载
- [DONE] 消息图片离屏导出和编辑器预览通过 `NativeThemeOffscreenHost` 解析并注入 `OFFSCREEN` 主题
- [DONE] 浮窗聊天、状态指示器、WebSession 浮动外壳和权限请求 Overlay 通过显式 `FLOATING` 或 `OVERLAY` 宿主解析主题
- [PENDING] 接入 Glance 宿主

本阶段当前改动已经过两次独立静态审查，确认主界面行为与上游一致。独立 WebChat 及其颜色解析器未修改。
