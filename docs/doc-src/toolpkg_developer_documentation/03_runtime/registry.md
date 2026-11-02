---
title: ToolPkg 注册 API
status: complete
---

# ToolPkg 注册 API

`ToolPkg` 是 ToolPkg 主入口运行时注入的注册对象。注册调用在主模块求值阶段收集为 JSON 定义，宿主完成解析后才把模块、Hook 和 Provider 接入对应运行时。它不是 `Tools` 命名空间，也不是直接执行内置工具的入口。

**版本约定：** 下文未单独注明的注册方法以 `ToolPkg.Registry` 的 API `1.0.0` 基线为准。消息长按菜单与聊天运行态 Hook 从 ToolPkg API `1.0.1` 起提供。Operit 宿主最低版本见[版本规则](../09_compatibility/api_versions.md)。

## 主入口执行约束

- 主入口需要导出 `registerToolPkg()`；宿主在注册会话中执行该函数并捕获注册定义。
- 注册调用需要发生在该会话开启期间；会话关闭后再调用 native registration 会报 `toolpkg registration session is not active`。
- 注册定义必须是可序列化 JSON 对象；带函数的字段由运行时解析为导出的模块函数引用。不能序列化成稳定函数引用的函数会在注册时抛错。
- 带 `screen` 的 UI 注册要求 screen 能解析为包内路径；`ComposeDslScreen` 函数引用会被归一化成模块路径。

## 注册时的归一、验证与错误

注册 API 分两个阶段工作：桥接层先把定义归一为 JSON 并在当前注册会话中按类别收集；主入口执行结束后，宿主再解析所有收集项并检查关联资源。方法声明返回 `void`，但校验、JSON 序列化或 NativeInterface 调用错误会同步抛出；宿主阶段任一条目验证失败，则整个 `registerToolPkg()` 注册结果失败，不会只保留前面已通过的条目。宿主执行主入口的预算为 12 秒。

- 注册会话只在宿主执行主入口 `registerToolPkg()` 期间有效。会话外调用 native 注册入口会报 `toolpkg registration session is not active`。提交空内容、非 JSON object 或 JSON 序列化失败也会中止注册。
- 带 `function` 的注册项必须提供函数值。若函数与当前模块 exports 中某个导出相同，桥接保存导出名；否则该函数必须带有 ToolPkg 模块路径和导出名元数据，桥接生成相对模块引用。普通闭包、临时匿名函数或无法解析到模块导出的函数会同步报错。Provider 的四个嵌套 handler 同样要求 `{ function }`。
- `screen` 可传包内模块路径字符串，或带 ToolPkg 模块路径元数据的 screen 函数/默认导出函数；桥接会统一斜线并转换为路径字符串。无法得到可序列化路径时调用失败。宿主随后检查路径是否合法且归档中确实存在该文件。
- 各 Hook 类注册项在宿主解析时都要求非空 `id` 和 `function`；同一注册类别内 ID trim 后按忽略大小写检测重复。重复检测按类别分别进行，不构成跨所有 Hook 家族的单一 ID 空间。XML render 还要求非空 `tag`，宿主保存前会转小写。
- 生命周期 Hook 的 `event` 会 trim 并转小写，但宿主只检查非空，不会验证它属于 `AppLifecycleEvent` 的固定联合成员。未知非空字符串可能被接受为注册项，但不能保证会收到相应事件。

### 类型组的默认值与关联校验

| 注册项 | 桥接/宿主处理 |
| --- | --- |
| `ToolboxUiModuleRegistration` | `id`、`screen` 必填；`runtime` 缺省为 `compose_dsl`，`keepAlive` 缺省 `false`，标题缺省为 `id`。宿主从 `id` 生成包内 route，并生成 toolbox 导航项；ID 和 route ID 分别与显式 UI route 合并后做不区分大小写的重复检测。 |
| `UiRouteRegistration` | `id`、`screen` 必填。`route` 非空时优先于 `routeId`；两者都空时按包 ID 和 `id` 生成 route。`runtime` 缺省为 `compose_dsl`，`keepAlive` 缺省 `false`，标题缺省为 `id`。route 与 screen 路径在宿主侧需唯一、合法且指向归档内文件。两个别名同时给出不同值时使用 `route`，不会因冲突单独报错。 |
| `NavigationEntryRegistration` | `id` 与 `surface` 必填；`route` 优先于 `routeId`。必须至少提供一个已注册的 route 或 `action`；非空 route 必须能解析到该 ToolPkg 已注册 route。surface 只接受 `toolbox`、`main_sidebar_plugins`（忽略大小写）。标题缺省为 `id`，空 icon 变为缺省，`order` 缺省为 `0`。 |
| `DesktopWidgetRegistration` | `id` 与 `route`（或 `routeId`）必填；render route 优先取 `render`，再取 `renderRouteId`，都未提供时回退为 route。宿主要求 route 和最终 render route 都引用已注册 route；因此声明中可选的 render 字段运行时可以省略，但独立 route 不能省略。标题缺省为 `id`，subtitle/description 缺省为空，空 icon 变为缺省，`order` 缺省 `0`。 |
| `ChatMessageMenuItemRegistration` | `id`、`function` 必填；`senders` 去空白、忽略大小写并去重，只接受 `user`、`ai`。`dialog.screen` 必须是可序列化路径且存在于包归档。标题缺省为 `id`，`order` 缺省为 `0`。 |
| `AiProviderRegistration` | `id` 必填且在 Provider 类别内不得重复；`listModels`、`sendMessage`、`testConnection`、`calculateInputTokens` 四个嵌套 `{ function }` 均必填。`displayName` trim 后为空时回退为 `id`，`description` trim 后可为空。 |

标题等 `LocalizedText` 字段在宿主侧会 trim 文本并忽略空值；无法得到有效本地化内容时使用表中的默认标题/空文本。注册函数只负责声明；运行时执行、资源读取和 WASM 调用要在后续适当的调用上下文中完成。其他 Hook 专有 payload 和返回值语义见各自 Hook 页面。

## UI 与导航注册

### `ToolPkg.registerToolboxUiModule(definition)`

```ts
registerToolboxUiModule(definition: ToolboxUiModuleRegistration): void
```

- **API 版本：** `1.0.0` 基线
- **用途：** 注册可从工具箱打开的 UI 模块。
- **`id`：** 包内稳定标识；宿主将其与容器包名组合用于解析注册项。
- **`runtime?`：** 运行时标记；应与目标模块实现匹配。当前类型未限定字面量，具体支持值由 UI 运行时决定。
- **`screen`：** Compose DSL screen 函数或包内模块路径。注册桥会把函数引用规范化为序列化路径。
- **`params?`：** 传给 screen 的 `ToolParams` 初始参数。
- **`title?`：** 字符串或按语言代码映射的本地化标题。
- **`keepAlive?`：** 请求保留页面实例；是否保留仍受对应 UI 容器生命周期控制。

### `ToolPkg.registerUiRoute(definition)`

```ts
registerUiRoute(definition: UiRouteRegistration): void
```

- **API 版本：** `1.0.0` 基线
- **`id`：** route 注册标识。
- **`route?` / `routeId?`：** 路由别名。宿主先用非空 `route`，否则取 `routeId`；两者都空时按包 ID 和注册 `id` 生成 route。若同时提供且值不同，以 `route` 为准。
- **`runtime?`：** UI runtime 标识。
- **`screen`：** screen 函数或包内模块路径，必须能被宿主序列化和加载。
- **`params?`：** screen 初始参数。
- **`title?`：** 本地化标题。
- **`keepAlive?`：** 页面存活策略提示。

### `ToolPkg.registerNavigationEntry(definition)`

```ts
registerNavigationEntry(definition: NavigationEntryRegistration): void
```

- **API 版本：** `1.0.0` 基线
- **`id`：** 导航项稳定 ID。
- **`surface`：** 当前声明值为 `toolbox` 或 `main_sidebar_plugins`，决定导航项出现位置。
- **`route?`：** 点击后打开的已注册路由。
- **`action?`：** 导航动作 Hook；若提供函数，必须可以解析为持久化模块函数引用。
- **`title?`、`icon?`、`order?`：** 本地化标题、图标名和同 surface 内排序值。
- **动作事件：** `navigation_entry_action`；payload 字段见[Hook 事件索引](../05_hooks/index.md)。

### `ToolPkg.registerDesktopWidget(definition)`

```ts
registerDesktopWidget(definition: DesktopWidgetRegistration): void
```

- **API 版本：** `1.0.0` 基线
- **`id`：** 小组件注册 ID。
- **`route?` / `routeId?`：** 点击打开的 route 别名；宿主优先使用非空 `route`，否则取 `routeId`，并要求该 route 已注册。
- **`render?` / `renderRouteId?`：** 小组件本体对应的 UI route；宿主优先使用 `render`，其次取 `renderRouteId`，两者都空时回退到点击 route，因此 render 实际可省略。最终 render route 也必须已注册。
- **`title?`、`subtitle?`、`description?`：** 本地化展示文本。
- **`icon?`、`order?`：** 图标和列表排序。
- 注册桥当前把定义序列化后交给宿主；路由存在性和桌面容器行为由 widget 宿主校验。

## Hook 注册方法

以下方法都在模块注册期提交定义；回调签名和返回值并不相同，不能共享一个“通用 hook 返回值”假设。每个 Hook 的触发点、阶段、payload、数据合并和错误语义见[Hook 参考](../05_hooks/index.md)。

| 方法 | API 版本 | 定义字段 | 宿主效果 |
| --- | --- | --- | --- |
| `registerAppLifecycleHook` | 1.0.0 | `id`、`event`、`function` | 订阅指定 application/activity 生命周期事件 |
| `registerMessageProcessingPlugin` | 1.0.0 | `id`、`function` | 参与消息处理/回复接管链 |
| `registerXmlRenderPlugin` | 1.0.0 | `id`、`tag`、`function` | 处理指定 XML tag 的渲染请求 |
| `registerInputMenuTogglePlugin` | 1.0.0 | `id`、`function` | 提供输入菜单开关定义并响应开关事件 |
| `registerChatInputHook` | 1.0.0 | `id`、`function` | 观察编辑事件并拦截/替换提交 |
| `registerChatViewHook` | 1.0.0 | `id`、`function` | 观察聊天视图打开、更新和关闭 |
| `registerChatMessageHook` | 1.0.0 | `id`、`function` | 收到消息持久化通知，返回值不改写已保存消息 |
| `registerChatMessageMenuItem` | **1.0.1** | `id`、`title`、`icon?`、`order?`、`senders?`、`function`、`dialog?` | 在消息长按菜单添加动作项，可打开 Compose DSL 弹窗 |
| `registerChatRuntimeHook` | **1.0.1** | `id`、`function` | 订阅聊天运行状态变化 |
| `registerToolLifecycleHook` | 1.0.0 | `id`、`function` | 观察工具调用请求、权限、执行和结果阶段 |
| `registerPromptInputHook` | 1.0.0 | `id`、`function` | 修改 Prompt 输入处理阶段数据 |
| `registerPromptHistoryHook` | 1.0.0 | `id`、`function` | 修改正式 Prompt 历史准备阶段数据 |
| `registerPromptEstimateHistoryHook` | 1.0.0 | `id`、`function` | 修改 token 估算使用的历史数据 |
| `registerSystemPromptComposeHook` | 1.0.0 | `id`、`function` | 修改系统提示词组合阶段数据 |
| `registerToolPromptComposeHook` | 1.0.0 | `id`、`function` | 修改工具提示词和可用工具列表 |
| `registerPromptFinalizeHook` | 1.0.0 | `id`、`function` | 修改模型请求发送前的最终 Prompt 数据 |
| `registerPromptEstimateFinalizeHook` | 1.0.0 | `id`、`function` | 修改 token 估算的最终 Prompt 数据 |
| `registerSummaryGenerateHook` | 1.0.0 | `id`、`function` | 修改摘要提示词或生成结果 |

以上版本号来自类型声明的 `@since` 标记和 ToolPkg namespace 基线；运行时的逐方法门禁只对版本规则页列出的 facade 方法执行。

## AI Provider 注册

### `ToolPkg.registerAiProvider(definition)`

```ts
registerAiProvider(definition: AiProviderRegistration): void
```

- **API 版本：** `1.0.0` 基线
- **`id`：** Provider 稳定 ID；必填且在 AI Provider 注册项中唯一（忽略大小写）。
- **`displayName?`、`description?`：** UI 展示文本；`displayName` trim 后为空时回退到 `id`，`description` 可以为空。
- **`listModels.function`：** 返回 `{ models: Array<{ id, name }> }`。
- **`sendMessage.function`：** 返回 `{ text, usage? }`；`usage` 可包含 `input`、`cachedInput`、`output` token 数。
- **`testConnection.function`：** 返回 `{ success, message?, error? }`。
- **`calculateInputTokens.function`：** 返回 `{ tokens }`。
- 四个回调都必须放在 `{ function }` 字段中，并能映射到已导出的模块函数；回调收到 provider 专属事件及 `AiProviderConfig`，参数和返回形状见 `ToolPkg.AiProvider*` 类型。
- Provider 每个方法可返回值或 Promise；其具体并发、请求超时和错误映射由 Provider bridge 决定，需结合[AI Provider 专页](../05_hooks/ai_provider.md)阅读。

## 资源与持久配置

### `ToolPkg.readResource(key, outputFileName?, internal?): Promise<string>`

- **API 版本：** ToolPkg `1.0.0` namespace 基线；方法未单独标注 `@since`。
- `key`：`manifest.resources[]` 中声明的资源 key。桥接层会把传入值转成字符串并 trim；空 key 直接返回 rejected Promise。宿主查找资源 key 时忽略大小写。
- `outputFileName?`：输出文件名；桥接层 trim 后传给 native。省略时使用 manifest 资源路径的最后一段作为文件名；目录资源会自动补 `.zip`（已有 `.zip` 后缀时不重复添加）。native 会去掉传入文件名中的目录部分，只使用 basename。
- `internal?`：只有严格布尔值 `true` 才向 native 层传递内部访问标记；它决定输出到普通或内部的 clean-on-exit 目录。
- 目标包名取自当前调用上下文中的 UI package、`toolPkgId`、container package、subpackage ID 或 package name 参数；传入 subpackage ID 时宿主会解析到对应容器。没有目标时 Promise 以错误拒绝。
- 资源必须来自已启用的 ToolPkg 容器，且清单中的资源文件/目录必须存在；找不到资源或 native 返回空路径时 Promise 拒绝。成功结果是宿主返回的非空绝对路径字符串。
- `registerToolPkg()` 注册收集期间不能调用该方法；native 会立即拒绝注册阶段操作，不应把资源释放副作用放入主入口。

### `ToolPkg.getConfigDir(pluginId?): string`

- **API 版本：** ToolPkg `1.0.0` namespace 基线；方法未单独标注 `@since`。
- 有 `pluginId` 时先转字符串并 trim 后作为目标；空值则从当前调用上下文解析 ToolPkg/package 目标。传入 subpackage ID 时解析到对应容器包，否则按目标 ID 解析。
- 目标为空或 native 层没有返回非空目录路径时同步抛错；成功返回该 ToolPkg 容器的持久配置目录绝对路径。
- 返回路径供插件读写自己的持久配置文件；不要把 `readResource()` 的临时释放路径与配置目录混用。

## IPC

### `ToolPkg.ipc.on(channel, handler): () => void`

在当前 runtime context 注册消息处理函数。`channel` 会转成字符串并 trim，空 channel 或非函数 handler 会同步抛错；同一 context 的同一 channel 只保留一个 handler，新 handler 会覆盖旧 handler。返回的注销函数只会在该 handler 仍是当前注册值时删除它，避免误删后来替换的 handler。

### `ToolPkg.ipc.off(channel, handler?): boolean`

移除当前 context 中 channel 上的 handler。channel 为空时返回 `false`；省略 handler 时删除该 channel 的当前 handler；提供 handler 时必须是同一个函数引用，否则返回 `false`。返回值表示是否确实移除了监听。

### `ToolPkg.ipc.call(channel, payload?, options?): Promise<TResult>`

- `channel` 会 trim；空 channel、非法 `targetRuntime` 或当前上下文与显式目标不一致时返回 rejected Promise。`targetRuntime` 只接受 `main`、`ui`、`sandbox`、`provider`。
- 未指定目标时，main context 直接调用本地 handler；非 main context 默认把请求发送到同包 ToolPkg main。指定当前 context 时同样走本地 handler；指定其他 runtime/context 时通过 native IPC bridge 转发。
- `ui`、`sandbox`、`provider` 目标必须提供 `targetContextKey`；main 目标只能使用当前 ToolPkg 的 `toolpkg_main:<package>` context key。显式 runtime 与 context key 推断出的 runtime 不一致时调用失败；目标 context 不存在或未激活时调用失败。
- payload 会先 JSON 序列化；不可序列化时 Promise 拒绝。handler 的返回值可以是普通值或 Promise，跨 context 返回值必须能编码为 JSON。
- 本地 handler 的异常会转为 rejected Promise。跨 context bridge 返回 `{ success: true, value }` 时解析为 `value`；`success` 为 false、返回 JSON 无效或 bridge 不可用时按 `message` 拒绝。native 跨 context 执行使用 15 秒脚本预算。

`meta` 包含 `channel`，并可带 `callerContextKey`、`currentContextKey`、`currentRuntime` 和 `packageTarget`。payload/result 的静态默认类型为 `unknown`，调用方应通过泛型明确业务结构。

## WASM

### `ToolPkg.wasm.call(moduleId, exportName, args?): Promise<WasmCallResult>`

- **API 版本：** ToolPkg `1.0.0` namespace 基线；方法未单独标注 `@since`。
- `moduleId` 与 `exportName` 会 trim；任一为空时 Promise 拒绝。`args` 缺省为空数组，非数组值会拒绝。
- 每个参数必须是普通对象并有 `type`、`value` 自有字段。`type` 会 trim 并转小写；支持 `i32`、`i64`、`f32`、`f64`。`i32` 要求有限整数且位于 32 位有符号范围；`i64` 接受带符号十进制字符串、JavaScript 安全范围内的整数，运行时也接受 `bigint`；浮点参数要求有限数值，`f32` 还必须能转换为有限的 32 位浮点值。
- 宿主只从已启用容器读取 WASM 模块；module ID 查找和 manifest 的 exports 列校验忽略大小写，但 native 最终按 trim 后的 `exportName` 查找实际 WASM 导出，导出函数名本身应按模块中的大小写传入。manifest 的 exports 列非空时，导出名还必须出现在该列表中；模块或导出不可用时调用失败。
- native 会继续校验参数数量以及每个参数的精确 ABI 类型；参数数量或 `i32`/`i64`/`f32`/`f64` 类型与 WASM 函数签名不匹配时调用失败。
- Native 返回一个 JSON envelope。单个结果项会解码为 number/string，多个结果项会保留为带 `type`、`value` 和可选 `bits` 的数组；无结果解码为 `undefined`。`i64` 结果使用 string，浮点结果可能解码为 `NaN` 或正/负 `Infinity`。
- 参数归一、WASM JSON 无效、native 返回 `success: false`、模块加载失败或导出调用失败都会使 Promise 拒绝，错误优先采用 envelope 的 `message`。
- WASM 模块按容器包名和 module ID 缓存；模块字节内容变化时宿主关闭旧句柄并重新加载，包卸载时清理对应缓存。
- 注册入口收集期不能执行 WASM 调用；模块、导出函数和 ABI 的有效性由 manifest 解析与宿主 WASM bridge 校验。

相关页面：[包 Manifest 与资源格式](../02_package_model/package_format.md)、[Hook 参考](../05_hooks/index.md)、[API 版本规则](../09_compatibility/api_versions.md)。
