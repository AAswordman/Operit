---
title: 全局运行时 API
status: complete
---

# 全局运行时 API

ToolPkg 的 JS context 会把全局 API 注入到脚本作用域。`examples/types/index.d.ts` 为这些对象提供 TypeScript 声明；它们不是 ECMAScript 内建 API。仓库模块页面解释 `Tools.*` 子模块的方法契约。

## 声明文件入口

仓库项目可在 TypeScript 编译配置中包含 `examples/types/index.d.ts`。该入口重导出 `core`、`results`、`tool-types`、`toolpkg`、`java-bridge`、Android、UI、Compose DSL 和工具模块声明，并把一部分对象提升为全局类型/值。

- `ToolPkg`：包注册、Hook、资源、IPC、WASM，见[注册 API](./registry.md)。
- `Tools`：工具模块集合，见[模块 API 索引](../04_modules/index.md)。
- `Java`/`Kotlin`、`NativeInterface`：Java Bridge 和底层原生桥，见[Java Bridge](../07_types_and_libraries/java_bridge.md)。

## 工具调用

### `toolCall` 参数类型

```ts
interface ToolParams {
  [key: string]: string | number | boolean | object;
}

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

### 重载

```ts
toolCall<T extends string>(toolType: string, toolName: T, toolParams?: ToolParams)
  : Promise<ToolReturnType<T>>;
toolCall<T extends string>(toolName: T, toolParams?: ToolParams)
  : Promise<ToolReturnType<T>>;
toolCall<T extends string>(config: ToolConfig & { name: T })
  : Promise<ToolReturnType<T>>;
toolCall<T extends string, TIntermediate = unknown>(
  toolType: string,
  toolName: T,
  toolParams: ToolParams | undefined,
  options: ToolCallOptions<TIntermediate>
): Promise<ToolReturnType<T>>;
toolCall<T extends string, TIntermediate = unknown>(
  toolName: T,
  toolParams: ToolParams | undefined,
  options: ToolCallOptions<TIntermediate>
): Promise<ToolReturnType<T>>;
toolCall(toolName: string): Promise<any>;
```

调用形式：

- `toolCall(toolName, params?)`：按工具名调用，由宿主解析工具类型。
- `toolCall(toolType, toolName, params?)`：显式给出工具类型和名称。
- `toolCall({ type?, name, params?, onIntermediateResult? })`：配置对象形式。
- 带第四/第三个 `options` 参数的重载可接收中间结果回调。
- 单参数兼容重载返回 `Promise<any>`；优先传入有类型映射的工具名和参数，以获取 `ToolReturnType<T>`。

`ToolReturnType<T>` 由 `examples/types/tool-types.d.ts` 的 `ToolResultMap` 按工具名条件映射。底层是异步桥接；`Promise.all()` 只并发等待 Promise，不承诺宿主 executor 真正并行。并行能力取决于对应工具执行器。

### `complete(result): void`

显式提交脚本最终结果。传入值必须能序列化为 JSON；Java Bridge 句柄由运行时按桥接句柄格式序列化。普通导出函数也可以直接 `return` 结果；不要在一次执行中把 `complete()` 当作进度 API。

### `sendIntermediateResult(result): void`

向当前工具调用发送中间结果，不结束最终执行。调用方可以通过 `toolCall` 的 `onIntermediateResult` 接收。对于流式文本，结果格式必须与调用工具的执行器中间结果协议一致；消息处理插件和 AI Provider 的具体 chunk 结构见各自 Hook 页面。

## 当前执行上下文

### `getEnv(key): string | undefined`

读取当前包环境变量。不存在的 key 返回 `undefined`；是否可见取决于当前包配置和执行来源。

### `getState(): string | undefined`

读取当前调用关联的状态字符串；没有关联状态时返回 `undefined`。具体状态产生方由调用该工具的宿主工作流决定。

### `getLang(): string`

返回当前运行上下文使用的语言标签，供插件选择语言文本。它不是系统地区/时区接口。

### `getCallerName(): string | undefined`

返回当前调用方名称；没有调用方身份时为 `undefined`。

### `getChatId(): string | undefined`

返回当前运行上下文绑定的聊天 ID；后台任务或非聊天调用可没有该值。

### `getCallerCardId(): string | undefined`

返回当前调用上下文关联的角色卡 ID；未绑定时为 `undefined`。

### `getPluginConfigDir(pluginId?): string`

解析 ToolPkg/插件持久配置目录。可显式传 `pluginId`；省略时从当前调用上下文确定目标。路径由 native bridge 提供，目标无法解析或目录不可用时抛错。

`OPERIT_DOWNLOAD_DIR` 是 Operit 下载目录常量；`OPERIT_CLEAN_ON_EXIT_DIR` 是退出时清理目录常量。需要持久保存的数据不要放进临时清理目录。

## 聚合对象 `Tools`

```ts
const Tools: {
  Files: typeof Files;
  Net: typeof Net;
  System: typeof System;
  SoftwareSettings: typeof SoftwareSettings;
  UI: typeof UI;
  FFmpeg: typeof FFmpeg;
  Tasker: Tasker.Runtime;
  Workflow: Workflow.Runtime;
  Chat: typeof Chat;
  Memory: typeof Memory;
  calc(expression: string): Promise<CalculationResultData>;
}
```

每个子对象的方法和结果契约以[模块 API 索引](../04_modules/index.md)中的对应页面为准。`Tools.Tasker` 与 `Tools.Workflow` 指向各自的 Runtime API，不是静态模块描述对象。

## 工具函数和辅助对象

### `_`

当前声明的轻量工具集：

- `isEmpty(value): boolean`
- `isString(value): boolean`
- `isNumber(value): boolean`
- `isBoolean(value): boolean`
- `isObject(value): boolean`
- `isArray(value): boolean`
- `forEach(collection, iteratee)`：遍历数组或对象。
- `map(collection, iteratee)`：遍历并返回映射数组。

它不是完整 Lodash；只应依赖声明文件列出的成员。

### `dataUtils`
- `parseJson(text): any | null`：成功时返回 `JSON.parse(text)` 的值；解析异常被捕获并返回 `null`，所以合法 JSON 文本 `null` 与解析失败都可能得到 `null`。
- `stringifyJson(value): string`：成功时返回 `JSON.stringify(value)`；JSON 序列化抛错时返回字符串 `"{}"`。若顶层输入按 `JSON.stringify` 会得到 `undefined`，当前 wrapper 会直接返回 `undefined`，与声明的 `string` 不符。
- `formatDate(value?: Date | string): string`：使用本地时区格式化为 `YYYY-MM-DD HH:mm:ss`；省略或传入 falsy 值时使用当前时间。无效日期不会抛出校验错误，会生成包含 `NaN` 的日期文本。

### `exports`

CommonJS 风格导出表。主模块用它暴露 `registerToolPkg()` 和注册回调；子模块函数通过 ToolPkg 注册引用解析器绑定到对应导出。

## 全局平台对象

`index.d.ts` 还将以下对象声明为全局值：

- `Intent`、`IntentFlag`、`IntentAction`、`IntentCategory`
- `Android`、`UINode`、`UI`
- `Icons`
- `Java`、`Kotlin`
- `NativeInterface`

这些对象的方法不在本页重复；分别见[Android API](../04_modules/android.md)、[UI API](../04_modules/ui.md)、[Material Icons 和 Compose DSL](../06_ui_and_compose/index.md)、[Java Bridge](../07_types_and_libraries/java_bridge.md)。