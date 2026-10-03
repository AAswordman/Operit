---
title: QuickJS 全局运行时
status: draft
---

# QuickJS 全局运行时

`quickjs-runtime.d.ts` 为 QuickJS 全局对象提供 TypeScript 类型；实际兼容实现位于 `quickjs` 模块的 `QuickJsNativeCompatScriptBuilder` 和 `QuickJsNativeHostDispatcher`。这些 API 不需要从 npm 导入。

## Console

```ts
interface Console {
  log(...data: any[]): void;
  info(...data: any[]): void;
  warn(...data: any[]): void;
  error(...data: any[]): void;
  debug(...data: any[]): void;
}
```

如果 QuickJS 已有对应函数，兼容层会保留它；否则会把参数转成字符串后通过 `NativeInterface.__call("console.<level>", args)` 转发。字符串原样传递，其他值优先使用 `JSON.stringify()`，序列化失败时退回 `String(value)`。当前 `QuickJsNativeHostDispatcher` 对 `console.*` 直接返回 `null`，不调用通用 `forwardCall`，因此这条 polyfill 路径不会保证产生宿主日志。Console 不返回业务结果，不应用它传递工具输出。

## Timer API

```ts
type TimerHandler = ((...args: any[]) => any) | string;
function setTimeout(handler: TimerHandler, timeout?: number, ...args: any[]): number;
function clearTimeout(timerId?: number): void;
function setInterval(handler: TimerHandler, timeout?: number, ...args: any[]): number;
function clearInterval(timerId?: number): void;
```

- `handler` 可以是函数或字符串；字符串会在回调时交给 `eval()` 执行。其他类型会在创建 timer 时抛出 `Timer callback must be a function or string`。
- `timeout` 会转成数字、向下取整并限制为非负值；非有限值按 `0` 处理。创建 timer 时返回运行时递增分配的整数 ID。
- 附加参数由 timer 包装器保存，并在触发时作为回调参数传入。
- timer 由单独的 native scheduler 调度，再派发回 QuickJS；实际回调时间受线程调度影响，不应视为精确时钟。
- `setInterval` 使用固定周期调度，周期下限为 `1 ms`，即使传入 `0` 也不会形成零周期。
- `clearTimeout` 与 `clearInterval` 共用取消逻辑。省略 ID 会尝试取消 ID `0`，不会取消全部 timers。
- timer callback 的异常会交给 `reportDetailedError`；该函数不可用时写入 `console.error`。异常不会作为创建 timer 的同步异常返回。
- native scheduler 不可用时，创建 timer 会抛出 `NativeInterface.scheduleTimer is unavailable`。QuickJS host 关闭时会取消仍排队的 native timer。

这套实现没有提供 `AbortSignal` 或 timer Promise。需要取消单个任务时保存返回的 ID 并调用相应 clear 函数。

```js
const timerId = setTimeout((name) => console.log(`ready: ${name}`), 250, "worker");
clearTimeout(timerId);
```

## ToolPkg 工具参数边界

包工具通过 `METADATA.tools[].parameters[]` 声明参数类型；在 ToolPkg 包工具调用路径中，Android 宿主会先按 metadata 转换参数，再调用 QuickJS 函数。类型转换不属于 `quickjs-runtime.d.ts` 的全局对象，也不是 QuickJS polyfill 的行为。直接脚本执行入口使用的参数路径不同。完整类型规则与失败行为见[包格式中的参数转换说明](../02_package_model/package_format.md#参数类型与工具调用转换)。

## Microtask

`queueMicrotask(callback): void` 仅在全局尚无此函数时补为 `Promise.resolve().then(callback)`。它不创建 native timer；callback 异常遵循 Promise reaction 的 QuickJS 行为。

## Storage

`localStorage` 与 `sessionStorage` 是两个相互独立的内存对象，均实现：

| 成员 | 契约 |
| --- | --- |
| `length` | 当前键数，只读 |
| `key(index)` | 按当前对象键枚举顺序返回键；索引越界或为负数时返回 `null` |
| `getItem(key)` | 将 key 转成字符串；键不存在时返回 `null` |
| `setItem(key, value)` | 将 key 和 value 都转成字符串后写入 |
| `removeItem(key)` | 删除字符串化后的键；键不存在时无效果 |
| `clear()` | 清空当前 Storage，不影响另一个 Storage |

实现没有 Android 持久化后端，也没有配额或跨进程同步。数据只存在于承载这些对象的 JS global context 生命周期中；不要用它保存必须跨引擎重建保留的数据。

## Performance

`performance.now(): number` 如果已有实现则保留。QuickJS 兼容层在其缺失时记录一次 `Date.now()`，之后返回 `Date.now() - start`。该回退以墙上时钟计算，不保证单调或亚毫秒精度。

## 全局声明

`quickjs-runtime.d.ts` 通过 `declare global` 扩展 `Console`、`Storage`、`Performance` 并声明全局变量，最后用 `export {}` 保持模块作用域。导入该声明即可获得类型；无需在 ToolPkg 脚本里声明同名变量。

## 实现来源

- `examples/types/quickjs-runtime.d.ts`
- `quickjs/src/main/java/com/ai/assistance/operit/core/tools/javascript/QuickJsNativeCompatScriptBuilder.kt`
- `quickjs/src/main/java/com/ai/assistance/operit/core/tools/javascript/QuickJsNativeHostDispatcher.kt`
