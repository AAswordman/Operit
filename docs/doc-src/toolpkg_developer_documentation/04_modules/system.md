# API 文档：`system.d.ts`

`system.d.ts` 描述 `Tools.System`。这些方法通过宿主工具注册表访问 Android 系统设置、应用、通知、定位、蓝牙、Intent、终端和音乐播放能力。声明中的方法都返回 Promise；参数会先由 `JsTools.kt` 归一为宿主工具使用的 snake_case 字段。

## 运行时入口与通用规则

```ts
Tools.System
```

当前 facade 包含以下能力：

- 基础控制：`sleep`、系统设置读写、Toast、通知。
- 应用操作：安装、卸载、启动、停止、枚举和 Usage Access 使用时长。
- 设备状态：设备信息、通知、位置、蓝牙 Classic/BLE。
- Android 工具：Shell、Intent、广播。
- 持久终端：会话创建、命令执行、流式输出、隐藏命令、输入、屏幕和关闭。
- 音乐：单曲、队列、暂停、恢复、停止、跳转、音量和状态。

宿主成功结果的数据字段见[公共结果类型](results.md)。工具参数错误、权限不足、设备不支持或执行异常会产生失败的宿主结果；具体 facade Promise 的错误包装由核心运行时处理。

## 基础控制

### `sleep(milliseconds)`

```ts
function sleep(milliseconds: string | number): Promise<SleepResultData>;
```

`JsTools` 先对输入执行 `parseInt`，再调用 `sleep` 工具。宿主默认值为 `1000`；负数不会报错，而是将实际等待时间钳制为 `0`。结果包含：

- `requestedMs`：解析后的原始请求值。
- `sleptMs`：实际等待毫秒数，最小为 `0`。

```ts
const result = await Tools.System.sleep(1000);
console.log(result.requestedMs, result.sleptMs);
```

### `getSetting(setting, namespace?)`

```ts
function getSetting(setting: string, namespace?: string): Promise<SystemSettingData>;
```

读取 Android 系统设置。`namespace` 默认为 `system`，只接受 `system`、`secure` 或 `global`。`setting` 为空、命名空间非法、设置不存在或读取权限不足时失败。成功数据包含 `namespace`、`setting` 和字符串形式的 `value`。

### `setSetting(setting, value, namespace?)`

```ts
function setSetting(setting: string, value: string, namespace?: string): Promise<SystemSettingData>;
```

写入系统设置，命名空间规则与 `getSetting` 相同。`setting` 或 `value` 为空时失败。Android 6.0 及以上如果没有 `WRITE_SETTINGS` 权限，宿主会打开系统授权页并返回失败结果，不会假装写入成功。成功数据仍为 `SystemSettingData`。

### `getDeviceInfo()`

```ts
function getDeviceInfo(): Promise<DeviceInfoResultData>;
```

读取设备信息并返回 `DeviceInfoResultData`。字段定义和当前序列化形式见[公共结果类型](results.md)；该调用不接受参数。

### `toast(message)`

```ts
function toast(message: string): Promise<string>;
```

`message` 为空或全为空白时失败。成功时把 Toast 投递到主线程并返回字符串 `"OK"`；返回类型是 TypeScript 原始 `string`，不是 `StringResultData`。

### `sendNotification(message, title?)`

```ts
function sendNotification(message: string, title?: string): Promise<string>;
```

`message` 为空时失败。`title` 为空时使用 `Notification`。Android 8.0 及以上会创建/使用 `AI_REPLY_CHANNEL` 通知渠道；通知正文会同时设置简短文本和完整大文本，成功返回 `"OK"`。缺少通知权限或发送异常时返回失败结果。

## 应用管理

### `usePackage(packageName)`

```ts
function usePackage(packageName: string): Promise<string>;
```

请求宿主加载工具包。返回的是包加载工具的字符串结果；被加载包的具体导出工具和结果类型由包自身决定，不能从这个方法的返回类型推导。

### `installApp(path)`

```ts
function installApp(path: string): Promise<AppOperationData>;
```

`path` 必须指向存在的 APK 文件。宿主通过 Android 安装 Intent 打开安装流程，返回表示“安装请求已发出”的 `AppOperationData`，不等待用户确认或系统安装完成。若 APK 位于 Operit 自身私有目录，宿主会先复制到临时清理目录，再通过 `FileProvider` 提供给安装器。

结果中的 `operationType` 当前为 `install_request`，`packageName` 在安装请求路径中保存传入 APK 路径，`success` 和 `details` 表示请求是否成功发出。

### `uninstallApp(packageName)`

```ts
function uninstallApp(packageName: string): Promise<AppOperationData>;
```

包名必须非空且当前已安装。宿主打开系统卸载确认页，返回 `operationType = "uninstall_request"` 的请求结果，不等待用户确认。

### `stopApp(packageName)`

```ts
function stopApp(packageName: string): Promise<AppOperationData>;
```

包名必须非空。宿主调用 `ActivityManager.killBackgroundProcesses()`，需要 `KILL_BACKGROUND_PROCESSES` 权限；这表示请求停止后台进程，不保证目标应用的所有进程都已经结束。

### `listApps(includeSystem?)`

```ts
function listApps(includeSystem?: boolean): Promise<AppListData>;
```

默认不包含系统应用。成功结果包含 `includesSystemApps` 和按显示名称排序的 `packages` 字符串列表，列表项格式为 `应用名 (package.name)`。

### `startApp(packageName, activity?)`

```ts
function startApp(packageName: string, activity?: string): Promise<AppOperationData>;
```

不传 `activity` 时使用目标包的默认启动 Intent；传入时构造指定包/Activity 的 Launcher Intent。找不到启动 Intent、包名为空或启动异常时失败。成功结果的 `operationType` 为 `start`；它只表示启动请求成功提交。

### `getAppUsageTime(options?)`

```ts
function getAppUsageTime(options?: {
    packageName?: string;
    sinceHours?: number | string;
    limit?: number | string;
    includeSystemApps?: boolean;
}): Promise<AppUsageTimeResultData>;
```

`JsTools` 会把 camelCase 选项转换为 `package_name`、`since_hours` 和 `include_system_apps`。宿主默认值为：

- `sinceHours = 24`。
- `limit = 10`。
- `includeSystemApps = false`。

`sinceHours` 和 `limit` 必须是正整数；Android 5.0 以下不支持。没有 Usage Access 权限时，宿主打开使用情况访问设置页并返回失败结果，不返回伪造统计数据。

不传 `packageName` 时，宿主按前台累计时长降序返回最多 `limit` 条，并默认过滤系统应用。传入 `packageName` 时只保留该包，结果最多一条；此时不会因为 `includeSystemApps = false` 再过滤掉明确指定的系统包。

结果包含：

- `startTime`、`endTime`：本次查询的 Unix 毫秒时间边界。
- `sinceHours`、`requestedPackageName`、`includesSystemApps`。
- `totalEntries` 和 `entries`。
- 每条 entry 包含 `packageName`、`appName`、`totalForegroundTimeMs`、`lastTimeUsed` 和 `isSystemApp`。

```ts
const usage = await Tools.System.getAppUsageTime({
    sinceHours: 24,
    limit: 5,
    includeSystemApps: false
});
```

## 通知与定位

### `getNotifications(limit?, includeOngoing?)`

```ts
function getNotifications(limit?: number, includeOngoing?: boolean): Promise<NotificationData>;
```

默认 `limit = 10`、`includeOngoing = false`。宿主需要当前应用被授权为 Notification Listener Service；没有授权时会打开通知使用权设置页并返回失败结果。

成功数据包含 `notifications` 和读取时间 `timestamp`。每条通知当前至少包含 `packageName`、`text` 和通知时间戳。`limit` 会传给通知存储快照层；facade 不做额外正数校验。

### `getLocation(highAccuracy?, timeout?, includeAddress?)`

```ts
function getLocation(
    highAccuracy?: boolean,
    timeout?: number,
    includeAddress?: boolean
): Promise<LocationData>;
```

默认 `highAccuracy = false`、`timeout = 10` 秒、`includeAddress = true`。宿主行为如下：

- 没有粗略或精确位置权限时立即失败。
- `highAccuracy = true` 只有在拥有精确权限且 GPS 可用时才会选择 GPS；否则会回退到网络或被动 provider。
- 十分钟内的最后已知位置会优先直接返回。
- 没有可用最后位置时请求位置更新，等待 `timeout` 秒；超时会移除监听并尝试返回最后已知位置。
- `includeAddress = true` 时使用 `Geocoder` 补充地址，反查失败只会留下空地址字段，不会使已有坐标失效。

结果包含 `latitude`、`longitude`、`accuracy`、`provider`、`timestamp` 和 `rawData`；地址可包含 `address`、`city`、`province`、`country`。

## 蓝牙

蓝牙工具在 `Tools.System.bluetooth` 下。Android 12 及以上需要 `BLUETOOTH_CONNECT` 和 `BLUETOOTH_SCAN`；更早版本使用位置权限作为兼容门禁。需要连接权限或扫描权限的操作不会自动绕过权限检查，应先调用 `requestPermission()`。

### `bluetooth.requestPermission()`

```ts
function requestPermission(): Promise<string>;
```

如果权限已经满足，立即返回 `"Bluetooth permission granted"`。否则请求当前系统版本所需权限；用户拒绝时失败。

### `bluetooth.getState()`

```ts
function getState(): Promise<BluetoothStateData>;
```

设备没有 Bluetooth adapter 时仍返回成功数据：`supported = false`、`enabled = false`、`state = "unsupported"`。有 adapter 但缺少连接权限时失败；成功时 `state` 为 `off`、`turning_on`、`on`、`turning_off` 或 `unknown`。

### `bluetooth.requestEnable()`

```ts
function requestEnable(): Promise<string>;
```

设备不支持或缺少连接权限时失败。已经开启时返回 `"Bluetooth is already enabled"`；否则打开系统开启蓝牙对话框并返回 `"Bluetooth enable request opened"`，不等待用户最终选择。

### `bluetooth.listBondedDevices()`

```ts
function listBondedDevices(): Promise<BluetoothBondedDevicesData>;
```

需要连接权限。结果包含按设备名和地址排序的 `devices`；每项包含 `name`、`address`、`type` 和 `bondState`。运行时把 Android 枚举转换为 `classic`、`le`、`dual`、`unknown` 以及 `none`、`bonding`、`bonded`、`unknown` 字符串。

### `bluetooth.scan(options?)`

```ts
function scan(options?: {
    durationMs?: number | string;
    includeBle?: boolean;
}): Promise<BluetoothScanResultData>;
```

宿主默认扫描 `10000` 毫秒并包含 BLE。扫描需要连接和扫描权限；Classic discovery 与 BLE scan 会收集到同一个按地址去重的设备表，结果按名称和地址排序。结果包含 `devices`、实际使用的 `durationMs` 和 `includesBle`；扫描设备还包含 `source` 和可选 `rssi`。

### Classic 连接和收发

```ts
function connect(options: {
    address: string;
    uuid?: string;
}): Promise<BluetoothSessionData>;

function listen(options?: {
    name?: string;
    uuid?: string;
}): Promise<BluetoothSessionData>;

function accept(listenerSessionId: string, timeoutMs?: number | string): Promise<BluetoothSessionData>;

function send(sessionId: string, options: {
    text?: string;
    dataBase64?: string;
}): Promise<BluetoothTransferData>;

function read(sessionId: string, options?: {
    maxBytes?: number | string;
    timeoutMs?: number | string;
}): Promise<BluetoothReadData>;

function sendAndRead(sessionId: string, options: {
    text?: string;
    dataBase64?: string;
    maxBytes?: number | string;
    timeoutMs?: number | string;
}): Promise<BluetoothReadData>;

function close(sessionId: string): Promise<string>;
```

运行时规则：

- `address` 必填；不传 UUID 时使用默认 SPP UUID `00001101-0000-1000-8000-00805F9B34FB`。
- `listen` 默认名称为 `Operit Bluetooth`；返回 `mode = "classic_listener"` 的监听会话。
- `accept` 默认超时 `30000` 毫秒，成功后创建新的 `mode = "classic"` 会话。
- `read` 默认 `maxBytes = 4096`、`timeoutMs = 3000`；没有数据时在超时后返回 `bytesRead = 0`。
- `send` 和 `sendAndRead` 中如果同时提供 `dataBase64` 与 `text`，优先解码并发送 Base64 数据；否则按 UTF-8 编码文本。
- `close` 关闭 Classic、监听或 BLE 会话，成功返回 `"Bluetooth session closed"`。

会话结果包含 `sessionId`、`address` 和 `mode`；读结果包含 `bytesRead`，并按数据内容提供 `text` 或 `dataBase64`。

### BLE

```ts
function connect(options: {
    address: string;
    autoConnect?: boolean;
}): Promise<BluetoothSessionData>;

function discoverServices(sessionId: string, timeoutMs?: number | string): Promise<BluetoothBleServicesData>;

function readCharacteristic(sessionId: string, options: {
    serviceUuid: string;
    characteristicUuid: string;
    timeoutMs?: number | string;
}): Promise<BluetoothReadData>;

function writeCharacteristic(sessionId: string, options: {
    serviceUuid: string;
    characteristicUuid: string;
    text?: string;
    dataBase64?: string;
}): Promise<BluetoothTransferData>;

function writeAndReadCharacteristic(sessionId: string, options: {
    writeServiceUuid: string;
    writeCharacteristicUuid: string;
    readServiceUuid: string;
    readCharacteristicUuid: string;
    text?: string;
    dataBase64?: string;
    timeoutMs?: number | string;
}): Promise<BluetoothReadData>;

function subscribe(sessionId: string, options: {
    serviceUuid: string;
    characteristicUuid: string;
    enable?: boolean;
}): Promise<BluetoothTransferData>;

function readNotifications(sessionId: string, limit?: number | string): Promise<BluetoothBleNotificationData>;
```

BLE `connect` 的 `autoConnect` 默认为 `false`。服务发现默认等待 `10000` 毫秒，特征读取和写入后读取默认等待 `5000` 毫秒；写特征回调本身还有固定的 5 秒等待。所有 service/characteristic UUID 都必须能被 `UUID.fromString` 解析。`subscribe` 默认启用通知；`readNotifications` 默认读取 20 条，并在返回后从会话队列中移除已取出的通知。

服务结果包含 `sessionId`、service UUID 和特征 UUID/属性列表。通知项包含 `characteristicUuid`、`bytesRead`、可选 `text`/`dataBase64` 以及时间戳。

## Shell、Intent 与广播

### `shell(command)`

```ts
function shell(command: string): Promise<ADBResultData>;
```

宿主通过 Shizuku/Android shell 执行命令；声明注释中的 root 描述不是唯一实现条件，当前实现还要求对应 shell 服务可用。`command` 为空时失败；包含字面量 `rm -rf` 或 `format` 时会被参数校验拦截。成功数据包含 `command`、`output` 和 `exitCode`。底层失败不会返回 `ADBResultData`，而是把 stderr/stdout 合并到错误文本中。

当前工具保留 `timeout` 兼容参数但 `StandardShellToolExecutor` 不使用它；不要依赖该字段产生超时控制。

### `intent(options?)`

```ts
function intent(options?: {
    action?: string;
    uri?: string;
    package?: string;
    component?: string;
    flags?: number | string;
    extras?: Record<string, any> | string;
    type?: 'activity' | 'broadcast' | 'service';
}): Promise<IntentResultData>;
```

`action` 和 `component` 至少提供一个。`type` 只接受 `activity`、`broadcast` 或 `service`，默认 `activity`；启动 service 时必须提供 component。

运行时行为：

- `component` 按 `package/class` 解析，类名以 `.` 开头时会拼接包名。
- `flags` 优先按 JSON 整数数组解析，失败后尝试按单个整数解析。
- `extras` 按 JSON 对象解析，基础标量和部分字符串/整数数组会写入 Intent；无法识别的值转成字符串。
- Activity 会自动追加 `FLAG_ACTIVITY_NEW_TASK`。
- `type = broadcast` 调用 `sendBroadcast`，`type = service` 调用 `startService`，其余调用 `startActivity`。

当前运行时序列化的成功字段是 `action`、`uri`、`package_name`、`component`、`flags`、`extras_count` 和 `result`。`IntentResultData.type` 虽然存在于 `.d.ts`，但当前 Kotlin DTO 没有该字段，不能依赖它判断实际执行分支。

### `sendBroadcast(options?)`

```ts
function sendBroadcast(options?: {
    action: string;
    uri?: string;
    package?: string;
    component?: string;
    extras?: Record<string, any> | string;
    extra_key?: string;
    extra_value?: string;
    extra_key2?: string;
    extra_value2?: string;
}): Promise<IntentResultData>;
```

`action` 必填。`extra_key`/`extra_value` 和第二组字段会先写入 Intent，`extras` JSON 对象随后写入；重复键可能被后写入的值覆盖。成功时固定调用 `sendBroadcast`，返回与 `intent()` 相同的实际字段，但 `result` 为 `Broadcast sent successfully`。

## 终端会话

终端能力位于 `Tools.System.terminal`。所有会话 ID 都来自宿主终端管理器；屏幕读取只包含当前可见屏幕，不包含滚动历史。

### `terminal.create(sessionName?)`

```ts
function create(sessionName?: string): Promise<TerminalSessionCreationResultData>;
```

虽然声明把 `sessionName` 标为可选，当前宿主创建工具实际要求非空、非空白 `session_name`；调用时应始终传入名称。已有同名会话会被复用，返回 `isNewSession = false`；否则创建新会话并返回 `isNewSession = true`。结果包含 `sessionId`、`sessionName` 和 `isNewSession`。

### `terminal.exec(sessionId, command, timeoutMs?)`

```ts
function exec(sessionId: string, command: string, timeoutMs?: number | string): Promise<TerminalCommandResultData>;
```

`sessionId` 必填且必须仍存在。默认超时是 `1800000` 毫秒（30 分钟）。宿主收集命令的全部输出，结果包含 `command`、`output`、`exitCode`、`sessionId` 和 `timedOut`。

超时处理不是立即丢弃会话：宿主发送中断信号，最多再等待 3 秒确认命令停止，然后返回 `exitCode = -1`、`timedOut = true` 的结果；该路径不会把超时本身作为失败错误，也会保留终端会话。会话不存在、无法取得输出流或其他异常才返回失败。

### `terminal.execStreaming(sessionId, command, options?)`

```ts
function execStreaming(sessionId: string, command: string, options?: {
    timeoutMs?: number | string;
    onIntermediateResult?: (event: TerminalStreamEventData) => void;
}): Promise<TerminalCommandResultData>;
```

中间回调收到两类事件：

- `type = "start"`：包含命令和会话 ID，尚无输出块。
- `type = "chunk"`：包含 `chunk`、从 0 开始的 `chunkIndex` 和累计 `receivedChars`。

Promise 最终仍返回 `TerminalCommandResultData`。超时的最终结果仍使用 `timedOut = true` 和 `exitCode = -1`，并保留会话。

### `terminal.hiddenExec(command, options?)`

```ts
function hiddenExec(command: string, options?: {
    executorKey?: string;
    timeoutMs?: number | string;
}): Promise<HiddenTerminalCommandResultData>;
```

`command` 不能为空。默认 `executorKey = "default"`、`timeoutMs = 120000` 毫秒；同一个 key 复用隐藏执行器的登录上下文。结果包含 `command`、`output`、`exitCode`、`executorKey` 和 `timedOut`。超时返回 `timedOut = true`；非超时但隐藏执行器报告失败时返回错误。

### `terminal.close(sessionId)`

```ts
function close(sessionId: string): Promise<TerminalSessionCloseResultData>;
```

`sessionId` 不能为空。成功结果包含 `sessionId`、`success = true` 和关闭消息；同时清理宿主内部的名称到 ID 缓存。

### `terminal.screen(sessionId)`

```ts
function screen(sessionId: string): Promise<TerminalSessionScreenResultData>;
```

读取会话当前屏幕并去除每行尾部空格及末尾空行。结果包含 `sessionId`、`rows`、`cols` 和 `content`；会话不存在时失败。

### `terminal.input(sessionId, options?)`

```ts
function input(sessionId: string, options?: {
    input?: string;
    control?: string;
}): Promise<string>;
```

`input` 和 `control` 至少提供一个。控制键会先做别名归一，例如 `return` → `enter`、`escape` → `esc`、`del` → `delete`。支持 `enter`、`tab`、方向键、`home`、`end`、分页键、退格、删除及 `ctrl`/`alt`/`shift`/`meta` 等组合；Ctrl 组合要求输入恰好一个字符。成功返回描述发送结果的字符串。

## 音乐播放

音乐工具使用 ExoPlayer，并共享一个进程内播放管理器。结果类型为 `MusicPlaybackResultData`，包含 `state`、当前音源/标题/艺术家、`durationMs`、`positionMs`、`bufferedPositionMs`、`volume`、`loop`、队列索引/大小和 `message`。

### `music.play(options)`

```ts
function play(options: {
    source: string;
    sourceType: 'path' | 'url' | 'uri';
    title?: string;
    artist?: string;
    loop?: boolean;
    volume?: number | string;
    startPositionMs?: number | string;
}): Promise<MusicPlaybackResultData>;
```

`source` 和 `sourceType` 必填。运行时规则：

- `path` 要求本地文件存在且是普通文件。
- `url` 只接受 `http` 或 `https` scheme。
- `uri` 要求 URI 含有 scheme。
- `loop` 默认 `false`，`volume` 默认 `1`，音量必须在 `0..1`。
- `startPositionMs` 如果提供必须是非负整数。

每次播放会停止并清空当前媒体项，建立单曲播放；播放失败时返回错误。

### `music.playQueue(options)`

```ts
function playQueue(options: {
    items: Array<{
        source: string;
        sourceType: 'path' | 'url' | 'uri';
        title?: string;
        artist?: string;
    }>;
    loop?: boolean;
    volume?: number | string;
    startIndex?: number | string;
    startPositionMs?: number | string;
}): Promise<MusicPlaybackResultData>;
```

facade 要求 `options` 是对象且 `items` 是数组；队列不能为空，每个 item 必须有 `source` 和 `sourceType`。`startIndex` 默认为 `0` 且必须落在队列范围内；`startPositionMs` 非负；音量规则与单曲相同。队列循环使用 ExoPlayer 的全队列循环模式，单曲 `loop` 使用单项循环模式。

### 其他控制

```ts
function pause(): Promise<MusicPlaybackResultData>;
function resume(): Promise<MusicPlaybackResultData>;
function stop(): Promise<MusicPlaybackResultData>;
function seek(positionMs: number | string): Promise<MusicPlaybackResultData>;
function setVolume(volume: number | string): Promise<MusicPlaybackResultData>;
function status(): Promise<MusicPlaybackResultData>;
```

`pause`、`resume`、`stop`、`seek` 和 `setVolume` 都要求已经创建过有效播放会话；没有播放器时宿主返回 `No active music playback session`。`seek` 要求非负整数，`setVolume` 要求数值在 `0..1`。`status` 返回当前状态，即使播放器内部记录了最近错误，也会把错误文本放在结果 `message` 中。

## 示例

### 设置与应用

```ts
await Tools.System.setSetting('screen_brightness', '120');
await Tools.System.startApp('com.android.settings');
```

### 发送广播

```ts
await Tools.System.sendBroadcast({
    action: 'com.example.SYNC',
    extra_key: 'mode',
    extra_value: 'full'
});
```

### 使用终端会话

```ts
const session = await Tools.System.terminal.create('demo');
await Tools.System.terminal.exec(session.sessionId, 'pwd', 5000);
const screen = await Tools.System.terminal.screen(session.sessionId);
console.log(screen.content);
await Tools.System.terminal.close(session.sessionId);
```

### 流式执行终端命令

```ts
const finalResult = await Tools.System.terminal.execStreaming(
    session.sessionId,
    'printf hello',
    {
        timeoutMs: 5000,
        onIntermediateResult: event => {
            if (event.type === 'chunk') console.log(event.chunk);
        }
    }
);
console.log(finalResult.output);
```

### 播放网络音频

```ts
await Tools.System.music.play({
    source: 'https://example.com/song.mp3',
    sourceType: 'url',
    volume: 0.8
});
```

## 已确认的声明差异

- `usePackage`、Toast、通知、蓝牙字符串操作和终端输入在 `.d.ts` 中返回 `string`；旧页面曾写成 `StringResultData`，那不是这些 facade 的 TypeScript 返回类型。
- `terminal.create(sessionName?)` 的声明参数可选，但当前 `create_terminal_session` 宿主工具要求非空 `session_name`。
- `IntentResultData.type` 出现在 `.d.ts`，当前 Kotlin DTO 不序列化该字段；实际字段以 `action`、`uri`、`package_name`、`component`、`flags`、`extras_count` 和 `result` 为准。
- Bluetooth 结果中的 `type`、`bondState`、`source` 和 BLE `properties` 在声明中使用字面量联合，运行时 Kotlin DTO 使用普通字符串/字符串列表，不执行同等的 TypeScript 枚举约束。
- 音乐 `state` 在声明中是联合类型，运行时 DTO 是字符串；当前实现使用 `idle`、`preparing`、`playing`、`paused`、`ended`、`stopped` 和 `error` 等值。

## 相关文件

- `examples/types/system.d.ts`
- `examples/types/results.d.ts`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsTools.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/ToolRegistration.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardSystemOperationTools.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardTerminalCommandExecutor.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/BluetoothSessionManager.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardMusicPlaybackTools.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardIntentToolExecutor.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardSendBroadcastToolExecutor.kt`
- [公共结果类型](results.md)
