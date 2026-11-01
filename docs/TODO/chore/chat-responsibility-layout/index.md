---
fork: https://github.com/3316891527/Operit
status: implemented-awaiting-ci
---

# 聊天代码按职责归类

工作区迁出后，原 `conversation.chat` 有 13 个直属子包、178 个 Kotlin 文件。`components` 同时容纳输入、消息渲染、附件、列表布局和页面组装；历史列表、归档与批量操作分散在多个根包。部分共享类型定义在具体 UI 文件中，被 `ChatViewModel` 和状态存储共同使用。

本批只整理 Kotlin 包结构，根层保留 `screens`、`modules`、`foundation`、`viewmodel`。没有拆分 Gradle 模块，也没有拆解 `ChatViewModel` 和输入框内部逻辑。当前主源码共有 184 个 Kotlin 文件，其中增加的 6 个文件承载从原文件提取的 9 个既有共享声明。

```text
chat/
	foundation/
		format/
		layout/
		lazy/
		list/
		mentions/
		model/
		progress/
		stream/
	modules/
		attachments/
		characters/
		export/
		history/
		input/
		messages/
		sharing/
		sidebar/
		usage/
		voice/
	screens/
	viewmodel/
		history/
```

`modules` 按聊天功能归类，输入框的 Agent、经典模式、输入扩展与提及候选集中在 `input`；消息样式、时间线、XML 渲染、复制、编辑、详情与翻译集中在 `messages`。图片选择、附件预览、音视频播放和外部分享导入归入 `attachments`，消息截图生成与分享预览归入 `sharing`。

`foundation` 承载自定义列表、滚动扩展、流式适配、布局、格式和文本规则。该包不依赖 `modules`、`screens` 或 `viewmodel`。自定义列表的上游版本和来源说明随目录迁移，说明内源码链接改为相对路径。

`screens` 保留页面组装和页面标题。`viewmodel` 保留页面级状态与协调；批量控制器和聊天列表动画状态归入 `viewmodel.history`。面板持有的录音会话仍在语音功能内，生命周期不变。

共享声明放在 `foundation.model`，原样保留名称、字段、默认值和可见性：

- `ChatStyle`、`CharacterSelectorTarget`、`PendingQueueMessageItem`
- `ChatHistoryDisplayMode`、`TranslationUiState`
- `ChatBatchAction`、`ChatBatchItemResult`、`ChatBatchProgress`、`ChatBatchSummary`

- [x] 保存本批迁移前快照，建立主源码、测试和文档的逐文件映射
- [x] 完成 173 个主源码文件、17 个 JVM 测试文件、1 个 Android 测试文件及 1 份来源说明的迁移
- [x] 将基础能力归入 `foundation`，功能代码归入 `modules`，页面组装归入 `screens`
- [x] 将 9 个共享声明原样提取到 6 个独立文件
- [x] 同步显式导入、全限定名和原同包引用，将拆分包的 2 处星号导入展开为对应显式引用
- [x] 核对声明体与提取内容，检查旧路径残留、目标冲突和完整差异空白
- [x] 更新相关文档，保留先前工作区迁移和既往清单的阶段记录
- [x] 完成本地整理与静态核对，按后续交付要求转入独立分支和 PR 验证

本批不调整发送、流式渲染、复制、删除、语音、导出或权限行为，不新增旧包转发或类型别名。跨功能调用继续沿用原来的对象、参数和回调。

交付分支为 `refactor/conversation-chat-workspace-layout`，PR 目标为 `dev`。本批以 [PR #93](https://github.com/3316891527/Operit/pull/93) 的文件操作实现为基线；本批新增提交只整理目录和共享声明，PR 相对 `dev` 的差异同时包含该依赖。

逐文件映射、保留原路径文件和原样提取记录见 [files.json](files.json)，静态核对及完整回归范围见[验证记录](01-validation.md)。本批承接[会话工作区迁移](../conversation-workspace-layout/index.md)，已经合并核对两轮迁移前后的全部既有源码。

[DONE] 本地目录整理、共享声明提取与静态核对；编译、构建、测试和 CI 尚未验证本次迁移。
