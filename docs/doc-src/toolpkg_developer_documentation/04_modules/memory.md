# API 文档：`memory.d.ts`

`memory.d.ts` 描述的是全局 `Tools.Memory` 命名空间。它通过宿主工具访问当前记忆空间，也可以根据调用上下文中的角色卡选择固定的记忆 profile。

本页中的所有方法都返回 Promise。facade 支持两种调用形式：位置参数形式和一个对象形式；对象形式只在第一个参数是非数组对象时启用。

## 运行时入口与作用域

```ts
Tools.Memory
```

`callerCardId` 会先经过 `String(...).trim()`；空字符串会被忽略。宿主按以下顺序选择 profile：

1. 使用显式传入的 `caller_card_id`。
2. 否则使用当前 ToolPkg 调用上下文的 caller card id。
3. 如果角色卡绑定了固定记忆 profile，使用该 profile。
4. 否则使用当前活动记忆空间。

因此 `callerCardId` 是 profile 选择参数，不是记忆内容字段。没有固定绑定的角色卡仍会回退到活动记忆空间。

## 查询与读取

### `query(...)`

两种声明形式为：

```ts
query(
  query: string,
  folderPath?: string,
  limit?: number,
  startTime?: string,
  endTime?: string,
  snapshotId?: string,
  threshold?: number,
  callerCardId?: string
): Promise<MemoryQueryResultData>;

query(options: {
  query: string;
  folderPath?: string;
  limit?: number;
  startTime?: string;
  endTime?: string;
  snapshotId?: string;
  threshold?: number;
  callerCardId?: string;
}): Promise<MemoryQueryResultData>;
```

facade 将字段转换为宿主参数：`folderPath` -> `folder_path`、`startTime` -> `start_time`、`endTime` -> `end_time`、`snapshotId` -> `snapshot_id`、`callerCardId` -> `caller_card_id`。查询字符串会原样传入搜索实现；支持自然语言、空格短语、`|` 分隔的关键词和关键词内部的 `*` 模糊占位符。

#### 默认值与校验

- `query` 为空或只包含空白字符时失败，错误为 `Query parameter cannot be empty.`。
- 普通查询默认 `limit` 为 `20`。`limit < 1` 不会失败，而是被宿主钳制为 `1`；当前实现没有更大的上限。
- 当查询恰好是 `"*"` 且没有显式 `limit` 时，内部使用最大整数以返回所有匹配项。结果内容仍可能按通配查询规则缩短。
- `threshold` 默认 `0`；非数字或小于 `0` 时失败，错误为 `Invalid threshold. Expected a non-negative number.`。
- `startTime` 和 `endTime` 只接受本地时间格式 `YYYY-MM-DD` 或 `YYYY-MM-DD HH:mm`。按天时，起始边界为当天 `00:00:00.000`，结束边界为当天 `23:59:59.999`；按分钟时，结束边界包含该分钟的 `59.999`。
- 时间解析使用设备当前时区。解析失败或 `startTime` 晚于 `endTime` 时，Promise reject。

#### 快照与分页去重

不传 `snapshotId` 或传空值时，宿主生成 UUID 快照，并在结果的 `snapshotId` 中返回它。传入非空 id 时，如果该 id 尚不存在，宿主会按该 id 创建快照，而不是要求它预先存在。

同一 profile 下复用快照会排除此前已经返回的 memory id，再把本次返回的 memory id 加入快照。多个并发查询共享同一快照时，选取和写入 seen 集合在同一锁内完成。每个 profile 最多保留 32 个快照，超出后按最近访问时间清理较旧快照。

#### 返回结构

`MemoryQueryResultData` 当前字段为：

- `memories: MemoryInfo[]`
- `snapshotId?: string`
- `snapshotCreated: boolean`
- `excludedBySnapshotCount: number`

每个 `MemoryInfo` 包含：

- `title`
- `content`
- `source`
- `tags: string[]`
- `createdAt`：按本地环境格式化为 `yyyy-MM-dd HH:mm`
- `chunkInfo?`
- `chunkIndices?: number[]`

普通记忆在非通配查询中返回完整内容；恰好查询 `"*"` 时，普通记忆内容只保留前 10 个字符，超出时追加 `...`。文档型记忆会二次搜索匹配分块：普通查询最多拼接 5 个相关分块，`limit > 20` 或通配查询时返回文档摘要和分块信息而不是完整分块内容。

### `getByTitle(...)`

```ts
getByTitle(
  title: string,
  chunkIndex?: number,
  chunkRange?: string,
  query?: string,
  limit?: number,
  callerCardId?: string
): Promise<string>;

getByTitle(options: {
  title: string;
  chunkIndex?: number;
  chunkRange?: string;
  query?: string;
  limit?: number;
  callerCardId?: string;
}): Promise<string>;
```

该方法按标题精确查找记忆。`title` 为空或找不到记忆时失败。普通记忆直接返回格式化后的完整内容；只有文档型记忆同时提供分块参数时才进入分块读取。

文档型记忆的参数优先级为 `query` > `chunkRange` > `chunkIndex`：

- `query` 在文档内搜索分块，默认最多返回 20 个分块；`limit` 至少按 1 处理。
- `chunkRange` 使用 1 起始的闭区间，例如 `"3-7"`。超出文档范围或起点大于终点时失败。
- `chunkIndex` 使用 1 起始的单个分块编号。
- 没有匹配分块时失败。成功值是字符串，内容包含文档标题、分块编号和分隔线，而不是 `MemoryQueryResultData`。

如果目标不是文档型记忆，传入分块参数不会强制进行分块查询，方法仍返回整条记忆。

## 记忆写入与整理

### `create(...)`

```ts
create(
  title: string,
  content: string,
  contentType?: string,
  source?: string,
  folderPath?: string,
  tags?: string,
  callerCardId?: string
): Promise<string>;

create(options: {
  title: string;
  content: string;
  contentType?: string;
  source?: string;
  folderPath?: string;
  tags?: string;
  callerCardId?: string;
}): Promise<string>;
```

`title` 和 `content` 都必须非空。默认值为 `contentType: "text/plain"`、`source: "ai_created"`、`folderPath: ""`。`tags` 是逗号分隔字符串，宿主会去除空白标签、删除重复项，再写入记忆。成功时返回包含标题和生成 UUID 的字符串。

### `update(...)`

```ts
update(
  oldTitle: string,
  updates?: {
    newTitle?: string;
    content?: string;
    contentType?: string;
    source?: string;
    credibility?: number;
    importance?: number;
    folderPath?: string;
    tags?: string;
  },
  callerCardId?: string
): Promise<string>;

update(options: {
  oldTitle: string;
  newTitle?: string;
  content?: string;
  contentType?: string;
  source?: string;
  credibility?: number;
  importance?: number;
  folderPath?: string;
  tags?: string;
  callerCardId?: string;
}): Promise<string>;
```

`oldTitle` 用于精确定位现有记忆；不存在时失败。未提供的更新字段保留原值。`credibility` 和 `importance` 在宿主中按浮点数解析；无法解析时回退到原值。`tags` 按逗号分割并去掉空白项，但更新路径不会像创建路径那样显式去重。

位置参数形式的 `callerCardId` 必须作为第三个参数传入；对象形式可以直接放在更新对象中。facade 对位置参数更新对象会把显式第三参数写回 `callerCardId`，因此不要只把 caller id 放在第二个 `updates` 对象中再省略第三参数。

### `deleteMemory(...)`

```ts
deleteMemory(title: string, callerCardId?: string): Promise<string>;
deleteMemory(options: {
  title: string;
  callerCardId?: string;
}): Promise<string>;
```

按标题精确删除记忆。标题为空或目标不存在时失败；成功时返回包含标题的确认字符串。

### `move(...)`

```ts
move(
  targetFolderPath: string,
  titles?: string[] | string,
  sourceFolderPath?: string,
  callerCardId?: string
): Promise<string>;

move(options: {
  targetFolderPath: string;
  titles?: string[] | string;
  sourceFolderPath?: string;
  callerCardId?: string;
}): Promise<string>;
```

`targetFolderPath` 必须存在，但空字符串表示未分类目录。记忆选择规则如下：

- 只传 `titles`：按标题选择。
- 只传 `sourceFolderPath`：移动该来源文件夹下的记忆。
- 两者都传：取标题匹配集合与来源文件夹集合的交集。
- 两者都不传：失败。

数组会被 facade 用逗号连接；宿主还会按逗号、换行和 `|` 分隔并去重标题。找不到任何匹配记忆时失败；成功结果包含移动数量和目标目录。

## 记忆链接

### `link(...)`

```ts
link(
  sourceTitle: string,
  targetTitle: string,
  linkType?: string,
  weight?: number,
  description?: string,
  callerCardId?: string
): Promise<MemoryLinkResultData>;

link(options: {
  sourceTitle: string;
  targetTitle: string;
  linkType?: string;
  weight?: number;
  description?: string;
  callerCardId?: string;
}): Promise<MemoryLinkResultData>;
```

facade 调用宿主工具 `link_memories`。源标题和目标标题都必须存在；默认 `linkType` 为 `related`，默认 `weight` 为 `0.7`。宿主把 weight 钳制到 `0.0` 到 `1.0`，然后返回：

- `sourceTitle`
- `targetTitle`
- `linkType`
- `weight`
- `description`

任一记忆不存在或 repository 操作失败时 Promise reject。

### `queryLinks(...)`

```ts
queryLinks(
  linkId?: number,
  sourceTitle?: string,
  targetTitle?: string,
  linkType?: string,
  limit?: number,
  callerCardId?: string
): Promise<MemoryLinkQueryResultData>;

queryLinks(options: {
  linkId?: number;
  sourceTitle?: string;
  targetTitle?: string;
  linkType?: string;
  limit?: number;
  callerCardId?: string;
}): Promise<MemoryLinkQueryResultData>;
```

可按 link id、源标题、目标标题和 link type 筛选。标题筛选会先精确解析为 memory id；标题不存在时失败。`limit` 默认 `20`，宿主把它钳制到 `1..200`；非整数值失败。

结果字段为 `totalCount` 和 `links`。每个 link 包含 `linkId`、`sourceTitle`、`targetTitle`、`linkType`、`weight` 和 `description`。`totalCount` 是当前返回列表的数量，不是未截断的总数量。

### `updateLink(...)`

```ts
updateLink(
  linkId?: number,
  sourceTitle?: string,
  targetTitle?: string,
  linkType?: string,
  newLinkType?: string,
  weight?: number,
  description?: string,
  callerCardId?: string
): Promise<MemoryLinkResultData>;

updateLink(options: {
  linkId?: number;
  sourceTitle?: string;
  targetTitle?: string;
  linkType?: string;
  newLinkType?: string;
  weight?: number;
  description?: string;
  callerCardId?: string;
}): Promise<MemoryLinkResultData>;
```

定位时优先使用 `linkId`。没有 id 时必须提供 `sourceTitle` 和 `targetTitle`；可用 `linkType` 消除同一对记忆之间的多条链接歧义。至少要提供 `newLinkType`、`weight`、`description` 中的一项。更新后的 weight 仍会被钳制到 `0.0..1.0`。找不到链接或匹配到多条链接时失败。

### `deleteLink(...)`

```ts
deleteLink(
  linkId?: number,
  sourceTitle?: string,
  targetTitle?: string,
  linkType?: string,
  callerCardId?: string
): Promise<string>;

deleteLink(options: {
  linkId?: number;
  sourceTitle?: string;
  targetTitle?: string;
  linkType?: string;
  callerCardId?: string;
}): Promise<string>;
```

定位规则与 `updateLink` 相同：优先按 id，否则按源标题、目标标题和可选 link type 查找。多条匹配时必须传 id 或更具体的 link type；成功时返回删除的 link id。

## 错误与异步语义

- facade 的每个方法都通过 `toolCall` 异步调用宿主；`success: false` 会让 Promise reject。
- 记忆不存在、标题或必填字段为空、时间格式错误、阈值非法、分块越界、链接歧义和 repository 异常均不会作为成功字符串返回。
- 记忆查询的 HTTP/工具式分页语义由 `snapshotId` 控制；不要仅用 `limit` 代替快照去重。
- `callerCardId` 不会改变返回数据字段，只改变宿主选择的记忆 profile。

## 示例

### 使用对象形式查询并翻页

```ts
const firstPage = await Tools.Memory.query({
  query: '网络请求超时',
  folderPath: 'dev/network',
  limit: 5,
  threshold: 0.1
});

const secondPage = await Tools.Memory.query({
  query: '网络请求超时',
  folderPath: 'dev/network',
  limit: 5,
  snapshotId: firstPage.snapshotId || undefined,
  threshold: 0.1
});
```

### 读取文档分块

```ts
const text = await Tools.Memory.getByTitle({
  title: '网络排错记录',
  chunkRange: '3-7',
  callerCardId: 'card-1'
});
console.log(text);
```

### 创建并链接记忆

```ts
await Tools.Memory.create({
  title: 'OkHttp 使用记录',
  content: '记录请求构建和超时配置。',
  folderPath: 'dev/http',
  tags: 'android,http,okhttp'
});

const link = await Tools.Memory.link({
  sourceTitle: 'OkHttp 使用记录',
  targetTitle: '网络请求排错',
  linkType: 'related',
  weight: 0.8
});
console.log(link.linkType, link.weight);
```

## 相关实现

- `examples/types/memory.d.ts`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsTools.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/ToolRegistration.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/MemoryQueryToolExecutor.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/ToolResultDataClasses.kt`
- [结果类型参考](./results.md)
- [全局运行时 API](../03_runtime/global_api.md)
