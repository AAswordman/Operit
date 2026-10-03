---
title: 生命周期与聊天 Hook
status: draft
---

# 生命周期与聊天 Hook

本页涵盖应用生命周期、导航动作、聊天视图、消息持久化、消息长按菜单和聊天运行态 Hook。通知型 Hook 的返回值通常被宿主忽略；消息菜单点击是例外，可通过已注册 dialog 打开 UI。

## 应用生命周期

### `ToolPkg.registerAppLifecycleHook({ id, event, function })`

- **API 版本：** `1.0.0`。
- `event` 为以下一个精确事件名：
  - Application：`application_on_create`、`application_on_foreground`、`application_on_background`、`application_on_low_memory`、`application_on_trim_memory`、`application_on_terminate`
  - Activity：`activity_on_create`、`activity_on_start`、`activity_on_resume`、`activity_on_pause`、`activity_on_stop`、`activity_on_destroy`
- payload 结构为 `{ extras?: JsonObject }`；具体 extras 随系统事件变化，类型不承诺每个事件都提供相同键。
- 同一事件的注册项按 ToolPkg 加载顺序、包名、Hook ID 串行执行。回调运行在 IO dispatcher；返回值不改变系统生命周期。
- 单个回调失败记录日志，后续 Hook 继续。
- 新加载的 ToolPkg Hook 会接收宿主生命周期 registry 当前可重放的事件快照；这不等同于重新触发 Android 系统生命周期。

## 导航动作

`ToolPkg.registerNavigationEntry()` 可选 `action` 回调。用户触发导航项动作时，Hook event 为 `navigation_entry_action`，payload 可含 `entryId`、`routeId`、`surface`、`title` 和 `description`。payload 字段均为可选，因为宿主根据入口类型填充上下文。

注册 `route` 时由宿主打开目标路由；注册 `action` 时执行模块导出函数。不要同时依赖两种路径一定都会执行，最终入口行为由导航注册定义和宿主 surface 决定。

## 聊天视图观察

### `ToolPkg.registerChatViewHook({ id, function })`

- **API 版本：** `1.0.0`。
- **事件：** `view_opened`、`view_updated`、`view_closed`。
- payload：`viewId?`、`chatId?`、`workspacePath?`、`workspaceEnv?`、`runtime?`、`title?`。
- 同一事件内按容器加载顺序、包名、Hook ID 串行调用；返回值忽略，失败只记录日志。
- 新增 Hook 注册时，宿主会将当前仍打开的视图以 `view_opened` 重放给新增 Hook，不会重放给原有 Hook。

## 聊天消息持久化通知

### `ToolPkg.registerChatMessageHook({ id, function })`

- **API 版本：** `1.0.0`。
- **事件：** `message_persisted`。
- payload 包含 `chatId` 和消息快照：`timestamp`、`sender`、`roleName`、`content`、`completedAt`、`provider`、`modelName`、`inputTokens`、`outputTokens`、`cachedInputTokens`、`sentAt`、`outputDurationMs`、`waitDurationMs`、`displayMode`、`selectedVariantIndex`、`variantCount?`、`isFavorite`。
- 通知在 IO scope 中异步执行；回调结果被忽略，不能通过该 Hook 撤销或改写已持久化消息。
- 同一消息的 Hook 按已同步列表串行分发。失败记录日志，不阻断其他 Hook。

`timestamp` 是消息模型时间戳，不等于外层 `HookEventBase.timestampMs` 的派发时间。

## 聊天消息长按菜单

### `ToolPkg.registerChatMessageMenuItem(definition)`

- **API 版本：** **`1.0.1`**；需要 Operit `1.12.1+4` 或更新版本。
- `id`：包内菜单项 ID；宿主内部菜单 ID 会带上容器包名。
- `title`：必填 `LocalizedText`；本地化解析后若为空，显示 item ID。
- `icon?`、`order?`：图标名和排序值。
- `senders?`：允许显示的 sender 列表；空列表表示不按 sender 过滤，非空列表按忽略大小写匹配。
- `function`：点击回调。
- `dialog?`：预注册 Compose DSL screen 路径和默认标题。未声明 dialog 时，即使回调返回 dialog 数据，宿主也不会打开弹窗。

点击事件名为 `chat_message_menu_item_click`，payload：

- `action: 'click'`
- `chatId`
- `messageIndex`
- `menuItemId`
- `message`：快照，字段与持久化消息大体相同，并含 `variantCount`。

回调可返回 `{ dialog: { title?, state?, moduleSpec? } }`：

- 宿主先构造默认 state：`chatId`、`messageIndex`、`message`、`menuItemId`，再用返回的 `dialog.state` 同名键覆盖。
- 宿主先构造默认 `moduleSpec`：菜单项 ID、Compose DSL runtime、已注册 screen、解析后的标题、ToolPkg ID 和菜单项 ID，再合并回调的 `moduleSpec`。
- 返回的 dialog title 非空时覆盖注册标题；空标题继续使用注册标题。
- Hook 执行或返回解码失败时记录日志，不创建 dialog。

该 Hook 的消息快照和 dialog 类型均为 API `1.0.1`。详细注册字段见[ToolPkg 注册 API](../03_runtime/registry.md)。

## 聊天运行态通知

### `ToolPkg.registerChatRuntimeHook({ id, function })`

- **API 版本：** **`1.0.1`**；需要 Operit `1.12.1+4` 或更新版本。
- **事件：** `state_changed`。
- `chatId`：会话 ID。
- `slot`：`main` 或 `floating`。
- `state`：`idle`、`processing`、`connecting`、`receiving`、`executing_tool`、`tool_progress`、`processing_tool_result`、`summarizing`、`executing_plan`、`completed` 或 `error`。
- `message?`：部分处理状态的用户可见文本。
- `toolName?`：执行工具、工具进度或处理工具结果时提供。
- `progress?`：仅工具进度状态提供的数值；本类型未声明归一化范围，不应自行假设一定是 0 到 1。
- `isActive`：idle/completed/error 为 false，其余状态为 true。
- `activeChatIds`：当前活动会话 ID 排序后的数组。
- `currentTurnToolInvocationCount`、`activeConversationCount`、`currentSessionToolCount`：宿主计算的对应计数。
- `timestamp`：宿主状态事件时间戳。

该通知返回值被忽略。Hook 串行运行在 IO dispatcher，失败只写日志，不改变聊天运行状态。

## 权威实现

- `plugins/toolbox/ToolboxPlugin.kt`：应用生命周期注册、排序和可重放事件。
- `plugins/toolpkg/ToolPkgChatViewHookBridge.kt`：聊天视图通知和新增 Hook replay。
- `plugins/toolpkg/ToolPkgChatMessageHookBridge.kt`：消息持久化快照。
- `plugins/toolpkg/ToolPkgChatMessageMenuItemBridge.kt`：sender 过滤、点击结果和 dialog 合并。
- `plugins/toolpkg/ToolPkgChatRuntimeHookBridge.kt`：运行状态字段映射。
- `examples/types/toolpkg.d.ts`：公开事件和数据类型。

相关页面：[ToolPkg 注册 API](../03_runtime/registry.md)、[Hook 总索引](./index.md)、[API 版本规则](../09_compatibility/api_versions.md)。