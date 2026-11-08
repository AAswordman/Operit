# 功能 UI、工具与 Android 集成路径整理

> 本记录对应第二轮快照 `5efa22e`。当时保留的九份 Android 包声明与旧插件界面命名已在后续收尾中统一，必要旧注册接口按空操作兼容，当前实现见 [收尾记录](04-layout-completion.md) 与 [最终路径清单](ui-utilities-migration-final.csv)。

基线为工具箱清理后的 `8566d30589199a01570eb7cb2901f533a9cf5074`。维护者要求继续使用原分支和 PR，将平铺的功能包分类、按职责整理 util，并将 provider 与 widget 源码移出根部。

## 功能分类

功能层采用以下目录。原有 toolbox 的有效页面按真实用途分别放回对应组，空首页和孤立文件管理器已经删除。

```text
ui/
	features/
		conversation/
			chat/
			memory/
			assistant/
		automation/
			terminal/
			workflow/
			websession/
			shell/
			sql/
			ffmpeg/
			autoglm/
		models/
			token/
			tokenstats/
			codex/
			antigravity/
		extensions/
			packages/
			github/
		management/
			settings/
			storage/
			databackup/
			update/
			performance/
			resources/
		support/
			about/
			help/
			agreement/
			onboarding/
			demo/
			diagnostics/
	main/
		screens/
			NativeToolScreens.kt
```

原 toolbox 中的日志页进入 management/databackup/logcat，UI 调试进入 support/diagnostics/uidebugger，HTML 导出进入 conversation/chat/export/htmlpackager，TTS/STT 页面进入 management/settings/speech。仍有路由调用的三个包装函数位于 ui/main/screens/NativeToolScreens.kt，各页面原路由 ID 保留。

## 根部工具归属

原 util 共核对 77 个源码文件，其中 47 个位于根部，43 个根部文件按实际职责迁移。

- 文件路径、归档与文件操作归 util/files；Android 语言与定位归 util/android；网络辅助归 util/network；媒体处理归 util/media；文档转换归 util/document；文本解析与分段归 util/text；数学公式转换归现有 util/markdown；日志与日志脱敏归 util/logging。
- ANR、崩溃恢复、全局异常与异常文本归 core/diagnostics；聊天辅助与 Token 缓存归 core/chat/context；思考、消息标记与结构化文本转换归 core/chat/protocol；TTS 清理与分段归 core/speech/text。
- 序列化归 data/serialization，发布目录访问归 data/updates，Skill 仓库 ZIP 池归 data/skill；两个 ToolPkg 压缩器归 core/tools/packTool/minify。
- AssetCopyUtils、OCRUtils、WaifuMessageProcessor 与 ToolPkgWasmRuntime 保留根部完整名称，分别维持已确认的 Java 桥与 JNI 入口。已有 stream、streamnative、markdown、logging、vector、toolmarkup、exceptions、ripgrep 等明确子包继续保留。

没有重写日志、路径、压缩、网络、序列化或语音算法，类名未顺带调整。逐文件归属以 [CSV](ui-utilities-migration.csv) 和 [JSON](ui-utilities-migration.json) 为准。

## Android 集成与稳定身份

```text
integrations/
	documents/
	appwidget/
```

三个 DocumentsProvider 与七个 Widget 相关文件物理位置分别归入以上目录。其中三个 Provider、五个 Widget/Glance/配置 Activity 文件保留原 package 声明，维持系统文档访问、Glance 身份、桌面绑定与配置入口。ToolPkgDesktopWidgetHost 和 ToolPkgDesktopWidgetDslRenderer 的实际包名改为 integrations.appwidget，并更新调用方。

OfflineResourcesActivity 物理位置归 management/resources，但继续保留原完整类名与 Manifest 入口。九份保留包声明的例外由映射中的 preservePackage 显式记录，其余移动源码要求路径与 package 声明一致。

## 实施与验证

范围内共 677 个主源码文件，包含 590 个 UI、77 个 util、3 个 Provider 和 7 个 Widget 文件；643 个改变物理位置，34 个位置保留。公开或模块内顶层声明 1,614 个纳入映射；同步移动 95 个既有测试文件。

1,072 个改动 Kotlin/Java 文件的正文与基线一致，1,350 个其余源码逐字节一致；源码总集合无新增或遗漏。3,020 处隐式引用、43 份固定入口、3,181 个可执行目录文本文件核对通过。Kotlin/Java 字面量变化为 0；Android JS 测试中的 TTS 完整名称同步更新。

三处完整名称引用行的既有行尾空格仅作精确清理，归一化规则单独列入映射，未放宽其他正文格式。测试断言与输入保持原值。详细静态证据、工作流范围及可选设备回归见 [第二轮验证记录](verification-round2.md)。

[DONE] 路径迁移、调用引用、测试路径与静态核对。仓库构建和 JVM 测试按最终提交另行派发。
