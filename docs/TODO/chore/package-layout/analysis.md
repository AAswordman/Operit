# 包职责、命名与上游维护关系分析

本稿保留实施前的职责分析，基线为 dev `6a4b9bfb399c670321518089592ae3e02e38897e`，工作分支 `refactor/package-layout`。维护者已批准原 API 117 个源文件的迁移；已实施路径与最终类名见 [实施记录](01-api-migration.md)。下文的其他候选均不代表已经实施。

本计划按维护者最新提出的顺序，以 API 包为首个讨论范围，替代此前先处理三个 MCP 路径不一致文件的建议。

## 结论与范围

优先做按职责归位的包迁移。`api` 顶层名暂时保留，`com.ai.assistance.operit` 根包、Gradle namespace、applicationId 和对外协议标识暂不改。类名只调整明显歧义的少数项目；不把本轮扩大为方法拆分、业务逻辑优化、依赖注入改造或状态机重写。

维护者已决定按自己的项目运营本仓库。本轮依据当前职责整理内部结构，不再把持续向上游贡献作为包布局约束。

## 实际核查范围

扫描了主应用根包下 1549 个 Kotlin/Java 源文件的目录与大小；进一步核对 API 117 个源文件的分类，以及维护者列出的非提供方实现、语音接口、本地语音实现、记忆提取、对话编排、插件注册、桌面 Widget 与 DocumentsProvider 的声明、依赖和关键实现。

UI、data、util 的全量逐类归属尚未定稿，不能把目录统计等同于已经完成全项目语义审计。文件大小统计为源码原始字节，不是 APK 体积，也不是本 fork 相对上游的净新增代码量。

| 范围 | 源文件数 | 大小 |
| --- | ---: | ---: |
| 主应用 Kotlin/Java 源码 | 1549 | 21.48 MiB |
| api | 117 | 1.76 MiB |
| ui | 775 | 11.19 MiB |
| ui/features | 603 | 8.04 MiB |
| util | 77 | 0.62 MiB |
| data | 251 | 2.24 MiB |

ui/features 在本次快照中有 24 个一级功能目录，占主应用上述源码字节约 37.4%，占 UI 约 71.8%。其中 chat、settings、packages 分别有 235、102、82 个源文件，适合作为后续职责检查的重点。

## 逐项判断

### api 与根包名

api 的确过于宽泛，而且当前还包含前台 Service、聊天运行时和记忆提取等不属于模型接口适配的职责。当前先保留这个顶层名称，收紧内部职责。后续若需要改名，应先明确它究竟表示模型访问、外部服务适配，还是公开 SDK，不能仅把 api 换成另一个宽泛词。

根前缀可以按项目品牌重新命名，但需分别处理：

- Kotlin/Java 源包：类的完整名称与源码目录。
- Gradle namespace：生成 R、BuildConfig 等代码的命名空间。
- applicationId：Android 安装、更新与应用数据归属的身份。

三者可以不同。内部目录和源包迁移不要求同步修改 applicationId。应用的签名与升级关系也不能从源码包名推断。当前不预设新的品牌前缀，也不调整已有用户的安装身份。

### speech、voice 与 local

维护者提出以 speech 为总领域、以 stt/tts 区分识别和合成，是合理的。现有 SpeechService 的接口只定义识别，VoiceService 的接口定义合成与播放；候选名称为 SttService、TtsService，工厂相应命名，是否实际改名留到实施表确认。

api/local 仅包含三个语音类，不是通用本地推理层：

- SherpaOnnxSpeechProvider 实现 SpeechService，归入 speech/stt。
- SherpaOnnxVoiceProvider 实现 VoiceService，归入 speech/tts。
- SherpaModelSession 同时提供 sttConfig 与 ttsConfig，属于共享 Sherpa 运行会话，不应强行归到其中一端。

另外还有 VAD、唤醒、录音和预录缓冲，不能把所有声音相关代码都理解成 STT 或 TTS。建议让 stt/tts 成为两个主方向，并为实际共享的音频与运行时保留明确位置。目录中只有一个文件，不能单独作为删除目录或合并职责的理由。

候选结构：

```text
api/speech/
	stt/
	tts/
	audio/
	wake/
	runtime/
```

api/chat/voice/VoiceAudioRequest 需要单独判断。它不是 TTS 实现，而是一次聊天请求里的录音副本、直接音频输入、音频理解及 STT 协调。它属于聊天音频输入编排，候选移动到已存在的 core/chat/voice，不能把其职责整个改成语音引擎层。原有执行顺序和错误处理不在本轮修改。

### library

MemoryLibrary 的注释和实现表明，它主要从对话中分析、抽取和写入结构化记忆图谱，还包含自动分类；它不是整个记忆库的数据存取接口。真正的数据存取已有 MemoryRepository。

因此 library 命名不准确，放在 api/chat 下也混淆了模型适配与业务职责。候选将这五个文件归入 core/memory/extraction：MemoryLibrary、MemoryAutoSaveScheduler、ChatMemoryRebuildManager、ChatMemoryRebuildTimeScope、ChatMemoryWindowPlanner。

MemoryLibrary 可考虑更精确的记忆提取类名，但不能顺带重写内部 927 行实现，也不能据此宣称整个记忆领域已完成分层。

### llmprovider

该目录有 45 个源文件，其中 24 个是维护者列出的共享服务、工具或支持类，另有 21 个提供方实现。当前目录承担的职责明显超出提供方适配。

候选归属见下表，逐文件表见同目录 api-migration-candidates.csv。以下均为提案，尚未实施。

| 当前职责或类 | 候选归属 |
| --- | --- |
| AIService、AIServiceFactory | api/chat 根目录，分别作为接口与装配入口 |
| ApiKeyProvider、ApiKeyPoolAvailabilityTester | 已有 api/chat/keypool |
| RateLimitedAIService、TokenTrackingAIService | api/chat/decorator |
| RateLimiterRegistry、RequestConcurrencyRegistry、SlidingWindowRateLimiter、LlmRetryPolicy | api/chat/request |
| ModelListFetcher、ModelFetchErrorHelper、ModelConfigConnectionTester、MediaCapabilityProbe、ThinkingQualityMapping | api/chat/model |
| MediaLinkBuilder、MediaLinkParser | 已有 core/chat/media |
| StructuredToolCallBridge | api/chat/protocol |
| EndpointCompleter、UnsafeModelSsl | api/chat/transport |
| HttpStatusCodeException | 已有 api/chat/error |
| ToolPkgJsAiProviderService | plugins/toolpkg/ai |
| DeepseekFileUploader、LocalGenerationEnd | 可保留在提供方适配范围，后续再按提供方族归组 |

提供方专用协议辅助类可以与对应适配器放在同一包；没必要强制要求该目录内每个文件都必须以 Provider 结尾。应迁出的重点是被多个提供方或其他业务共用的职责。也不为接口、工厂或每一个测试器各新建一个小包：第一轮按已有稳定职责合并归组。

AIServiceFactory.kt 内还有请求跟踪上下文、网络 EventListener 等声明。第一轮允许整体归位并保留文件内部关系，不能仅因挪出 llmprovider 就声称其中的职责已经解耦。

### 提供方类名

OpenAI、Claude、Gemini 的确混用了组织、产品和协议层级。ChatGPT 是产品名，不适合用来统称 OpenAI API 兼容协议。当前还分别实现了 Chat Completions 与 Responses，更适合按协议标识适配器。

可选的统一风格是 OpenAIChatCompletionsProvider、OpenAIResponsesProvider、AnthropicMessagesProvider、GoogleGeminiProvider；这是一组候选名称，不是本轮已确定的改名清单。

ApiProviderType 在 ModelConfigData.kt 中已有 OPENAI、ANTHROPIC、GOOGLE 等配置值。类名整理不应顺便更改这些持久化类型、用户可见选项或导入导出字段。

### enhance

这个目录的名字无法清楚表达内容。实际包含：

- ConversationService：历史准备、上下文处理、摘要和标题生成。
- ConversationRoundManager：多轮内容及展示内容状态。
- ConversationMarkupManager、ReferenceManager：消息协议、工具结果标记和引用提取。
- ToolExecutionManager、OrderedToolResults：对话中的工具执行与结果汇总。
- MultiServiceManager：按功能模型配置装配、缓存、租借和释放模型服务。
- FileBindingService：结构化编辑、补丁应用和差异生成。
- ConfiguredStreamingAIService、FunctionalThinkingAIService：模型服务装饰。

候选分别归入 core/chat/context、core/chat/runtime、core/chat/protocol、core/chat/tools、api/chat/runtime、core/chat/filebinding 和 api/chat/decorator。这样表达现有职责，不修改逻辑，也不承诺当前已有的跨层依赖会因此全部消失。

EnhancedAIService 自身承担更高层对话协调，长远看不属于模型协议适配包；但它已有 Java 桥调用者，首轮保留完整类名，将公开接口迁移留待单独决策。

### util

根目录混合了日志诊断、媒体池、模型协议解析、ToolPkg 编译与运行时等职责，确实需要整理。应按实际使用范围逐组归属；真正跨业务的基础类型可以保留。

util/stream 有 16 个文件，并非单文件占位。streamnative、ripgrep 等还涉及原生边界。不能以文件夹数量或文件数作为唯一标准，也不能把所有不确定归属的类转存到另一个 common/core 大包。

### ui 与 data

ui/features 按功能组织本身合理。24 个功能域、603 个文件和 8.04 MiB 的体积，说明规模较大，但不能单凭占比判定它不应存在。

后续重点是核对 chat、settings、packages 中哪些类确实承担非 UI 职责，并统一同一功能域内的 screens/components/viewmodel 等目录规则。单纯增加或减少目录层级不会减少业务耦合。ViewModel 依赖业务服务本身也不等于放错层。

data 的 20 多个目录同样不能仅凭数量合并。它有模型、偏好、资源、仓储、MCP 等真实边界；data/model 的 61 个文件和 data/preferences 的 45 个文件可优先核对业务归属。实体、序列化类型和数据库生成关系要单独检查，本轮不因目录美观改变已发布数据格式。

### widget、provider 与 plugins

widget 中的类继承 GlanceAppWidgetReceiver，包含 ToolPkg 桌面组件和语音助手桌面组件。它表示 Android 桌面 Widget；插件只是其中一种内容来源。若统一 Android 外部集成的位置，可候选归入 integrations/appwidget，但 root/widget 作为 Android 惯用组件包本身也成立。

provider 中三个类都是 DocumentsProvider，向系统文件界面提供工作区、记忆和应用数据。它们不是负责弹出文件选择器的类。可候选归入 integrations/documents，保留 DocumentsProvider 类名，以及原有 authority、document ID 和权限语义。

plugins 中有宿主注册器、生命周期 Hook、消息/视图 Hook 和 ToolPkg 桥接，确实是跨功能的插件扩展层，作为顶层包有合理性。应澄清它与 core/tools/packTool 的边界，而不是仅因文件数少或顶层包多就撤掉它。

## 已确认的迁移边界

这些是源码中已找到的依赖，不是泛化风险清单：

- examples/subagent/src/packages/subagent.ts:66 直接通过 Java.com.ai.assistance.operit.api.chat.EnhancedAIService 调用类；:73 还引用其嵌套 SendMessageOptions。deepsearching、message_insert 也有同类调用。
- app/src/main/cpp/streamnative/native_toolpkg_wasm.cpp:385 等 JNI 导出符号包含 util/ToolPkgWasmNative 的完整包名；XML/Markdown 分割器也有类似绑定。
- AndroidManifest.xml:174、:185、:196 声明 DocumentsProvider 与基于 applicationId 的 authority。
- AndroidManifest.xml:413、:425 声明桌面 Widget Receiver。此类 ComponentName 可能保存在已有桌面绑定中，不能只更新 Manifest 就假定用户界面完全不受影响。
- app/proguard-rules.pro 中存在按完整类名配置的 Shizuku 与 JavaScript 接口 keep 规则。
- tools/adb 使用固定 EXECUTE_JS action；该协议不会随 applicationIdSuffix 改变。

因此首轮优先移动内部实现，保留已确认的对外类名和标识。对外接口如需迁移，应单独明确发布兼容方案，不在本轮默认添加旧包名转发类。

## 是否需要完全脱离上游

大范围根包名重命名会增加合并、补丁移植、冲突定位和贡献摘取成本，但 Git 的重命名识别也意味着迁移不必然让所有上游修改都无法合并。是否适合上游应以具体职责整理和维护收益讨论，不能预先断言所有整理都不会被接受。

累计增删十万或二十万行，不能直接代表同等规模的功能差异；批量改包名本身就会放大差异。本轮没有把未重新核对的 fork 净差异行数当作独立维护的依据。

维护者已明确自主运营；是否选择性移植上游修复由后续具体任务决定，不是本轮实施前提。

## 建议实施顺序

1. 保留 api 顶层名和现有根前缀，先完成语音与 llmprovider 内部职责的迁移表与外部名字清单。
2. 第一批迁移 speech/stt、speech/tts 及共享语音支持，取消 api/local 的语音专用空泛层级；不改变执行顺序、配置值和模型格式。
3. 第二批迁移 llmprovider 中的共享基础职责，复用现有 keypool、error 和 core/chat/media 等包。与 JNI、Java 桥和 Android 组件直接绑定的类先保留。
4. 再整理 enhance 与记忆提取业务归属，然后按功能域复核 util、UI、data、Android 集成与插件边界。
5. 最后再决定是否整体更换源根包与项目品牌。applicationId 是否变化是独立的发布与数据迁移决策。

实际实施只允许包、目录、必要类名及其绑定引用的机械更新，不夹带重试、解析、计费、记忆、语音或 UI 行为改动。每批按改动范围执行仓库验证，不运行本地构建，也不持续轮询 CI。此分析稿不构成已经完成迁移或通过运行验证的记录。