# API 文档：`system.d.ts`

`system.d.ts` 描述的是 `Tools.System` 命名空间。它负责与设备、应用、系统设置、通知、位置以及终端会话交互。

## 作用

当前定义覆盖：

- 睡眠与基础系统设置读写。
- 应用安装、卸载、启动、停止、枚举。
- 应用前台使用时长统计。
- 设备信息、通知、定位、蓝牙。
- Shell / Intent / Broadcast。
- 持久终端会话。

## 运行时入口

```ts
Tools.System
```

## 主要 API

### 基础控制

#### `sleep(milliseconds)`

```ts
sleep(milliseconds: string | number): Promise<SleepResultData>
```

#### `getSetting(setting, namespace?)`

读取系统设置，返回 `SystemSettingData`。

#### `setSetting(setting, value, namespace?)`

修改系统设置，返回 `SystemSettingData`。

### 设备与提示

#### `getDeviceInfo()`

获取设备信息，返回 `DeviceInfoResultData`。

#### `toast(message)`

设备端显示 Toast，返回 `StringResultData`。

#### `sendNotification(message, title?)`

发送通知，返回 `StringResultData`。

### 音乐播放

音乐播放能力在 `Tools.System.music` 下，返回 `MusicPlaybackResultData`。

#### `music.play(options)`

播放单个音频。

```ts
Tools.System.music.play({
  source: "/sdcard/Download/song.mp3",
  sourceType: "path",
  title: "Song",
  artist: "Artist",
  loop: false,
  volume: 1,
  startPositionMs: 0
})
```

#### `music.playQueue(options)`

播放音频队列。`items` 中每个曲目需要提供 `source` 和 `sourceType`，可选 `title`、`artist`。

```ts
Tools.System.music.playQueue({
  items: [
    { source: "/sdcard/Download/one.mp3", sourceType: "path", title: "One" },
    { source: "/sdcard/Download/two.mp3", sourceType: "path", title: "Two" }
  ],
  loop: false,
  volume: 1,
  startIndex: 0,
  startPositionMs: 0
})
```

#### 其他控制

```ts
Tools.System.music.pause()
Tools.System.music.resume()
Tools.System.music.stop()
Tools.System.music.seek(30000)
Tools.System.music.setVolume(0.8)
Tools.System.music.status()
```

### 应用管理

#### `usePackage(packageName)`

加载某个工具包。返回值类型在定义里是 `Promise<any>`，具体取决于目标包的导出能力。

#### `installApp(path)`

安装 APK，返回 `AppOperationData`。

#### `uninstallApp(packageName)`

卸载应用，返回 `AppOperationData`。

#### `stopApp(packageName)`

停止应用，返回 `AppOperationData`。

#### `listApps(includeSystem?)`

枚举已安装应用，返回 `AppListData`。

#### `startApp(packageName, activity?)`

启动应用，可选指定 Activity，返回 `AppOperationData`。

#### `getAppUsageTime(options?)`

读取 Android Usage Access 提供的前台应用使用时长，返回 `AppUsageTimeResultData`。

```ts
getAppUsageTime({
  packageName?,
  sinceHours?,
  limit?,
  includeSystemApps?
}): Promise<AppUsageTimeResultData>
```

说明：

- 默认统计最近 `24` 小时。
- 传 `packageName` 时，返回该应用的使用时长。
- 不传 `packageName` 时，按使用时长降序返回前 `limit` 个应用。
- 若没有“使用情况访问权限”，运行时会引导用户进入授权页面。

### 通知与定位

#### `getNotifications(limit?, includeOngoing?)`

返回 `NotificationData`。

#### `getLocation(highAccuracy?, timeout?, includeAddress?)`

返回 `LocationData`。`includeAddress` 默认是 `true`；设为 `false` 时只返回坐标，跳过系统坐标反查。

### 蓝牙

蓝牙能力在 `Tools.System.bluetooth` 下。

#### `bluetooth.requestPermission()`

请求蓝牙附近设备权限，返回 `StringResultData`。

#### `bluetooth.getState()`

读取蓝牙适配器状态，返回 `BluetoothStateData`。

#### `bluetooth.requestEnable()`

打开系统蓝牙开启对话框，返回 `StringResultData`。

#### `bluetooth.listBondedDevices()`

列出已配对蓝牙设备，返回 `BluetoothBondedDevicesData`。

#### `bluetooth.scan(options?)`

```ts
bluetooth.scan({ durationMs?, includeBle? }): Promise<BluetoothScanResultData>
```

扫描附近蓝牙 Classic 与 BLE 设备。

#### Classic 连接和收发

```ts
bluetooth.connect({ address, uuid? }): Promise<BluetoothSessionData>
bluetooth.listen({ name?, uuid? }): Promise<BluetoothSessionData>
bluetooth.accept(listenerSessionId, timeoutMs?): Promise<BluetoothSessionData>
bluetooth.send(sessionId, { text?, dataBase64? }): Promise<BluetoothTransferData>
bluetooth.read(sessionId, { maxBytes?, timeoutMs? }): Promise<BluetoothReadData>
bluetooth.sendAndRead(sessionId, { text?, dataBase64?, maxBytes?, timeoutMs? }): Promise<BluetoothReadData>
bluetooth.close(sessionId): Promise<StringResultData>
```

`sendAndRead` 用于发送命令后立刻读取响应。文本按 UTF-8 发送，二进制用 `dataBase64`。

#### BLE

```ts
bluetooth.ble.connect({ address, autoConnect? }): Promise<BluetoothSessionData>
bluetooth.ble.discoverServices(sessionId, timeoutMs?): Promise<BluetoothBleServicesData>
bluetooth.ble.readCharacteristic(sessionId, { serviceUuid, characteristicUuid, timeoutMs? }): Promise<BluetoothReadData>
bluetooth.ble.writeCharacteristic(sessionId, { serviceUuid, characteristicUuid, text?, dataBase64? }): Promise<BluetoothTransferData>
bluetooth.ble.writeAndReadCharacteristic(sessionId, { writeServiceUuid, writeCharacteristicUuid, readServiceUuid, readCharacteristicUuid, text?, dataBase64?, timeoutMs? }): Promise<BluetoothReadData>
bluetooth.ble.subscribe(sessionId, { serviceUuid, characteristicUuid, enable? }): Promise<BluetoothTransferData>
bluetooth.ble.readNotifications(sessionId, limit?): Promise<BluetoothBleNotificationData>
```

`writeAndReadCharacteristic` 用于写入命令后读取响应 characteristic。BLE 通知通过 `subscribe` 开启后，使用 `readNotifications` 读取已收到的数据。

### Shell 与 Intent

#### `shell(command)`

```ts
shell(command: string): Promise<ADBResultData>
```

类型注释明确说明：该能力需要 root。

#### `intent(options?)`

```ts
intent({
  action?,
  uri?,
  package?,
  component?,
  flags?,
  extras?,
  type?: 'activity' | 'broadcast' | 'service'
}): Promise<IntentResultData>
```

#### `sendBroadcast(options?)`

```ts
sendBroadcast({
  action,
  uri?,
  package?,
  component?,
  extras?,
  extra_key?,
  extra_value?,
  extra_key2?,
  extra_value2?
}): Promise<IntentResultData>
```

## 终端会话 API

终端能力在 `Tools.System.terminal` 下。

### `terminal.create(sessionName?)`

创建或获取终端会话，返回 `TerminalSessionCreationResultData`。

### `terminal.exec(sessionId, command, timeoutMs?)`

在指定会话中执行命令，返回 `TerminalCommandResultData`。
若发生超时，不会抛错；仍会正常返回结果，并带有 `timedOut = true`。

类型注释建议总是显式传入 `timeoutMs`。

### `terminal.close(sessionId)`

关闭会话，返回 `TerminalSessionCloseResultData`。

### `terminal.screen(sessionId)`

读取当前可见终端屏幕，返回 `TerminalSessionScreenResultData`。

### `terminal.input(sessionId, options?)`

```ts
input(sessionId, {
  input?,
  control?
}): Promise<StringResultData>
```

说明：

- 仅输入文本可传 `input`。
- 仅控制键可传 `control`。
- 两者同时传入时，视为组合键，例如 `control: 'ctrl', input: 'c'`。

## 示例

### 睡眠与 Toast

```ts
await Tools.System.sleep(1000);
await Tools.System.toast('执行完成');
```

### 启动应用

```ts
await Tools.System.startApp('com.android.settings');
```

### 读取最近一天的应用使用时长

```ts
const usage = await Tools.System.getAppUsageTime({
  sinceHours: 24,
  limit: 5
});
console.log(usage.toString());
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
console.log(screen.toString());
await Tools.System.terminal.close(session.sessionId);
```

## 相关文件

- `examples/types/system.d.ts`
- `examples/types/results.d.ts`
- `docs/doc-src/package-dev/results.md`
