# API 文档：`results.d.ts`

`results.d.ts` 是所有工具返回结构的集中定义。它本身不提供运行时方法，但几乎所有 `Tools.*`、`toolCall()` 和部分全局对象最终都会返回这里定义的数据类型。

## 审计状态

本页已按 `examples/types/results.d.ts` 的 162 个 interface 和 742 个顶层字段完成声明/运行时交叉核对；其中 38 个 `BaseResult` 包装接口的 `data` 映射也已逐项核对。字段说明优先以当前 Android/Kotlin 序列化 DTO 和工具构造路径为准，不能从 TypeScript 声明单独推断运行时形状。

本轮补齐的结果类型证据来自 `ToolResultDataClasses.kt`、`StandardCalculator.kt`、`StandardSystemOperationTools.kt`、`BluetoothSessionManager.kt`、`StandardTerminalCommandExecutor.kt`、`StandardMusicPlaybackTools.kt` 和 `StandardFFmpegTool.kt`。其中包括计算/日期/连接、应用使用统计和 Bluetooth 嵌套类型、终端流与会话、音乐播放、FFmpeg 流信息及字符串包装等此前未闭环的接口/字段。

本页还单独记录了当前运行时可见但未出现在 `results.d.ts` 的数据类；这些条目属于实现观察，不会被误报为稳定的 TypeScript 契约。所有结果消费都应先检查外层 `BaseResult`/`ToolResult` 的成功状态，再读取 `data`，并保留对可空、未知字符串和声明/运行时差异的兼容处理。

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

#### 逐字段结构

| 类型 | 声明字段 | 语义/运行时要点 |
| --- | --- | --- |
| `FileEntry` | `name: string`、`isDirectory: boolean`、`size: number`、`permissions: string`、`lastModified: string` | Android DTO 中对应 `DirectoryListingData.FileEntry` 嵌套类型，不是独立顶层类；`size` 是字节数。`.d.ts` 声明的 `toString()` 在 Kotlin 嵌套数据类上没有对应自定义实现。 |
| `FileExistsData` | `env: 'android' \| 'linux'`、`path: string`、`exists: boolean`、`isDirectory?: boolean`、`size?: number` | 不存在时仍返回目标路径和 `exists=false`；Kotlin 的 `isDirectory`、`size` 默认分别为 `false`、`0`，`env` 默认 `android` 并通过 `@EncodeDefault` 编码。 |
| `FileInfoData` | `env: 'android' \| 'linux'`、`path: string`、`exists: boolean`、`fileType: string`、`size: number`、`permissions: string`、`owner: string`、`group: string`、`lastModified: string`、`rawStatOutput: string` | `fileType` 文档枚举为 `file` / `directory` / `other`；`rawStatOutput` 是底层 stat 文本。不存在时其他元数据仍是结果字段，不能代替 `exists` 判断；具体 owner/group/权限/时间值由 Android、Linux 或 SAF 路径提供。 |
| `DirectoryListingData` | `env: 'android' \| 'linux'`、`path: string`、`entries: FileEntry[]` | 目录项数组；Kotlin 将 `FileEntry` 定义为该 DTO 内的嵌套类型。 |
| `FileContentData` | `env: 'android' \| 'linux'`、`path: string`、`content: string`、`size: number` | 文本内容与字节大小。读取工具可能在 `content` 中附加行号或截断提示，因此 `size` 应按该工具构造结果的语义读取，不保证等于返回字符串的 UTF-8 字节长度。 |
| `BinaryFileContentData` | `env: 'android' \| 'linux'`、`path: string`、`contentBase64: string`、`size: number` | 二进制内容以 Base64 表示；`size` 是原文件字节数，不是 Base64 字符串长度。 |
| `FilePartContentData` | `env: 'android' \| 'linux'`、`path: string`、`content: string`、`partIndex: number`、`totalParts: number`、`startLine: number`、`endLine: number`、`totalLines: number` | 行范围在当前 Linux 实现中用 0 起始、左闭右开区间表示；`toString()` 将其格式化为 1 起始的闭区间。当前实现保留兼容值 `partIndex=0`、`totalParts=1`，不是分页结果数量。 |
| `FileOperationData` | `env: 'android' \| 'linux'`、`operation: string`、`path: string`、`successful: boolean`、`details: string` | 单项文件操作状态；字段可表示失败，即使工具成功返回了一个结构化结果。`toString()` 返回 details 摘要，工具调用整体仍需检查外层结果。 |
| `FileApplyResultData` | `operation: FileOperationData`、`aiDiffInstructions: string` | TypeScript 声明遗漏了 Android DTO 序列化的可空 `diffContent?: string \| null`。`toString()` 可把 diff 封装为渲染标记；不要把呈现字符串当作原始 JSON 字段。 |
| `FindFilesResultData` | `env: 'android' \| 'linux'`、`path: string`、`pattern: string`、`files: string[]` | 搜索根路径、模式及匹配路径列表；`toString()` 在匹配较多时只展示部分路径，结构化 `files` 才是完整返回列表。 |
| `GrepLineMatch` | `lineNumber: number`、`lineContent: string`、`matchContext?: string` | Kotlin 中对应 `GrepResultData.LineMatch`；`matchContext` 可空且默认为 `null`。 |
| `GrepFileMatch` | `filePath: string`、`lineMatches: GrepLineMatch[]` | Kotlin 中对应 `GrepResultData.FileMatch`，按文件分组匹配行；声明侧顶层名称与运行时嵌套类型名称不同。 |
| `GrepResultData` | `env: 'android' \| 'linux'`、`searchPath: string`、`pattern: string`、`filePattern?: string`、`matches: GrepFileMatch[]`、`totalMatches: number`、`filesSearched: number` | Kotlin DTO 没有 `filePattern` 字段；它是搜索输入过滤条件，不会随结果回传。标准 grep 路径最多返回 20 个文件分组，`totalMatches` 可能受请求上限约束；语义搜索分支的 `filesSearched` 当前为 0。`toString()` 最多内联显示 30 个匹配组。 |

宿主 `FileExistsData`、`FileInfoData`、`DirectoryListingData`、`FileContentData`、`BinaryFileContentData`、`FileOperationData` 等 Kotlin DTO 把 `env` 默认设为 `android`，并通过 `@EncodeDefault` 编码。`FileApplyResultData.diffContent` 是与声明之间已确认的额外运行时字段。时间字符串的格式、权限文本格式及各环境可获得的 owner/group 信息由底层文件工具决定；此表不把它们推断成跨环境固定格式。

### 2. 网络结果

常见类型：

- `HttpResponseData`
- `Link`
- `VisitWebResultData`

其中：

- `HttpResponseData` 包含 `statusCode`、`statusMessage`、`headers`、`contentType`、`content`
- `VisitWebResultData` 除了页面正文外，还可能包含 `metadata`、`links`、`imageLinks`、`visitKey`
- 当网页正文过长时，`VisitWebResultData` 还可能包含 `contentSavedTo`、`contentTruncated`、`originalContentLength`

#### 逐字段结构

| 类型 | 声明字段 | 语义/运行时要点 |
| --- | --- | --- |
| `HttpResponseData` | `url: string`、`statusCode: number`、`statusMessage: string`、`headers: Record<string, string>`、`contentType: string`、`content: string`、`size: number` | HTTP 状态、头、文本和响应体字节数。Kotlin DTO 还包含 nullable `contentBase64` 与默认空映射 `cookies`，二者缺失于 `.d.ts`；当前 HTTP 调用路径会将响应体字节编码为 Base64，并按响应体实际字节数设置 `size`。 |
| `Link` | `text: string`、`url: string` | 网页链接的可见文本和目标 URL；Kotlin 中对应 `VisitWebResultData.LinkData` 嵌套类型，属性值一致，但没有独立顶层 `Link` DTO。 |
| `VisitWebResultData` | `url: string`、`title: string`、`content: string`、`metadata?: Record<string, string>`、`links?: Link[]`、`imageLinks?: string[]`、`visitKey?: string`、`contentSavedTo?: string`、`contentTruncated?: boolean`、`originalContentLength?: number` | 页面正文、元信息、链接/图片链接及访问 key。Kotlin 默认空映射/空列表/`contentTruncated=false`；正文超过阈值时会保存完整内容并用预览替换 `content`，`contentSavedTo` 指向文件，`originalContentLength` 按字符数记录。`toString()` 的内联链接和图片预览最多各显示 120 项，不等于结构化列表长度上限。 |

#### 网络声明与运行时差异

`.d.ts` 的 `HttpResponseData` 未声明 `contentBase64`、`cookies`；Android DTO 包含这两个属性，`contentBase64` 可空、`cookies` 默认为空映射，默认值是否在输出 JSON 中省略取决于序列化配置。`VisitWebResultData` 的可选字段在 Kotlin DTO 中有默认值，序列化输出可能包含空列表、空映射和 `false`，不能只按“字段可选”推断字段必然缺失。`Link` 与 `FileEntry` 一样，是声明侧独立接口、运行时嵌套数据类；运行时没有为链接项提供 `.d.ts` 所列的自定义 `toString()`。

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

#### 逐字段结构

| 类型 | 声明字段 | 语义/运行时要点 |
| --- | --- | --- |
| `DeviceInfoResultData` | `deviceId`、`model`、`manufacturer`、`androidVersion: string`；`sdkVersion: number`；`screenResolution: string`；`screenDensity: number`；`totalMemory`、`availableMemory`、`totalStorage`、`availableStorage: string`；`batteryLevel: number`；`batteryCharging: boolean`；`cpuInfo`、`networkType: string`；`additionalInfo: Record<string, string>` | 设备、屏幕、内存/存储、供电、处理器和网络快照。分辨率为像素宽高字符串；`screenDensity` 是 Android `displayMetrics.density` 浮点值。容量字段按 B/KB/MB/GB/TB 格式化；存储取外部存储路径统计。`additionalInfo` 当前含 device/product/hardware/build fingerprint/build time。 |
| `SleepResultData` | `requestedMs: number`、`sleptMs: number` | 请求等待时间和实际等待时间，单位毫秒。无效输入会采用 `1000`；负数保留在 `requestedMs`，实际 `sleptMs` 钳制为 `0`。 |
| `SystemSettingData` | `namespace: string`、`setting: string`、`value: string` | 命名空间限 `system`、`secure`、`global`。读取只在值非空时返回此 DTO；写入时 `value` 是请求写入的值，当前实现不重新读取验证，且忽略平台 `putString` 的布尔返回值。 |
| `AppOperationData` | `operationType: string`、`packageName: string`、`success: boolean`、`details: string` | 常见类型为 `install_request`、`uninstall_request`、`start`、`stop`。前两者表示已发起系统安装/卸载界面，不表示用户确认完成；`stop` 表示已请求结束后台进程。`success` 是 payload 字段，仍需区分外层状态；`toString()` 对 `install_request` / `uninstall_request` 返回 `details`。 |
| `AppListData` | `includesSystemApps: boolean`、`packages: string[]` | 是否包含系统应用及字符串列表。每项格式为 `应用标签 (package.name)`，不是纯包名；列表按完整字符串排序。 |
| `AppUsageTimeEntry` | `packageName: string`、`appName: string`、`totalForegroundTimeMs: number`、`lastTimeUsed: number`、`isSystemApp: boolean` | 单应用使用时长条目；时长以毫秒表示。 |
| `AppUsageTimeResultData` | `startTime`、`endTime: number`；`sinceHours: number`；`requestedPackageName?: string`；`includesSystemApps: boolean`；`totalEntries: number`；`entries: AppUsageTimeEntry[]` | 时间戳为 epoch 毫秒，使用量按包名汇总前台时长并按时长降序；`totalEntries` 是截断后的返回条数。默认查询最近 24 小时、最多 10 项；特定包名筛选最多返回 1 项，空筛选回传为 `null`/可省略。无使用统计权限时工具失败并打开设置页。 |
| `BluetoothStateData` | `supported: boolean`、`enabled: boolean`、`state: 'unsupported' \\| 'off' \\| 'turning_on' \\| 'on' \\| 'turning_off' \\| 'unknown'` | 适配器不存在时外层调用仍成功，结果为 `supported=false`、`enabled=false`、`state='unsupported'`；其他状态由 Android adapter 状态映射。Kotlin `state` 是普通字符串，字面量联合不是运行时校验。 |
| `BluetoothDeviceData` | `name?: string`、`address: string`、`type: 'classic' \\| 'le' \\| 'dual' \\| 'unknown'`、`bondState: 'none' \\| 'bonding' \\| 'bonded' \\| 'unknown'` | 已配对设备条目；name 可空。当前 Android 常量映射到这些字符串，DTO 本身仍是开放字符串。 |
| `BluetoothBondedDevicesData` | `devices: BluetoothDeviceData[]` | 已配对设备按名称升序、再按地址排序。 |
| `BluetoothScannedDeviceData` | 继承 `BluetoothDeviceData`；另有 `source: 'classic' \\| 'ble'`、`rssi?: number` | 声明侧使用继承；Kotlin DTO 实际是包含重复设备字段的独立扁平类。`source` 区分经典扫描/BLE；经典扫描缺少 RSSI 时为 `null`。 |
| `BluetoothScanResultData` | `devices: BluetoothScannedDeviceData[]`、`durationMs: number`、`includesBle: boolean` | 扫描总是运行经典扫描，BLE 由 `include_ble` 控制；两路结果按设备地址合并并按名称/地址排序。`includesBle` 反映请求选项。 |
| `BluetoothSessionData` | `sessionId: string`、`address: string`、`mode: 'classic' \\| 'classic_listener' \\| 'ble'` | 经典连接在建立 socket 后返回；listener 模式用地址 `local`；BLE 在创建 GATT 并登记会话后立即返回，不等待连接或 service discovery 完成。`mode='ble'` 不代表服务已就绪。 |
| `BluetoothTransferData` | `sessionId: string`、`bytesWritten: number` | 写入操作返回实际写入字节数；BLE 通知订阅/取消也复用此类型，`bytesWritten` 固定为 `0`。 |
| `BluetoothReadData` | `sessionId: string`、`bytesRead: number`、`text?: string`、`dataBase64?: string` | 非空读取同时返回 UTF-8 解码文本和 Base64；经典读取或 BLE 特征读取超时会返回 `bytesRead=0`，并省略 `text` / `dataBase64`，不把超时本身记为读取错误。 |
| `BluetoothBleServicesData` | `sessionId: string`、`services: BluetoothBleServiceData[]` | BLE service discovery 完成后返回该会话的 service 列表。 |
| `BluetoothBleServiceData` | `uuid: string`、`characteristics: BluetoothBleCharacteristicData[]` | BLE service UUID 与 characteristics。 |
| `BluetoothBleCharacteristicData` | `uuid: string`、`properties: Array<'read' \\| 'write' \\| 'write_no_response' \\| 'notify' \\| 'indicate'>` | characteristic UUID 和由 Android 属性位映射出的操作能力；宿主实际为 `List<String>`。 |
| `BluetoothBleNotificationData` | `sessionId: string`、`notifications: BluetoothBleNotificationEntry[]` | 读取队列中当前通知，并从该会话缓冲区移除已返回条目。 |
| `BluetoothBleNotificationEntry` | `characteristicUuid: string`、`bytesRead: number`、`text?: string`、`dataBase64?: string`、`timestamp: number` | 通知来源 characteristic、字节数及可选文本/Base64；时间戳由宿主在收到通知时设为 epoch 毫秒。 |
| `NotificationData` | `notifications: Array<{ packageName: string; text: string; timestamp: number }>`、`timestamp: number` | 外层时间戳是快照时间；条目按最新优先排列。`include_ongoing=false` 会排除 ongoing 通知，`limit` 负值按 `0` 处理。通知文本由标题、正文、大文本和文本行去重拼接，均为空时回退 ticker。 |
| `LocationData` | `latitude`、`longitude`、`accuracy: number`；`provider: string`；`timestamp: number`；`rawData: string`；`address?`、`city?`、`province?`、`country?: string` | 经纬度为十进制度，精度单位米；时间戳取 Android `Location.time`（epoch 毫秒），provider 缺失时为 `unknown`。不请求地址解析时地址字段在 DTO 中为空字符串；字段默认值是否省略取决于 JSON 默认值配置。 |

这些 Android DTO 多数使用 `Int`、`Long`、`Float` 或 `Double`，在 JSON 中都表现为 number。`BluetoothStateData.state` 及 Bluetooth 设备类型/来源/属性在 Kotlin 层是字符串，因此消费端要容忍未知值。可空 Bluetooth 字段在 DTO 中可能以 `null` 表示，具体缺省/null 序列化行为还取决于调用路径的 JSON 配置。
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

#### 逐字段结构

| 类型 | 声明字段 | 语义与运行时处理 |
| --- | --- | --- |
| `SimplifiedUINode` | `className?`、`text?`、`contentDesc?`、`resourceId?`、`bounds?: string`；`isClickable: boolean`；`children: SimplifiedUINode[]` | Android DTO 中前五个文本字段是 nullable；`children` 是子节点数组。`toTreeString(indent?)` 只保留关键控件类型、有文本/描述、可点击或包含可保留后代的节点，并递归生成树文本。 |
| `UIPageResultData` | `packageName: string`、`activityName: string`、`uiElements: SimplifiedUINode` | 当前应用包名、Activity 名和根 UI 节点；`toString()` 会调用根节点的 `toTreeString()`，展示文本不是额外 JSON 字段。 |
| `UIActionResultData` | `actionType: string`、`actionDescription: string`、`coordinates?: [number, number]`、`elementId?: string` | 动作类型、动作描述及可选目标坐标/元素 ID。Kotlin 使用可空 `Pair<Int, Int>`，序列化后按二元数组消费；动作整体成功仍由外层结果判断。 |
| `CombinedOperationResultData` | `operationSummary: string`、`waitTime: number`、`pageInfo: UIPageResultData` | 一次操作摘要、等待毫秒数和操作后的页面信息；Kotlin `waitTime` 为 `Int`。 |
| `AutomationExecutionResultData` | `functionName: string`、`providedParameters: Record<string, string>`、`agentId?: string \| null`、`displayId?: number \| null`、`executionSuccess: boolean`、`executionMessage: string`、`executionError?: string \| null`、`finalState?: { nodeId: string; packageName: string; activityName: string } \| null`、`executionSteps: number` | 自动化函数执行快照。源码数据类名为 `AutomationExecutionResult`；`executionError` 和 `finalState` 可为 `null`，`executionSuccess` 只表示执行状态，不代表最终页面满足业务断言。`finalState` 是执行结束时的节点、包名和 Activity。 |

`SimplifiedUINode.shouldKeepNode?()` 出现在 `.d.ts`，但 Kotlin 实现中的同名方法是 `private`，仅由 `toTreeString()` 内部调用，调用端不应把它视为可用的公开节点 API。各 DTO 的 `toString()`、`toTreeString()` 产出展示文本，不属于结构化结果字段。

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

#### 逐字段结构

| 类型 | 声明字段 | 语义与运行时处理 |
| --- | --- | --- |
| `ADBResultData` | `command: string`、`output: string`、`exitCode: number` | 执行的 ADB 命令、标准输出/错误汇总及退出码；`exitCode === 0` 通常表示成功，不能替代外层 `success` 判断。 |
| `IntentResultData` | `action`、`uri`、`package_name`、`component`、`result: string`；`flags`、`extras_count: number`；`type: 'activity' \| 'broadcast' \| 'service'` | Intent 执行快照。当前 Kotlin DTO 序列化 `action`、`uri`、`package_name`、`component`、`flags`、`extras_count`、`result` 七个字段，没有声明中的 `type`；`uri`、`package_name`、`component` 在展示时以字符串 `"null"` 表示未设置。 |
| `TerminalCommandResultData` | `command: string`、`output: string`、`exitCode: number`、`sessionId: string`、`timedOut?: boolean` | 指定终端会话中的命令结果。Kotlin `timedOut` 默认 `false`；超时会取消当前命令并保留会话，字段可因默认值序列化策略而缺省。 |
| `TerminalStreamEventData` | `type: string`、`command: string`、`sessionId: string`、`chunk?: string \| null`、`chunkIndex?: number \| null`、`receivedChars?: number \| null` | 流式事件当前使用 `start` 和 `chunk`；`chunkIndex` 从 0 开始，`receivedChars` 是累计字符数。`toString()` 对 `chunk` 事件返回块内容，对 `start` 返回启动摘要。 |
| `HiddenTerminalCommandResultData` | `command: string`、`output: string`、`exitCode: number`、`executorKey: string`、`timedOut?: boolean` | 隐藏执行器的命令结果；`executorKey` 是执行器标识，超时字段与普通终端结果同样默认 `false`。 |
| `TerminalSessionCreationResultData` | `sessionId: string`、`sessionName: string`、`isNewSession: boolean` | 会话创建或复用结果；`isNewSession=false` 表示取得已有会话，不表示命令执行失败。 |
| `TerminalSessionCloseResultData` | `sessionId: string`、`success: boolean`、`message: string` | 被关闭的会话、关闭操作状态和消息；此处的 `success` 是 payload 字段，仍应区分外层结果状态。 |
| `TerminalSessionScreenResultData` | `sessionId: string`、`rows: number`、`cols: number`、`content: string` | 当前终端可见屏幕快照。`content` 不含历史滚屏缓冲区；`rows`/`cols` 描述屏幕尺寸。 |
| `FFmpegResultData` | 声明：`command`、`returnCode`、`output`、`duration: number`、`videoStreams: FFmpegStreamInfo[]`、`audioStreams: FFmpegStreamInfo[]` | 当前 Kotlin DTO 实际字段为 `command`、`returnCode`、`output`、`duration`、可选 `outputFile` 和可选 `mediaInfo`；没有声明中的顶层 `videoStreams`/`audioStreams`。`mediaInfo` 内含 `format`、`duration`、`bitrate` 以及嵌套 `StreamInfo` 列表。 |
| `StringResultData` | `value: string` | 工具内部的字符串结果包装；`toString()` 返回原始字符串值。 |

`FFmpegResultData` 的运行时嵌套 `StreamInfo` 字段为 `index`、`codecType`、`codecName`、可选 `resolution`、`frameRate`、`sampleRate`、`channels`；这与 `.d.ts` 的 `FFmpegStreamInfo`（`type`、`codec` 和模板字符串速率字段）不是同一结构。`FFmpegStreamInfo` 当前只在声明侧出现，不能据此假设 FFmpeg 返回顶层流列表。`toString()` 只负责命令、返回码、耗时、输出文件、媒体信息和日志的展示。

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

#### 工作流结果字段与节点结构

| 类型 | 声明字段 | 语义与运行时处理 |
| --- | --- | --- |
| `WorkflowResultData` | `id`、`name`、`description: string`；`nodeCount`、`connectionCount`、`createdAt`、`updatedAt`、`totalExecutions`、`successfulExecutions`、`failedExecutions: number`；`enabled: boolean`；`lastExecutionTime?: number \| null`、`lastExecutionStatus?: string \| null` | 列表摘要。节点数/连接数由当前工作流的数组长度计算；时间来自 Kotlin `Long` 字段，创建/更新时间默认使用 epoch 毫秒。状态从执行状态名生成，例如 `SUCCESS`、`FAILED`、`RUNNING`。 |
| `WorkflowListResultData` | `workflows: WorkflowResultData[]`、`totalCount: number` | 当前列表和数量；工具实现以实际返回列表长度设置 `totalCount`。失败占位结果为 `[]` 和 `0`，需先看外层 `success` / `error`。 |
| `WorkflowDetailResultData` | `id`、`name`、`description: string`；`nodes: WorkflowNode[]`、`connections: WorkflowNodeConnection[]`；`enabled: boolean`；`createdAt`、`updatedAt`、`totalExecutions`、`successfulExecutions`、`failedExecutions: number`；`lastExecutionTime?: number \| null`、`lastExecutionStatus?: string \| null` | 完整工作流与统计快照。失败占位对象的字符串为空、数组为空、时间/计数为 0；不能只看 `data` 判断成功。 |
| `NodePosition` | `x: number`、`y: number` | 节点画布位置；Kotlin 存为 `Float`。 |
| `StaticValue` | `__type?: string`、`value: string` | `ParameterValue` 的静态字符串值变体。 |
| `NodeReference` | `__type?: string`、`nodeId: string` | 引用另一个节点输出的变体。 |
| `TriggerNode` | `__type?: string`、`id: string`、`type: 'trigger'`、`name: string`、`description: string`、`position: NodePosition`、`triggerType: TriggerType`、`triggerConfig: Record<string, string>` | 触发入口与配置。解析输入缺少 ID 时生成 UUID；名称默认为 `Trigger`，触发类型默认为 `manual`，配置默认为空对象。 |
| `ExecuteNode` | `__type?: string`、`id: string`、`type: 'execute'`、`name: string`、`description: string`、`position: NodePosition`、`actionType: string`、`actionConfig: Record<string, string \| ParameterValue>`、`jsCode?: string \| null` | 工具动作或 JavaScript 动作。解析输入缺少名称时默认为 `Action`；动作配置的原始字符串、数字和布尔值会转成静态 `ParameterValue`。 |
| `ConditionNode` | `__type?: string`、`id: string`、`type: 'condition'`、`name: string`、`description: string`、`position: NodePosition`、`left: ParameterValue`、`operator: ConditionOperator`、`right: ParameterValue` | 比较两个参数值。输入缺少名称/操作符时分别默认为 `Condition` / `EQ`；无效操作符也回退到 `EQ`。 |
| `LogicNode` | `__type?: string`、`id: string`、`type: 'logic'`、`name: string`、`description: string`、`position: NodePosition`、`operator: LogicOperator` | 逻辑节点；`operator` 为 `AND` 或 `OR`。解析输入缺少名称/操作符时默认为 `Logic` / `AND`；也兼容输入字段 `operatorLogic`。 |
| `ExtractNode` | `__type?: string`、`id: string`、`type: 'extract'`、`name: string`、`description: string`、`position: NodePosition`、`source: ParameterValue`、`mode: ExtractMode`、`expression: string`、`group: number`、`defaultValue: string`、`others?: ParameterValue[]`、`startIndex?: number`、`length?: number`、`randomMin?: number`、`randomMax?: number`、`randomStringLength?: number`、`randomStringCharset?: string`、`useFixed?: boolean`、`fixedValue?: string` | 提取/组合/随机生成节点。运行时这些可选数值/布尔字段都有默认值：`startIndex=0`、`length=-1`、`randomMin=0`、`randomMax=100`、`randomStringLength=8`、`useFixed=false`；字符集默认为英数字。缺少名称时为 `Extract`，无效 `mode` 回退为 `REGEX`。`expression` 也兼容输入别名 `pattern` / `path`。 |
| `WorkflowNodeConnection` | `id: string`、`sourceNodeId: string`、`targetNodeId: string`、`condition?: WorkflowConnectionCondition \| null` | 连接来源、目标和可选条件。输入解析还兼容 source/target 的 ID、索引和名称别名；无效端点、自连接会被忽略，输出统一为节点 ID。 |

工作流节点的 `type` 静态区分 `trigger`、`execute`、`condition`、`logic`、`extract`；`WorkflowNode` 是这五种接口的联合类型。运行时序列化器使用 `__type` 作为 sealed class discriminator；输入也可按 `__type` 推断节点类型，未知类型不会进入解析结果。`TriggerType` 的声明允许额外字符串，运行时也使用开放字符串；当前实现还识别 `app_open`。

`ParameterValue` 的静态形状是 `string | StaticValue | NodeReference`。运行时采用 sealed class：字符串、数字和布尔输入都转换为字符串静态值；`null` 转为空字符串静态值；对象含 `nodeId`、`ref` 或 `refNodeId` 时转换为节点引用，否则取对象的 `value`，没有该字段时将对象 JSON 文本作为静态值。序列化时的 `__type` 是判别字段，不是业务输入必填字段。

`ConditionOperator` 取 `EQ`、`NE`、`GT`、`GTE`、`LT`、`LTE`、`CONTAINS`、`NOT_CONTAINS`、`IN`、`NOT_IN`。数字两侧都能解析为数值时比较数值；两侧都不是数字时按字符串比较；一侧为数字、另一侧非数字时会报类型不匹配。`IN` / `NOT_IN` 的右值可为 JSON 数组或逗号分隔字符串，混合数字/文本列表会报错。`LogicOperator` 为 `AND` / `OR`；`ExtractMode` 为 `REGEX`、`JSON`、`SUB`、`CONCAT`、`RANDOM_INT`、`RANDOM_STRING`。

`WorkflowConnectionConditionKeyword` 的固定字面量为 `true`、`false`、`on_success`、`success`、`ok`、`on_error`、`error`、`failed`；`WorkflowConnectionCondition` 还允许其他字符串作为正则条件。执行器把 `success` / `ok` / `on_success` 与成功状态匹配，把 `error` / `failed` / `on_error` 与失败状态匹配；空条件默认要求上游成功，其他字符串按上游结果做正则匹配。工作流结果 DTO 的 `toString()` 只生成展示文本，不属于 JSON 字段。

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
- `ModelConfigConnectionTestOutcome`
- `ModelConfigConnectionTestItemResultData`
- `ModelConfigConnectionTestResultData`

这一部分主要给 `Tools.SoftwareSettings` 使用。

`ModelConfigConnectionTestResultData.success` 表示连接测试没有硬失败，例如请求或工具调用没有报错。它不等于多模态能力已经被证明。

`ModelConfigConnectionTestResultData.verified` 表示本次请求的所有测试项都得到验证。单项 `tests[].outcome` 有三种取值：

- `passed`：该项已验证通过
- `unverified`：请求已连通，但返回内容没有证明对应能力
- `failed`：该项请求或执行失败

统计字段中，`passedTests`、`unverifiedTests`、`failedTests` 分别对应这三类单项结果。

#### 模型配置结果字段

| 类型/分组 | 声明字段 | 语义与运行时处理 |
| --- | --- | --- |
| `ModelConfigResultItem`：标识 | `id`、`name`、`apiProviderType`、`apiEndpoint`、`modelName: string`；`modelList: string[]` | 配置 ID、名称、provider 标识、endpoint、默认模型名及可选模型列表。运行时把 `apiProviderType` 映射为 provider type ID，不是面向用户的显示名；`modelList` 由配置中的模型名生成。 |
| `ModelConfigResultItem`：密钥状态 | `apiKeySet: boolean`、`apiKeyPreview: string` | 不返回完整密钥。当前 `apiKeyPreview` 为空密钥返回空串；长度不超过 4 时每个字符替换为 `*`；更长时只保留前 3 位和后 2 位，中间替换为 `***`。TTS/STT 配置使用相同预览规则。 |
| `ModelConfigResultItem`：采样与长度 | `maxTokensEnabled: boolean` / `maxTokens: number`、`temperatureEnabled: boolean` / `temperature: number`、`topPEnabled: boolean` / `topP: number`、`topKEnabled: boolean` / `topK: number`、`presencePenaltyEnabled: boolean` / `presencePenalty: number`、`frequencyPenaltyEnabled: boolean` / `frequencyPenalty: number`、`repetitionPenaltyEnabled: boolean` / `repetitionPenalty: number` | 每个数值和对应的启用开关成对返回；启用状态与数值是不同属性。 |
| `ModelConfigResultItem`：自定义请求参数 | `hasCustomParameters: boolean`、`customParameters: string`、`hasCustomHeaders: boolean`、`customHeaders: string` | 自定义参数和请求头保留为 JSON 字符串。`hasCustomHeaders` 在运行时由 headers 去除首尾空白后是否为空、是否等于 `{}` 推导；`hasCustomParameters` 直接映射配置状态。 |
| `ModelConfigResultItem`：上下文/摘要 | `contextLength: number`、`maxContextLength: number`、`enableMaxContextMode: boolean`、`summaryTokenThreshold: number`、`enableSummary: boolean`、`enableSummaryByMessageCount: boolean`、`summaryMessageCountThreshold: number` | 上下文长度与摘要阈值/开关；`summaryMessageCountThreshold` 是按消息数触发摘要的阈值，其他数值的有效范围由对应模型/provider 实现决定。 |
| `ModelConfigResultItem`：MNN | `mnnForwardType: number`、`mnnThreadCount: number` | 本地 MNN forward 类型与线程数。 |
| `ModelConfigResultItem`：llama | `llamaThreadCount`、`llamaContextSize`、`llamaBatchSize`、`llamaUBatchSize`、`llamaGpuLayers: number`；`llamaUseMmap`、`llamaFlashAttention`、`llamaKvUnified`、`llamaOffloadKqv: boolean` | llama.cpp 本地推理的线程、context/batch、GPU 层数和运行选项。 |
| `ModelConfigResultItem`：能力开关 | `enableDirectImageProcessing`、`enableDirectAudioProcessing`、`enableDirectVideoProcessing`、`enableGoogleSearch`、`enableClaude1hPromptCache`、`enableToolCall: boolean` | 图片/音频/视频直传、Google Search、Claude 1 小时 prompt cache 和工具调用能力开关。它们描述配置，不代表当前模型端点已实测支持。 |
| `ModelConfigResultItem`：并发/密钥池 | `requestLimitPerMinute`、`maxConcurrentRequests`、`apiKeyPoolCount: number`；`useMultipleApiKeys: boolean` | 请求速率、并发上限、多密钥开关和密钥池条目数；`apiKeyPoolCount` 运行时取池中 key 的数量。 |
| `FunctionModelMappingResultItem` | `functionType`、`configId: string`；`configName?: string \| null`；`modelIndex: number`；`actualModelIndex?: number \| null`、`selectedModel?: string \| null` | 函数到配置的绑定。列表接口保留已保存的 `modelIndex`；配置缺失时名称/选中模型可能为空。查询单项绑定时另返回解析后的 `actualModelIndex`。 |
| `ModelConfigsResultData` | `totalConfigCount: number`、`defaultConfigId: string`、`configs: ModelConfigResultItem[]`、`functionMappings: FunctionModelMappingResultItem[]` | 配置列表、数量、默认配置 ID 和函数绑定列表。 |
| `ModelConfigCreateResultData` | `created: boolean`、`config: ModelConfigResultItem`、`changedFields: string[]` | 新配置结果；`changedFields` 记录创建时除单独处理的 `name` 以外、实际提供并应用的更新字段。 |
| `ModelConfigUpdateResultData` | `updated: boolean`、`config: ModelConfigResultItem`、`changedFields: string[]`、`affectedFunctions: string[]` | `updated` 表示本次是否提供并应用了至少一个可写字段，不比较新旧值是否相同；因此提供相同值时仍可能为 `true`。未提供可写字段时外层 `ToolResult.success` 仍可为 `true`，而 `updated` 为 `false`、`changedFields` 为空。 |
| `ModelConfigDeleteResultData` | `deleted: boolean`、`configId: string`、`affectedFunctions: string[]`、`fallbackConfigId: string` | 删除状态、被影响的函数及回退配置 ID；当前实现使用默认配置 ID 作为 fallback。 |
| `FunctionModelConfigsResultData` | `defaultConfigId: string`、`mappings: FunctionModelMappingResultItem[]` | 列出各函数类型的绑定；尚未配置的类型也会返回默认配置 ID 和模型索引 `0`。 |
| `FunctionModelConfigResultData` | `defaultConfigId`、`functionType`、`configId`、`configName`、`selectedModel: string`；`modelIndex`、`actualModelIndex: number`；`config: ModelConfigResultItem` | 单一函数当前绑定、所选模型及完整配置。请求索引和实际采用索引分开表达。 |
| `FunctionModelBindingResultData` | `functionType`、`configId`、`configName`、`selectedModel: string`；`requestedModelIndex`、`actualModelIndex: number` | 绑定写入结果同时返回请求索引和实际保存/采用的索引。 |
| `ModelConfigConnectionTestItemResultData` | `type: string`、`success: boolean`、`outcome: ModelConfigConnectionTestOutcome`、`error?: string \| null` | 运行时 `type` 由 `ModelConnectionTestType.name.lowercase()` 序列化，固定值为 `chat`、`tool_call`、`image`、`audio`、`video`；`success` 只有 outcome 为 `passed` 时为真。失败项可能包含 `error`。 |
| `ModelConfigConnectionTestResultData` | `configId`、`configName`、`providerType`、`testedModelName: string`；`requestedModelIndex`、`actualModelIndex`、`totalTests`、`passedTests`、`unverifiedTests`、`failedTests: number`；`success`、`verified: boolean`；`tests: ModelConfigConnectionTestItemResultData[]` | 汇总配置、实际测试模型和探针统计。探针类型完整集合为 `CHAT`、`TOOL_CALL`、`IMAGE`、`AUDIO`、`VIDEO`（输出到结果项时转为小写）；`success` 表示没有 `failed` 项，`verified` 仅当至少有一项且所有项都是 `passed` 时为真。 |
| `ModelConfigConnectionTestOutcome` | `'passed' \| 'unverified' \| 'failed'` | `passed` 已验证；`unverified` 请求成功但未证明能力；`failed` 请求或工具执行失败。 |

模型配置输出中 `.d.ts` 声明的 `ModelConfigResultItem` 有 54 个属性；Android DTO 另序列化 `apiProviderTypeId`，并且 `apiProviderType` 与该字段都取自同一 provider ID。`apiProviderTypeId` 当前缺失于 `.d.ts`。`ModelConfigConnectionTestOutcome` 的 Kotlin 字段实际序列化为小写字符串，类型别名只提供静态约束。以上接口的 `toString()` 是展示摘要方法，不是 `data` 的 JSON 属性。

### 8. Chat 结果
#### 逐字段数据结构
| 类型 | 字段与运行时语义 |
| --- | --- |
| `ChatServiceStartResultData` | `isConnected: boolean` 表示服务连接结果；`connectionTime: number` 是连接时间戳，Kotlin 默认使用 `System.currentTimeMillis()`。 |
| `ChatCreationResultData` | `chatId: string` 是新建会话 ID；`createdAt: number` 默认是 Unix epoch 毫秒。 |
| `ChatSwitchResultData` | `chatId: string`、`chatTitle: string` 描述切换目标；`chatTitle` 可为空字符串；`switchedAt: number` 默认是 Unix epoch 毫秒。 |
| `ChatTitleUpdateResultData` | `chatId: string`、`title: string` 是目标会话及更新标题；`updatedAt: number` 默认是 Unix epoch 毫秒。 |
| `ChatDeleteResultData` | `chatId: string` 是删除目标；`deletedAt: number` 默认是 Unix epoch 毫秒。 |
| `ChatInfo` | `id/title: string`；`messageCount: number`；`createdAt/updatedAt: string` 直接由会话模型时间值转成字符串；`isCurrent: boolean`；`inputTokens/outputTokens: number`（宿主字段为 `Long`）；`characterCardName/characterCardId/characterGroupId?: string \| null`。characterCardId 由绑定名称解析；未解析或未绑定时可空。 |
| `ChatListResultData` | `totalCount: number` 是匹配总数；`currentChatId: string \| null`；`chats: ChatInfo[]` 仅包含按 limit 截取后的可见项，因此可能少于 `totalCount`。 |
| `ChatFindResultData` | `matchedCount: number` 是匹配数；`chat: ChatInfo \| null` 是选中的匹配会话，无选中项时为 `null`。 |
| `AgentStatusResultData` | `chatId/state: string`；`message?: string \| null` 是可选详情；`isIdle/isProcessing: boolean` 分别表示空闲和处理状态。`state` 的运行时固定键为 `idle`、`completed`、`processing`、`connecting`、`receiving`、`executing_tool`、`tool_progress`、`processing_tool_result`、`summarizing`、`executing_plan`、`error`；聊天不存在、服务未连接或异常失败路径返回 `unknown`。`idle` 与 `completed` 将 `isIdle=true`；处理、连接、接收、工具执行/进度、工具结果、摘要和计划状态将 `isProcessing=true`；`error`/`unknown` 两个标志通常都为 `false`。工具名、进度或状态消息会写入 `message`。声明仍是普通 `string`，因此调用端应按上述已知值分支并保留未知值处理。` |
| `MessageSendResultData` | `chatId/message: string` 是目标会话和已发送文本；`aiResponse?: string \| null`、`receivedAt?: number \| null` 仅在可用时提供；`sentAt: number` 默认是 Unix epoch 毫秒。 |
| `MessageSendStreamEventData` | `type: string` 当前实现发送 `start` 与 `chunk`；`chatId/message: string` 为目标和原始输入；`waifu: boolean` 表示分段聚合模式；`chunk?: string \| null` 仅用于增量块；`chunkIndex?: number \| null` 从 0 递增；`receivedChars?: number \| null` 按宿主字符串长度累计，不是字节数。 |
| `ChatMessageInfo` | `sender/content: string`；`timestamp: number` 原样取自宿主消息；`roleName/provider/modelName?: string` 在 Kotlin DTO 中默认空字符串。返回前会过滤 `sender == "summary"` 的内部摘要消息，并简化 content 中的 XML block。 |
| `ChatMessagesResultData` | `chatId/order: string`；`limit: number` 是实际生效的条数或区间长度；`messages: ChatMessageInfo[]`；`start/end?: number` 仅区间查询填充，表示从 0 开始的 offset 范围，`end` 包含在内。普通查询默认 `order="desc"`、`limit=20` 并将 limit 限制在 1..200；区间查询默认 `order="asc"`，要求 `0 <= start <= end`，请求条数为 `end - start + 1`。 |
| `ChatCallResultData` | ToolPkg API `1.0.1`：`text: string` 是移除已识别协议 metadata 与工具 XML 后的助手文本；`turns: PromptTurn[]` 保留助手文本段及模型工具调用，`kind` 当前由实现产生 `ASSISTANT`/`TOOL_CALL`；`ChatCallFinishReason` 为 `"stop" \| "tool_call"`，有工具调用 turn 时为 `tool_call`；`metadata: JsonObject`，有识别到的 meta tag 时含 `protocolMeta: [{provider, payload}]`；`receivedAt: number` 默认 Unix epoch 毫秒；`toString()` 返回非空 `text`，否则返回 finish reason 摘要。 |

这些数据类声明的 `toString(): string` 返回面向日志/展示的文本，不是 JSON 数据字段。时间戳的单位只对上述 Kotlin 默认由 `System.currentTimeMillis()` 生成的字段明确为毫秒；`ChatMessageInfo.timestamp` 直接沿用消息模型值。
#### 包装结果
`ChatServiceStartResult`、`ChatCreationResult`、`ChatListResult`、`ChatFindResult`、`AgentStatusResult`、`ChatSwitchResult`、`ChatTitleUpdateResult`、`ChatDeleteResult`、`MessageSendResult`、`ChatMessagesResult` 都扩展 `BaseResult` 并包含对应的 `data` 字段。失败时优先检查包装的 `success` 与 `error`；不能仅凭 data 中的占位值判断操作成功。
#### 角色卡结果字段
| 类型 | 字段与运行时语义 |
| --- | --- |
| `CharacterCardListResultData` | `totalCount: number` 为列表总数；`cards: CharacterCardInfo[]`；其 `toString()` 输出列表摘要。 |
| `CharacterCardInfo` | `id/name/description: string`；`isDefault: boolean`；`createdAt/updatedAt: number` 对应宿主 `Long` 时间字段。该轻量条目用于 Chat Manager 的角色卡列表。 |
| `CharacterCardToolAccessConfigResultItem` | `enabled: boolean`；`allowedBuiltinTools/allowedPackages/allowedSkills/allowedMcpServers: string[]`。字段分别表示配置开关与允许项名称/ID 列表。 |
| `CharacterCardResultItem` | 完整角色卡配置：`id/name/description/characterSetting/openingStatement/otherContentChat/otherContentVoice/advancedCustomPrompt/marks: string`；`attachedTagIds: string[]`；`chatModelBindingMode: 'FOLLOW_GLOBAL' \| 'FIXED_CONFIG'`、`chatModelConfigId: string \| null`、`chatModelIndex: number`；`memoryProfileBindingMode: 'FOLLOW_GLOBAL' \| 'FIXED_PROFILE'`、`memoryProfileId: string \| null`；`toolAccessConfig: CharacterCardToolAccessConfigResultItem`；`isDefault: boolean`；`createdAt/updatedAt: number`（宿主 `Long`）。结果转换会先将工具访问配置规范化。 |
| `CharacterCardsResultData` | `totalCount: number`；`activeCharacterCardId: string \| null`；`cards: CharacterCardResultItem[]`；`toString()` 只输出数量与当前 ID 摘要。 |
| `CharacterCardResultData` | `card: CharacterCardResultItem` 与 `activeCharacterCardId: string \| null`；单卡完整配置及当前激活项。 |
| `CharacterCardCreateResultData` / `CharacterCardImportResultData` | 创建结果有 `created: boolean`、导入结果有 `imported: boolean`；两者均返回 `card` 和 nullable `activeCharacterCardId`。创建还返回 `changedFields: string[]`。 |
| `CharacterCardUpdateResultData` | `updated: boolean`、更新后的 `card`、nullable `activeCharacterCardId` 与 `changedFields: string[]`。 |
| `CharacterCardDeleteResultData` | `deleted: boolean`、`characterCardId: string`、nullable `activeCharacterCardId`。 |
| `CharacterCardActivationResultData` | `activeCharacterCardId: string \| null`；`null` 表示没有当前激活卡。 |
| `CharacterCardExportResultData` | `characterCardId: string` 与导出的 `tavernJson: string`。 |

角色卡的 API 操作结果仍要结合外层 `ToolResult.success` 判定；失败路径可能带占位 data，不能仅凭 `created`、`updated` 或 `deleted` 字段推断调用整体成功。显式声明 `toString()` 摘要的角色卡结果 DTO 包括列表、单卡、创建/更新/删除/激活/导入/导出结果；嵌套 `CharacterCardInfo`、`CharacterCardResultItem` 与工具访问配置只声明为数据结构，没有额外的自定义 API 方法。
### 9. 记忆查询与链接结果
#### `MemoryQueryResultData`
| 字段 | 声明类型 | 运行时语义 |
| --- | --- | --- |
| `memories` | `MemoryQueryResultMemoryInfo[]` | 本次返回的记忆项。查询会先按 snapshot 去重，再应用结果上限；已在同一 snapshot 返回过的项计入 `excludedBySnapshotCount`。 |
| `snapshotId?` | `string \| null` | 查询 snapshot 标识。未指定时生成 UUID；指定时复用已存在的 snapshot，首次使用时创建。 |
| `snapshotCreated?` | `boolean` | 本次调用是否创建了 snapshot；数据类默认值为 `false`。 |
| `excludedBySnapshotCount?` | `number` | 本次搜索中因 snapshot 去重而排除的匹配数；默认 `0`，不包括超过结果上限而未选择的项。 |
| `toString()` | `string` | 返回格式化文本；空结果会按 snapshot 信息决定是否附加摘要。该方法不是 JSON 字段。 |

#### `MemoryQueryResultMemoryInfo`
| 字段 | 声明类型 | 运行时语义 |
| --- | --- | --- |
| `title` | `string` | 记忆标题。 |
| `content` | `string` | 普通记忆通常返回正文；通配查询可能只返回短摘要。文档记忆按命中的 chunk 形成内容，最多展示 5 段；通配查询或 limit 大于 20 时只给文档/分块摘要。 |
| `source` | `string` | 记忆来源字段。 |
| `tags` | `string[]` | 记忆标签名称列表，不是标签实体对象。 |
| `createdAt` | `string` | 使用设备默认 locale 格式化为 `yyyy-MM-dd HH:mm`。 |
| `chunkInfo?` | `string \| null` | 文档记忆命中 chunk 时的摘要，例如 `Chunk 2/8`；普通记忆、未找到具体 chunk 时为空或省略。 |
| `chunkIndices?` | `number[] \| null` | 命中的 chunk 索引；值按零起始，展示在 `chunkInfo` 中时会加一。普通记忆或无 chunk 命中时为空或省略。 |

#### `MemoryLinkResultData`
| 字段 | 声明类型 | 运行时语义 |
| --- | --- | --- |
| `sourceTitle` | `string` | 关系起点记忆标题。 |
| `targetTitle` | `string` | 关系终点记忆标题。 |
| `linkType` | `string` | 关系类型字符串；允许值由 Memory API/存储实现决定。 |
| `weight` | `number` | 关系强度；声明注释描述范围为 `0.0` 到 `1.0`，Kotlin 数据模型使用 `Float`。 |
| `description` | `string` | 关系描述；字段必填，空文本与否由调用参数/实现决定。 |
| `toString()` | `string` | 返回包含起点、终点、关系类型和强度的摘要文本；不是 JSON 字段。 |

#### `MemoryLinkQueryResultData`
| 字段 | 声明类型 | 运行时语义 |
| --- | --- | --- |
| `totalCount` | `number` | 当前返回的 link 条目数。执行器在过滤掉端点记忆缺失的 link 后，以实际 `links.length` 赋值；它不是不受 limit 限制的全库总数。 |
| `links` | `Array<{ linkId: number; sourceTitle: string; targetTitle: string; linkType: string; weight: number; description: string }>` | 查询返回的 link 快照。`linkId` 在 Kotlin 模型中为 `Long`，`weight` 为 `Float`，序列化后均表现为 JSON number。 |
| `toString()` | `string` | 无 link 时返回 `No memory links found.`；否则逐项显示 ID、端点、类型、权重，并在描述非空时追加描述。不是 JSON 字段。 |

#### 包装结果
- `MemoryLinkResult` 与 `MemoryLinkQueryResult` 都扩展 `BaseResult` 并包含 `data`。包装的 `success`/`error` 语义见本页[结果包装约定](#命名约定)；具体工具发生失败时，仍以工具实际返回的 `ToolResult` 为准。
- `MemoryQueryResultMemoryInfo.chunkInfo` 和 `chunkIndices` 在声明中既可选又可为 `null`；调用端应同时容忍字段缺省和显式空值。
- 查询 snapshot 用于同一查询链/并行查询去重；snapshot 状态按当前实现保存在 profile 关联的进程内 store，不应当作持久化记忆数据。

### 10. 计算、日期与连接结果

| 接口 | 声明字段 | 说明 |
| --- | --- | --- |
| `CalculationResultData` | `expression: string`、`result: number`、`formattedResult: string`、`variables: Record<string, number>` | 计算器结构化结果；`result` 为 Double，`formattedResult` 由计算器格式化。`variables` 只收集当前存在的 `ans`、`pi`、`e`，不是表达式引用变量的全集。`toString()` 输出表达式、格式化结果和非空变量。 |
| `DateResultData` | 声明：`date: Date`、`formattedDate: string`、`timestamp: number` | 当前 TypeScript 声明与 Android 序列化数据类不一致：运行时字段是 `date: string`（`Date.toString()`）、输入 `format: string`、`formattedDate`，没有 `timestamp`。 |
| `ConnectionResultData` | `connectionId: string`、`isActive: boolean`、`timestamp: number` | Kotlin DTO 默认时间戳为 epoch 毫秒，`toString()` 固定生成模拟连接摘要。当前 Android 主源码中未找到构造该 DTO 的调用点；不要据此推断存在真实连接流程。 |

`DateResultData` 的 Android 实际数据类字段为 `date: string`、`format: string`、`formattedDate: string`，没有 `timestamp`；其 `toString()` 返回 `formattedDate`。调用端按当前运行时读取日期结果时应以 `date`、`format`、`formattedDate` 为准；`Date` 和 `timestamp` 仅存在于当前 `.d.ts` 声明，不应据此假设运行时会返回这些形状。此差异需要在类型声明侧单独修正。

### 11. 应用使用统计与蓝牙嵌套类型

| 接口 | 声明字段 | 说明 |
| --- | --- | --- |
| `AppUsageTimeEntry` | `packageName: string`、`appName: string`、`totalForegroundTimeMs: number`、`lastTimeUsed: number`、`isSystemApp: boolean` | 单个应用的使用统计；前台时长字段单位为毫秒。 |
| `BluetoothDeviceData` | `name?: string`、`address: string`、`type: 'classic' \| 'le' \| 'dual' \| 'unknown'`、`bondState: 'none' \| 'bonding' \| 'bonded' \| 'unknown'` | 设备基础结构；已配对列表以它作为条目类型。设备名可缺省。 |
| `BluetoothScannedDeviceData` | 继承 `BluetoothDeviceData`；另有 `source: 'classic' \| 'ble'`、`rssi?: number` | 扫描设备增加来源和可选 RSSI。不要把 RSSI 当作字节数或百分比。 |
| `BluetoothBleServiceData` | `uuid: string`、`characteristics: BluetoothBleCharacteristicData[]` | 一个 BLE service 及其 characteristic 列表。 |
| `BluetoothBleCharacteristicData` | `uuid: string`、`properties: Array<'read' \| 'write' \| 'write_no_response' \| 'notify' \| 'indicate'>` | characteristic UUID 与声明的访问/通知属性。 |
| `BluetoothBleNotificationEntry` | `characteristicUuid: string`、`bytesRead: number`、`text?: string`、`dataBase64?: string`、`timestamp: number` | 单条通知；文本和 Base64 字节字段可选。Kotlin 默认时间戳取 `System.currentTimeMillis()`，单位为毫秒。 |

`AppUsageTimeResultData` 中的 `startTime`、`endTime`、`sinceHours`、`requestedPackageName?`、`includesSystemApps`、`totalEntries`、`entries` 分别描述查询窗口、可选的包名过滤条件、系统应用筛选条件、返回条目数及条目列表。`totalEntries` 与 `entries.length` 是不同字段，调用端应按实际返回值处理。

Android DTO 将 `BluetoothDeviceData.type`、`bondState`、`BluetoothScannedDeviceData.source` 和 `BluetoothBleCharacteristicData.properties` 声明为普通 `String` / `List<String>`；TypeScript 的字面量联合类型只约束静态调用，不保证运行时值一定落在这些成员中。调用端应保留对未知字符串值的处理。

### 12. 终端流、音乐与 FFmpeg 结果

| 接口 | 声明字段 | 说明 |
| --- | --- | --- |
| `TerminalStreamEventData` | `type: string`、`command: string`、`sessionId: string`、`chunk?: string \| null`、`chunkIndex?: number \| null`、`receivedChars?: number \| null` | 声明注释列出当前的 `start` 与 `chunk` 事件；`type` 本身仍是 `string`，不是封闭联合类型。`chunkIndex` 从 0 开始，`receivedChars` 是累计字符数而非字节数。 |
| `HiddenTerminalCommandResultData` | `command: string`、`output: string`、`exitCode: number`、`executorKey: string`、`timedOut?: boolean` | 描述隐藏终端执行结果。Kotlin 中 `timedOut` 默认 `false`；超时时会取消当前命令并保留终端会话。 |
| `TerminalSessionCreationResultData` | `sessionId: string`、`sessionName: string`、`isNewSession: boolean` | 会话 ID、名称及本次是否新建。 |
| `TerminalSessionCloseResultData` | `sessionId: string`、`success: boolean`、`message: string` | 被关闭会话及关闭结果。 |
| `TerminalSessionScreenResultData` | `sessionId: string`、`rows: number`、`cols: number`、`content: string` | 单屏可见快照；不包含滚屏或历史缓冲区。 |
| `MusicPlaybackResultData` | `state: MusicPlaybackState`、`source?: string \| null`、`sourceType?: string \| null`、`title?: string \| null`、`artist?: string \| null`、`durationMs?: number \| null`、`positionMs: number`、`bufferedPositionMs: number`、`volume: number`、`loop: boolean`、`queueIndex?: number \| null`、`queueSize?: number \| null`、`message: string` | `MusicPlaybackState` 的声明值和当前运行时值均为 `idle`、`preparing`、`playing`、`paused`、`ended`、`stopped`、`error`；Kotlin DTO 仍把它实现为普通 `String`。输入工具的 `source_type` 严格接受 `path`、`url`、`uri`：`path` 必须指向存在的普通文件，`url` 的 URI scheme 必须是 `http` 或 `https`，`uri` 必须包含任意非空 scheme。时间位置单位为毫秒，`start_position_ms`/`position_ms` 不得为负数；音量必须在 `0..1`；队列 `start_index` 必须满足 `0 <= start_index < items.length`。队列结果的 `queueIndex` 从 0 开始，仅在队列大小大于 1 时返回；`message` 是本次操作或播放器错误文本。 |
| `FFmpegStreamInfo` | `index: number`、`type: 'video' \| 'audio'`、`codec: FFmpegVideoCodec \| FFmpegAudioCodec`、`frameRate?: \`${number}/${number}\` \| \`${number}\``、`sampleRate?: \`${number}\``、`channels?: 1 \| 2 \| 4 \| 6 \| 8` | 声明侧媒体流信息；`index` 从 0 开始，帧率和采样率按字符串序列化，视频/音频专属字段可能缺省。`FFmpegVideoCodec` 的固定值为 `h264`、`hevc`、`vp8`、`vp9`、`av1`、`libx265`、`libvpx`、`libaom`、`mpeg4`、`mjpeg`、`prores`；`FFmpegAudioCodec` 的固定值为 `aac`、`mp3`、`opus`、`vorbis`、`flac`、`pcm`、`wav`、`ac3`、`eac3`。`FFmpegResultData` 的当前 Android 运行时并不返回该声明结构，而是使用 `mediaInfo` 下的 `StreamInfo`：`index`、`codecType`、`codecName` 以及可选 `resolution`、`frameRate`、`sampleRate`、`channels`。 |

### 13. 沙箱、MCP 与语音服务结果

| 接口 | 声明字段 | 说明 |
| --- | --- | --- |
| `SandboxScriptExecutionResultData` | `success: boolean`、`scriptPath: string`、`functionName: string`、`params?: unknown`、`envFilePath?: string \\| null`、`startedAtMs: number`、`finishedAtMs: number`、`durationMs: number`、`result?: unknown`、`error?: string \\| null`、`events: string[]`、`executionMode?: string \\| null`、`scriptLabel?: string \\| null`、`requestedWaitMs?: number \\| null` | 脚本执行输入/结果以 JSON 形式归一；空白 `params_json` 表示 `{}`，非 JSON 文本会保留为 JSON 字符串。事件项是折叠空白后的 log/intermediate/completed/failed 摘要。时间字段为 epoch 毫秒及其差值；`params`、`result` 仍应按具体脚本契约校验。 |
| `EnvironmentVariableReadResultData` | `key: string`、`value?: string \\| null`、`exists: boolean` | 输入 key 会 trim；`exists` 按读取值是否为 `null` 计算。未设置时 `value=null`，外层调用仍可成功返回此结果。 |
| `EnvironmentVariableWriteResultData` | `key: string`、`requestedValue: string`、`value?: string \\| null`、`exists: boolean`、`cleared: boolean` | `key` 会 trim；`requestedValue` 保留原输入。去空白后为空会删除变量，其他值以 trim 后内容写入；`value` / `exists` 表示写后读取状态，`cleared` 表示走了删除路径。 |
| `SandboxPackageResultItem` | `packageName: string`、`displayName: string`、`description: string`、`isBuiltIn: boolean`、`enabledByDefault: boolean`、`enabled: boolean`、`imported: boolean`、`isDisabledByUser: boolean`、`toolCount: number`、`manageMode: string` | `imported` 当前等于 `enabled`；`isDisabledByUser` 来自用户禁用集合。`manageMode` 实际为 built-in 包 `toggle_only`、外部包 `file_and_toggle`。 |
| `SandboxPackagesResultData` | `externalPackagesPath: string`、`scriptDevGuide: string`、`totalCount: number`、`builtInCount: number`、`externalCount: number`、`enabledCount: number`、`disabledCount: number`、`packages: SandboxPackageResultItem[]`、`packageLoadErrors: Record<string, string>` | 列表强制刷新并按包名小写排序；计数从本次包列表与启用集合计算。读取失败返回空列表/零计数并由外层结果报错。 |
| `SandboxPackageUpdateResultData` | `packageName: string`、`requestedEnabled: boolean`、`previousEnabled: boolean`、`currentEnabled: boolean`、`message: string` | 前后开关状态与操作消息；外层调用成功条件是实际 `currentEnabled` 等于请求状态。 |
| `McpRestartLogPluginResultItem` | `id: string`、`displayName: string`、`shortName: string`、`status: string`、`message: string`、`serviceName: string`、`log: string` | `status` 虽在声明中是普通 `string`，当前重启实现把 `PluginStatus` 序列化为小写 `waiting`、`loading`、`success` 或 `failed`；`shortName` 是插件 ID 按 `/` 分段后的最后一段。`log` 来自插件日志缓存，单插件缓存最多保留最近 2,000,000 个字符。 |
| `McpRestartWithLogsResultData` | `timeoutMs: number`、`elapsedMs: number`、`timedOut: boolean`、`progress: number`、`message: string`、`pluginsTotal: number`、`pluginsStarted: number`、`successCount: number`、`failedCount: number`、`plugins: McpRestartLogPluginResultItem[]`、`extraLogs: Record<string, string>` | `timeout_ms` 限制到 `5000..600000` ms，默认 `120000`；`elapsedMs` 是本次等待耗时，`timedOut` 表示耗时达到上限。`progress` 是加载进度（`0..1`），`pluginsStarted` 当前统计状态为 `SUCCESS` 的插件数；成功/失败计数也按插件明细状态统计。超时或存在失败插件时外层失败，但保留已收集结果。加载状态不可用时返回空/零数据并失败；`extraLogs` 收集没有对应插件条目的日志。 |
| `SpeechTtsHttpConfigResultItem` | `urlTemplate: string`、`apiKeySet: boolean`、`apiKeyPreview: string`、`headers: Record<string, string>`、`httpMethod: string`、`requestBody: string`、`contentType: string`、`localeTag: string`、`voiceId: string`、`modelName: string`、`responsePipeline: Array<{ type: string; path: string; headers: Record<string, string> }>` | TTS HTTP 配置快照。`apiKeySet` 表示是否配置密钥，`apiKeyPreview` 是预览字段而非完整密钥契约。管线条目的对象键名就是 `type`、`path`、`headers`；运行时将 `type` trim 并转小写，只接受 `parse_json`、`pick`、`parse_json_string`、`http_get`、`http_request_from_object`、`base64_decode`。`pick` 必须提供有效 JSON path；`http_get` 可使用该条目的 `headers`；管线最终必须得到音频二进制，否则 TTS 测试失败。 |
| `SpeechTtsVitsPackageConfigResultItem`（运行时额外） | `packagePath: string`、`speakerId: string`、`options: Record<string, string>` | Android `SpeechServicesConfigResultData` 实际序列化的嵌套对象，但当前 `results.d.ts` 没有声明该接口或 `ttsVitsPackageConfig` 字段。它保存本地 VITS/Piper 包路径、说话人 ID 和额外选项；不能因为声明缺失而假设该对象不存在。 |
| `SpeechSttHttpConfigResultItem` | `endpointUrl: string`、`apiKeySet: boolean`、`apiKeyPreview: string`、`modelName: string` | STT HTTP endpoint、密钥配置状态/预览和模型名。 |
| `SpeechServicesConfigResultData` | `ttsServiceType: string`、`ttsHttpConfig: SpeechTtsHttpConfigResultItem`、`ttsCleanerRegexs: string[]`、`ttsSpeechRate: number`、`ttsPitch: number`、`sttServiceType: string`、`sttHttpConfig: SpeechSttHttpConfigResultItem` | 实际 payload 还包含运行时额外的 `ttsVitsPackageConfig: SpeechTtsVitsPackageConfigResultItem`。TTS 服务类型实际输出大写枚举名：`SIMPLE_TTS`、`HTTP_TTS`、`OPENAI_WS_TTS`、`SILICONFLOW_TTS`、`MINIMAX_TTS`、`MIMO_TTS`、`DOUBAO_TTS`、`OPENAI_TTS`、`VITS_TTS`；STT 服务类型为 `SHERPA_NCNN`、`OPENAI_STT`、`DEEPGRAM_STT`。语速、音调由配置快照直接返回；服务类型字段在声明中仍是普通 `string`。 |
| `SpeechServicesUpdateResultData` | `updated: boolean`、`changedFields: string[]`、`ttsServiceType: string`、`sttServiceType: string`、`ttsApiKeySet: boolean`、`sttApiKeySet: boolean` | 有字段提交时 `updated=true`，`changedFields` 记录传入字段名，不比较值是否变化；更新后会重置 TTS/STT service 实例。两个 key 标志不返回密钥，但当前实现将 `sttApiKeySet` 也从 TTS API key 计算：它不是 STT key 的真实状态。 |
| `SpeechServicesTtsPlaybackTestResultData` | `ttsServiceType: string`、`providerClass: string`、`initialized: boolean`、`playbackTriggered: boolean`、`interrupt: boolean`、`textLength: number`、`speechRate: number`、`pitch: number`、`errorType?: string \\| null`、`errorMessage?: string \\| null`、`httpStatusCode?: number \\| null`、`errorBody?: string \\| null`、`causeMessage?: string \\| null` | `textLength` 统计 trim 后文本长度；空文本在播放测试前直接失败。`initialized` 表示 voice service 初始化结果，`playbackTriggered` 表示 `speak()` 是否成功发起，不代表播放完成。网络/TTS 诊断来自 Throwable；HTTP 字段只从 `TtsException` 提取，错误字段可以为空。 |

### 14. 声明未覆盖但运行时可见的数据类

`ToolResultDataClasses.kt` 还定义了一些当前没有对应 `results.d.ts` 接口的序列化数据类。它们可能通过特定工具或流式路径出现，下面的字段名来自运行时源码；由于声明文件没有给出稳定的 TypeScript 契约，调用端应以具体 module 的返回路径为准，并保留未知字段处理。

| 运行时类型 | 实际字段 | 运行时语义 |
| --- | --- | --- |
| `HttpStreamEventData` | `type`、`url: string`、`statusCode?: number \| null`、`statusMessage?: string \| null`、`headers: Record<string, string>`、`contentType?: string \| null`、`chunk?: string \| null`、`chunkIndex?: number \| null`、`receivedBytes?: number \| null` | HTTP 流事件。当前展示逻辑识别 `response_started` 和 `chunk`；`receivedBytes` 是累计字节数，未设置的可选字段会以 `null` 或省略形式出现。该类型未在当前 `results.d.ts` 中声明。 |
| `ScriptExecutionTraceData` | `kind`、`level?: string \| null`、`message`、`callId?: string \| null`、`timestampMs: number` | 脚本执行过程事件。`kind=log` 时可按 `level` 生成日志前缀，`kind=intermediate` 表示中间结果，其他值按普通 trace 展示；`timestampMs` 是 epoch 毫秒。 |
| `ComputerDesktopActionResultData` | `action`、`target?: string \| null`、`resultSummary`、`tabs?: ComputerTabInfo[] \| null`、`pageContent?: ComputerPageInfoNode \| null` | 电脑桌面动作结果。`tabs` 条目的字段是 `id`、`title`、`url`、`isActive`；`pageContent` 节点字段是 `interactionId?: number \| null`、`type`、`description`、`children`。`type` 的源码示例包括 `container`、`button`、`link`、`text`、`input`。当前声明文件没有对应接口。 |
| `AutomationConfigSearchResult` | `searchPackageName?: string \| null`、`searchAppName?: string \| null`、`foundConfigs: ConfigInfo[]`、`totalFound: number` | 自动化配置搜索结果。`ConfigInfo` 字段为 `appName`、`packageName`、`description`、`isBuiltIn`、`fileName`、`matchType`；`matchType` 的源码固定语义为 `packageName` 或 `appName`。 |
| `AutomationPlanParametersResult` | `functionName`、`targetPackageName?: string \| null`、`requiredParameters: ParameterInfo[]`、`planSteps: number`、`planDescription` | 自动化计划所需参数。`ParameterInfo` 字段为 `key`、`description`、`type`、`isRequired`、`defaultValue?: string \| null`。 |
| `AutomationFunctionListResult` | `packageName?: string \| null`、`functions: FunctionInfo[]`、`totalCount: number` | 自动化函数列表。`FunctionInfo` 字段为 `name`、`description`、`targetNodeName`。 |
| `ChatCallTurnInfo` | `kind`、`content`、`toolName?: string \| null`、`metadata: Record<string, unknown>` | `ChatCallResultData.turns` 的 Android 实际嵌套元素。当前实现生成 `ASSISTANT` 或 `TOOL_CALL`，工具调用 turn 通过 `toolName` 和 `metadata` 携带附加信息；声明侧使用 `ToolPkg.PromptTurn`，不是独立的 `results.d.ts` 接口。 |
| `BooleanResultData` / `IntResultData` / `BinaryResultData` | `value: boolean` / `value: number` / `value: byte[]` | 工具结果层的基础标量/二进制包装类；它们不在当前 `results.d.ts` 的公开结果接口列表中。`BinaryResultData.toString()` 只显示字节数，不返回原始内容。 |

`MediaInfo` 和 `StreamInfo` 是 `FFmpegResultData.mediaInfo` 的运行时嵌套类型，字段已在 [FFmpeg 结果说明](#5-shell--intent--terminal--ffmpeg-结果)中列出；`NotificationData.notifications[]`、`MemoryQueryResultData.memories[]`、`MemoryLinkQueryResultData.links[]` 和 `VisitWebResultData.links[]` 也都是 Kotlin 嵌套数据类，声明侧分别使用内联对象或顶层同名接口表达。

### 15. `BaseResult` 包装接口映射

以下接口都声明 `data` 字段并扩展 `BaseResult`。`data` 的具体结构由右列类型决定；操作结果应先检查外层 `success` / `error`，再读取 `data`。

| 包装接口 | `data` 类型 |
| --- | --- |
| `CalculationResult` | `CalculationResultData` |
| `DateResult` | `DateResultData` |
| `ConnectionResult` | `ConnectionResultData` |
| `DirectoryListingResult` | `DirectoryListingData` |
| `FileContentResult` | `FileContentData` |
| `BinaryFileContentResult` | `BinaryFileContentData` |
| `FilePartContentResult` | `FilePartContentData` |
| `FileOperationResult` | `FileOperationData` |
| `FileApplyResult` | `FileApplyResultData` |
| `HttpResponseResult` | `HttpResponseData` |
| `VisitWebResult` | `VisitWebResultData` |
| `SystemSettingResult` | `SystemSettingData` |
| `AppOperationResult` | `AppOperationData` |
| `AppListResult` | `AppListData` |
| `UIPageResult` | `UIPageResultData` |
| `UIActionResult` | `UIActionResultData` |
| `AutomationExecutionResult` | `AutomationExecutionResultData` |
| `ADBResult` | `ADBResultData` |
| `IntentResult` | `IntentResultData` |
| `TerminalCommandResult` | `TerminalCommandResultData` |
| `HiddenTerminalCommandResult` | `HiddenTerminalCommandResultData` |
| `TerminalSessionCreationResult` | `TerminalSessionCreationResultData` |
| `TerminalSessionCloseResult` | `TerminalSessionCloseResultData` |
| `TerminalSessionScreenResult` | `TerminalSessionScreenResultData` |
| `DeviceInfoResult` | `DeviceInfoResultData` |
| `CombinedOperationResult` | `CombinedOperationResultData` |
| `ChatServiceStartResult` | `ChatServiceStartResultData` |
| `ChatCreationResult` | `ChatCreationResultData` |
| `ChatListResult` | `ChatListResultData` |
| `ChatFindResult` | `ChatFindResultData` |
| `AgentStatusResult` | `AgentStatusResultData` |
| `ChatSwitchResult` | `ChatSwitchResultData` |
| `ChatTitleUpdateResult` | `ChatTitleUpdateResultData` |
| `ChatDeleteResult` | `ChatDeleteResultData` |
| `MessageSendResult` | `MessageSendResultData` |
| `ChatMessagesResult` | `ChatMessagesResultData` |
| `MemoryLinkResult` | `MemoryLinkResultData` |
| `MemoryLinkQueryResult` | `MemoryLinkQueryResultData` |



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
- `docs/doc-src/toolpkg_developer_documentation/04_modules/files.md`
- `docs/doc-src/toolpkg_developer_documentation/04_modules/network.md`
- `docs/doc-src/toolpkg_developer_documentation/04_modules/system.md`
- `docs/doc-src/toolpkg_developer_documentation/04_modules/ui.md`
- `docs/doc-src/toolpkg_developer_documentation/04_modules/workflow.md`
- `docs/doc-src/toolpkg_developer_documentation/04_modules/software_settings.md`
- `docs/doc-src/toolpkg_developer_documentation/04_modules/chat.md`
- `docs/doc-src/toolpkg_developer_documentation/04_modules/memory.md`
