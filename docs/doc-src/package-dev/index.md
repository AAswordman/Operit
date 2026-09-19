# API 文档：`index.d.ts`

`index.d.ts` 是整个包开发类型系统的总入口。它做三件事：

- 重导出各个 `.d.ts` 文件中的类型。
- 把常用对象和函数注入全局作用域。
- 组装运行时常用的 `Tools` 命名空间。

如果你只在脚本顶部引用一个类型入口，通常就是它。

## 推荐引用方式

```ts
/// <reference path="./types/index.d.ts" />
```

这样 IDE 可以直接识别全局 API 和结果类型。

## `index.d.ts` 做了什么

### 1. 重导出核心类型

它会重导出：

- `core.d.ts`
- `results.d.ts`
- `tool-types.d.ts`
- `java-bridge.d.ts`
- `toolpkg.d.ts`
- `compose-dsl.d.ts`
- `compose-dsl.material3.generated.d.ts`

### 2. 导出主要命名空间

它还显式导出以下模块：

- `Net`
- `System`
- `SoftwareSettings`
- `UI`
- `UINode`
- `FFmpegVideoCodec` / `FFmpegAudioCodec` / `FFmpegResolution` / `FFmpegBitrate`
- `Tasker`
- `Workflow`
- `ToolPkg`
- `Chat`
- `Memory`
- Android 相关类与枚举

### 3. 注入全局对象与函数

这也是 `index.d.ts` 最重要的职责：你在脚本里通常不需要 `import`，就能直接使用这些对象。

## 全局可用的函数

### 工具调用与结果返回

- `toolCall(...)`
- `complete(result)`
- `sendIntermediateResult(result)`

### 上下文读取

- `getEnv(key)`
- `getState()`
- `getLang()`
- `getCallerName()`
- `getChatId()`
- `getCallerCardId()`

## 全局可用的对象与类

### Android / UI 相关

- `Intent`
- `IntentFlag`
- `IntentAction`
- `IntentCategory`
- `UINode`
- `Android`

### 运行时工具对象

- `Tools`
- `_`
- `dataUtils`
- `exports`
- `NativeInterface`
- `Java`
- `Kotlin`

### 常量

- `OPERIT_DOWNLOAD_DIR`
- `OPERIT_CLEAN_ON_EXIT_DIR`

## `Tools` 命名空间

`Tools` 会把各模块运行时入口组装到一个对象下：

```ts
const Tools: {
  Files
  Net
  System
  SoftwareSettings
  UI
  FFmpeg
  Tasker
  Workflow
  Chat
  Memory
  calc
}
```

其中：

- `Tools.Tasker` 对应 `Tasker.Runtime`
- `Tools.Workflow` 对应 `Workflow.Runtime`
- `Tools.calc(expression)` 返回 `Promise<CalculationResultData>`

## 全局类型

`index.d.ts` 还把大量结果类型提升到了全局作用域里，例如：

- `CalculationResultData`
- `SleepResultData`
- `SystemSettingData`
- `AppOperationData`
- `AppListData`
- `DeviceInfoResultData`
- `UIPageResultData`
- `UIActionResultData`
- `FileContentData`
- `HttpResponseData`
- `VisitWebResultData`
- `WorkflowDetailResultData`
- `ModelConfigResultItem`
- `MemoryLinkResultData`

此外，它还把以下桥接类型作为全局类型暴露：

- `ComposeDslContext`
- `ComposeDslScreen`
- `ComposeNode`
- `ComposeCanvasCommand`
- `JavaBridgeApi`
- `JavaBridgeClass`
- `JavaBridgeInstance`
- `JavaBridgeHandle`
- `JavaBridgePackage`
- `JavaBridgeJsInterfaceMarker`
- `JavaBridgeJsInterfaceImpl`
- `JavaBridgeJsMethod`
- `JavaBridgeInterfaceRef`
- `JavaBridgeCallbackResult`

## 示例

### 典型脚本写法

```ts
/// <reference path="./types/index.d.ts" />

const page = await UINode.getCurrentPage();
const title = page.findByText('设置');

if (title) {
  await title.click();
}

const response = await Tools.Net.httpGet('https://example.com');
complete({
  ok: true,
  status: response.statusCode
});
```

### 使用上下文函数

```ts
const apiKey = getEnv('OPENAI_API_KEY');
const state = getState();
const chatId = getChatId();

sendIntermediateResult({ state, chatId });
```

## 本目录文档索引

当前 `docs/doc-src/package-dev` 已同步以下文件：

- `android.md`
- `chat.md`
- `core.md`
- `cryptojs.md`
- `ffmpeg.md`
- `files.md`
- `jimp.md`
- `memory.md`
- `network.md`
- `okhttp.md`
- `results.md`
- `software_settings.md`
- `system.md`
- `tasker.md`
- `tool-types.md`
- `host-capabilities.md`
- `toolpkg.md`
- `ui.md`
- `workflow.md`

## 尚未单独展开的类型文件

`examples/types` 中还有一些高级或扩展定义目前未在本目录单独成文，例如：

- `compose-dsl.d.ts`
- `compose-dsl.material3.generated.d.ts`
- `java-bridge.d.ts`
- `pako.d.ts`

这些类型已经由 `index.d.ts` 重导出，使用时仍然以对应 `.d.ts` 为准。

## 相关文件

- `examples/types/index.d.ts`
- `docs/doc-src/package-dev/core.md`
- `docs/doc-src/package-dev/results.md`
- `docs/doc-src/package-dev/toolpkg.md`
