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

### `agentStatus(chatId)`

查看某个聊天当前是否在处理中。

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

### `call(options)`

自 ToolPkg API `1.0.1` 起可用。

```ts
call(options: {
  functionType:
    | 'CHAT'
    | 'SUMMARY'
    | 'TITLE_GENERATION'
    | 'MEMORY'
    | 'UI_CONTROLLER'
    | 'TRANSLATION'
    | 'GREP'
    | 'ROLE_RESPONSE_PLANNER'
    | 'IMAGE_RECOGNITION'
    | 'AUDIO_RECOGNITION'
    | 'VIDEO_RECOGNITION'
  turns: ToolPkg.PromptTurn[]
  recordTokenUsage?: boolean
  enableThinking?: boolean
}): Promise<ChatCallResultData>
```

调用指定功能模型，并使用项目统一的 `PromptTurn` 结构传入上下文。这个接口不写入聊天记录，也不执行模型返回的工具调用。

返回值：

- `text`：去掉协议 metadata 和工具 XML 后的助手文本
- `turns`：模型输出按 `PromptTurn` 形状切分后的结果，可能包含 `ASSISTANT` 和 `TOOL_CALL`
- `finishReason`：`stop` 或 `tool_call`
- `metadata`：宿主从协议标签提取的结构信息；当前 `protocolMeta` 为数组，每项包含 `provider` 和原始 `payload`

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
- `AgentStatusResultData`
- `ChatSwitchResultData`
- `ChatTitleUpdateResultData`
- `ChatDeleteResultData`
- `MessageSendResultData`
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
