# API 文档：`tasker.d.ts`

`tasker.d.ts` 只定义 `Tools.Tasker.triggerEvent()` 这一项 Tasker 集成能力。它把参数交给宿主注册的 `trigger_tasker_event` 工具，再通过 Tasker Plugin Library 发出事件；成功表示事件请求已提交，不表示 Tasker 侧任务已经执行完毕。

## 运行时入口

```ts
Tools.Tasker
```

`JsTools` 中的 facade 实现为：

```ts
triggerEvent: (params) => toolCall('trigger_tasker_event', params || {})
```

因此 `undefined` 或 `null` 会被替换为空对象后调用；公开声明仍要求传入 `TriggerTaskerEventParams`。工具调用是异步 Promise：成功时解析为宿主状态字符串，工具失败时 rejected。

相关实现位于：

- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsTools.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/ToolRegistration.kt`
- `app/src/main/java/com/ai/assistance/operit/integrations/tasker/AIAgentTasker.kt`

## `Tasker.TriggerTaskerEventParams`

```ts
interface TriggerTaskerEventParams {
  task_type: string
  arg1?: string
  arg2?: string
  arg3?: string
  arg4?: string
  arg5?: string
  args_json?: string
}
```

### `task_type`

事件类型标识，必填且不能是空白字符串。宿主把它传给 Tasker Plugin Library 的 event update，Tasker 侧应使用相同的事件配置来接收。

缺失或空白 `task_type` 时，`trigger_tasker_event` 返回失败工具结果，`Tools.Tasker.triggerEvent()` 会 rejected，并携带宿主的缺少必填参数错误。

### `arg1` 到 `arg5`

五个可选字符串槽位。宿主只从参数 map 中读取这五个字段并填入 `AIAgentActionUpdate`；缺失值在 Tasker 输出对象中归一为空字符串。

### `args_json`

声明把它定义为可选 JSON 字符串，但当前实现不会把它原样作为 Tasker 的最终 JSON 字段传递。宿主先收集除 `task_type` 外的参数，再执行：

```kotlin
Gson().toJson(args)
```

结果写入 Tasker update 的 `args_json` 字段。因此 Tasker 侧收到的 JSON 是参数 map 的序列化结果；如果调用同时传入 `args_json`，它会作为该 map 中的一个字符串值再次嵌套，而不是被解析成对象。

例如：

```ts
await Tools.Tasker.triggerEvent({
  task_type: 'import_payload',
  arg1: 'daily',
  args_json: JSON.stringify({ force: true })
});
```

Tasker 侧的 `args_json` 形态近似为：

```json
{"arg1":"daily","args_json":"{\"force\":true}"}
```

实际 JSON 字段顺序由宿主参数 map 的构造顺序决定。

## `Tools.Tasker.triggerEvent(params)`

```ts
triggerEvent(params: TriggerTaskerEventParams): Promise<string>
```

调用过程如下：

1. facade 把参数传给 `trigger_tasker_event`。
2. 宿主校验 `task_type`，并把其余参数整理为字符串 map。
3. `triggerAIAgentAction()` 创建 `AIAgentActionUpdate`，填入 `task_type`、`arg1` 到 `arg5` 和生成的 `args_json`。
4. 宿主通过 `ActivityConfigAIAgentAction.requestQuery()` 提交 Tasker 插件事件。
5. 工具成功时返回本地化的“已触发 Tasker 事件”状态字符串。

该调用只确认宿主已接受并提交事件请求。它不会等待 Tasker 监听的任务、工作流或后续动作完成，也不会返回 Tasker 任务的输出值。

### 失败语义

以下情况会导致 Promise rejected：

- `task_type` 缺失或为空白。
- Tasker Plugin Library 或宿主提交事件时抛出异常。
- native 工具调用本身失败。

失败结果的具体 message 使用宿主当前语言的本地化文案；它不是 `results.d.ts` 中的结构化结果对象。

## 示例

### 触发简单事件

```ts
const status = await Tools.Tasker.triggerEvent({
  task_type: 'sync_notes',
  arg1: 'daily',
  arg2: 'force'
});
console.log(status);
```

### 传递五个槽位

```ts
await Tools.Tasker.triggerEvent({
  task_type: 'process_item',
  arg1: 'one',
  arg2: 'two',
  arg3: 'three',
  arg4: 'four',
  arg5: 'five'
});
```

### 传递结构化数据

```ts
await Tools.Tasker.triggerEvent({
  task_type: 'import_payload',
  args_json: JSON.stringify({
    source: 'assistance',
    timestamp: Date.now(),
    items: ['a', 'b', 'c']
  })
});
```

由于当前宿主会再次序列化整个参数 map，Tasker 侧应按嵌套字符串读取 `args_json`，而不是假设它已经是 JSON 对象。

## 声明与运行时差异

- `triggerEvent()` 的参数在声明中是必填对象，但 facade 对 nullish 参数使用 `{}` 兜底。
- `task_type` 是宿主唯一明确必填且非空白校验的字段。
- `args_json` 不会按 JSON 解析；宿主把除 `task_type` 外的参数 map 再序列化为 Tasker update 的 `args_json`。
- `arg1` 到 `arg5` 以字符串字段写入 Tasker update；缺失值在 Tasker 输出对象中变为空字符串。
- Promise 成功值是宿主本地化状态文本，不是 Tasker 任务结果。
- 成功只代表事件提交，不代表 Tasker 侧动作完成。
- Tasker 接收方向和 `Workflow` 的 Tasker 触发方向是两条不同链路；本页只描述 `Tools.Tasker.triggerEvent()` 主动向 Tasker 发事件的能力。

## 相关声明与源码

- `examples/types/tasker.d.ts`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsTools.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/ToolRegistration.kt`
- `app/src/main/java/com/ai/assistance/operit/integrations/tasker/AIAgentTasker.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsInitRuntimeScriptBuilder.kt`
