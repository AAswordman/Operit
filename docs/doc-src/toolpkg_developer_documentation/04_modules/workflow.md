# API 文档：`workflow.d.ts`

`workflow.d.ts` 描述的是 `Workflow` 类型命名空间，以及运行时入口 `Tools.Workflow`。它围绕工作流的节点、连接、增删改查与触发执行展开。

## 作用

当前定义覆盖：

- 工作流节点与连接类型。
- 创建、读取、更新、局部补丁、删除、触发。
- 工作流详情和列表结果类型。

## 类型命名空间与运行时入口

类型命名空间：

```ts
Workflow
```

运行时入口：

```ts
Tools.Workflow
```

注意：`Tools.Workflow` 暴露的是 `Workflow.Runtime` 接口里的方法。

## 核心类型

### 节点类型

`Workflow.Node` 是以下联合类型之一：

- `Workflow.Trigger`
- `Workflow.Execute`
- `Workflow.Condition`
- `Workflow.Logic`
- `Workflow.Extract`

### `Workflow.NodeInput`

这是创建或更新工作流时最重要的输入类型。公共字段包括：

- `id?`
- `type: 'trigger' | 'execute' | 'condition' | 'logic' | 'extract'`
- `name?`
- `description?`
- `position?`

按节点类型还可带上不同字段：

- 触发器：`triggerType`、`triggerConfig`
- 执行节点：`actionType`、`actionConfig`、`jsCode`
- 条件节点：`left`、`operator`、`right`
- 提取节点：`source`、`mode`、`expression`、`group`、`defaultValue` 等
- 逻辑节点：通过 `type: 'logic'` 与操作符表达

### `Workflow.ParameterValueInput`

输入值既可以是：

- 直接字面量：`string | number | boolean | null`
- 引用对象：`{ value?, nodeId?, ref?, refNodeId? }`

### 连接类型

#### `Workflow.ConnectionInput`

- `id?`
- `sourceNodeId?`
- `targetNodeId?`
- `condition?: ConnectionCondition | null`

#### `Workflow.ConnectionCondition`

支持关键字：

- `true`
- `false`
- `on_success`
- `success`
- `ok`
- `on_error`
- `error`
- `failed`

也支持正则条件字符串。

### Patch 类型

局部更新支持：

- `PatchOperation = 'add' | 'update' | 'remove'`
- `NodePatch`
- `ConnectionPatch`
- `PatchParams`

## 运行时 API
### `Tools.Workflow.getAll()`

```ts
getAll(): Promise<WorkflowListResultData>
```

获取全部工作流。

### `Tools.Workflow.create(name, description?, nodes?, connections?, enabled?)`

```ts
create(
  name: string,
  description?: string,
  nodes?: Workflow.NodeInput[] | string | null,
  connections?: Workflow.ConnectionInput[] | string | null,
  enabled?: boolean
): Promise<WorkflowDetailResultData>
```

创建工作流。节点和连接数组也可直接传 JSON 字符串。

### `Tools.Workflow.get(workflowId)`

```ts
get(workflowId: string): Promise<WorkflowDetailResultData>
```

按 ID 读取工作流详情。

### `Tools.Workflow.update(workflowId, updates?)`

```ts
update(workflowId: string, updates?: Omit<Workflow.UpdateParams, 'workflow_id'>): Promise<WorkflowDetailResultData>
```

整体更新工作流，可传 `name`、`description`、`nodes`、`connections` 和 `enabled`。

### `Tools.Workflow.patch(workflowId, patch?)`

```ts
patch(workflowId: string, patch?: Omit<Workflow.PatchParams, 'workflow_id'>): Promise<WorkflowDetailResultData>
```

按 patch 操作局部更新工作流，可传 `name`、`description`、`enabled`、`node_patches` 和 `connection_patches`。

### `Tools.Workflow.setEnabled(workflowId, enabled)`

```ts
setEnabled(workflowId: string, enabled: boolean): Promise<WorkflowDetailResultData>
```

设置工作流启用状态；该 facade 根据布尔值转调 `enable` 或 `disable`。

### `Tools.Workflow.enable(workflowId)` / `Tools.Workflow.disable(workflowId)`

```ts
enable(workflowId: string): Promise<WorkflowDetailResultData>
disable(workflowId: string): Promise<WorkflowDetailResultData>
```

启用或禁用工作流。若当前已经是目标状态，实现返回现有详情，不额外执行状态写入。

### `Tools.Workflow.delete(workflowId)`

```ts
delete(workflowId: string): Promise<string>
```

删除工作流。成功时字符串说明被删除的 ID；ID 为空、记录不存在或删除失败时外层调用失败。

类型定义里该成员写作 `'delete'(...)`，脚本里既可以写 `Tools.Workflow.delete(id)`，也可以更稳妥地写成 `Tools.Workflow['delete'](id)`。

### `Tools.Workflow.trigger(workflowId)`

```ts
trigger(workflowId: string): Promise<string>
```

手动触发工作流。工作流执行失败时外层调用失败；取消调用会向 repository 转发取消请求。


## 解析与更新行为

- `create` 的 `enabled` 缺省为 `true`；`name` 不能为空。`nodes`、`connections` 可传数组或 JSON 字符串，`null`、缺省或空数组表示空集合。
- `update` 只替换已提供且非空的节点/连接 JSON；未提供或空白字符串保留原值。传 `[]` 会清空对应集合。其余未提供字段保留原值；不产生内容或启用状态变化时不会写回。
- `patch` 的 `node_patches` / `connection_patches` 可传数组或 JSON 字符串，操作为 `add`、`update`、`remove`。节点 update 合并已提供字段且不能改变节点 `type`；删除节点同时删除关联连接。连接和节点 patch 引用不存在的 ID、缺少所需对象或使用未知操作时，整个请求失败。
- 节点输入通常应提供 `type`。运行时也会按 `__type` 后缀或节点字段推断类型；无法识别的节点会被跳过。解析节点失败且一个都未得到时，整个输入失败。
- 连接可用节点 ID、索引或名称解析端点；指向不存在节点的连接与自连接会被忽略。非空连接数组若没有任何有效连接则调用失败。
- 节点解析按具体节点类型应用字段默认值；无效的 condition operator 回退到 `EQ`，无效的 logic operator 回退到 `AND`，无效的 extract mode 回退到 `REGEX`。Extract 数值默认值为 `startIndex=0`、`length=-1`、`randomMin=0`、`randomMax=100`、`randomStringLength=8`。
- 运行时 `TriggerNode.triggerType` 还识别声明联合中未列出的 `app_open`。结果中的 `lastExecutionStatus` 来自 Kotlin enum 名称 `SUCCESS`、`FAILED`、`RUNNING`，字段仍应按普通字符串处理。
创建、读取、更新、patch、启停失败时，宿主通常仍返回空 `WorkflowDetailResultData`，并在外层结果提供错误；删除和触发则返回字符串结果。调用端应先检查外层 `success`，再使用 `data` 或字符串结果。

## 节点执行语义（Android 当前实现）

### 触发与依赖调度

- 未指定 `triggerNodeId` 时，只选择 `triggerType == "manual"` 的所有触发节点；指定时按 ID 选择单个触发节点，不要求它是 `manual`。没有触发节点、没有手动入口或指定 ID 不存在时，执行失败。
- 环检测覆盖整个工作流；显式连接和执行/条件/提取节点中的 `NodeReference` 都会形成依赖边。任一部分有环时，即使该部分不在本次触发分支上，也会拒绝运行。调度集由所选触发器正向可达的节点及这些节点的依赖祖先组成，然后按拓扑顺序执行；因此依赖祖先也可能运行，即使它本身不是触发器的后继。
- 触发节点成功结果是 `triggerExtras` 序列化后的 JSON 对象字符串。节点引用读取已成功节点的结果；引用失败节点会抛错，引用未完成节点会抛错，引用跳过节点得到跳过原因。执行参数名会 trim，空白参数名被忽略。
- 执行节点参数若匹配工具 schema，且是非必填参数的静态空白字符串，则会省略；必填参数和节点引用解析出的空字符串仍会传给工具。包工具 schema 查询可能启用并加载对应包，初始化异常在 schema 探测路径中被吞并，最终仍由实际工具调用报告错误。

### 连接条件与失败分支

执行目标节点前，运行时对所有可达入边做“任一条件成立”判断；来源为跳过状态时该边不成立。空条件对 `ConditionNode` / `LogicNode` 等价于 `true`，对其他来源节点表示要求来源成功。`success`、`ok`、`on_success` 匹配成功状态；`error`、`failed`、`on_error` 匹配失败状态。`true` / `false` 将来源成功结果解析为布尔值（接受 `true/1/yes/y/on` 与 `false/0/no/n/off`，其他文本按 `false` 处理）；其余非空条件作为正则表达式匹配来源结果，正则无效时按不匹配处理。

节点执行失败后，调度器仍会处理可运行的后续节点，包括满足错误条件的分支。最终只有在每个失败节点至少存在一条错误条件出边，且对应目标节点已成功时，失败才视为已接管；存在未接管失败时，工作流整体失败。成功边不会接管失败状态。

### 条件、逻辑和提取节点

| 节点 | 当前行为 |
| --- | --- |
| `ConditionNode` | `EQ` / `NE` / `GT` / `GTE` / `LT` / `LTE`：两侧都能解析为数值时按数值比较；两侧都不是数值时按字符串比较；只有一侧是数值时报类型不匹配。`CONTAINS` / `NOT_CONTAINS` 对原字符串执行包含判断。`IN` / `NOT_IN` 先把右值解析为 JSON 数组，失败时按逗号分隔并 trim；非空列表不得混合数值与非数值，左值也必须与列表类型一致。空列表的 `IN` 为 false、`NOT_IN` 为 true。 |
| `LogicNode` | 从所有入边对应的成功结果中读取可识别布尔值，跳过失败、跳过和无法解析的值。`AND` 要求至少一个可用输入且全部为 true；`OR` 有任意 true 即为 true；没有可用输入时两者均为 false。 |
| `ExtractNode: REGEX` | 空 pattern、非法正则、未匹配或捕获组不存在时返回 `defaultValue`；否则返回指定组文本。 |
| `ExtractNode: JSON` | 将 source 解析为 JSON object/array，按点分隔属性及 `[index]` 读取；非法 JSON、路径缺失或结果为 null 时返回 `defaultValue`。取到的 object/array 返回 JSON 文本，标量返回文本值。 |
| `ExtractNode: SUB` | 以 Kotlin 字符串索引截取；`startIndex < 0`、大于字符串长度或 source 为空时返回默认值。负 `length` 表示取到末尾，长度超出末尾时截断；有效位置的 `length=0` 返回空字符串。 |
| `ExtractNode: CONCAT` | 先解析 `source`，再按原顺序将 `others` 无分隔符拼接。若 source 是空白静态值且存在入边，会回退使用第一条入边成功且非跳过的结果。 |
| `ExtractNode: RANDOM_INT` | 固定模式把 `fixedValue` trim 后解析为 Long 整数，非法时失败；随机模式会交换反向的 min/max，并生成包含上下界的整数。 |
| `ExtractNode: RANDOM_STRING` | 固定模式原样返回 `fixedValue`；随机模式将长度负值按 0 处理，空字符集回退到大小写英文字母和数字。 |

### 执行节点结果和错误

执行节点的 `actionType` 为空白时失败。工具调用成功后，节点引用/后续条件看到的是结果对象的展示文本；若结果为 `MessageSendResultData` 且 `aiResponse` 非空，则改用 `aiResponse`。工具调用失败时节点记录工具错误，缺少错误文本时使用通用错误。条件、逻辑、提取和工具执行中的普通异常会把节点标记为失败；协程取消会记录取消日志后继续向上抛出，不会转换成普通节点失败结果。运行时初始化失败在底层执行结果中标记为可重试；这与调用端看到的外层 ToolResult 是不同层次。


## 示例

### 创建最小工作流

```ts
const created = await Tools.Workflow.create(
  'demo-workflow',
  '文档同步示例',
  [
    {
      id: 'trigger_1',
      type: 'trigger',
      name: '手动触发',
      triggerType: 'manual',
      position: { x: 80, y: 80 }
    },
    {
      id: 'exec_1',
      type: 'execute',
      name: '发送通知',
      actionType: 'send_notification',
      actionConfig: {
        message: '工作流已执行'
      },
      position: { x: 320, y: 80 }
    }
  ],
  [
    {
      sourceNodeId: 'trigger_1',
      targetNodeId: 'exec_1',
      condition: 'on_success'
    }
  ],
  true
);
```

### 读取详情

```ts
const detail = await Tools.Workflow.get(created.id);
console.log(detail.nodes.length);
console.log(detail.connections.length);
```

### 局部追加节点

```ts
await Tools.Workflow.patch(created.id, {
  node_patches: [
    {
      op: 'add',
      node: {
        id: 'exec_2',
        type: 'execute',
        name: '写日志',
        actionType: 'write_file',
        actionConfig: {
          path: '/sdcard/workflow.log',
          content: 'workflow finished'
        },
        position: { x: 560, y: 80 }
      }
    }
  ]
});
```

### 触发与删除

```ts
await Tools.Workflow.trigger(created.id);
await Tools.Workflow['delete'](created.id);
```

## 返回值

本文件主要使用以下结果类型：

- `WorkflowResultData`
- `WorkflowListResultData`
- `WorkflowDetailResultData`
- `StringResultData`

## 相关文件

- `examples/types/workflow.d.ts`
- `examples/types/results.d.ts`
- `docs/doc-src/toolpkg_developer_documentation/04_modules/results.md`
