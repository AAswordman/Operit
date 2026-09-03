# API 文档：`chat.d.ts`

`chat.d.ts` 描述的是 `Tools.Chat` 命名空间，用于管理会话、切换会话、发送消息以及读取消息记录。

## 作用

当前定义覆盖：

- 启动聊天服务。
- 创建、查询、切换、删除聊天会话。
- 发送普通消息与高级消息。
- 枚举角色卡与读取聊天消息。

## 运行时入口

```ts
Tools.Chat
```

## 主要 API

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

启动聊天服务或浮窗。

可选参数：

- `initial_mode?`
- `auto_enter_voice_chat?`
- `wake_launched?`
- `timeout_ms?`
- `keep_if_exists?`

### `createNew(group?, setAsCurrentChat?, characterCardId?)`

```ts
createNew(group?: string, setAsCurrentChat?: boolean, characterCardId?: string): Promise<ChatCreationResultData>
```

创建新的聊天会话。

### `listAll()`

返回全部聊天列表。

### `listChats(params?)`

带过滤条件列出聊天：

- `query?`
- `match?: 'contains' | 'exact' | 'regex'`
- `limit?`
- `sort_by?: 'updatedAt' | 'createdAt' | 'messageCount'`
- `sort_order?: 'asc' | 'desc'`

### `findChat(params)`

```ts
findChat({ query, match?, index? }): Promise<ChatFindResultData>
```

根据标题或 ID 查找聊天。

### `getCurrentChatRuntimeState(chatId?)`

```ts
getCurrentChatRuntimeState(chatId?: string): Promise<CurrentChatRuntimeStateResultData>
```

查询当前对话运行状态。省略 `chatId` 时查询默认当前对话，传入 `chatId` 时查询指定对话。返回的 `aiBehavior` 可能为：

- `idle`
- `requesting`（请求已发出，等待首个模型响应事件）
- `thinking`
- `processing_tool_result`
- `executing_plan`
- `calling_tool`
- `waiting_tool_result`
- `waiting_tool_confirmation`
- `generating_response`
- `summarizing`
- `retrying`
- `cancelled`（用户取消当前操作；下一次非终止状态会替换它）
- `error`

返回值还包含用户交互状态、应用前后台状态、工具名称，以及可选的 `error` 和 `retry` 对象。`error.source` 为 `ai`、`tool`、`api` 或 `system`，`error.code` 是可扩展的归一化字符串；Operit 本地网络错误通过 `error.appCode` 暴露 `5000` 至 `5007` 的应用码，服务商原始 code 和 HTTP 状态位于 `error.providerCode`、`error.httpStatusCode`。`retry` 包含 `attempt`、`maxAttempts` 和 `retryAfterMs`。这个接口不暴露 `main`/`floating` runtime 选择器。

### `getGlobalChatRuntimeState()`

```ts
getGlobalChatRuntimeState(): Promise<GlobalChatRuntimeStateResultData>
```

查询所有聊天聚合后的运行状态，返回 `globalActivity`、`applicationState`、`activeChatIds` 和 `updatedAt`。

### `switchTo(chatId)`

切换当前聊天。

### `updateTitle(chatId, title)`

更新聊天标题。

### `deleteChat(chatId)`

删除聊天。

### `sendMessage(message, chatId?, roleCardId?, senderName?, options?)`

发送普通消息。

`options` 支持：

- `runtime?: 'main' | 'floating'`
- `persist_turn?: boolean`
- `notify_reply?: boolean`
- `hide_user_message?: boolean`
- `disable_warning?: boolean`
- `timeout_ms?: number`

其中：

- `runtime` 用于指定本次消息发送到哪个 chat runtime，未指定时默认走 `floating`
- `timeout_ms` 用于控制本次发送的最长等待时间，单位毫秒

### `listCharacterCards()`

列出可用角色卡。

### `getMessages(chatId, options?)`

```ts
getMessages(chatId: string, options?: { order?: 'asc' | 'desc'; limit?: number }): Promise<ChatMessagesResultData>
```

读取某个聊天的消息记录。

### `getMessagesRange(chatId, options)`

```ts
getMessagesRange(chatId: string, options: { order?: 'asc' | 'desc'; start: number; end: number }): Promise<ChatMessagesResultData>
```

按从 0 开始的消息序号区间读取某个聊天的消息记录，`start` 和 `end` 都是包含边界。

## 返回值

`chat.d.ts` 的返回值都定义在 `results.d.ts` 中，常见的有：

- `ChatServiceStartResultData`
- `ChatCreationResultData`
- `ChatListResultData`
- `ChatFindResultData`
- `CurrentChatRuntimeStateResultData`
- `GlobalChatRuntimeStateResultData`
- `ChatSwitchResultData`
- `ChatTitleUpdateResultData`
- `ChatDeleteResultData`
- `MessageSendResultData`
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

## 相关文件

- `examples/types/chat.d.ts`
- `examples/types/results.d.ts`
- `examples/types/index.d.ts`
