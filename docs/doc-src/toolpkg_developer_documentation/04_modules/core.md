# API 文档：`core.d.ts`

`core.d.ts` 是整个脚本运行环境的基础定义文件。它提供了工具调用、结果结构、底层桥接接口，以及若干全局工具对象。

## 作用

当前定义覆盖：

- 通用参数与结果类型。
- `toolCall()` 与 `complete()`。
- `NativeInterface` 原生桥接。
- 轻量工具对象 `_` 与 `dataUtils`。
- CommonJS 风格的 `exports`。

## 基础类型

### `ToolParams`

```ts
interface ToolParams {
  [key: string]: string | number | boolean | object;
}
```

用于描述工具调用参数。

### `ToolConfig`

```ts
interface ToolConfig {
  type?: string;
  name: string;
  params?: ToolParams;
  onIntermediateResult?: (value: unknown) => void;
}
interface ToolCallOptions<TIntermediate = unknown> {
  onIntermediateResult?: (value: TIntermediate) => void;
}
```
配置对象和显式 `options` 都允许提供中间结果回调。带显式 `options` 的两个 `toolCall` 重载是声明中明确的中间结果用法；配置对象字段是否被当前宿主调用适配路径消费，仍以实现为准。
用于对象形式的 `toolCall()`。

### `BaseResult`

```ts
interface BaseResult {
  success: boolean;
  error?: string;
}
```

### `StringResult` / `BooleanResult` / `NumberResult`

这三种结果都继承 `BaseResult`，并带有：

- `data`
- `toString()`

### `ToolResult`

```ts
type ToolResult = StringResult | BooleanResult | NumberResult | (BaseResult & { data: any })
```

### `ToolReturnType<T>`

它会根据 `tool-types.d.ts` 中的 `ToolResultMap` 为 `toolCall()` 推导返回类型。

## 全局函数

### `toolCall()`

`core.d.ts` 中一共定义了 6 个重载：

```ts
toolCall<T extends string>(toolType: string, toolName: T, toolParams?: ToolParams)
toolCall<T extends string>(toolName: T, toolParams?: ToolParams)
toolCall<T extends string>(config: ToolConfig & { name: T })
toolCall<T extends string, TIntermediate = unknown>(
  toolType: string, toolName: T, toolParams: ToolParams | undefined,
  options: ToolCallOptions<TIntermediate>
)
toolCall<T extends string, TIntermediate = unknown>(
  toolName: T, toolParams: ToolParams | undefined,
  options: ToolCallOptions<TIntermediate>
)
toolCall(toolName: string)
```

最常用的是后两种：

```ts
const result = await toolCall('read_file', { path: '/sdcard/a.txt' });
```

或者：

```ts
const result = await toolCall({
  name: 'http_request',
  params: { url: 'https://example.com' }
});
```

补充说明：

- `toolCall()` 运行时走的是异步桥接，但 JS 回调的分发仍会回到同一个 QuickJS 运行时线程。
- `Promise.all([...toolCall(...)])` 只表示“同时等待多个 Promise”，不保证底层工具一定并行执行；是否真正并行，取决于对应工具执行器本身。
- 如果需要中间结果，请使用带 `onIntermediateResult` 的重载。

### `complete(result)`

```ts
complete<T>(result: T): void
```

结束脚本执行并返回结果。

补充说明：

- 导出函数既可以直接 `return result`，也可以显式调用 `complete(result)`。
- Java Bridge 契约以 [Java Bridge](../07_types_and_libraries/java_bridge.md) 为准。
- `complete(result)` 适合你需要手动结束或配合 emitter 使用的场景。

## `NativeInterface`

`NativeInterface` 是更底层的原生桥接接口。多数业务代码优先使用 `Tools.*`、`toolCall()` 或全局对象；只有在需要桥接级能力时再直接用它。

### 工具调用与日志
| 方法 | 返回值 | 契约 |
| --- | --- | --- |
| `callTool(toolType, toolName, paramsJson)` | `string` | 同步执行工具并返回 JSON 字符串。`toolType` 为空或 `default` 时不加类型前缀；工具名为空或参数 JSON 无效时返回 `success: false` 和 `message`。成功结果包含 `success`、`data`，二进制结果还可能包含 `dataType: "base64"`。 |
| `callToolAsync(callbackId, toolType, toolName, paramsJson)` | `void` | 在后台线程执行；结束后调用 `window[callbackId](result, isError)`。参数 JSON 解析/准备失败也通过同一回调传递错误结果。 |
| `callToolAsyncStreaming(callbackId, intermediateCallbackId, toolType, toolName, paramsJson)` | `void` | 在后台线程收集工具流；非最终项调用 `window[intermediateCallbackId](result, isError)`，最终项调用 `window[callbackId](result, isError)`。工具没有产生任何结果时最终回调收到失败结果。 |
| `logInfo(message)` | `void` | 写入 ToolPkg 日志。 |
| `logError(message)` | `void` | 写入错误级 ToolPkg 日志。 |
| `logDebug(message, data)` | `void` | 当前 `JsEngine` 实现为空操作，不保证产生日志。 |

同步/异步结果不是 `ToolResult` 对象本身，而是 JSON 文本。失败字段使用运行时的 `message`，不能只按 `BaseResult.error` 读取。大二进制结果可能被宿主放入一次性句柄注册表，具体阈值不是声明契约。

### ToolPkg 注册桥
| 方法 | 说明 | 版本 |
| --- | --- | --- |
| `registerToolPkgToolboxUiModule(specJson)` | 注册 Toolbox UI module 描述。 | 基线 |
| `registerToolPkgAppLifecycleHook(specJson)` | 注册应用生命周期 Hook。 | 基线 |
| `registerToolPkgMessageProcessingPlugin(specJson)` | 注册消息处理插件。 | 基线 |
| `registerToolPkgXmlRenderPlugin(specJson)` | 注册 XML render 插件。 | 基线 |
| `registerToolPkgInputMenuTogglePlugin(specJson)` | 注册输入菜单 toggle 插件。 | 基线 |
| `registerToolPkgChatInputHook(specJson)` | 注册聊天输入 Hook。 | 基线 |
| `registerToolPkgChatMessageHook(specJson)` | 注册聊天消息 Hook。 | 基线 |
| `registerToolPkgChatMessageMenuItem(specJson)` | 注册消息长按菜单项。 | `ToolPkg API 1.0.1` |
| `registerToolPkgChatRuntimeHook(specJson)` | 注册聊天运行态 Hook。 | `ToolPkg API 1.0.1` |

每个 `specJson` 都必须是对应声明对象的 JSON 文本；字段、触发时机和返回值消费规则见[注册 API](../03_runtime/registry.md)及[Hook 参考](../05_hooks/index.md)。这些入口面向当前 ToolPkg 注册会话，不能当作普通业务调用 API。

### 其他宿主入口
- `getPluginConfigDir(pluginId): string`：返回插件/ToolPkg 持久配置目录。运行时会去除 ID 首尾空白；空 ID 或路径解析失败时返回空字符串，不抛出异常。
### 图片注册
- `registerImageFromBase64(base64, mimeType): string`
- `registerImageFromPath(path): string`
声明注释描述它们应返回可嵌入消息的 `<link type="image" id="...">`，但当前 `JsEngine` 成功路径丢弃了 `ImagePoolManager` 返回的 ID 并返回空字符串；失败时返回 `[image registration failed]` 或带错误消息的字符串。这是已确认的声明/运行时差异，不能把当前返回值当作有效图片链接。
### 错误上报
- `reportError(errorType, errorMessage, errorLine, errorStack): void`
将类型、消息、行号和堆栈拼接为错误日志；它不抛出结构化异常，也不替代 `complete()` 或工具失败回调。
### Java / Kotlin 桥接
| 方法 | 输入 | 返回与失败 |
| --- | --- | --- |
| `javaClassExists(className): boolean` | 完整类名字符串。 | 直接返回是否可加载；不存在或加载异常返回 `false`。 |
| `javaLoadDex(path, optionsJson): string` / `javaLoadJar(path, optionsJson): string` | 路径字符串和 JSON 对象文本；JAR 需为 Android 可执行归档。 | 返回 JSON envelope：成功为 `{success:true,data:...}`，失败为 `{success:false,message:...}`。加载选项与缓存行为见 [Java Bridge](../07_types_and_libraries/java_bridge.md)。 |
| `javaListLoadedCodePaths(): string` | 无。 | 返回同一 envelope，`data` 为当前 engine session 已加载 DEX/JAR 条目数组。 |
| `javaGetApplicationContext(): string` / `javaGetCurrentActivity(): string` | 无。 | 返回 envelope；`data` 是 `{__javaHandle,__javaClass}` 句柄标记。没有当前 Activity 时返回 `{success:false,message:...}`。 |
| `javaNewInstance(className, argsJson): string` | 类名和 JSON 数组文本；数组元素是构造参数。 | 返回 envelope；成功 `data` 是转换后的返回值或 Java 句柄标记。 |
| `javaCallStatic(className, methodName, argsJson): string` / `javaCallInstance(instanceHandle, methodName, argsJson): string` | 类名/实例句柄、方法名和 JSON 参数数组文本。 | 返回 envelope；成功 `data` 是桥接转换值。 |
| `javaHasInstanceMethod(instanceHandle, methodName): string` | 实例句柄与方法名。 | 返回 envelope；成功时 `data` 是布尔值。 |
| `javaGetStaticField(className, fieldName): string` / `javaGetInstanceField(instanceHandle, fieldName): string` | 类名/实例句柄与字段或属性名。 | 返回 envelope；成功 `data` 是转换后的字段/属性值。 |
| `javaSetStaticField(className, fieldName, valueJson): string` / `javaSetInstanceField(instanceHandle, fieldName, valueJson): string` | 类名/实例句柄、字段或属性名，以及一个 JSON 值文本。 | 返回 envelope；成功 `data` 是写入值的桥接转换结果。 |

除 `javaClassExists` 外，原生桥失败 payload 的字段是 `message`，并非 `core.d.ts` 注释中所写的 `error`；调用方应先检查 `success` 再读取 `data` 或 `message`。


补充说明：

- 对脚本开发者承诺的高层 Java Bridge 接口，统一以 [Java Bridge](../07_types_and_libraries/java_bridge.md) 为准。
- Java 实例句柄的解绑属于运行时内部生命周期管理，不再提供公开的 `release` / `releaseAll` 脚本接口。
- `Java.implement(...)` / `Java.proxy(...)` 产生的 JS 回调对象改为运行时自动解绑，旧的脚本侧手动释放接口已移除。
- `obj.methodName()` 是首选用法，运行时会优先把实例成员按方法来解释，尽量保证语法糖可用。
- `obj.call('methodName', ...)` 仍然保留，主要用于极少数字段/方法同名冲突或底层调试场景。
- `Java.implement(...)` 创建的 JS 回调实际仍在 QuickJS 运行时线程执行，不会把 JS 代码真正迁移到 Java 子线程里运行。

## 全局工具对象

### `_`

当前只声明了一个轻量 Lodash 风格子集：

- `isEmpty`
- `isString`
- `isNumber`
- `isBoolean`
- `isObject`
- `isArray`
- `forEach`
- `map`

### `dataUtils`

- `parseJson(jsonString)`
- `stringifyJson(obj)`
- `formatDate(date?)`

### `exports`

```ts
var exports: { [key: string]: any }
```

用于 CommonJS 风格导出。

## 示例

### 使用 `toolCall()`

```ts
const file = await toolCall('read_file', {
  path: '/sdcard/demo.txt'
});
complete(file);
```

### 直接记录日志

```ts
NativeInterface.logInfo('start');
NativeInterface.logDebug('payload', JSON.stringify({ ok: true }));
```

### 注册图片

```ts
const registrationResult = NativeInterface.registerImageFromPath('/sdcard/demo.png');
complete({ registrationResult });
```

## 与 `index.d.ts` 的关系

`core.d.ts` 定义的是“基础能力”；`index.d.ts` 会在此基础上把更多对象和辅助函数挂到全局作用域里，例如：

- `sendIntermediateResult`
- `getEnv`
- `Tools`
- `Java`
- `Kotlin`

这些补充内容请结合[全局运行时 API](../03_runtime/global_api.md)和[模块 API 索引](./index.md)一起看。

## 相关文件

- `examples/types/core.d.ts`
- `examples/types/tool-types.d.ts`
- [全局运行时 API](../03_runtime/global_api.md)
- [模块 API 索引](./index.md)
