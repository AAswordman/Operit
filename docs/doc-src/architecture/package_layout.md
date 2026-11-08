# 应用源码包职责

本文记录 API、功能 UI、工具与 Android 集成整理后的包布局。源码根前缀为 com.ai.assistance.operit，api 顶层名称保持现状。目录整理表达现有职责，业务算法与执行流程保持原值。

## 模型访问与语音

```text
api/
	chat/
		AIService.kt
		AIServiceFactory.kt
		EnhancedAIService.kt
		AIForegroundService.kt
		llmprovider/
		keypool/
		decorator/
		request/
		model/
		protocol/
		transport/
		error/
		runtime/
	speech/
		stt/
			http/
		tts/
		audio/
		wake/
		runtime/
```

AIService 与 AIServiceFactory 是模型访问接口和装配入口。llmprovider 放提供方协议适配及专用辅助，keypool 管理密钥与会话，decorator 包装服务，request 管理并发、速率与重试参数，model 负责目录、能力与思考配置，protocol 处理模型工具调用协议，transport 构造地址与 TLS 配置，error 表达请求失败，runtime 装配功能模型服务。

speech/stt 中的 SttService 与 SttServiceFactory 提供识别和转写入口；speech/tts 中的 TtsService 与 TtsServiceFactory 提供合成和播放入口。audio 放共享录音、VAD 与预录缓冲，wake 放唤醒和声纹，runtime 放 STT/TTS 共用的 Sherpa 会话。

EnhancedAIService 保留现有 Java 桥完整名称，AIForegroundService 保留 Android Service 组件名。它们暂留 api/chat 是本轮明确的兼容边界。

## 对话、记忆与插件桥接

```text
core/
	chat/
		context/
		runtime/
		protocol/
		tools/
		filebinding/
		hooks/
		media/
		voice/
	memory/
		extraction/
plugins/
	toolpkg/
		ai/
```

对话的上下文、轮次运行状态、消息标记、工具执行、文件绑定、输入 Hook、媒体标记与音频输入分别放在 core/chat 的对应职责包。模型协议的工具调用转换仍在 api/chat/protocol，与对话消息标记区分。

ConversationMemoryExtractor、MemoryAutoSaveScheduler、ChatMemoryRebuildManager、ChatMemoryRebuildTimeScope 与 ChatMemoryWindowPlanner 放在 core/memory/extraction，负责从对话提取、重建和调度记忆；实际数据存取继续使用现有 MemoryRepository。

ToolPkgJsAiProviderService、TokenUsage 与 ToolPkgMainHookRunner 位于 plugins/toolpkg/ai，提供 JavaScript 模型适配桥接。

## 变更边界与审查

旧、新路径、五处类名调整、兼容边界与静态核对方式见 [API 包迁移记录](../../TODO/chore/package-layout/index.md)。对应单元与设备测试的包路径随职责调整，测试内容保持原值。

namespace、applicationId、authority、配置枚举、DataStore 键、JSON 字段和未纳入本轮的 Java 桥入口沿用现有定义；Android 组件包名按后续批准的范围统一到源码目录。根包品牌、三个 MCP 路径不一致文件及未纳入本轮映射的目录属于后续工作。
## 功能界面分类

```text
ui/
	features/
		conversation/
		automation/
		models/
		extensions/
		management/
		support/
	main/
		screens/
			NativeToolScreens.kt
```

conversation 放聊天、记忆与助手；automation 放终端、工作流、网页会话及仍在使用的原生工具；models 放模型配置、用量和登录；extensions 放包与 GitHub；management 放设置、存储、备份、更新、性能与资源；support 放帮助、协议、引导、演示与诊断。分类只增加包层级，各页面原路由 ID 沿用现有定义。

空工具箱首页及孤立文件管理器已删除。日志归备份，UI 调试归诊断，HTML 打包归聊天导出，STT/TTS 页面归设置；三个仍有路由调用的页面包装函数位于 ui/main/screens。有效插件模块与动态导航协议继续保留。

## 工具与 Android 集成

```text
core/
	diagnostics/
	speech/
		text/
	tools/
		packTool/
			minify/
data/
	serialization/
	updates/
	skill/
util/
	files/
	android/
	network/
	media/
	document/
	text/
	logging/
integrations/
	documents/
	appwidget/
```

43 个根部工具文件按真实职责归位；聊天上下文与标记进入现有 core/chat 对应包，其余明确的工具子包保留。AssetCopyUtils、OCRUtils、WaifuMessageProcessor、ToolPkgWasmRuntime 保留根部完整名称，维持既有 Java 桥和 JNI 入口。

DocumentsProvider 与 Widget 源码物理归档到 integrations。三个 Provider 使用 integrations.documents，七个 Widget 相关文件统一使用 integrations.appwidget，OfflineResourcesActivity 使用 ui.features.management.resources。包声明均与物理目录一致，Manifest 和 Widget XML 同步更新。旧桌面实例可能需要重新添加，不提供旧类转发或组件别名。

第二轮的逐文件归属、兼容例外与检查证据见 [功能、工具与集成整理](../../TODO/chore/package-layout/03-ui-utilities-migration.md)。


## 插件生命周期与界面模块

ToolPkgLifecyclePlugin 位于 plugins/toolpkg，负责应用生命周期 Hook 的同步与重放。插件界面模块统一使用 ToolPkg.registerUiModule，宿主数据类型为 ToolPkgUiModule，插件详情字段为 uiModules；显式详情页导航面为 package_details。内置插件、SDK、独立脚本与宿主桥接使用相同的新接口。旧工具箱注册函数、全局调用名和类型声明保留为空操作兼容入口，不返回内容或注册任何内容；旧导航声明忽略。真实界面注册省略 runtime 时仍使用 compose_dsl。

插件桌面定义通过 registerDesktopWidget 声明布局与路由，由 integrations.appwidget 中的原生接收器承接 Android 桌面实例。插件注册项与 Android ComponentName 属于两层身份，原生组件改名不要求插件改写 registerDesktopWidget 的调用，但已有桌面绑定可能失效。
