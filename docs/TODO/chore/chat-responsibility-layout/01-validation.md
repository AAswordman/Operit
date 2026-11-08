# 聊天职责整理验证

本批基线为 `9b9bb75d93b96b72204067fa576db30e5279d960`，承接尚未提交的工作区迁移。来源、目标、保留原路径文件和原样提取内容见 [files.json](files.json)。

## 已完成的静态核对

- 完成 173 个主源码文件、17 个 JVM 测试文件、1 个 Android 测试文件及 1 份列表来源说明的迁移，旧路径没有保留转发文件
- 根层仅保留 `foundation`、`modules`、`screens`、`viewmodel`，主源码分别有 63、111、4、6 个 Kotlin 文件
- 9 个共享声明原样提取到 6 个文件，逐项对照名称、字段、默认值、可见性和完整声明文本
- 核对 381 个非私有顶层符号名的归属集合和来源文件，原有同名接口及工厂函数组合保持原样
- 核对应用及测试源码中的 374 处聊天导入、46 处原同包引用，拆分包的 2 处星号导入已经衔接到对应显式引用
- `foundation` 没有导入 `modules`、`screens` 或 `viewmodel`；队列条目、角色选择目标、聊天样式和共享状态类型由基础声明包提供
- 本批声明体内仅 `ChatViewModel.kt`、`AIChatScreen.kt` 中的全限定包路径发生替换，其余变化限于声明归位、包声明和导入
- 合并前一批工作区迁移，对基线全部 1938 个 Kotlin、Java 源文件逐一对照；归一化全限定路径并扣除清单内原样提取的声明后，全部声明体一致
- 当前应用源码共有 1944 个 Kotlin、Java 文件，增加的 6 个文件全部属于共享声明，没有遗漏原有文件
- 应用、工作流、终端目录和根构建配置的旧聊天包路径扫描无结果；既往 `chore/package-layout/` 清单未改写
- 实施阶段完整差异通过临时 Git 索引执行空白检查，当时未改变真实暂存区或 HEAD；后续提交与远端验证由对应 PR 记录

旧路径扫描可在仓库根目录执行：

```sh
rg -n 'features[./]conversation[./]chat[./](archive|attachments|batch|components|copy|export|folders|motion|sidebar|util|voicemessages|webview|workspace)([./]|\b)' app .github terminal build.gradle.kts settings.gradle.kts
```

预期无输出，`rg` 退出码为 1。声明体核对以两批清单和迁移前快照为依据，比较时只移除包声明及导入、映射完整包路径，并核对新文件包含原样提取的声明。

本地实施阶段未执行编译、构建或测试；远端派发与结果以目录整理和集成 PR 的验证说明为准。上述结果属于文本、声明和引用映射核对，不能代替 Kotlin 编译和 Android 运行验证。

## Android 测试数据字符串

`ToolCardStreamBoundaryAndroidTest` 中的工具请求、工具结果和未闭合请求前缀统一使用 Kotlin 三引号原始字符串，保留 XML 属性中的双引号及 `$value` 插值。此次只修正 4 处测试数据的字面量写法，原有 8 项测试及其断言保持不变。

已对照集成分支中的同一测试文件，并完成差异空白检查；本地未执行编译或测试，本次未手动派发工作流。

## 完整回归范围

以下保留全部既有聊天回归类，包含原路径未变的待发消息队列测试。本批只迁移已有测试，不新增行为测试。执行后预期所有既有断言通过，Android 测试源码能够解析新包路径。

<details><summary>既有自动回归类</summary>

| 编号 | 执行的测试类 | 覆盖范围 |
| --- | --- | --- |
| C1 | `GalleryDragSelectionTest` | 图片拖选 |
| C2 | `ImagePreviewTransformTest` | 图片预览变换 |
| C3 | `SentImageSourcesTest` | 已发送图片来源 |
| C4 | `ChatBatchControllerTest` | 聊天批量操作控制器 |
| C5 | `ChatAreaTokenSpeedTest` | 消息 Token 速度显示 |
| C6 | `MessageCopyTextTest` | 消息复制文本 |
| C7 | `MessageEditorContentTest` | 消息编辑内容 |
| C8 | `PastedTextAttachmentTest` | 粘贴文本附件 |
| C9 | `TokenHitRateFormatTest` | Token 命中率格式 |
| C10 | `MessageStatsFormattingTest` | 消息统计格式 |
| C11 | `StatusCardHtmlDocumentTest` | 状态卡片文档 |
| C12 | `ThinkToolsRowsTest` | 思考与工具分组行 |
| C13 | `ThinkingMarkdownStreamTest` | 思考 Markdown 流 |
| C14 | `ToolTimingIndexTest` | 工具耗时索引 |
| C15 | `BubbleImageLayoutTest` | 气泡图片布局 |
| C16 | `FolderChatHistoryTreeTest` | 文件夹聊天历史树 |
| C17 | `ChatListMotionStateTest` | 聊天列表动画状态 |
| C18 | `PendingMessageQueueStoreTest` | 待发消息队列，测试路径保留 |
| C19 | `StreamingTimelineRefreshAndroidTest` | Android 流式时间线刷新 |

</details>

<details><summary>Android 界面回归范围</summary>

| 编号 | 做什么 | 预期行为 |
| --- | --- | --- |
| M1 | 打开主聊天，切换历史并接收流式回复 | 页面、滚动位置、消息分组和刷新沿用原行为 |
| M2 | 在 Agent 和经典输入框切换设置、提及、角色及待发队列 | 原入口、选择、队列操作和发送回调正常 |
| M3 | 切换消息样式，打开详情、编辑、翻译、复制和工具卡片 | 内容、样式与原交互一致 |
| M4 | 选择图片并查看图片、音视频及失效附件 | 选择、拖选、预览和失效状态一致 |
| M5 | 从侧栏和历史面板执行文件夹、归档、批量操作 | 共用状态、进度、列表刷新和删除动画一致 |
| M6 | 录制、试听、发送和取消语音消息 | 权限、录音会话和关闭后的资源清理一致 |
| M7 | 分享消息图片，使用既有项目导出入口 | 原内容渲染、预览、保存和导出回调正常 |
| M8 | 打开悬浮聊天和主题预览，使用输入扩展 | 共用组件、共享类型和插件桥接解析到新位置 |
| M9 | 打开工作区、终端、编辑器及各类文件预览 | 前一批工作区迁移继续正常衔接，文件操作行为一致 |

</details>

工作区迁移的回归范围见[工作区迁移验证](../conversation-workspace-layout/01-validation.md)。复制、剪切、删除的全部 17 条自动用例与 13 项实机范围继续保留在[文件操作验证](../../fix/workspace-copy-streaming-progress/01-validation.md)，不以本页的包迁移检查替代。

## 结果记录

自动测试和 Android 界面项均尚未运行，以下表格用于填写实际结果。

<details><summary>待填写结果与证据</summary>

| 编号 | 结果与证据 |
| --- | --- |
| C1 | |
| C2 | |
| C3 | |
| C4 | |
| C5 | |
| C6 | |
| C7 | |
| C8 | |
| C9 | |
| C10 | |
| C11 | |
| C12 | |
| C13 | |
| C14 | |
| C15 | |
| C16 | |
| C17 | |
| C18 | |
| C19 | |
| M1 | |
| M2 | |
| M3 | |
| M4 | |
| M5 | |
| M6 | |
| M7 | |
| M8 | |
| M9 | |

</details>

[DONE] 本地静态核对和完整回归范围记录，实际测试结果待填写。
