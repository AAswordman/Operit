# GLANCE Widget 宿主

## 旧实现

`VoiceAssistantGlanceWidget` 使用固定蓝色背景和白色文字，`ToolPkgDesktopGlanceWidget` 使用独立的固定浅色、深色和强调色。两者均未读取活动角色卡或群组的主题快照。ToolPkg DSL 的 `primary`、`surface`、`onSurface` 等语义颜色 token 也固定映射到独立色值。

两个 Widget 的 `updatePeriodMillis` 均为 `0`。ToolPkg 包运行时变化会刷新桌面 Widget，但主题保存和活动主题目标切换不会主动刷新任何已安装的 Widget。

## 修改意图

让 Glance 作为 `native_v1` 的受限静态宿主：从活动主题快照解析 `GLANCE` 环境的明暗颜色投影，并将该投影提供给语音助手 Widget、ToolPkg Widget 外壳和 ToolPkg DSL 语义颜色 token。

Glance 不能承载 Compose `MaterialTheme`、动态背景媒体、模糊、窗口系统栏或自定义 Typography。该宿主只消费可表达为 `ColorProvider` 的静态颜色结果和既有固定尺寸文本样式。

## 最小范围

- 新增纯 `NativeThemeGlancePaletteV1`，将同一快照解析为 day/night `GLANCE` 颜色对
- `useSystemTheme` 时分别投影明暗解析结果；用户固定主题模式时投影保持该已选模式
- 语音助手 Widget 的容器和文字颜色消费投影，保留启动浮窗的 Intent 与服务语义
- ToolPkg Widget 外壳、未配置态、错误态和操作标签消费投影
- ToolPkg DSL 的已发布语义颜色 token 消费投影；显式颜色值、DSL 结构和动作语义保持不变
- 应用级观察活动主题快照，变化时刷新语音助手和 ToolPkg Widget；Widget 自身渲染仍直接读取最新快照
- Android 12+ 进程创建时监听壁纸颜色变化并刷新已安装的 Widget，使动态颜色投影重新读取当前壁纸色板
- 活动 Glance 会话在 `provideContent` 内观察主题快照和动态色版本号，变化后重新解析调色板
- ToolPkg 包运行时刷新继续只刷新 ToolPkg Widget

## 兼容要求

- 保留两个 `GlanceAppWidgetReceiver`、Manifest 声明、尺寸、配置 Activity 与桌面放置行为
- 保留语音 Widget 的 `FloatingChatService` 启动方式、`INITIAL_MODE` 和自动进入语音聊天参数
- 保留 ToolPkg Widget 的选择持久化、路由启动 Intent、DSL 解析、引擎租约和运行时刷新接口
- 不变更 ToolPkg Compose DSL 格式、颜色 token 名称、显式颜色解析或点击行为
- 主题来源始终遵循活动角色卡、群组和默认角色的既有作用域规则

## 验证

- JVM 测试覆盖 GLANCE 宿主标记、系统明暗投影、固定明暗模式、自定义颜色与语义 token 映射
- Android 12+ 测试覆盖真实 `Context` 的动态颜色投影、Glance `ColorProvider` 转换和活动内容重投影
- 设备验证覆盖两个 Widget 的主题切换、角色/群组切换、语音启动、ToolPkg 配置、语义颜色 token、ToolPkg 包刷新，以及应用未预先打开时的壁纸替换
- 完成后通过 Builder `build_current_release` 验证生产源集、资源合并和签名产物

## 实施记录

- `NativeThemeGlancePaletteV1` 使用活动主题快照生成 day/night 静态颜色对，并将两个结果标记为 `GLANCE` 宿主
- 语音助手 Widget、ToolPkg Widget 外壳和 ToolPkg DSL 的 Material3 颜色 token 消费同一投影
- ToolPkg 显式颜色值、节点与动作语义、Widget 配置持久化和两个 Widget Receiver 未改变
- 应用级主题流刷新两个 Widget；Android 12+ 进程创建时的壁纸颜色监听器在动态色变化时刷新两个 Widget
- `NativeThemeGlanceContentHost` 在活动 Widget 会话内观察主题快照和动态色版本号，避免捕获旧调色板
- 新增 JVM 与 Android 测试覆盖调色板、强制明暗模式、动态色、语义颜色 token 和刷新协调器
- `git diff --check` 已通过；本地未运行 Gradle、JVM 或 Android 测试

[DONE]
