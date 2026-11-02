---
title: Compose DSL
status: complete
---

# Compose DSL

Compose DSL 是 `runtime: "compose_dsl"` UI 模块使用的声明式节点协议。页面导出默认 screen 函数或名为 `Screen` 的函数，宿主以一个 `ComposeDslContext` 调用它。返回值可以是 `ComposeNode`，也可以是 `Promise<ComposeNode>`。

```ts
export default function Screen(ctx: ComposeDslContext): ComposeNode | Promise<ComposeNode> {
  return ctx.UI.Column({
    content: [ctx.UI.Text({ text: "Hello" })]
  });
}
```

## 节点与渲染周期

`ComposeNodeFactory<TProps>` 的签名是 `(props?: TProps, children?: ComposeChildren) => ComposeNode`。`ctx.UI.Name(props, children)` 和 `ctx.h(type, props, children)` 都创建节点，不会立即渲染 native UI。节点运行时形状为：

```ts
interface ComposeNode {
  type: string;
  props?: Record<string, unknown>;
  children?: ComposeNode[];
}
```

JS bridge 额外写入 `__composeNode: true` 和 `slots`：`children` 参数归一化为数组；props 内含 Compose 节点的字段会放到 `slots[field]`，其他值保留在 `props`。嵌套函数会被替换成 action ID，列表和记录中的函数也递归处理。宿主据 action ID 回调 QuickJS，不能把函数当成普通 JSON 字段处理。

`UI` 是动态 Proxy：任意字符串属性都产生同名节点工厂；类型声明只为登记过的组件提供补全与 props 校验。未知组件虽然能构造节点，但宿主分发时只有专用 renderer 或 registry 中的节点会被渲染；其它节点会显示 `Unsupported node: <type>` 文本。

当前分发顺序为：`AiChat`、`AdaptiveSidePanel`、`Canvas`、`WebView`、`Markdown`、`AlertDialog`、`Dialog` 专用 renderer，然后查找自动生成的 Material 3/Foundation registry。`Canvas` 因为使用自定义绘制 parser，由 `ToolPkgComposeDslScreen.kt` 的手写 renderer 处理，不在生成 Kotlin registry 中；其声明仍位于 `compose-dsl.material3.generated.d.ts` 的 generated registry。

初次渲染、重渲染和 action 分发共享本次 Compose runtime bundle。action 完成后宿主会等待待处理状态变更，再生成最终 response。异步 action 期间会安排中间 render checkpoint；state listener 可能继续推送中间 render。设置 `__no_render`、`__noRender` 或 `__local` action payload 时，最终 action response 不包含 tree。action ID 由函数 prop、嵌套数组或记录中的函数统一注册，不能把 action ID 当作普通业务字符串复用。

`ComposeDslRuntime.createContext()` 当前只返回初始 `state`/`memo` store 和 action API，没有实现 runtime wrapper 可选调用的 `updateRuntimeOptions()`。因此 render 时传入的 state/memo 是本次 bundle 的初值；后续 action/rerender 的 runtime options 不会通过该缺失方法自动替换已有 store。

## 状态 API

| 方法 | 契约 |
| --- | --- |
| `useState<T>(key, initialValue)` | 返回 `[value, setValue]`。key 去除空白后不得为空；同一 key 只在 state store 中不存在时采用 initialValue。setter 写入新值并标记 dirty；同一微任务内的多次写入合并为一次异步 listener flush/中间重渲染。 |
| `useMutable<T>(key, initialValue)` | 返回 `[value, setValue]`，数据保存在 memo store。setter 不触发状态通知；需要时用作 action 内临时值。 |
| `useRef<T>(key, initialValue)` | 在 memo store 中保留 `{ current }` 对象，同一 key 后续调用返回同一对象；更新 `current` 不触发重渲染。 |
| `useMemo<T>(key, factory, deps?)` | 按 key 首次保存 factory 的结果；后续直接返回已有值。当前实现不比较或使用 `deps`，不会按依赖变化重新计算。 |

state、memo 初值来自宿主传入的 `runtimeOptions.state` 和 `runtimeOptions.memo` 的浅拷贝。Render/action response 带回更新后的两份 store。key 是持久身份：不同逻辑值不要复用同一个 key。

setter 只接受新值，不支持 React 风格的 updater function；如果把函数传给 `setValue`，它会作为值写入 store。`useState` 会安排同一微任务内的批量 listener flush，`useMutable`/`useRef`/`useMemo` 不会主动触发重渲染。`useMemo` 的 `deps` 仅为声明兼容参数，当前实现不比较依赖数组。

## 颜色、单位和公共值

- `MaterialTheme.colorScheme` 是动态 Proxy。访问任意字符串属性都会生成 `{ __colorToken, alpha? }` token；它不会在 JS 层校验是否为真实 Material 3 颜色名。`copy({ alpha })` 返回新 token，缺失或非 number 的 alpha 会保留原值。
- `number.px`、`number.dp`、`number.fraction` 返回 `{ __unit, value }`。bridge 还为数组安装相同 getter，把数组每个元素包装成单位值；该数组扩展行为没有在 `Number` 类型声明中体现。
- `ComposeColor` 可为颜色字符串或 token；`ComposePadding` 只定义 `horizontal`/`vertical`，而 modifier 的 padding 对象另有 all/start/top/end/bottom 字段。
- `ComposeShape`、`ComposeBorder`、`ComposeTextMeasureRequest`、`ComposeTextMeasureResult`、`ComposeTemplateValues` 和 `ComposeRouteInfo` 的完整字段以声明文件为准；运行时对未知对象字段通常保留并在 native renderer 阶段决定是否使用。

## 基础 UI 工厂

所有下列组件都继承 `ComposeCommonProps`，除非表中特别注明。该公共字段集合为：

- identity/lifecycle：`key?`、`onLoad?`
- layout：`topBarTitle?`、`modifier?`、`zIndex?`、`weight?`、`weightFill?`、`width?`、`height?`、`fillMaxHeight?`、`fillMaxWidth?`、`fillMaxSize?`、`spacing?`
- padding：`padding?: number | ComposePadding`、`paddingStart?`、`paddingTop?`、`paddingEnd?`、`paddingHorizontal?`、`paddingVertical?`、`paddingBottom?`
- background：`background?`、`backgroundColor?`、`containerColor?`、`backgroundAlpha?`、`backgroundBrush?`、`backgroundShape?`

颜色支持 CSS-like string 或 `ComposeColorToken`。padding 对象包含 `horizontal?`、`vertical?`。shape 类型和 modifier 操作见后文及[声明文件](../../../../examples/types/compose-dsl.d.ts)。

| 工厂 | 组件专属字段 | 关键行为 |
| --- | --- | --- |
| `Column` | `content?`、`horizontalAlignment?`、`verticalArrangement?` | 垂直排列子项。 |
| `Row` | `content?`、`horizontalArrangement?`、`verticalAlignment?`、`onClick?` | 水平排列；可注册点击 action。 |
| `Box` | `content?`、`contentAlignment?` | 叠放子项。 |
| `Spacer` | `width?`、`height?` | 不继承 `ComposeCommonProps`。 |
| `Text` | 必填 `text`；`style?`、`color?`、`fontWeight?`、`fontSize?`、`fontFamily?`、`maxLines?`、`softWrap?`、`overflow?` | 字体样式枚举为 `headlineSmall/Medium`、`titleLarge/Medium/Small`、`bodyLarge/Medium/Small`、`labelLarge/Medium/Small`。 |
| `Markdown` | 必填 `text`；`color?`、`fontSize?`、`enableDialogs?`、`streamTagName?` | 用于 Markdown 内容而非普通纯文本节点。 |
| `TextField` | 必填 `value`、`onValueChange(value)`；`label?`、`placeholder?`、`leadingIcon?`、`trailingIcon?`、`prefix?`、`suffix?`、`supportingText?`、`singleLine?`、`minLines?`、`maxLines?`、`readOnly?`、`isError?`、`isPassword?`、`style?` | 文本变化通过 action 回调返回完整字符串。 |
| `Switch` | 必填 `checked`、`onCheckedChange(checked)`；`enabled?`、`thumbContent?`、选中/未选中 thumb 与 track color | 状态由调用方传入；回调参数是新布尔值。 |
| `Checkbox` | 必填 `checked`、`onCheckedChange(checked)`；`enabled?` | 状态由调用方传入。 |
| `Button` | 必填 `onClick()`；`content?`、`text?`、`enabled?`、`contentPadding?`、`shape?` | label 可用 text 或 Compose 子节点。 |
| `IconButton` | 必填 `onClick()`；`content?`、`icon?`、`enabled?`、`shape?` | `icon` 接受图标名字符串。 |
| `Card` | `content?`、容器/内容 color 与 alpha、`shape?`、`border?`、`elevation?` | `containerAlpha?`、`contentAlpha?` 为 Card 专属字段。 |
| `Surface` | `content?`、`containerColor?`、`contentColor?`、`shape?`、`alpha?`、`onClick?` | 点击回调可异步。 |
| `Icon` | `name?`、`tint?`、`size?`、`spin?`、`spinDurationMs?` | 名称见[Material Icons](./material_icons.md)；runtime 也接受任意字符串。 |
| `LazyColumn` | `content?`、`spacing?` | 子节点按纵向列表布局。 |
| `LinearProgressIndicator` | `progress?` | 进度字段为可选 number。 |
| `CircularProgressIndicator` | `strokeWidth?`、`color?` | 未声明 progress 字段，按不定进度组件使用。 |
| `SnackbarHost` | 无专属字段 | 继承公共字段。 |
| `AiChat` | 无专属字段 | 嵌入宿主 AI chat surface，不含 workspace panel。 |
| `AdaptiveSidePanel` | 必填 `open`、`side`、`onOpenChanged(open)`；`defaultWidth?`、`minWidth?`、`minContentWidth?`、`breakpoint?` | 用 side slot 提供响应式尾侧面板内容。 |
| `Canvas` | `commands?`、`transform?`、`onTransform?`、`onSizeChanged?` | command 为 line/rect/roundRect/circle/text/path/icon 绘制联合类型。 |
| `WebView` | URL/HTML、header、Web 设置项、controller 与导航/资源/生命周期回调 | 详见下方 WebView 契约。 |

`ComposeArrangement` 为 `start/center/end/spaceBetween/spaceAround/spaceEvenly`；`ComposeAlignment` 为 `start/center/end`。组件 props 的完整继承关系和可空性以 `examples/types/compose-dsl.d.ts` 为准。

## Material 3/Foundation generated registry

`compose-dsl.material3.generated.d.ts` 当前声明 84 个 generated props 接口和 84 个 registry key。自动生成的 Kotlin registry 实际包含其中 83 个；`Canvas` 是唯一不在 generated registry 中的 key，因为它由专用 renderer 分发。其余 generated key 与 Kotlin registry 一一对应，包含声明中的 `DropdownMenu`、`ListItem`、导航/抽屉、Tabs、chips、按钮、反馈和基础 Foundation 组件。

生成组件统一接收 `ComposeNodeFactory<Props>`，函数 prop 会进入 action registry。状态控件（`Switch`、`Checkbox`、toggle button、chip、tab、navigation item 等）是 controlled API：点击只派发 callback，下一次 render 仍需由调用方传入新的 `checked`/`selected`/`expanded` 值。slot 字段（如 `content`、`label`、`drawerContent`、`tabs`）会从 props 拆成 `slots`，普通 `children` 作为 fallback 的规则由具体 renderer 决定。

生成器以 Gradle cache 中的 Compose Material3/Foundation source jar 为输入；重新生成会同时更新 Kotlin registry、Kotlin renderers 和 TypeScript props，不能手工只改 generated `.d.ts`。

## Canvas

`CanvasProps` 接收 `commands?: ComposeCanvasCommand[]`、`transform?`、`onTransform?` 和 `onSizeChanged?`。坐标和尺寸可写普通 number 或 `{ value, unit }`，unit 为 `px`、`dp`、`fraction`；颜色是 string 或颜色 token。

| `type` | 必填字段 | 可选字段 |
| --- | --- | --- |
| `line` | `x1`、`y1`、`x2`、`y2` | `color`、`alpha`、`strokeWidth`、`unit` |
| `rect` | `x`、`y`、`width`、`height` | `brush`、`color`、`alpha`、`strokeWidth`、`filled`、`unit` |
| `roundRect` | `x`、`y`、`width`、`height` | `radius`、`brush`、`color`、`alpha`、`strokeWidth`、`filled`、`unit` |
| `circle` | `cx`、`cy`、`radius` | `color`、`alpha`、`strokeWidth`、`filled`、`unit` |
| `text` | `x`、`y`、`text` | `color`、`alpha`、`fontSize`、`minWidth`、`maxWidth`、`minHeight`、`maxHeight`、`maxLines`、`overflow`、`unit` |
| `drawPath` | `path` | `color`、`alpha`、`strokeWidth`、`style`、`unit` |
| `drawRoundRect` | `x`、`y`、`width`、`height` | `cornerRadius`、`brush`、`color`、`alpha`、`strokeWidth`、`style`、`unit` |
| `drawText` | `text`、`x`、`y` | `color`、`alpha`、`fontSize`、`fontWeight`、`minWidth`、`maxWidth`、`minHeight`、`maxHeight`、`maxLines`、`overflow`、`unit` |
| `drawIcon` | `icon`、`x`、`y` | `size`、`color`、`alpha`、`unit` |

`brush` 当前声明为 `{ type: "verticalGradient", colors: ComposeColor[] }`；runtime 只接受 `verticalGradient` 且 colors 非空。缺少 type/colors、颜色无法解析或 command 字段不合法时，Canvas renderer 可能在 Compose 渲染阶段抛错。缺少 `unit` 时默认按 `fraction` 处理；`fraction` 按画布宽/高换算，`dp` 按 density 换算，其它单位按像素数使用。缺少 `strokeWidth` 时为 `1`。

`drawPath.path` 是按序操作数组：`moveTo(x,y)`、`lineTo(x,y)`、`cubicTo(x1,y1,x2,y2,x3,y3)`、`quadTo(x1,y1,x2,y2)` 或 `close`。Canvas 变换含 `scale`、`offsetX`、`offsetY`、`pivotX`、`pivotY`；手势事件包含 centroid、pan、zoom 和 rotation。运行时会把手势 scale 限制在 `0.6..2.0`，并为 `onTransform` action payload 设置 `__no_render: true`；尺寸变化 callback 只在尺寸真正变化时派发。完整字段类型见 `examples/types/compose-dsl.d.ts`。

## WebView


`WebViewProps` 可设置 `url` 或 `html`，另有 `baseUrl`、`mimeType`、`encoding`、`headers`、JavaScript/DOM storage/database、多窗口、文件/content access、user agent、嵌套滚动、zoom、viewport、mixed content、media gesture、text zoom、cache、safe browsing 和第三方 cookie 等字段。每项均为可选，默认由宿主 WebView 配置决定；不要假定属性默认值等于 Android WebView 默认值。`url` 与 `html` 都未设置时，具体加载行为由 renderer 当前实现决定。

事件包括 `onPageStarted`、`onPageFinished`、`onReceivedError`、`onReceivedHttpError`、`onReceivedSslError`、`onDownloadStart`、`onConsoleMessage`、`onUrlChanged`、`onProgressChanged`、`onStateChanged` 和 `onLifecycleEvent`。`onShouldOverrideUrlLoading(request)` 可返回 allow/cancel/rewrite/external 决策；`onInterceptRequest(request)` 可返回 allow/block/rewrite/respond 决策。两者都支持 Promise，且可返回 null/undefined 表示未作决定。响应 body 必须在 `text`、`base64`、`filePath` 三种互斥来源中选一种；宿主会把 callback action 的结果转为 WebView 决策，异常或超时按 WebView host 的失败路径处理。

`addJavascriptInterface(name, object)` 要求 object 是非数组对象，并且至少包含一个函数方法；函数会注册为 Compose action，而不是直接把任意 JS 对象暴露给 Android WebView。`removeJavascriptInterface` 会移除当前 execution context/controller 下的映射。controller 和 WebView 绑定 route instance、execution context、controller key，route 销毁或 context 清理后旧 controller 不应继续使用。

`createWebViewController(key)` 要求非空 key，创建绑定当前 route instance 与 execution context 的 controller。可调用 `loadUrl(url, headers?)`、`loadHtml(html, options?)`、`reload()`、`stopLoading()`、`goBack()`、`goForward()`、`clearHistory()`、`getState()`、`addJavascriptInterface(name, object)` 和 `removeJavascriptInterface(name)`；这些同步命令通过 native bridge 派发并通常返回 `void`。空 URL、空 interface name 或非对象 interface 会在 JS 层抛错；host 返回结构化失败时会抛出 bridge error，但缺失 native 方法/调用异常在当前 `invokeNative` 路径可能被转成 `undefined`，不能把每个同步调用都当成可靠的失败异常。`evaluateJavascript(script)` 通过 suspend callback 返回 Promise，native callback 报错时 reject。

## Modifier

`Modifier` 是链式动态 Proxy。每次调用都会返回新 proxy，并在 `__modifierOps` 中追加 `{ name, args }`；`toJSON()` 复制当前操作列表。声明的操作全集为：

- 尺寸：`fillMaxSize`、`fillMaxWidth`、`fillMaxHeight`、`width`、`height`、`requiredWidth`、`requiredHeight`、`size`、`requiredSize`、`widthIn`、`heightIn`、`sizeIn`、`requiredWidthIn`、`requiredHeightIn`、`requiredSizeIn`、`defaultMinSize`、`wrapContentWidth`、`wrapContentHeight`、`wrapContentSize`、`aspectRatio`
- 绘制：`alpha`、`rotate`、`scale`、`zIndex`、`background`、`border`、`clip`、`clipToBounds`、`shadow`
- 事件/测量：`clickable`、`combinedClickable`、`tapGestures`、`dragGestures`、`transformGestures`、`onSizeChanged`、`onGloballyPositioned`
- 系统 inset 与布局：`imePadding`、`statusBarsPadding`、`navigationBarsPadding`、`systemBarsPadding`、`safeDrawingPadding`、`weight`、`align`、`matchParentSize`

数字可通过 `.px`、`.dp`、`.fraction` 转成 `{ __unit, value }`；数组的相同属性会对每个元素逐项包装。Modifier Proxy 本身不校验方法名或参数，任何字符串属性都可以追加 `{ name, args }`；非法操作可能在宿主渲染阶段失败。每次调用返回新的 proxy，`toJSON()` 返回当前操作列表的浅副本；`then` 被显式设为 `undefined`，因此 modifier 不会被误识别为 Promise。回调参数会被转换为 action ID，手势/测量回调的 payload 由对应 renderer 生成。

## `ComposeDslContext` 方法

| 方法 | 返回与副作用 |
| --- | --- |
| `measureText(request)` | 同步调用 native 测量并 JSON parse；缺少/空结果抛错。request 含 text、maxWidth 与可选字体尺寸、高宽界限、行数和 overflow。 |
| `callTool(toolName, params?)` | `Promise<T>`；委托全局 `toolCall(toolName, params || {})`。 |
| `toolCall(...)` | 提供与全局工具调用兼容的三种重载：`(name, params?)`、`(type, name, params?)`、`({ type?, name, params? })`。 |
| `getEnv(key)` | 当前 call runtime 有环境读取器时返回 string；否则为 `undefined`。 |
| `setEnv(key, value)` | 调用 native `setEnv`，将 null/undefined value 变成空字符串，随后 resolve `void`。兼容层会吞掉 native 调用异常，不能据 Promise resolve 推断写入成功。 |
| `setEnvs(values)` | JSON 编码后调用 native `setEnvs`，随后 resolve `void`；当前 JS 层同样不检查 native 返回状态。 |
| `navigate(route, args?)` | 空 route reject；非空时调用 `navigateToRoute` 并返回已 resolve Promise，JS wrapper 不检查 native 返回状态。 |
| `showToast(message)` | 调用 `toolCall("toast", { message: String(message || "") })` 并返回工具 Promise。 |
| `reportError(error)` | 写 `console.error` 后 resolve；它不向宿主上报结构化异常。 |
| `createWebViewController(key)` | 创建当前 UI runtime 绑定的 WebView controller；见上节。 |
| `openFilePicker(options?)` | 返回 `Promise<{ cancelled, files }>`；通过 native callback 完成，bridge/native error 会 reject。picker 默认 `document`；模式还可为 image/video/media/directory/camera。 |
| `getModuleSpec()` | 返回从注册 runtime module entry 解析的 module spec。 |
| `getCurrentPackageName()`、`getCurrentToolPkgId()`、`getCurrentUiModuleId()` | 返回当前 runtime 身份；未设置时 `undefined`。 |
| `formatTemplate(template, values)` | 把所有 `{key}` 替换为对应值；null/undefined 替换为空串，其余转字符串。 |
| `listRoutes()`、`getHostRoutes()` | 同步返回 JSON 数组；缺少/非法 JSON 时返回 `[]`。前者列出当前 runtime route，后者列出宿主 route。 |
| `isPackageImported(packageName?)` | 空目标返回 `false`；省略时使用当前包名。先查 native，结果不明确时回退工具调用。 |
| `importPackage`、`removePackage`、`usePackage` | 返回 Promise；空目标立即 resolve 空字符串。先调用 native，native 无结果时回退 `import_package`、`remove_package`、`use_package` 工具。 |
| `listImportedPackages()` | 优先解析 native JSON；缺失或 JSON 无效时回退工具调用，native 空响应的当前路径会调用 fallback。 |
| `resolveToolName(request)` | 空工具名 resolve 空字符串；否则优先取 native 解析结果，native 无结果时返回 `packageName:toolName`，若 toolName 已含 `:` 则原样保留。 |
| `h(type, props?, children?)` | 创建与 `UI` 工厂相同的节点，可传动态 type 字符串。 |

`ComposeFilePickerOptions` 字段为 `picker?`、`mimeTypes?`、`allowMultiple?`、`persistPermission?`。`picker` 默认 `document`，可为 `image`、`video`、`media`、`directory` 或 `camera`。`mimeTypes` 只允许 document picker，并要求非空的 `type/subtype` 字符串；`allowMultiple` 只适用于 document/image/video/media；`persistPermission` 只适用于 document/directory，省略时这两类默认启用持久 URI 权限。非法组合会在 native picker 入口拒绝。返回每个文件的 `uri`，可选 `path`、`name`、`mimeType`、`size`；directory picker 不 staging 文件，URI 是唯一位置字段，其它选取结果通常会复制到临时清理目录后提供 `path`。

`getModuleSpec()` 返回当前注册 runtime module 的 spec；`getCurrentPackageName()`、`getCurrentToolPkgId()`、`getCurrentUiModuleId()` 未设置身份时返回 `undefined`。`setEnv`/`setEnvs`、`navigate` 和 package-manager fallback 的 Promise resolve 只表示 bridge 调用已发出，当前 JS 层不检查所有 native 写入/导航结果。`reportError` 只写 `console.error` 并 resolve，不向宿主提交结构化错误。

## API 版本

`DialogProperties`、`AlertDialogProps`、`DialogProps`，以及 `UI.AlertDialog`、`UI.Dialog` 从 ToolPkg API `1.0.1` 起提供；其他本页 Compose 声明未标注更高 `@since`。API `1.0.1` 的 Operit 最低版本见[版本规则](../09_compatibility/api_versions.md)。

## 相关页面

- [Material 3 组件](./material3_components.md)
- [Material Icons](./material_icons.md)
- `examples/types/compose-dsl.d.ts`
- `examples/types/compose-dsl.material3.generated.d.ts`
- `tools/compose_dsl/generate_compose_dsl_artifacts.py`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsComposeDslBridge.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsComposeDslRuntimeScript.kt`
- `app/src/main/java/com/ai/assistance/operit/ui/common/composedsl/ToolPkgComposeDslScreen.kt`
- `app/src/main/java/com/ai/assistance/operit/ui/common/composedsl/ToolPkgComposeDslGeneratedRegistry.kt`
- `app/src/main/java/com/ai/assistance/operit/ui/common/composedsl/ToolPkgComposeDslGeneratedRenderers.kt`