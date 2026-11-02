# API 文档：`android.d.ts`

`android.d.ts` 声明 Android shell、Intent、包管理、Content Provider、系统设置和设备控制 API。运行时实现是随 QuickJS 加载的 `app/src/main/assets/js/AndroidUtils.js`，这些类主要把操作转发给 `Tools`。

## 运行时入口与当前差异

- QuickJS 启动时将 `Android`、`Intent`、`PackageManager`、`ContentProvider`、`SystemManager` 和 `DeviceController` 加载为运行时全局类。
- `AndroidUtils.js` 的 `AdbExecutor.executeShell()` 调用 `Tools.system.shell(command, timeout)`；当前 `JsTools.kt` 定义的是 `Tools.System`（大写 `S`），没有 `Tools.system` 别名。因此 shell-backed 方法在取用 `.shell` 前就会抛出异常，`executeShell()` 捕获后记录日志并重新抛出。`PackageManager.isInstalled()` 会捕获该异常并返回 `false`。
- `Intent.start()`、`sendBroadcast()` 和 `startService()` 调用 `Tools.System.intent()`，不走上述小写 `system` 路径；它们仍会按宿主 Intent 工具执行。
- `AdbExecutor.executeAdb()` 出现在声明中，但 `AndroidUtils.js` 没有该方法实现。
- `IntentFlag`、`IntentAction` 和 `IntentCategory` 在声明中是 `const enum`；运行时脚本及 bootstrap globals 未创建同名对象。TypeScript 构建可内联常量；未编译的普通 JavaScript 不应依赖 `IntentAction.ACTION_VIEW` 等运行时对象。
- `toolCall()` 成功时把宿主结果 envelope 解包为 `data`，失败时以 Promise rejection 返回错误。Intent 成功结果实际为 `IntentResultData`，但当前 Kotlin DTO 序列化不含 `.d.ts` 声明的 `type` 字段。

## `AdbExecutor`

`PackageManager`、`ContentProvider`、`SystemManager` 和 `DeviceController` 继承此类。

| 方法 | 运行时行为 |
| --- | --- |
| `executeAdb(command, timeout?)` | 仅有 `.d.ts` 声明；当前运行时无实现。 |
| `executeShell(command, timeout = 15000)` | 调用 `Tools.system.shell(command, timeout)`；返回结果中的 `output`，若结果或 `output` 为空则返回 `""`。调用失败时写日志并重新抛出。当前 `Tools.system` 别名缺失，调用会在进入宿主 shell 工具前失败。 |
| `parseKeyValueOutput(output, separator = ': ')` | 空输入返回 `{}`；逐行查找第一个 separator，分别 trim key/value；不含 separator 的行忽略，重复 key 由后值覆盖。 |
| `escapeShellArg(str)` | 非字符串先用 `String()` 转换，再整体用单引号包裹；内部单引号按 shell 规则闭合引用、插入转义引号并重新开始引用。 |

除了 `escapeShellArg()` 外，多个方法直接把参数拼入命令，没有统一的参数引用处理；具体差异见各类方法说明。

## `IntentFlag` / `IntentAction` / `IntentCategory`

三组 `const enum` 为 TypeScript 提供编译期常量，包含 Activity flags、URI 权限 flags、receiver flags、常见 Android action 和 category。枚举完整成员以 [`android.d.ts`](../../../../examples/types/android.d.ts) 为准。它们不是 `AndroidUtils.js` 创建的运行时对象；普通 JavaScript 可直接传对应的数字或字符串常量。

## `Intent`

### 属性初始值

| 属性 | 初始值与用途 |
| --- | --- |
| `action`、`packageName`、`component`、`uri`、`type` | `undefined`；设置 Intent 的 action、目标包/组件、URI 和 MIME 类型候选值。 |
| `extras` | `{}`；由 `putExtra(key, value)` 写入。 |
| `flags`、`categories` | `[]`；分别由 `addFlag()`、`addCategory()` 维护。 |
| `executor` | 新建的 `AdbExecutor`；当前 Intent 执行方法不使用它，改为调用 `Tools.System.intent()`。 |

### 配置方法

| 方法 | 行为 |
| --- | --- |
| `constructor(action?)` | 将可选 action 直接保存；省略时为 `undefined`。 |
| `setComponent(packageName, component)` | 保存 packageName。若 component 不含 `.`，拼成 `packageName.component`；若包含 `.` 且不含 packageName 子串，则也拼成 `packageName.component`；否则原样保留。之后执行时转成 `packageName/component`。因此 `.MainActivity` 这类相对类名会形成双点路径，不应假设它按 Android `ComponentName` 规则规范化。 |
| `setPackage(packageName)`、`setAction(action)`、`setData(uri)`、`setType(type)` | 直接保存传入值并返回当前 Intent；不做格式校验。 |
| `addCategory(category)` | 仅在值为 truthy 且数组中尚不存在严格相等值时追加。 |
| `removeCategory(category)`、`hasCategory(category)` | 按严格相等值移除第一项或检查是否存在。 |
| `getCategories()` | 返回新数组副本。 |
| `clearCategories()` | 将 categories 重置为空数组。 |
| `addFlag(flag)` | 按严格相等值去重后追加；不校验 flag 范围。 |
| `putExtra(key, value)` | 直接赋值到 extras；同名 key 会覆盖。 |

### 执行方法

| 方法 | 前置条件与转发行为 |
| --- | --- |
| `start()` | 要求 `action` 为 truthy；仅设置 package 或 component 仍会同步抛错。把 flags JSON 序列化，合并 extras，并以 `type: 'activity'` 调用 `Tools.System.intent()`。宿主执行 Activity 时会自动补 `FLAG_ACTIVITY_NEW_TASK`（若尚未设置）。 |
| `sendBroadcast()` | 要求 action 为 truthy；合并 extras，但不传递 flags；以 `type: 'broadcast'` 调用 `Tools.System.intent()`。 |
| `startService()` | 要求 packageName 和 component；action 可省略。合并 extras，但不传递 flags；以 `type: 'service'` 调用 `Tools.System.intent()`。 |

三种方法均返回宿主 `IntentResultData` 的 Promise；宿主失败会使 Promise reject。当前 Kotlin 结果字段为 `action`、`uri`、`package_name`、`component`、`flags`、`extras_count` 和 `result`，没有声明中的 `type`。

`addCategory()` 保存的数组会被执行方法放进 extras 的 `categories` key，并不会调用 Android Intent 的 category API；`setType()` 的值同样放进 extras 的 `type` key，不会设置 Android Intent MIME type。`sendBroadcast()` 和 `startService()` 都忽略此对象的 flags 数组。

## `PackageManager`

继承 `AdbExecutor`。以下命令参数由模板字符串直接拼接；这些方法自身不调用 `escapeShellArg()`。

| 方法 | 默认值、实现和返回值 |
| --- | --- |
| `install(apkPath, replaceExisting = true)` | 执行 `pm install -r <apkPath>`；`false` 时省略 `-r`。shell timeout 为 60 秒，返回 shell 输出字符串。 |
| `uninstall(packageName, keepData = false)` | 执行 `pm uninstall <packageName>`；`keepData` 为 true 时加 `-k`。返回 shell 输出字符串。 |
| `getInfo(packageName)` | 执行 `dumpsys package <packageName>`；从文本中抽取版本号、版本名和安装时间，并用格式匹配启发式收集 activities/services。未解析字段为 `undefined`，`permissions` 当前始终是空数组。 |
| `getList(includeSystem = false)` | 默认执行 `pm list packages -3`，true 时用 `-a`；只收集以 `package:` 开头的行并去掉前缀，返回包名数组。 |
| `clearData(packageName)` | 执行 `pm clear <packageName>`，返回 shell 输出字符串。 |
| `isInstalled(packageName)` | 执行 `pm list packages` 并用 `grep <packageName>` 搜索输出，检查是否包含 `package:<packageName>`；任何异常都被捕获并返回 false。它按子串判断，不是精确包名比较。 |

## `ContentProvider`

继承 `AdbExecutor`。构造函数和 `setUri()` 直接保存 URI；`query()`、`insert()`、`update()`、`delete()` 在 URI 为空时同步抛出 `URI not set`。

| 方法 | 实现和返回值 |
| --- | --- |
| `query(projection?, selection?, selectionArgs?, sortOrder?)` | 执行 `content query`；URI、projection、where 和 sort 参数使用 `escapeShellArg()`。selectionArgs 会先以空格合成单个 `--arg`。返回值按首行空格分隔列名、后续行空格分隔字段解析成 `Record<string, string>[]`；值中含空格时无法无损解析。 |
| `insert(values)` | 每个字段转成 `key:s:value` 的 `--bind` 参数；绑定字符串经 `escapeShellArg()` 后传给 `content insert`。值按字符串类型提交，返回 shell 输出。 |
| `update(values, selection?, selectionArgs?)` | 使用与 insert 相同的 string bind；where 经 shell quoting；每个 selectionArg 单独追加 `--arg`。 |
| `delete(selection?, selectionArgs?)` | 可选 where 经 shell quoting；每个 selectionArg 单独追加 `--arg`。 |

URI 缺失会同步抛错；其余宿主 shell 错误按 `executeShell()` 的 rejection 路径传播。

## `SystemManager`

继承 `AdbExecutor`。`getSetting()`、`setSetting()` 和 `listSettings()` 仅接受 `system`、`secure`、`global`；其他值同步抛出 namespace 错误。namespace 和 key 直接插入命令，set 方法只对 value 使用 `escapeShellArg()`。

| 方法 | 实现和返回值 |
| --- | --- |
| `getProperty(prop)` | 执行 `getprop <prop>`，trim 输出后返回字符串。 |
| `setProperty(prop, value)` | 执行 `setprop <prop> '<value>'`，value 用 `escapeShellArg()`；返回 shell 输出。 |
| `getAllProperties()` | 执行 `getprop` 并解析 `[key]: [value]`。当前循环以 `match !== undefined` 判断结束，但 `RegExp.exec()` 用 `null` 表示结束；因此匹配耗尽后会解引用 null 并抛错。 |
| `getSetting(namespace, key)` | 执行 `settings get <namespace> <key>`，trim 输出。 |
| `setSetting(namespace, key, value)` | 执行 `settings put <namespace> <key> '<value>'`，返回 shell 输出。 |
| `listSettings(namespace)` | 执行 `settings list <namespace>`；按每行第一个 `=` 拆 key/value，并保留后续 `=`，返回映射。 |
| `getScreenInfo()` | 执行 `wm size; wm density`；只匹配 `Physical size` 与 `Physical density`，返回 width/height、densityDpi 及 `densityDpi / 160`。未匹配的数值为 `undefined`。 |

## `DeviceController`

继承 `AdbExecutor`，构造时创建独立的 `SystemManager` 实例。

| 方法 | 实现和返回值 |
| --- | --- |
| `takeScreenshot(outputPath)` | 执行 `screencap -p <outputPath>` 并返回 shell 输出；路径直接拼入命令。 |
| `recordScreen(outputPath, timeLimit = 180, bitRate = 4, size?)` | 调用 `screenrecord`；timeLimit 仅用 `Math.min(timeLimit, 180)` 限制上限，没有下限校验；bitRate 作为 Mbps 乘以 1,000,000，size 可选。size 和 outputPath 直接拼入命令。 |
| `setBrightness(brightness)` | 对输入 floor 后限制在 0–255，再写入 system namespace 的 `screen_brightness`。 |
| `setVolume(stream, volume)` | stream 映射为 music=3、call=0、ring=2、alarm=4、notification=5；volume 不做范围检查。当前以 `if (!validStreams[stream])` 检查映射，call 的值 0 被当作无效并抛错。 |
| `setAirplaneMode(enable)` | 先写 global `airplane_mode_on` 为 `1`/`0`，再发送 `AIRPLANE_MODE` 广播并传入字符串 state。第一步失败时不会发广播；成功时方法实际返回 IntentResultData，而声明写的是 `Promise<string>`。 |
| `setWiFi(enable)` | 执行 `svc wifi enable` 或 `svc wifi disable`。 |
| `setBluetooth(enable)` | 根据 enable 拼接 `service call bluetooth_manager 6 i32 n` 命令，n 为 1 或 0。 |
| `lock()`、`unlock()` | lock 发送 keyevent 26；unlock 先发 wakeup keyevent 224，再发 menu keyevent 82。后者不是对安全锁屏的解锁保证。 |
| `reboot(mode?)` | 执行 `reboot`，有 mode 时直接追加该字符串；运行时不限制 mode 值。 |

除本地转换/检查外，这些方法都依赖 `executeShell()`；当前 `Tools.system`/`Tools.System` 大小写差异会使它们在调用宿主工具前失败。

## `Android` 总入口

```ts
new Android()
```

构造时分别创建 `packageManager`、`systemManager` 和 `deviceController`；`deviceController.systemManager` 是另一独立实例。`createIntent(action?)` 返回新的 `Intent`，`createContentProvider(uri)` 返回新的 `ContentProvider`。两个 factory 只构造对象，不会立即调用宿主工具。

## 示例

```ts
const intent = new Android()
  .createIntent('android.intent.action.VIEW')
  .setData('https://example.com');

const result = await intent.start();
console.log(result.action, result.result);
```

上例只使用由 `AndroidUtils.js` 实际创建的类和字符串 action；不要把未由运行时导出的 `IntentAction`/`IntentFlag` 当成普通 JavaScript 全局值。

## 相关实现

- `examples/types/android.d.ts`
- `examples/types/index.d.ts`
- `app/src/main/assets/js/AndroidUtils.js`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsLibraries.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsTools.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsInitRuntimeScriptBuilder.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardIntentToolExecutor.kt`
- `docs/doc-src/toolpkg_developer_documentation/04_modules/system.md`
