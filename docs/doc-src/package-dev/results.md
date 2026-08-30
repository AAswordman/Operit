# API 文档：`results.d.ts`

`results.d.ts` 是所有工具返回结构的集中定义。它本身不提供运行时方法，但几乎所有 `Tools.*`、`toolCall()` 和部分全局对象最终都会返回这里定义的数据类型。

## 作用

这份文件主要承担两类职责：

- 定义 `...Data` 形式的原始结果结构。
- 定义 `...Result` 形式的包装结果，其中通常包含 `BaseResult` 与 `data` 字段。

## 命名约定

### `...Data`

表示某个工具或能力的返回数据主体，例如：

- `FileContentData`
- `HttpResponseData`
- `UIPageResultData`
- `WorkflowDetailResultData`

### `...Result`

表示带 `success` / `error` 包装的结果对象，例如：

- `SystemSettingResult`
- `UIPageResult`
- `ChatCreationResult`
- `MemoryLinkResult`

### `toString()`

很多 `...Data` 类型都声明了 `toString()`，说明运行时支持把结果转换成可读文本。

## 主要结果分类

### 1. 文件与搜索结果

常见类型：

- `FileEntry`
- `FileExistsData`
- `FileInfoData`
- `DirectoryListingData`
- `FileContentData`
- `BinaryFileContentData`
- `FilePartContentData`
- `FileOperationData`
- `FileApplyResultData`
- `FindFilesResultData`
- `GrepLineMatch`
- `GrepFileMatch`
- `GrepResultData`

其中：

- `FileContentData` 包含 `env`、`path`、`content`、`size`
- `BinaryFileContentData` 通过 `contentBase64` 表示二进制内容
- `GrepResultData` 包含 `matches`、`totalMatches`、`filesSearched`

### 2. 网络结果

常见类型：

- `HttpResponseData`
- `Link`
- `VisitWebResultData`

其中：

- `HttpResponseData` 包含 `statusCode`、`statusMessage`、`headers`、`contentType`、`content`
- `VisitWebResultData` 除了页面正文外，还可能包含 `metadata`、`links`、`imageLinks`、`visitKey`
- 当网页正文过长时，`VisitWebResultData` 还可能包含 `contentSavedTo`、`contentTruncated`、`originalContentLength`

### 3. 系统 / 设备 / 应用结果

常见类型：

- `SleepResultData`
- `SystemSettingData`
- `AppOperationData`
- `AppListData`
- `AppUsageTimeResultData`
- `BluetoothStateData`
- `BluetoothBondedDevicesData`
- `BluetoothScanResultData`
- `BluetoothSessionData`
- `BluetoothTransferData`
- `BluetoothReadData`
- `BluetoothBleServicesData`
- `BluetoothBleNotificationData`
- `NotificationData`
- `LocationData`
- `DeviceInfoResultData`

其中：

- `SystemSettingData` 包含 `namespace`、`setting`、`value`
- `AppOperationData` 包含 `operationType`、`packageName`、`success`、`details`
- `AppUsageTimeResultData` 包含时间窗口、是否包含系统应用以及每个应用的前台使用时长条目
- `BluetoothStateData` 包含设备是否支持蓝牙、是否已开启以及当前状态
- `BluetoothBondedDevicesData` 包含已配对蓝牙设备列表
- `BluetoothScanResultData` 包含扫描到的设备列表、来源和 RSSI
- `BluetoothSessionData` 包含蓝牙会话 ID、地址和模式
- `BluetoothTransferData` 包含写入字节数
- `BluetoothReadData` 包含读取字节数、UTF-8 文本和 base64 字节
- `BluetoothBleServicesData` 包含 BLE service 与 characteristic 列表
- `BluetoothBleNotificationData` 包含已收到的 BLE 通知列表
- `NotificationData` 提供通知列表和抓取时间戳
- `LocationData` 提供经纬度、精度、地址等信息

### 4. UI 与自动化结果

常见类型：

- `SimplifiedUINode`
- `UIPageResultData`
- `UIActionResultData`
- `CombinedOperationResultData`
- `AutomationExecutionResultData`

其中：

- `UIPageResultData` 包含 `packageName`、`activityName`、`uiElements`
- `UIActionResultData` 描述一次点击、输入、滑动等动作
- `AutomationExecutionResultData` 额外包含 `agentId`、`displayId`、`executionSuccess`、`executionMessage`、`finalState`

### 5. Shell / Intent / Terminal / FFmpeg 结果

常见类型：

- `ADBResultData`
- `IntentResultData`
- `TerminalCommandResultData`
- `TerminalSessionCreationResultData`
- `TerminalSessionCloseResultData`
- `TerminalSessionScreenResultData`
- `FFmpegResultData`
- `StringResultData`

`StringResultData` 很简单，只包含：

- `value`
- `toString()`

很多“控制类 API”都会返回它。

### 6. 工作流结果与工作流结构

`results.d.ts` 不只是工作流返回值，还定义了工作流图本身的数据结构。

常见类型：

- `WorkflowResultData`
- `WorkflowListResultData`
- `NodePosition`
- `StaticValue`
- `NodeReference`
- `ParameterValue`
- `TriggerType`
- `TriggerNode`
- `ExecuteNode`
- `ConditionOperator`
- `ConditionNode`
- `LogicOperator`
- `LogicNode`
- `ExtractMode`
- `ExtractNode`
- `WorkflowNode`
- `WorkflowConnectionConditionKeyword`
- `WorkflowConnectionCondition`
- `WorkflowNodeConnection`
- `WorkflowDetailResultData`

其中：

- `WorkflowResultData` / `WorkflowListResultData` 更偏列表视图
- `WorkflowDetailResultData` 包含 `nodes`、`connections`、`enabled`、统计信息与最近执行状态
- `WorkflowNode` 是五类节点的联合类型

### 7. 软件设置与模型配置结果

常见类型：

- `SpeechTtsHttpConfigResultItem`
- `SpeechSttHttpConfigResultItem`
- `SpeechServicesConfigResultData`
- `SpeechServicesUpdateResultData`
- `ModelConfigResultItem`
- `FunctionModelMappingResultItem`
- `ModelConfigsResultData`
- `ModelConfigCreateResultData`
- `ModelConfigUpdateResultData`
- `ModelConfigDeleteResultData`
- `FunctionModelConfigsResultData`
- `FunctionModelConfigResultData`
- `FunctionModelBindingResultData`
- `ModelConfigConnectionTestItemResultData`
- `ModelConfigConnectionTestResultData`

这一部分主要给 `Tools.SoftwareSettings` 使用。

### 8. Chat 结果

常见类型：

- `ChatServiceStartResultData`
- `ChatCreationResultData`
- `ChatInfo`
- `ChatListResultData`
- `ChatSwitchResultData`
- `ChatTitleUpdateResultData`
- `ChatDeleteResultData`
- `MessageSendResultData`
- `ChatMessageInfo`
- `ChatMessagesResultData`
- `CharacterCardListResultData`
- `CharacterCardInfo`
- `ChatFindResultData`
- `ChatRuntimeStateError`
- `ChatRuntimeStateRetry`
- `CurrentChatRuntimeStateResultData`
- `GlobalChatRuntimeStateResultData`

同时也定义了对应的包装结果：

- `ChatServiceStartResult`
- `ChatCreationResult`
- `ChatListResult`
- `ChatFindResult`
- `CurrentChatRuntimeStateResult`
- `GlobalChatRuntimeStateResult`
- `ChatSwitchResult`
- `ChatTitleUpdateResult`
- `ChatDeleteResult`
- `MessageSendResult`
- `ChatMessagesResult`

### 9. 记忆链接结果

常见类型：

- `MemoryQueryResultData`
- `MemoryLinkResultData`
- `MemoryLinkQueryResultData`
- `MemoryLinkResult`
- `MemoryLinkQueryResult`

`MemoryQueryResultData` 内部包含 `memories[]`，每一项都有：

- `title`
- `content`
- `source`
- `tags`
- `createdAt`
- `chunkInfo`
- `chunkIndices`

`MemoryQueryResultData` 自身还包含：

- `snapshotId`
- `snapshotCreated`
- `excludedBySnapshotCount`

其中 `snapshotId` 可以是系统自动生成的，也可以是调用方主动传入并首次创建的自定义字符串。

`MemoryLinkQueryResultData` 内部包含 `links[]`，每一项都有：

- `linkId`
- `sourceTitle`
- `targetTitle`
- `linkType`
- `weight`
- `description`

## 示例

### 使用 `FileContentData`

```ts
const file = await Tools.Files.read('/sdcard/a.txt');
console.log(file.content);
console.log(file.size);
```

### 使用 `VisitWebResultData`

```ts
const page = await Tools.Net.visit('https://example.com');
console.log(page.title);
console.log(page.links?.length ?? 0);
if (page.contentSavedTo) {
  console.log(page.contentSavedTo);
}
```

### 使用 `WorkflowDetailResultData`

```ts
const detail = await Tools.Workflow.get('workflow_123');
console.log(detail.nodes.length);
console.log(detail.connections.length);
```

## 如何阅读这份文件

推荐按“谁返回它”来反查：

- 文件相关 → `files.d.ts`
- 网络相关 → `network.d.ts` / `okhttp.d.ts`
- 系统相关 → `system.d.ts`
- UI 相关 → `ui.d.ts`
- 工作流相关 → `workflow.d.ts`
- 软件设置相关 → `software_settings.d.ts`
- Chat 相关 → `chat.d.ts`
- 记忆相关 → `memory.d.ts`

## 相关文件

- `examples/types/results.d.ts`
- `docs/doc-src/package-dev/files.md`
- `docs/doc-src/package-dev/network.md`
- `docs/doc-src/package-dev/system.md`
- `docs/doc-src/package-dev/ui.md`
- `docs/doc-src/package-dev/workflow.md`
- `docs/doc-src/package-dev/software_settings.md`
- `docs/doc-src/package-dev/chat.md`
- `docs/doc-src/package-dev/memory.md`
