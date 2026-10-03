# API 文档：`ui.d.ts`

`ui.d.ts` 暴露两层 UI 自动化能力：`Tools.UI` 负责把调用转成宿主工具请求，`UINode` 负责把 `UIPageResultData.uiElements` 包装成可遍历、可搜索和可交互的 JS 对象。UI 操作依赖当前宿主选择的 UI backend 以及对应的无障碍、调试或 root 能力；声明本身不授予这些权限。

## 运行时入口

```ts
Tools.UI
UINode
```

`Tools.UI` facade 当前实际暴露 9 个方法：`getPageInfo`、`captureScreenshot`、`tap`、`longPress`、`clickElement`、`setText`、`pressKey`、`swipe` 和 `runSubAgent`。

工具成功结果会被宿主包装为 `ToolResult`，JS facade 返回其 `result` 对应的值；失败不会把失败数据伪装成成功值，具体 Promise reject/resolve 包装由 JS 工具调用层决定。

## `Tools.UI`

### `getPageInfo()`

```ts
function getPageInfo(): Promise<UIPageResultData>;
```

调用 `get_page_info`，不向工具传入参数。成功结果包含：

- `packageName`：当前窗口包名；
- `activityName`：当前 Activity 或宿主能解析到的前台 Activity 名称；
- `uiElements`：根 `SimplifiedUINode`。

无障碍 backend 会读取并简化 UI hierarchy XML；debugger/root backend 会通过 UIAutomator dump 和窗口信息生成同样的 DTO。节点的 `className` 通常是去掉包名后的短类名，`bounds` 使用 `"[left,top][right,bottom]"`。无法读取 UI hierarchy、缺少 backend 能力或解析失败时返回失败结果。

实现内部还识别 `format`、`detail` 或 `display` 等工具参数，但当前 `Tools.UI.getPageInfo()` 不提供参数对象，因此不能从 facade 调整这些选项。

### `captureScreenshot()`

```ts
function captureScreenshot(): Promise<string>;
```

调用 `capture_screenshot`。注册入口关闭状态指示并设置约 200ms 的执行延迟；成功时返回截图文件路径字符串，失败时返回失败结果。截图能力可能要求系统截图授权或当前 UI backend 可用；该方法不等待 UI 树刷新。

### `tap(x, y)` 与 `longPress(x, y)`

```ts
function tap(x: number, y: number): Promise<UIActionResultData>;
function longPress(x: number, y: number): Promise<UIActionResultData>;
```

参数按原值传给 `tap`/`long_press` 工具；常用 backend 会把坐标解析为整数，缺少坐标或不能解析为整数时失败。成功数据通常为：

```ts
interface UIActionResultData {
  actionType: string;
  actionDescription: string;
  coordinates?: [number, number] | null;
  elementId?: string | null;
}
```

无障碍实现的 `tap`/`longPress` 成功类型分别为 `tap` 和 `long_press`，并包含坐标；backend 不能执行手势时返回失败结果。

### `clickElement(...)`

```ts
function clickElement(
  param1: string | { [key: string]: any },
  param2?: string | number,
  param3?: number
): Promise<UIActionResultData>;

function clickElement(params: {
  resourceId?: string;
  className?: string;
  text?: string;
  contentDesc?: string;
  bounds?: string;
  index?: number;
  partialMatch?: boolean;
  isClickable?: boolean;
}): Promise<UIActionResultData>;
```

JS facade 根据实参数量转换为 `click_element` 的参数对象：

| 调用 | 实际参数 | 说明 |
| --- | --- | --- |
| `clickElement({ ... })` | 原对象 | 直接传递，不做字段白名单过滤。 |
| `clickElement(value)` | `{ resourceId: value }` | 若字符串同时看起来像 `"[x,y][x,y]"`，改传 `{ bounds: value }`。 |
| `clickElement(resourceId, index)` | `{ resourceId, index }` | 只有第一个参数不是 `resourceId`、`className`、`bounds` 三个字面量时采用此形式。 |
| `clickElement(type, value)` | 对应 `{ resourceId/className/bounds: value }` | `type` 为 `resourceId`、`className` 或 `bounds`。 |
| `clickElement(type, value, index)` | `{ resourceId/className: value, index }` | 三参数分支只特殊处理 `resourceId` 和 `className`；其它 type 会落入兼容分支，不应依赖。 |

常用 backend 至少支持 `resourceId`、`className`、`contentDesc` 和 `bounds`：

- `bounds` 会解析矩形并点击中心点；格式必须类似 `"[10,20][300,400]"`。
- `index` 默认是 `0`，用于多个匹配节点；负数或超出匹配数量时失败。
- 无障碍 backend 对 `resourceId` 使用后缀匹配，对 `className` 使用精确匹配，对 `contentDesc` 使用不区分大小写的匹配。
- 没有任何可识别 selector、UI hierarchy 为空、目标没有 bounds 或点击动作失败时返回失败结果。

声明中的 `text`、`partialMatch` 和 `isClickable` 字段并不是所有 backend 都消费；当前 facade 只负责原样转发对象，不能据此保证宿主一定按这些字段筛选。

### `setText(text, resourceId?)`

```ts
function setText(text: string, resourceId?: string): Promise<UIActionResultData>;
```

调用 `set_input_text`，必传字段为 `text`；只有 `resourceId` 为 truthy 值时才加入请求。具体 backend 可能使用当前焦点输入框、剪贴板粘贴或其他输入通道；`resourceId` 并不保证会改变目标选择。空字符串在支持清空输入框的 backend 中表示清空操作。

### `pressKey(keyCode)`

```ts
function pressKey(keyCode: string): Promise<UIActionResultData>;
```

调用 `press_key` 并传递 `{ key_code: keyCode }`。debugger/root backend 通常执行 Android `input keyevent`；缺少 key code、命令失败或当前 backend 不支持时失败。facade 不把 key code 规范化为数字。

### `swipe(startX, startY, endX, endY, duration?)`

```ts
function swipe(
  startX: number,
  startY: number,
  endX: number,
  endY: number,
  duration?: number
): Promise<UIActionResultData>;
```

必填坐标转换为 `start_x`、`start_y`、`end_x`、`end_y`。只有 `duration` 为 truthy 值时才加入请求，因此传入 `0` 等价于省略；backend 默认时长通常为 `300ms`。坐标解析失败或手势执行失败时返回失败结果。

### `runSubAgent(intent, maxSteps?, agentId?, targetApp?)`

```ts
function runSubAgent(
  intent: string,
  maxSteps?: number,
  agentId?: string,
  targetApp?: string
): Promise<AutomationExecutionResultData>;
```

调用 `run_ui_subagent`：

- `intent` 先执行 `String(intent || "")`；空值会变成空字符串。
- 提供 `maxSteps` 时以字符串形式写入 `max_steps`；工具描述默认步数为 `20`。
- `agentId` 和 `targetApp` 只有非 `null` 且字符串长度大于 `0` 时写入请求。
- 结果是自动化执行记录，不代表每个动作都成功；详细字段以 [`AutomationExecutionResultData`](./results.md) 为准。

## `UINode`

`UINode` 是 assets 中加载的 JS wrapper，不是 native 节点句柄。构造器接收 `SimplifiedUINode` 和可选父节点；`children` 在第一次读取时创建并缓存 wrapper。

### 属性

| 属性 | 行为 |
| --- | --- |
| `className`、`text`、`contentDesc`、`resourceId`、`bounds` | 直接读取原始节点字段，缺失时为 `undefined`。 |
| `isClickable` | 把原始值转成 boolean。 |
| `rawNode` | 返回原始 `SimplifiedUINode` 对象。 |
| `parent` | 父 wrapper；根节点为 `undefined`。 |
| `path` | 从根到当前节点的标识链，优先使用 resource ID、文本、content description，再退回类名和同类兄弟索引。 |
| `centerPoint` | 解析正整数 bounds 并返回向下取整的中心坐标；bounds 缺失或含负数等非匹配格式时为 `undefined`。 |
| `children`、`childCount` | 子 wrapper 列表及数量；不存在 children 数组时为空列表。 |

### 文本和搜索

```ts
allTexts(trim?: boolean, skipEmpty?: boolean): string[];
textContent(separator?: string): string;
hasText(text: string, caseSensitive?: boolean): boolean;
find(criteria: object | ((node: UINode) => boolean), deep?: boolean): UINode | undefined;
findAll(criteria: object | ((node: UINode) => boolean), deep?: boolean): UINode[];
```

`find`/`findAll` 只搜索后代，不把当前节点自身作为候选；`deep` 默认 `true`。对象 criteria 的 `exact` 和 `caseSensitive` 是搜索选项，默认都为 `true`；`clickable` 会匹配 `isClickable`，其它字段按同名属性比较。谓词抛异常时跳过该节点。

便利方法如下：

- `findByText`、`findAllByText`：文本匹配；
- `findById`、`findAllById`：默认 `exact: false`，资源 ID 支持部分匹配；
- `findByClass`、`findAllByClass`；
- `findByContentDesc`、`findAllByContentDesc`；
- `findClickable()`：搜索 `isClickable === true`；
- `closest(criteria)`：从父节点开始向上查找，不检查当前节点。

`hasText` 会检查当前节点和后代，默认区分大小写；空查询直接返回 `false`。`allTexts` 默认 trim 且跳过空文本，`textContent` 默认用一个空格连接。

### 节点动作

```ts
click(): Promise<UIActionResultData>;
longPress(): Promise<UIActionResultData>;
setText(text: string): Promise<UIActionResultData>;
wait(ms?: number): Promise<UINode>;
clickAndWait(ms?: number): Promise<UINode>;
longPressAndWait(ms?: number): Promise<UINode>;
```

- `click()` 优先使用 bounds 中心，其次按 resource ID、text、content description 选择器点击；节点不可点击时只记录 warning，不会提前终止。没有任何可用标识时抛 JS `Error`。
- `longPress()` 只支持有可解析 bounds 的节点，并按中心坐标长按；没有 bounds 时抛 JS `Error`。
- `setText()` 先调用当前节点的 `click()` 聚焦，再调用 `Tools.UI.setText(text)`；不会把当前节点的 resource ID 再传给 setText。
- `wait`、`clickAndWait`、`longPressAndWait` 默认等待 `1000ms`，等待后通过 `UINode.getCurrentPage()` 创建新页面 wrapper。

### 表示和静态方法

```ts
toString(): string;
toTree(indent?: string): string;
toTreeString(indent?: string): string;
toFormattedString?(): string;
equals(other: UINode): boolean;

static fromPageInfo(pageInfo: UIPageResultData): UINode;
static getCurrentPage(): Promise<UINode>;
static findAndWait(query: object, delayMs?: number): Promise<UINode>;
static clickAndWait(query: object, delayMs?: number): Promise<UINode>;
static longPressAndWait(query: object, delayMs?: number): Promise<UINode>;
```

`toTree` 输出完整 wrapper 树；`toTreeString` 复用宿主的过滤规则，只保留关键控件、有文本/描述、可点击或包含可保留后代的节点。`toFormattedString` 只由 `fromPageInfo` 动态挂到根 wrapper 上，包含包名、Activity 和过滤后的树。`equals` 依次按 resource ID、bounds、text+className 比较，缺少这些字段时返回 `false`。

`fromPageInfo`、`getCurrentPage` 可用；但后三个 `*AndWait` 静态方法当前会调用 `Tools.UI.combinedOperation(...)`。`JsTools.UI` facade 没有该成员，`ui.d.ts` 也没有声明它，因此在当前运行时执行这些方法会因 `combinedOperation is not a function` 失败。它们属于声明/资源代码遗留入口，不应作为可用 API 使用。

## 可运行示例

```ts
const page = await UINode.getCurrentPage();
const login = page.findByText('登录', { exact: true });
if (login) {
  await login.click();
}

const input = page.findByClass('EditText');
if (input) {
  await input.setText('hello');
}

const screenshotPath = await Tools.UI.captureScreenshot();
complete({ screenshotPath });
```

对象 selector 示例：

```ts
await Tools.UI.clickElement({
  resourceId: 'com.example:id/submit',
  index: 0
});
```

## 声明与实现差异

- `ui.d.ts` 导入了 `CombinedOperationResultData`，但没有声明 `Tools.UI.combinedOperation`；`UINode` 的三个静态等待方法仍引用该不存在的 facade 成员。
- `clickElement` 的声明把对象字段写得比当前 backend 更宽；`text`、`partialMatch` 和 `isClickable` 是否有效取决于 UI backend。
- `UINode.toFormattedString` 在声明中是可选实例方法，实际只有 `UINode.fromPageInfo` 创建的 wrapper 才拥有它。
- facade 的 `captureScreenshot` 声明为 `Promise<string>`，宿主内部结果仍是 `StringResultData`；工具失败不会产生有效路径。

## 相关源码

- `examples/types/ui.d.ts`
- `examples/types/results.d.ts`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsTools.kt`
- `app/src/main/assets/js/UINode.js`
- `app/src/main/java/com/ai/assistance/operit/core/tools/ToolRegistration.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardUITools.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/accessbility/AccessibilityUITools.kt`
- `docs/doc-src/toolpkg_developer_documentation/04_modules/results.md`
