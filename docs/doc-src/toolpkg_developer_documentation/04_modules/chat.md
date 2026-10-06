# API 文档：`chat.d.ts`

`chat.d.ts` 描述的是 `Tools.Chat` 命名空间，用于启动聊天服务、管理会话、发送消息、读取消息记录以及调用功能模型。

## 作用

当前定义覆盖：

- 启动聊天服务。
- 创建、查询、切换、删除聊天会话。
- 发送普通消息与流式消息。
- 枚举角色卡与读取聊天消息。
- 将结构化 `PromptTurn` 发送给指定功能模型。

## 运行时入口

```ts
Tools.Chat
```

## 运行时约定

这些方法都通过 ToolPkg 的异步工具桥接执行。宿主工具返回失败结果时，facade 返回的 Promise 会 rejected；成功结果只暴露结果数据对象，不包含宿主内部的 `ToolResult` 包装层。除特别说明外，空白字符串参数会在宿主侧被视为缺失参数。

### `startService(options?)`

```ts
startService(options?: {
  initial_mode?: 'WINDOW' | 'BALL' | 'VOICE_BALL' | 'FULLSCREEN' | 'RESULT_DISPLAY' | 'SCREEN_OCR'
  auto_enter_voice_chat?: boolean
  wake_launched?: boolean
  timeout_ms?: number
  keep_if_exists?: boolean
}): Promise<ChatServiceStartResultData>
```

启动并连接聊天浮窗服务。facade 只转发非空的 `initial_mode`、已提供的布尔选项和可数值化的 `timeout_ms`；宿主将模式按忽略大小写解析，将 `timeout_ms` 作为毫秒整数解析，非法模式、布尔值或超时值会失败。服务连接等待上限为 15 秒。

成功返回 `isConnected: true`；`connectionTime` 由结果对象创建时生成。`keep_if_exists` 和 `auto_enter_voice_chat` 只有为 `true` 时才会写入启动 Intent，`false` 等同于不启用对应行为。

### `createNew(group?, setAsCurrentChat?, characterCardId?)`

```ts
createNew(group?: string, setAsCurrentChat?: boolean, characterCardId?: string): Promise<ChatCreationResultData>
```

创建新的聊天会话。`group` 和 `characterCardId` 会转为字符串并忽略空白值；`setAsCurrentChat` 默认是 `true`，只接受 `true` 或 `false`。宿主等待新会话 ID 最多 5 秒；要求聊天服务已连接，并在请求切换当前会话时再等待最多 5 秒。

`createdAt` 是结果对象创建时的 Unix 毫秒时间戳。当前实现会调用角色卡读取接口，但该接口对任意 ID 都能构造快照，因而 `characterCardId` 的“必须存在”检查不能可靠地拒绝未知 ID。

### `listAll()`

```ts
listAll(): Promise<ChatListResultData>
```

列出聊天会话。运行时直接调用与 `listChats({})` 相同的 `list_chats` 工具，因此使用相同的默认过滤、排序和分页规则。

### `listChats(params?)`

带过滤条件列出聊天：

- `query?`：空值匹配全部标题；非空时按标题匹配。
- `match?: 'contains' | 'exact' | 'regex'`：默认 `contains`。`contains` 和 `exact` 区分大小写；非法正则不会抛正则错误，而是产生零个匹配。
- `limit?`：默认 `50`，宿主限制到 `1..200`；非整数会失败。`totalCount` 是截断前的匹配数，`chats` 最多包含 `limit` 项。
- `sort_by?: 'updatedAt' | 'createdAt' | 'messageCount'`：默认 `updatedAt`。
- `sort_order?: 'asc' | 'desc'`：默认 `desc`，值按忽略大小写解析。

返回的 `ChatListResultData` 还包括当前会话 ID。每个 `ChatInfo` 包含消息数、创建/更新时间、当前会话标记、输入/输出 token 计数，以及角色卡和角色组字段。

### `findChat(params)`

```ts
findChat({ query, match?, index? }): Promise<ChatFindResultData>
```

`query` 必填且不能是空白字符串；`match` 默认 `contains`，支持 `exact` 和 `regex`；`index` 默认 `0`，按匹配结果的零基索引选择会话，必须是整数。

宿主先尝试按会话 ID 精确匹配；没有 ID 命中时才按标题应用 `match`。索引越界、查询为空或没有匹配时调用失败；成功结果包含匹配总数 `matchedCount` 和选中的 `chat`。

### `agentStatus(chatId)`

```ts
agentStatus(chatId: string): Promise<AgentStatusResultData>
```

查询会话输入处理状态。`state` 当前可能是 `idle`、`completed`、`processing`、`connecting`、`receiving`、`executing_tool`、`tool_progress`、`processing_tool_result`、`summarizing`、`executing_plan` 或 `error`。`idle` 和 `completed` 将 `isIdle` 设为 `true`；处理中状态将 `isProcessing` 设为 `true`，相关阶段消息放在 `message` 中。

聊天不存在或浮窗服务未连接时调用失败；未提供状态时宿主按 `idle` 处理。

### `switchTo(chatId)`

```ts
switchTo(chatId: string): Promise<ChatSwitchResultData>
```

切换当前聊天。目标会话必须存在，服务必须已连接。宿主发起本地切换并等待当前会话 ID 更新，等待上限约为 1 秒；成功结果包含 `chatId`、`chatTitle` 和 `switchedAt`。

### `updateTitle(chatId, title)`

```ts
updateTitle(chatId: string, title: string): Promise<ChatTitleUpdateResultData>
```

更新会话标题。facade 会把 `chatId` 和 `title` 转为字符串；宿主会去除首尾空白，两个参数均不能为空，会话必须存在。结果中的 `title` 是去除首尾空白后的值，`updatedAt` 是结果创建时的 Unix 毫秒时间戳。

### `deleteChat(chatId)`

```ts
deleteChat(chatId: string): Promise<ChatDeleteResultData>
```

删除会话。目标会话必须存在且不能处于锁定状态；删除失败或锁定会话都会使调用失败。成功结果包含 `chatId` 和 `deletedAt`。

### `sendMessage(message, chatId?, roleCardId?, senderName?, options?)`

发送普通消息并等待完整回复。

`options` 支持：

- `runtime?: 'main' | 'floating'`
- `persist_turn?: boolean`
- `notify_reply?: boolean`
- `hide_user_message?: boolean`
- `disable_warning?: boolean`
- `timeout_ms?: number`

运行时行为：

- `runtime` 默认是 `floating`；未知值会失败。
- `message` 不能为空白。提供 `chatId` 时，宿主在该会话后台发送且不切换 UI；省略时发送到当前会话。
- `roleCardId` 若非空会作为本次发送的角色卡覆盖值；`senderName` 的空白值会被忽略。
- `persist_turn` 默认 `true`，`hide_user_message` 和 `disable_warning` 默认 `false`；四个布尔选项必须是 `true` 或 `false`。`notify_reply` 未提供时保持宿主默认值 `null`。
- `timeout_ms` 必须是正整数，且覆盖从发起请求到获得回复的剩余等待时间；未提供时获取回复的默认上限为 180 秒。已有同一会话消息处理时，等待前一条处理结束也受该期限约束。等待超时只结束本次调用：返回带超时时长的错误，已收到的部分内容会放入 `aiResponse`，目标会话在后台继续生成回复，不会被取消，稍后可经 `getMessages` 读取最终结果。会话只会被用户主动取消打断，取消原因会如实传递给正在运行的工具执行，`User cancelled` 专属于用户操作。

返回 `MessageSendResultData`，包含 `chatId`、原始 `message`、可为空的 `aiResponse`、`sentAt`，以及成功收到回复时的 `receivedAt`。

### `sendMessageStreaming(message, chatId?, roleCardId?, senderName?, options?)`

```ts
sendMessageStreaming(
  message: string,
  chatId?: string,
  roleCardId?: string,
  senderName?: string,
  options?: SendMessageStreamingOptions
): Promise<MessageSendResultData>
```

参数和普通 `sendMessage()` 相同，另支持：

- `waifu?: boolean`：启用字符队列式分段输出；省略时为 `false`。
- `onIntermediateResult?: (event: MessageSendStreamEventData) => void`：接收中间事件的回调。

宿主首先回调一个 `type: 'start'` 事件，然后回调一个或多个 `type: 'chunk'` 事件。普通模式的 chunk 直接来自回复流；`waifu: true` 时，chunk 会经过字符延迟和可选标点移除设置后再回调。每个 chunk 事件包含 `chunk`、零基 `chunkIndex` 和累计 `receivedChars`。Promise 最终仍解析为完整的 `MessageSendResultData`，而不是最后一个中间事件；最终结果失败或超时会使 Promise rejected。回调不存在或不是函数时不会注册中间事件处理器。

### `call(options)`

ToolPkg API `1.0.1` 起可用，Operit 最低版本和 manifest 声明要求见[API 版本规则](../09_compatibility/api_versions.md)。

```ts
call(options: ChatCallOptions): Promise<ChatCallResultData>
```

- `functionType`：必填非空字符串；声明将其限制为 `FunctionModelType` 的 11 个成员：`CHAT`、`SUMMARY`、`TITLE_GENERATION`、`MEMORY`、`UI_CONTROLLER`、`TRANSLATION`、`GREP`、`ROLE_RESPONSE_PLANNER`、`IMAGE_RECOGNITION`、`AUDIO_RECOGNITION`、`VIDEO_RECOGNITION`。运行时会 trim 并按忽略大小写映射到宿主 `FunctionType`，未知值使调用失败。
- `turns`：必填数组；facade 会 JSON 序列化后交给宿主。数组必须至少有一个成员；每项须为对象，`kind` 必须是有效 `PromptTurnKind`（运行时 trim 后不区分大小写），`content` 必须是字符串，`toolName` 只接受字符串或 null，`metadata` 只接受对象或 null。无效项会使调用失败。
- `recordTokenUsage?`：必须是 boolean；省略时宿主默认 `true`，用于记录本次功能模型调用的 token 用量。
- `enableThinking?`：必须是 boolean；省略时宿主默认 `false`。
- options 本身必须是非数组对象。facade 的对象、functionType、turns 数组和布尔值检查失败会同步抛错；JSON 序列化异常也会在发起宿主调用前抛出。宿主拒绝非法 functionType/turns 或模型调用失败时，返回 rejected Promise。

宿主按 `functionType` 选择功能模型配置，使用 `EnhancedAIService` 发起非流式请求；该请求不带可用工具列表，不写入聊天消息记录，也不会执行模型输出的工具调用 XML。

返回 `ChatCallResultData`：

- `text`：移除协议 metadata 和工具 XML 后的助手文本。
- `turns`：把模型输出切为 `ASSISTANT` 文本段与 `TOOL_CALL` 记录；工具调用记录保留 XML 文本及可解析到的 `toolName`。它只描述模型输出，不表示宿主执行了工具。
- `finishReason`：发现工具调用 XML 时为 `tool_call`，否则为 `stop`。
- `metadata`：无协议标签时为空对象；识别到带 provider 属性的 `<meta>` 标签时，`protocolMeta` 为 `{ provider, payload }` 数组，payload 是标签正文。
- `receivedAt`：宿主创建结果时的 Unix 毫秒时间戳。

ToolCall bridge 会把宿主成功结果解包为 `ChatCallResultData`，并将宿主失败映射为 Promise rejection。

### `listCharacterCards()`

```ts
listCharacterCards(): Promise<CharacterCardListResultData>
```

列出角色卡快照。成功结果包含 `totalCount` 和 `cards`；每张卡包含 `id`、`name`、`description`、`isDefault`、`createdAt` 和 `updatedAt`。读取角色卡失败时返回 rejected Promise。

### `getMessages(chatId, options?)`

```ts
getMessages(chatId: string, options?: { order?: 'asc' | 'desc'; limit?: number }): Promise<ChatMessagesResultData>
```

读取某个聊天的消息记录。

- `chatId` 必填；`order` 默认 `desc`，只接受 `asc/desc`。
- `limit` 默认 `20`，宿主限制到 `1..200`；非整数会失败。
- 结果按请求顺序返回，排除 `sender === 'summary'` 的内部摘要消息，并移除历史消息中的工具、工具结果和状态 XML 块。
- 返回 `ChatMessagesResultData` 的 `limit` 是生效后的限制值；每项包含 `sender`、清理后的 `content`、`timestamp`、`roleName`、`provider` 和 `modelName`。

### `getMessagesRange(chatId, options)`

```ts
getMessagesRange(chatId: string, options: { order?: 'asc' | 'desc'; start: number; end: number }): Promise<ChatMessagesResultData>
```

按从 0 开始且包含两端的消息序号区间读取记录。`start` 和 `end` 都必填整数，并且必须满足 `0 <= start <= end`；默认顺序为 `asc`。返回结果的 `limit` 等于 `end - start + 1`，并同时包含 `start` 和 `end` 字段；消息过滤和 XML 清理规则与 `getMessages()` 相同。

## 返回值

`chat.d.ts` 的返回值都定义在 `results.d.ts` 中，常见的有：

- `ChatServiceStartResultData`
- `ChatCreationResultData`
- `ChatListResultData`
- `ChatFindResultData`
- `AgentStatusResultData`
- `ChatSwitchResultData`
- `ChatTitleUpdateResultData`
- `ChatDeleteResultData`
- `MessageSendResultData`
- `MessageSendStreamEventData`
- `ChatCallResultData`
- `ChatMessagesResultData`
- `CharacterCardListResultData`

## 示例

### 创建会话并发送消息

```ts
const created = await Tools.Chat.createNew('work', true);
const chatId = created.chatId;

await Tools.Chat.sendMessage('帮我总结今天的待办', chatId, undefined, undefined, {
  timeout_ms: 60000
});
```

### 查找并切换聊天

```ts
const found = await Tools.Chat.findChat({
  query: '日报',
  match: 'contains'
});

if (found.chat) {
  await Tools.Chat.switchTo(found.chat.id);
}
```

### 读取最近消息

```ts
const messages = await Tools.Chat.getMessages('chat_123', {
  order: 'desc',
  limit: 20
});
console.log(messages.toString());
```

### 按区间读取消息

```ts
const messages = await Tools.Chat.getMessagesRange('chat_123', {
  order: 'asc',
  start: 200,
  end: 399
});
console.log(messages.messages.length);
```

### 接收流式回复

```ts
const result = await Tools.Chat.sendMessageStreaming(
  '逐步说明处理结果',
  undefined,
  undefined,
  undefined,
  {
    onIntermediateResult: (event) => {
      if (event.type === 'chunk') {
        console.log(event.chunk);
      }
    }
  }
);
console.log(result.aiResponse);
```

### 调用翻译功能模型

```ts
const result = await Tools.Chat.call({
  functionType: 'TRANSLATION',
  turns: [
    {
      kind: 'SYSTEM',
      content: 'Translate the user message into Chinese. Return only the translated text.'
    },
    {
      kind: 'USER',
      content: originalText,
      metadata: { targetLanguage: 'Chinese' }
    }
  ],
  recordTokenUsage: false
});

if (result.finishReason === 'tool_call') {
  console.warn(result.turns);
} else {
  console.log(result.text);
}
```

## 相关文件

- `examples/types/chat.d.ts`
- `examples/types/results.d.ts`
- `examples/types/index.d.ts`
