# API 文档：`network.d.ts`

`network.d.ts` 描述 `Tools.Net` 命名空间，包含三组能力：直接 HTTP 请求、网页访问与内容提取、持久 WebView 浏览器会话。

## 运行时入口与边界

```ts
Tools.Net
```

当前 facade 通过 `http_request`、`multipart_request`、`visit_web`、`manage_cookies` 和一组 `browser_*` 工具桥接到宿主。宿主失败时 Promise rejected；成功的 HTTP/网页调用返回结果数据，浏览器工具的 TypeScript 返回值是字符串，实际内容通常是宿主拼接的多段文本。

`visit()` 是网页提取工具，不是原始 HTTP 客户端。需要精确状态码、响应头、响应体或 API JSON 时，应使用 `httpGet()`、`httpPost()` 或 `http()`。

声明中还存在 `startBrowser()`、`stopBrowser()` 以及 userscript 管理方法，但当前 `JsTools` 没有这些 facade 属性，`ToolRegistration` 也没有对应工具注册。当前可用的浏览器会话创建入口是 `browserTabs({ action: 'create' })`；这些声明方法不能按已实现 API 使用。

## HTTP 请求

### `httpGet(url, ignore_ssl?)`

```ts
httpGet(url: string, ignore_ssl?: boolean): Promise<HttpResponseData>
```

facade 调用 `http_request`，固定使用 `GET`。`ignore_ssl` 会转换为字符串布尔参数。宿主只接受 `http`/`https` URL，默认连接超时 15 秒、读取超时 20 秒、写入超时 15 秒，默认跟随重定向并启用共享 Cookie。

### `httpPost(url, body, ignore_ssl?)`

```ts
httpPost(
  url: string,
  body: string | object,
  ignore_ssl?: boolean
): Promise<HttpResponseData>
```

固定使用 `POST`。当 `body` 是对象时，facade 先 JSON 序列化；字符串按宿主默认的 JSON body 类型处理。请求失败、URL 无效、方法不支持或响应没有 body 时调用失败。

### `http(options)`

```ts
http(options: {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH' | 'HEAD' | 'OPTIONS'
  headers?: Record<string, string>
  body?: string | object
  connect_timeout?: number
  read_timeout?: number
  follow_redirects?: boolean
  ignore_ssl?: boolean
  responseType?: 'text' | 'json' | 'arraybuffer' | 'blob'
  validateStatus?: boolean
}): Promise<HttpResponseData>
```

facade 会复制 options，将对象 body 和 headers JSON 序列化，并将 `ignore_ssl` 转为字符串布尔值。宿主实际支持的 HTTP 方法还包括 `TRACE`；声明未包含该字面量。

实现还读取以下运行时参数，但它们没有写入 `network.d.ts` 的 options 类型：`write_timeout`、`use_cookies`、`proxy_host`、`proxy_port`、`custom_cookies` 和 `body_type`。`body_type` 当前支持 `json`、`form`、`text`、`xml`；`multipart` 要求改用 `uploadFile()`。

`responseType` 和 `validateStatus` 当前只会被 facade 转发，`StandardHttpTools` 没有基于它们改变解码或成功判定的逻辑。响应无论 HTTP 状态码是否为 2xx，只要网络请求和 body 读取完成，通常都会作为成功的 `HttpResponseData` 返回。

### HTTP 返回值

`HttpResponseData` 的声明字段包括：

- `url`、`statusCode`、`statusMessage`。
- `headers`、`contentType`、文本 `content`、字节数 `size`。

运行时还序列化两个声明遗漏字段：

- `contentBase64`：响应 body 的无换行 Base64。
- `cookies`：当前 URL 对应的共享 Cookie 快照。

二进制响应仍会尝试按响应 charset 解码文本，因此需要原始字节时应读取 `contentBase64` 运行时字段，而不能依赖 `content`。

### `uploadFile(options)`

```ts
uploadFile(options: {
  url: string
  method?: 'POST' | 'PUT'
  headers?: Record<string, string>
  form_data?: Record<string, string>
  ignore_ssl?: boolean
  files: {
    field_name: string
    file_path: string
    content_type?: string
    file_name?: string
  }[]
}): Promise<HttpResponseData>
```

facade 将 `files` 和 `form_data` 序列化为 JSON，再调用 `multipart_request`。宿主只接受 `POST`/`PUT`，文件路径必须是当前 Android 文件系统中的可读文件；默认 MIME 类型为 `application/octet-stream`，默认文件名取路径最后一段。

headers、超时、Cookie、代理和 `ignore_ssl` 的处理与普通 HTTP 请求相同。表单 JSON、文件 JSON、URL 或文件不存在时会失败；成功返回同样包含运行时 `contentBase64` 与 `cookies` 的 `HttpResponseData`。

## 网页访问与提取

### `visit(urlOrParams)`

```ts
visit(urlOrParams: string | {
  url?: string
  visit_key?: string
  link_number?: number
  include_image_links?: boolean
  headers?: Record<string, string>
  user_agent_preset?: string
  user_agent?: string
}): Promise<VisitWebResultData>
```

字符串参数等价于 `{ url: string }`。对象参数中的 headers 会被 facade JSON 序列化。请求来源有两种：

- 直接提供 `url`。
- 同时提供 `visit_key` 和 `link_number`，从前一次访问结果的 `links` 中按一基序号选择 URL。

当两种来源同时存在时，当前宿主优先使用 `visit_key + link_number`。`visit()` 本身只支持链接序号，不支持 `image_number`；图片链接可以通过 `include_image_links: true` 出现在结果中，再交给 `Files.download({ visit_key, image_number })` 下载。

URL 必须使用 `http` 或 `https`。`user_agent_preset` 当前识别 desktop/pc/default/windows 和 android/mobile_android/mobile，其余值回退到桌面 UA；非空 `user_agent` 覆盖 preset。headers 中无法解析或不符合格式的内容会被忽略。

宿主使用带 WebView 的网页访问流程提取标题、正文、metadata、链接和可选图片链接，并为每次成功访问生成 `visitKey`。`visitKey` 缓存供后续 `visit()` 和 `Files.download()` 使用。

正文超过 `12000` 字符时，完整结果写入应用临时目录，返回内容只保留约 `8000` 字符预览，并设置：

- `contentSavedTo`：完整文本文件路径。
- `contentTruncated: true`。
- `originalContentLength`：截断前正文长度。

需要完整内容时应对 `contentSavedTo` 使用 `Tools.Files.readPart()`、`Tools.Files.read()` 或 `Tools.Files.grep()`。

## 持久浏览器会话

当前实现使用全局活动 WebView tab，而不是每个 facade 调用携带 `sessionId`。除 `browserNavigate()`、`browserResize()` 和 `browserTabs({ action: 'create' })` 可以在没有活动 tab 时创建/附着 tab 外，大部分操作要求已有活动 tab。实现还可能要求浮窗/overlay 权限。

所有当前浏览器方法返回字符串。字符串通常包含当前 open tabs、page state、snapshot、modal state、下载或控制台信息，而不是声明中的结构化 session 对象。

### `browserNavigate(urlOrOptions)`

```ts
browserNavigate(urlOrOptions: string | {
  url: string
  headers?: Record<string, string> | string
}): Promise<string>
```

字符串形式只设置 URL；对象形式可以附带 headers。URL 为空时 facade 同步抛错。宿主等待页面导航和 document ready，默认操作等待上限约 10 秒。

### `browserNavigateBack(options?)`

```ts
browserNavigateBack(options?: Record<string, unknown>): Promise<string>
```

只接受一个 options 对象；宿主回退当前活动 tab，没有历史记录时仍返回说明文本。

### `browserClick(options)`

```ts
browserClick(options: {
  session_id?: string
  ref?: string
  selector?: string
  element?: string
  button?: 'left' | 'right' | 'middle'
  modifiers?: Array<'Alt' | 'Control' | 'ControlOrMeta' | 'Meta' | 'Shift'>
  doubleClick?: boolean
}): Promise<string>
```

facade 要求 `ref` 或 `selector` 至少提供一个；button 默认 `left`；modifiers 必须是允许的字面量数组。`ref` 必须来自当前活动 tab 最近一次 snapshot，否则宿主返回 ref 不存在错误。`session_id` 虽然存在于声明中，但当前宿主按全局活动 tab 处理，不按该字段切换会话。

### `browserClose(options?)` / `browserCloseAll(options?)`

```ts
browserClose(options?: Record<string, unknown>): Promise<string>
browserCloseAll(options?: Record<string, unknown>): Promise<string>
```

两者只接受对象或省略参数。前者关闭当前 tab，后者关闭所有 tab；没有 tab 时返回说明文本。

### `browserConsoleMessages(options?)`

```ts
browserConsoleMessages(options?: {
  level?: string
  filename?: string
}): Promise<string>
```

level 默认 `info`，宿主接受 `error`、`warning`/`warn`、`info`、`debug`。提供 filename 时，内容写入浏览器工具输出文件并在返回文本中给出路径。

### `browserNetworkRequests(options?)`

```ts
browserNetworkRequests(options?: {
  includeStatic?: boolean
  filename?: string
}): Promise<string>
```

读取当前 tab 的网络请求记录；`includeStatic` 默认 `false`。提供 filename 时保存到文本文件。

### `browserSnapshot(options?)`

```ts
browserSnapshot(options?: {
  filename?: string
  selector?: string
  depth?: number
}): Promise<string>
```

获取当前页面的文本/YAML snapshot。`depth` 必须是非负整数；`selector` 可限制快照范围；filename 会把 snapshot 写入文件。snapshot 生成的 ref 是后续 click、hover、drag、type、selectOption 和 evaluate 的依据。

### `browserTakeScreenshot(options)`

```ts
browserTakeScreenshot(options: {
  type?: string
  element?: string
  ref?: string
  fullPage?: boolean
}): Promise<string>
```

当前宿主支持 png、jpeg/jpg，默认 png。`element` 与 `ref` 必须成对提供；`fullPage` 不能和元素截图同时使用。运行时还接受声明未列出的 `filename`，可将图片保存到指定输出文件；截图尺寸和像素数受设备上限约束。

### `browserType(options)`

```ts
browserType(options: {
  ref: string
  text: string
  element?: string
  submit?: boolean
  slowly?: boolean
}): Promise<string>
```

`ref` 和 `text` 必填，ref 必须来自当前 snapshot。`slowly` 使用逐字输入，`submit` 在输入后发送 Enter。

### `browserFillForm(options)`

```ts
browserFillForm(options: {
  fields: Array<{
    name: string
    type: string
    value: string | number | boolean | object
    ref?: string
    selector?: string
  }>
}): Promise<string>
```

facade 要求非空 fields 数组；宿主要求 fields 是可解析的非空 JSON 数组，并按每项的 ref/selector 填充页面。

### `browserFileUpload(options?)`

```ts
browserFileUpload(options?: {
  paths?: string[]
}): Promise<string>
```

处理当前活动 tab 的文件选择器。省略 options 或省略 `paths` 会取消选择；传入 paths 时，facade 要求它是数组，宿主要求每个路径是绝对路径、存在且为普通文件。没有活动文件选择器、路径无效或文件不存在时失败。

### `browserHandleDialog(options)`

```ts
browserHandleDialog(options: {
  accept: boolean
  promptText?: string
}): Promise<string>
```

`accept` 必须是 boolean。没有活动对话框时失败；接受 prompt 时可传 `promptText`，拒绝时忽略该文本。

### `browserHover(options)` / `browserDrag(options)`

```ts
browserHover(options: { ref: string; element?: string }): Promise<string>

browserDrag(options: {
  startElement: string
  startRef: string
  endElement: string
  endRef: string
}): Promise<string>
```

hover 要求 snapshot ref。drag 的 facade 要求四个字段，宿主按 startRef/endRef 定位当前 snapshot 节点；任一 ref 失效都会返回页面错误。

### `browserPressKey(keyOrOptions)`

```ts
browserPressKey(keyOrOptions: string | { key: string }): Promise<string>
```

字符串形式转换为 `{ key }`；对象和字符串最终都必须提供非空 key。Enter 等可能触发页面导航，宿主会等待后续页面状态。

### `browserEvaluate(options)`

```ts
browserEvaluate(options: {
  function: string
  ref?: string
  element?: string
}): Promise<string>
```

function 必填；提供 element 描述时必须同时提供 ref。宿主可在当前页面或指定 snapshot 元素上执行 JavaScript，并返回渲染后的字符串结果。

### `browserRunCode(options)`

```ts
browserRunCode(options: { code: string }): Promise<string>
```

code 必填。宿主执行其 Playwright-style 浏览器代码，并把执行结果和页面状态作为字符串返回。

### `browserSelectOption(options)`

```ts
browserSelectOption(options: {
  ref: string
  values: string[]
  element?: string
}): Promise<string>
```

ref 必填，values 必须是非空数组；宿主按当前 snapshot ref 选择下拉选项。

### `browserResize(options)`

```ts
browserResize(options: { width: number; height: number }): Promise<string>
```

width/height 必填且必须为正整数。宿主将 viewport 约束在设备渲染预算内，单边上限约为 4096 CSS 像素，并限制 CSS/物理像素总量；没有活动 tab 时会创建 about:blank tab。

### `browserWaitFor(options)`

```ts
browserWaitFor(options: {
  time?: number
  text?: string
  textGone?: string
}): Promise<string>
```

至少提供 time、text 或 textGone 之一。`time` 的单位是秒，宿主最多等待约 30 秒；text 等待出现，textGone 等待消失。条件超时会失败。

### `browserTabs(options)`

```ts
browserTabs(options: {
  action: string
  index?: number
}): Promise<string>
```

当前支持 `list`、`create`、`select`、`close`：

- `list` 返回打开的 tab 和活动页面状态。
- `create` 创建 `about:blank` tab，并可能要求 overlay 权限。
- `select` 按零基 index 选择 tab。
- `close` 按 index 或当前活动 tab 关闭。

未知 action 或 index 越界会失败。

## 声明中当前未暴露的能力

以下声明在 `network.d.ts` 中存在，但当前 `JsTools` 没有对应的 `Tools.Net` facade 映射，`ToolRegistration` 也没有宿主工具注册。下列签名记录声明形态，不代表这些方法可在当前运行时调用。

### `startBrowser(options?)` / `stopBrowser(sessionIdOrOptions?)`

```ts
startBrowser(options?: {
  url?: string
  headers?: Record<string, string> | string
  user_agent?: string
  session_name?: string
}): Promise<string>

stopBrowser(sessionIdOrOptions?: string | {
  session_id?: string
  close_all?: boolean
}): Promise<string>
```

当前应使用 `browserTabs({ action: 'create' })` 创建 tab，并使用 `browserTabs()`、`browserClose()` 或 `browserCloseAll()` 管理 tab；这些声明方法没有对应实现。

### Userscript 声明方法

```ts
browserUserscriptList(options?: {
  include_disabled?: boolean
}): Promise<string>

browserUserscriptInstall(options: {
  url?: string
  path?: string
  source?: string
  source_url?: string
  source_display?: string
}): Promise<string>

browserUserscriptStart(options: {
  script_id?: string | number
  name?: string
  namespace?: string
  source_url?: string
}): Promise<string>

browserUserscriptStop(options: {
  script_id?: string | number
  name?: string
  namespace?: string
  source_url?: string
}): Promise<string>

browserUserscriptUninstall(options: {
  script_id?: string | number
  name?: string
  namespace?: string
  source_url?: string
}): Promise<string>
```

这五个方法都没有对应的 facade 映射或宿主工具注册，不能作为当前可调用能力使用。

## Cookie 管理

```ts
Tools.Net.cookies.get(domain: string): Promise<HttpResponseData>
Tools.Net.cookies.set(domain: string, cookies: string | Record<string, string>): Promise<HttpResponseData>
Tools.Net.cookies.clear(domain?: string): Promise<HttpResponseData>
```

宿主维护进程内按域名划分的共享 Cookie。`set` 要求 domain，cookies 可以是简单值映射或带 domain/path/expiresAt/secure/httpOnly 的对象；`clear()` 不传 domain 时清理全部 Cookie。

当前实现返回 `StringResultData` 文本，而不是声明的 `HttpResponseData`：get 返回可读 JSON 状态，set/clear 返回操作说明。因此这里存在明确的声明/运行时返回类型差异。

## 示例

### 原始 GET 与通用请求

```ts
const response = await Tools.Net.httpGet('https://example.com');
console.log(response.statusCode);
console.log(response.content);

const apiResponse = await Tools.Net.http({
  url: 'https://example.com/api',
  method: 'POST',
  headers: { Authorization: 'Bearer token' },
  body: { hello: 'world' },
  follow_redirects: true
});
console.log(apiResponse.content);
```

### 访问网页并下载图片

```ts
const page = await Tools.Net.visit({
  url: 'https://example.com',
  include_image_links: true,
  user_agent_preset: 'desktop'
});

if (page.visitKey && page.imageLinks && page.imageLinks.length > 0) {
  await Tools.Files.download({
    visit_key: page.visitKey,
    image_number: 1,
    destination: '/sdcard/downloads/page-image.bin'
  });
}
```

### 创建 tab、获取 snapshot、点击元素

```ts
await Tools.Net.browserTabs({ action: 'create' });
await Tools.Net.browserNavigate('https://example.com');
const snapshot = await Tools.Net.browserSnapshot({ depth: 4 });
console.log(snapshot);

await Tools.Net.browserClick({
  ref: 'node_12',
  element: '登录按钮'
});
```

## 相关源码与声明

- `examples/types/network.d.ts`
- `examples/types/results.d.ts`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsTools.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/ToolRegistration.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardHttpTools.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardWebVisitTool.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardBrowserSessionTools.kt`
