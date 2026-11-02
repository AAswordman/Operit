---
title: 声明与文档覆盖索引
status: complete
---

# 声明与文档覆盖索引

此页跟踪 `examples/types/` 中的每个声明入口和其对应参考页。本轮已完成公开声明、方法、字段、版本门槛与宿主行为的逐项核对；声明与运行时不一致、未暴露入口和无法由实现证实的行为均单独记录，不以类型声明替代运行时契约。

## 声明文件映射

- `index.d.ts`：全局导出面；对应[全局运行时 API](../03_runtime/global_api.md)、[QuickJS 全局运行时](../03_runtime/quickjs_runtime.md)与本索引。
- `core.d.ts`：核心调用、结果基础类型和 NativeInterface；对应[核心模块参考](../04_modules/core.md)、[全局运行时 API](../03_runtime/global_api.md)及[Java Bridge](../07_types_and_libraries/java_bridge.md)。声明中的重载、NativeInterface 方法和已确认差异已逐项记录；`_` 与 `dataUtils` 的实现边界仍按全局页说明。
- `tool-types.d.ts`：工具名/参数/返回映射；对应 `04_modules/tool-types.md`。`ToolResultMap` 的 151 个键及返回类型已逐项列出，并按 `core.d.ts`、`JsTools.kt`、`ToolRegistration.kt` 和 `results.d.ts` 完成静态核对；已记录 `combined_operation`/`execute_terminal` 仅存在于声明文本、当前 facade/注册入口未进入映射、`read_file` 与 `read_file_full` 的兼容入口差异，以及包工具和代理工具的动态名称边界。
- `toolpkg.d.ts`：注册、Hook、IPC、WASM；对应[注册 API](../03_runtime/registry.md)及[Hook 参考](../05_hooks/index.md)。注册 API 的 UI/导航、Hook、聊天菜单和 AI Provider 字段、默认值、函数引用归一、关联资源校验及失败行为，以及 `readResource`/`getConfigDir`、IPC、本地/跨上下文路由、WASM 参数归一与结果解码均已按运行时核对。
- `results.d.ts`：宿主工具结果类型；对应 `04_modules/results.md`。状态：`complete`。162 个 interface、742 个顶层字段均已按 `ToolResultDataClasses.kt`、各标准工具构造路径和相关 DTO 完成源码交叉核对；38 个 `BaseResult` 包装到 `data` 类型的映射已逐项核对，22 个此前未列出的运行时结果数据接口已单独记录。此前剩余的 26 个接口/71 个字段已补齐，重点差异包括 `DateResultData` 的实际 `date`/`format` 字段、Bluetooth 和音乐状态的开放字符串实现、终端会话默认值、`FFmpegResultData` 的 `mediaInfo` 运行时形状，以及无当前构造调用点的 `ConnectionResultData`。
- `android.d.ts`：Android facade 和类；对应 `04_modules/android.md`。构造函数、属性、方法及其运行时路由已按 `AndroidUtils.js`、QuickJS bootstrap、`JsTools` 和 Intent executor 核对；记录了 `executeAdb` 声明缺少实现、`Tools.system.shell` 大小写不匹配、`const enum` 无运行时对象、Intent categories/type/flags 转发差异、`setAirplaneMode` 返回类型不符、`setVolume('call', ...)` 校验缺陷及 `getAllProperties()` 正则结束条件错误。
- `chat.d.ts`：Chat facade；对应 `04_modules/chat.md`。全部声明方法已按 `JsTools`、`ToolRegistration`、`StandardChatManagerTool`、`ToolResultDataClasses` 与相关服务源码完成方法级核对：包括服务启动、会话 CRUD/查找/状态、普通/流式发送、角色卡列表、消息读取/区间读取，以及 API `1.0.1` 的 `Chat.call()`；已记录默认值、参数校验、服务连接/等待、分页排序、后台发送、流式事件、结果字段、XML 清理、错误语义和角色卡 ID 校验的当前实现差异。
- `files.d.ts`：Files；对应 `04_modules/files.md`。23 个 facade 方法（含 `read`/`download` 重载）已按 `JsTools`、`ToolRegistration`、`StandardFileSystemTools`、`LinuxFileSystemTools`、`SafFileSystemTools`、`PathValidator`、`PathMapper`、`ToolExecutionLimits` 与结果数据类完成方法级核对；已记录 Android/Linux/SAF 分流、路径校验、读取限制、跨环境复制、结构化 apply、归档、打开/分享、下载索引以及 grep/grep_context 的运行时差异。
- `network.d.ts`：Net；对应 `04_modules/network.md`。34 个 `Net` 函数声明及 CookieManager 的 3 个方法已按 `JsTools`、`ToolRegistration`、`StandardHttpTools`、`StandardWebVisitTool`、`StandardBrowserSessionTools` 与结果数据类完成方法级核对；已记录 7 个声明但未映射的浏览器方法、HTTP/visit 参数与结果字段差异、全局活动 tab 行为和 Cookie 返回类型差异。
- `okhttp.d.ts`：OkHttp Bridge；对应 `04_modules/okhttp.md`。`OkHttp`、`OkHttpClientBuilder`、`OkHttpClient`、`RequestBuilder`、`HttpRequest`、`HttpStreamEvent`、`OkHttpExecuteOptions` 和 `OkHttpResponse` 已按 `OkHttp3.js`、`JsAssetLoader`、`JsLibraries`、`StandardHttpTools`、`ToolRegistration`、`ToolResultDataClasses` 和 `JsInitRuntimeScriptBuilder` 完成逐项核对；已记录全局加载、超时毫秒到秒换算、同步拦截器、流式回调、响应 Base64/cookie 字段，以及 `retryOnConnectionFailure`、`formParam` 和 `multipartParam` 的当前实现差异。
- `system.d.ts`：System；对应 `04_modules/system.md`。全部系统、应用、Usage Access、通知、定位、蓝牙 Classic/BLE、Shell、Intent、广播、终端会话和音乐方法已按 `JsTools.kt`、`ToolRegistration.kt`、`StandardSystemOperationTools`、`BluetoothSessionManager`、`StandardTerminalCommandExecutor`、`StandardMusicPlaybackTools`、Intent/Broadcast executor 与结果 DTO 完成逐项核对；已记录权限门禁、系统设置命名空间、安装/卸载请求语义、Usage Access 默认值和筛选、位置超时/最近位置回退、蓝牙 UUID/会话/通知消费、Shell 危险命令过滤、Intent 实际字段、终端超时取消/流式事件、音乐输入校验及声明差异。
- `software_settings.d.ts`：SoftwareSettings；对应 `04_modules/software_settings.md`。26 个公开方法均已列出 TypeScript 签名；环境变量、沙箱/MCP、speech 更新、角色卡字段校验与工具访问解析、模型配置创建/更新/删除、参数归一、函数绑定/索引与连接测试行为已有源码核对。`ModelConfigUpdateOptions` 缺少 6 个实现支持的 `llama_*` 更新字段、`SpeechServicesUpdateResultData.sttApiKeySet` 错取 TTS key 两项差异已记录。
- `memory.d.ts`：Memory；对应 `04_modules/memory.md`。10 个公开方法及位置/对象重载已按 `JsTools.Memory`、`ToolRegistration`、`MemoryQueryToolExecutor`、`MemoryRepository` 相关调用和结果 DTO 完成逐项核对；已记录 caller card profile 解析、查询快照及 32 个快照上限、时间边界、通配查询、文档分块、CRUD 默认值、移动筛选、链接定位/歧义和 weight/limit 钳制行为。
- `workflow.d.ts`：Workflow；对应 `04_modules/workflow.md`。10 个 `Runtime` 方法、CRUD/patch/节点和连接解析、启停与错误结果语义，以及 `WorkflowExecutor` 的触发选择、依赖调度、边条件、失败分支、Condition/Logic/Extract/Execute 节点执行行为均已按源码核对。
- `tasker.d.ts`：Tasker；对应 `04_modules/tasker.md`。`TriggerTaskerEventParams` 和 `Runtime.triggerEvent` 已按 `JsTools`、`JsInitRuntimeScriptBuilder`、`ToolRegistration`、`AIAgentTasker` 与 Tasker Plugin Library 调用链完成方法级核对；已记录 task_type 校验、arg1-arg5/args_json 归一、事件提交而非任务完成的异步语义、本地化状态文本和 Tasker 事件方向差异。
- `ui.d.ts`：UI/UINode；对应 `04_modules/ui.md`。9 个 `Tools.UI` facade 方法与 `UINode` 的属性、文本提取、搜索、动作、表示和静态方法已按 `JsTools.kt`、`UINode.js`、`ToolRegistration.kt`、`StandardUITools`/Accessibility backend 与结果 DTO 完成方法级核对；已记录 `clickElement` 参数归一、backend selector 差异、截图返回路径、坐标/滑动默认值，以及 `combinedOperation` 仅被 UINode 遗留静态方法引用但当前 facade 未暴露。
- `ffmpeg.d.ts`：FFmpeg；对应 `04_modules/ffmpeg.md`。`Tools.FFmpeg.execute`、`info`、`convert` 三个 facade 方法已按 `JsTools`、`JsInitRuntimeScriptBuilder`、`ToolRegistration`、`StandardFFmpegTool`、`FFmpegResultData`、内置 `ffmpeg.js` wrapper 和元数据完成方法级核对；已记录命令直传、Promise reject、转换命令构造、输入文件检查、FFprobe 补充信息、wrapper 返回值及声明/运行时字段差异。
- `cryptojs.d.ts`：CryptoJS；对应 `04_modules/cryptojs.md`。`WordArray`、`MD5`、`AES.decrypt`、`enc.Hex.parse`、`enc.Utf8`、`pad.Pkcs7` 和 `mode.ECB` 已按 `CryptoJS.js`、`JsNativeInterfaceDelegates.crypto`、`JsEngine` 与启动模块完成方法级核对；已记录 WordArray 运行时形状、Hex 不解码、AES key/cfg 归一、固定 ECB/NoPadding + 手动 PKCS7、错误转空结果及未导出构造器差异。
- `jimp.d.ts`：Jimp；对应 `04_modules/jimp.md`。`JimpWrapper`、读取/创建、裁剪/合成、尺寸读取、Base64 导出和资源释放已按 `Jimp.js`、`JsNativeInterfaceDelegates.imageProcessing`、`JsEngine` 与启动模块完成方法级核对；已记录 wrapper 构造器未导出、释放后的 `id` 变化、整数参数、PNG/JPEG 编码规则、内部二进制句柄输入和 bitmap 生命周期差异。
- `compose-dsl.d.ts`：Compose DSL runtime、节点工厂和 props；对应[Compose DSL 参考](../06_ui_and_compose/compose_dsl.md)。101 个公开 interface/type/registry 入口已按 `JsComposeDslBridge.kt`、`JsComposeDslRuntimeScript.kt`、`ToolPkgComposeDslScreen.kt` 和声明逐项核对；已记录节点/slot/action 归一、state/memo hooks、Modifier 动态 proxy、Canvas 单位和手势、WebView controller/事件、文件选择器校验、context 工具 fallback，以及 runtime wrapper 调用但 bridge 未实现的 `updateRuntimeOptions`。
- `compose-dsl.material3.generated.d.ts`：生成的 Material 3/Foundation 组件；对应[Material 3 组件参考](../06_ui_and_compose/material3_components.md)。84 个 generated props 接口和 84 个 registry key 已与生成声明、生成器、Kotlin renderer registry 对照；83 个 key 由自动生成 registry 实现，`Canvas` 由专用 renderer 覆盖，已记录 controlled state、slot/action 归一、未知节点 fallback 和生成文件来源。
- `java-bridge.d.ts`：Java/Kotlin Bridge；对应[Java Bridge 参考](../07_types_and_libraries/java_bridge.md)。`JavaBridgeApi`、class/package/instance proxy、接口 marker、suspend callback、JSON 值转换、句柄生命周期和 DEX/JAR loader 已按 `JsJavaBridge.kt`、`JsJavaBridgeDelegates.kt`、`JsEngine.kt` 与 `JsExternalJavaCodeLoader.kt` 完成方法级核对；已记录 `bigint` 声明不可按当前 JSON bridge 传递、Companion fallback、Thread/FutureTask 特例、native `message` envelope、GC 句柄释放及 prepared copy 路径。
- `material-icons.d.ts`：Material Icon 名称与 registry；对应[Material Icons](../06_ui_and_compose/material_icons.md)，字面量集已列入。
- `pako.d.ts`：Pako compression API；对应[内置库与压缩接口](../07_types_and_libraries/libraries.md)，已记录声明/实现差异。
- `quickjs-runtime.d.ts`：QuickJS runtime globals；对应[QuickJS 全局运行时](../03_runtime/quickjs_runtime.md)，timer、Storage、console 与 performance 已对照兼容层。ToolPkg metadata 参数转换不属于 QuickJS globals，参数类型、缺省/必填检查与转换失败路径另见包格式参考，并按 `JsToolManager` 实现核对。

## 明确的 API 版本标注

当前声明中标有 `@since ToolPkg API 1.0.1` 的公开能力包括：

- ToolPkg 注册：`registerChatMessageMenuItem`、`registerChatRuntimeHook`。
- 聊天菜单和运行态 Hook：对应 event name、sender、slot/state enum、handler、event payload、return/dialog 类型和 registration 类型。
- Chat 模块：`ChatCallOptions`、`Chat.call(options)`；结果为 `ChatCallResultData`。
- 结果类型：`ChatCallFinishReason`、`ChatCallResultData`。
- Compose DSL：`DialogProperties`、`AlertDialogProps`、`DialogProps`、`AlertDialog` 工厂、`Dialog` 工厂。
- NativeInterface 同步声明：`registerToolPkgChatMessageMenuItem`、`registerToolPkgChatRuntimeHook`。

宿主最低版本要求按[版本规则](./api_versions.md)单列：ToolPkg API `1.0.1` 要求 Operit `1.12.1+4` 或更新版本。`@since` 注释、类型存在和运行时逐方法门禁是三件不同的事；只有 `JsToolPkgApiRuntime` 明确包装的成员会在调用时执行 facade 版本校验。

## 已确认的声明/运行时差异

- `ChatMessageEventPayload` 的 sender/variant 字段以声明为准，实际值来自 `ChatMessage` 模型；逐字段差异待接口审计阶段核对。
- `pako.inflate` 声明接受可选 options 和 `Uint8Array`，实现却要求 `{ to: "string" }` 且只接受字符串；native 解码 raw DEFLATE 后按 UTF-8 返回文本，native 错误包装文案也有偏差。
- Compose `useMemo(key, factory, deps?)` 声明了 `deps`，当前 bridge 只按 key 首次缓存，不比较依赖项。
- `dataUtils.stringifyJson(value)` 声明返回 string，但顶层值序列化结果为 `undefined` 时 wrapper 也会返回 `undefined`；抛出异常时才回退为 `"{}"`。
- `Icons` runtime 是返回属性名字符串的 Proxy，并不校验 `KnownMaterialIconName` 枚举。
- `NativeInterface.registerImageFromBase64()` 和 `registerImageFromPath()` 的当前成功路径返回空字符串，未返回声明注释所描述的图片 link；失败路径返回错误文本。
- `NativeInterface.logDebug()` 在当前 `JsEngine` 中为空实现。
- `NativeInterface.javaLoadDex()`、`javaLoadJar()`、Java 实例/字段调用等 bridge envelope 的失败字段在运行时为 `message`，而 `core.d.ts` 的部分注释描述为 `error`。
- `DateResultData` 的 `.d.ts` 声明为 `date: Date`、`formattedDate`、`timestamp`；Android 数据类实际序列化 `date: string`、`format`、`formattedDate`，并没有 `timestamp`。
- `BluetoothDeviceData.type` / `bondState`、`BluetoothScannedDeviceData.source`、`BluetoothBleCharacteristicData.properties` 和 `MusicPlaybackResultData.state` 在 `.d.ts` 使用字面量联合类型；对应 Kotlin DTO 使用 `String` 或 `List<String>`，运行时并无这些联合类型的枚举约束。
- `ModelConfigResultItem` 的 Kotlin DTO 额外序列化 `apiProviderTypeId`，该字段缺失于 `.d.ts`；`apiProviderType` 与额外字段都映射同一个 provider ID。`ModelConfigConnectionTestOutcome` 和结果字段在 Kotlin 侧为字符串，实际由实现写入小写 `passed` / `unverified` / `failed`。
- `HttpResponseData` 的 Android DTO 还声明 `contentBase64` 与 `cookies`，缺失于 `.d.ts`；`cookies` 默认空映射，是否省略默认值取决于序列化配置。`Link`、`FileEntry`、`GrepLineMatch`、`GrepFileMatch` 在 `.d.ts` 为顶层接口，在 Kotlin 中分别表现为访问网页/列目录/Grep DTO 内的嵌套数据类。
- `GrepResultData.filePattern?` 在 `.d.ts` 被声明为结果字段，但 Kotlin DTO 不返回该字段；它只作为搜索工具输入过滤条件使用。Grep 的匹配分组、计数还受工具结果上限影响。
- `FilePartContentData` 的当前 Linux 实现把 `startLine`/`endLine` 表示为 0 起始左闭右开范围；`partIndex`/`totalParts` 保留为兼容字段，当前值固定为 `0`/`1`。
- `SimplifiedUINode.shouldKeepNode?()` 在 `.d.ts` 中看似可调用，但 Kotlin 实现为 `private` 辅助方法；它只参与 `toTreeString()` 的节点过滤。
- `IntentResultData.type` 在 `.d.ts` 中声明为活动/广播/服务联合类型，但当前 Android DTO 没有该字段；`action`、`uri`、`package_name`、`component`、`flags`、`extras_count`、`result` 才是当前序列化字段。
- `FFmpegResultData` 的 `.d.ts` 顶层 `videoStreams` / `audioStreams` 与 `FFmpegStreamInfo` 结构未在当前 Kotlin DTO 中实现；运行时使用可选 `outputFile`、`mediaInfo`，并在 `mediaInfo` 下返回 `StreamInfo`。
- `.d.ts` 的 `AutomationExecutionResultData` 对应源码类名 `AutomationExecutionResult`；其 `executionError` 与 `finalState` 在运行时为 nullable 字段，声明中的可选标记不能单独推断 JSON 字段一定缺省。

## 完成标准

- [x] 逐个 `.d.ts` 文件列出全部公开值、函数、类方法、事件、参数对象和结果字段。
- [x] 每个公开方法对应一个有行为说明的参考条目；不能只给类型链接。
- [x] 每个 `@since` 字段/方法/类型标注 API 版本，并与宿主最低 Operit 版本分开记录。
- [x] 每项关键错误、空值、超时、取消、缓存、并发和副作用均有实现依据。
- [x] 记录未实现、未暴露、声明不一致和无法由实现证实的项目。
- [x] 新文档内所有相对链接有效；旧 `package-dev` 页面已迁移到 `04_modules/` 并删除。