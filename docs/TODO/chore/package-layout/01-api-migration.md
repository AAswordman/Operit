# 原 API 117 个源文件的实施记录

源码比较基线为分析提交 `342694313de3a129e320f326d861903c662930f5`。清单覆盖的是 117 个源文件，包含 187 个公开或模块内顶层声明，并非只有 117 个同名类。

## 目录归属

语音统一到 api/speech：stt 提供识别与录音转写，tts 提供合成与播放；VAD、录音和预录缓冲放在 audio，唤醒放在 wake，SherpaModelSession 放在共享 runtime。聊天音频输入协调 VoiceAudioRequest 归入 core/chat/voice。

模型访问接口与装配入口放在 api/chat 根部。llmprovider 保留提供方适配、DeepseekFileUploader 和 LocalGenerationEnd；公共能力分别归入 keypool、decorator、request、model、protocol、transport 和 error。AIServiceFactory 文件内的请求上下文、共享 HTTP 客户端与监听器随文件整体移动。

对话业务分别归入 core/chat 的 context、runtime、protocol、tools、filebinding、hooks 和 media。功能模型装配归入 api/chat/runtime，服务装饰归入 api/chat/decorator。对话记忆提取、重建和调度五个文件归入 core/memory/extraction；数据仓储继续由现有 MemoryRepository 承担。ToolPkg 模型提供方桥接归入 plugins/toolpkg/ai，同文件 TokenUsage 和 ToolPkgMainHookRunner 一起移动。

逐文件旧、新路径与保留理由见 [实施表](api-migration-candidates.csv)。原地保留的 43 个文件也已核对引用；原位置保留不代表 import 不需要更新。

## 五处类名调整

- SpeechService → SttService
- SpeechServiceFactory → SttServiceFactory
- VoiceService → TtsService
- VoiceServiceFactory → TtsServiceFactory
- MemoryLibrary → ConversationMemoryExtractor

类名标识符和绑定引用同步更新。原有日志标签、配置枚举、存储键、模型参数、超时值和请求内容保持原值。没有拆方法、调整重试策略、修改解析或音频处理算法。

## 引用与测试

迁移映射覆盖完整名称、顶层辅助类型、顶层函数和 Kotlin 文件门面，补齐原同包可见而不需要 import 的引用。退役包的星号导入展开为具体类型，保留已有别名和合法的重载函数导入。

37 个既有测试文件跟随被测职责移动，测试断言和输入数据保持原值；同时更新留在原目录的测试调用。提供方协议测试继续归属提供方包，模型思考配置规则测试归入 model。Android 设备测试仅同步路径和引用，本轮没有在设备上执行。

## 对外入口与配置

EnhancedAIService 及其 SendMessageOptions 继续供现有 Java 桥调用，AIForegroundService 的 Android 组件名保持原值。Manifest、app Gradle 配置、R8 规则和三个现有插件入口文件均与基线摘要一致，JNI 代码不在移动范围。

语音档案仍使用原有枚举、JSON 字段和 DataStore 键。HttpTtsResponsePipelineStep 继续作为具体类型的 JSON 列表编码，OpenAiSpeechRequest 继续作为具体请求对象编码，两处编码逻辑与字段均未修改。

## 验证与交付

[静态核对](verification.md)记录正文、字面量、范围与外部引用的核对结果。可复现检查器位于 tools/package_layout/verify_api_migration.py，使用 Git 基线与当前工作树比较，不依赖 Kotlin 原生解析扩展。

构建与 JVM 测试使用工作分支的仓库工作流，记录派发和执行结果时分别标明状态。静态一致性不能替代 Kotlin 编译、R8 或设备能力验证；可选设备检查不作为本轮机械迁移的前置条件。

[DONE] 原 API 文件移动、类名与引用实施。仓库工作流结果另行记录，不将派发受理视为通过。
