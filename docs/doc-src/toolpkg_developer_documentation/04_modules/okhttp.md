# API 文档：`okhttp.d.ts`

`okhttp.d.ts` 描述的是由 `app/src/main/assets/js/OkHttp3.js` 提供的全局 HTTP 客户端 facade。启动脚本会自动加载该资产，并暴露 `OkHttpClientBuilder`、`OkHttpClient`、`RequestBuilder` 和 `OkHttp`；脚本不需要 `import`。

这个 facade 最终通过 `toolCall` 调用宿主的 `http_request` 工具。它提供链式构建、同步拦截器、请求体快捷方法和流式回调，但不是直接暴露 Kotlin `okhttp3.OkHttpClient` 实例。

## 声明入口

### `OkHttpConfig`

这是客户端配置的结构描述，字段为：

- `timeouts.connect`、`timeouts.read`、`timeouts.write`：数字，声明单位为毫秒。
- `followRedirects`：是否跟随重定向。
- `retryOnConnectionFailure`：是否在连接失败时重试。
- `interceptors`：同步请求拦截器数组。

当前 facade 通过 `OkHttp.newBuilder()` 设置这些字段，不接受一个 `OkHttpConfig` 对象作为公开构造参数。

### `HttpRequest`

```ts
interface HttpRequest {
  url: string;
  method: string;
  headers: Record<string, string>;
  body?: any;
  bodyType?: 'text' | 'json' | 'form' | 'multipart';
  formParams?: Record<string, string>;
  multipartParams?: Array<{
    name: string;
    value: string;
    contentType?: string;
  }>;
  execute(options?: OkHttpExecuteOptions): Promise<OkHttpResponse>;
}
```

普通的 `HttpRequest` 由 `OkHttpClient.newRequest().build()` 创建。`execute()` 使用创建它的客户端配置和拦截器；它不是本地执行器，仍会异步调用宿主工具。

### `HttpStreamEvent` 与 `OkHttpExecuteOptions`

```ts
interface HttpStreamEvent {
  type: 'response_started' | 'chunk';
  url: string;
  statusCode?: number;
  statusMessage?: string;
  headers?: Record<string, string>;
  contentType?: string;
  chunk?: string;
  chunkIndex?: number;
  receivedBytes?: number;
}

interface OkHttpExecuteOptions {
  onIntermediateResult?: (event: HttpStreamEvent) => void;
}
```

`response_started` 事件包含状态码、状态文本、响应头和内容类型；`chunk` 事件包含当前文本块、从 `0` 开始的 `chunkIndex` 和累计字节数。最终的完整响应不会作为中间事件传入，而是作为 Promise 的最终值返回。

### `OkHttpResponse`

```ts
interface OkHttpResponse {
  raw: HttpResponseData;
  statusCode: number;
  statusMessage: string;
  headers: Record<string, string>;
  content: string;
  contentType: string;
  size: number;
  json(): any;
  text(): string;
  bodyAsBase64(): string;
  isSuccessful(): boolean;
}
```

当前宿主的 `HttpResponseData` 实际还可能包含 `contentBase64` 和 `cookies`，但这两个字段没有出现在 `results.d.ts` 的声明中。facade 将原始值放入 `raw`，并把 `contentBase64` 暂存为内部的 `base64Content`，调用方应使用声明的方法 `bodyAsBase64()`，不要依赖这个未声明的属性名。

## 客户端构建器

### `OkHttp.newBuilder()` 与 `OkHttpClient.newBuilder()`

两者都返回新的 `OkHttpClientBuilder`，默认配置为：

- 连接超时 `10000` 毫秒。
- 读取超时 `30000` 毫秒。
- 写入超时 `30000` 毫秒。
- 跟随重定向。
- 重试配置初始为 `true`。
- 没有拦截器。

```ts
const builder = OkHttp.newBuilder();
// 静态方法也可用：const builder = OkHttpClient.newBuilder();
```

### `OkHttpClientBuilder` 方法

| 方法 | 实际行为 |
| --- | --- |
| `connectTimeout(timeout)` | 保存连接超时；执行请求时转成至少 1 秒的整数秒。 |
| `readTimeout(timeout)` | 保存读取超时；执行请求时转成至少 1 秒的整数秒。 |
| `writeTimeout(timeout)` | 保存写入超时；执行请求时转成至少 1 秒的整数秒。 |
| `followRedirects(follow)` | 控制宿主 `http_request` 的 `follow_redirects` 参数。 |
| `retryOnConnectionFailure(retry)` | 保存配置值，但当前 `OkHttp3.js` 没有把它传给宿主；当前实现不产生重试配置效果。 |
| `addInterceptor(interceptor)` | 只有参数是函数时才加入拦截器数组；始终返回当前 builder。 |
| `build()` | 用当前配置创建 `OkHttpClient`。 |

超时换算使用 `Math.ceil(timeout / 1000)`，并将小于 1 的结果钳制为 `1`。因此声明虽然使用毫秒，宿主工具实际接收的是秒。拦截器按添加顺序同步执行；可以修改传入请求，也可以返回另一个请求。异步拦截器不受支持。

## `OkHttpClient`

### `newRequest()`

返回绑定到当前客户端的 `RequestBuilder`。通过该方法创建的请求会使用当前客户端的超时、重定向设置和拦截器。

### `execute(request, options?)`

执行一个 `HttpRequest`，返回 `Promise<OkHttpResponse>`。执行过程如下：

1. 依次调用当前客户端的同步拦截器。
2. 将请求头序列化为 JSON 字符串。
3. 将 `url`、大写后的 `method`、`body`、`body_type`、重定向选项和三个超时参数传给 `http_request`。
4. 当 `options.onIntermediateResult` 是函数时，将 `stream: true` 和回调传给 `toolCall`。
5. 将宿主返回的 `HttpResponseData` 包装为 `OkHttpResponse`。

`http_request` 接受 `GET`、`POST`、`PUT`、`DELETE`、`HEAD`、`OPTIONS`、`PATCH` 和 `TRACE`。URL 必须是非空的 `http` 或 `https` URL。HTTP 4xx/5xx 响应仍然是正常的 HTTP 响应，Promise 会 resolve；使用 `isSuccessful()` 判断是否为 2xx。

### 快捷方法

```ts
get(url: string, headers?: Record<string, string>): Promise<OkHttpResponse>;
post(url: string, body: any, headers?: Record<string, string>): Promise<OkHttpResponse>;
put(url: string, body: any, headers?: Record<string, string>): Promise<OkHttpResponse>;
delete(url: string, headers?: Record<string, string>): Promise<OkHttpResponse>;
```

这四个方法都通过 `newRequest()` 构建请求。`post()` 和 `put()` 使用 `body(body)` 的默认类型 `text`，不会自动把对象声明为 JSON；发送 JSON 时应使用 `jsonBody()`。快捷方法不会暴露流式回调参数。

### `streamExecute(request, onIntermediateResult)`

等价于：

```ts
client.execute(request, { onIntermediateResult });
```

它返回最终的 `Promise<OkHttpResponse>`，并在请求期间调用回调。当前宿主通常依次产生一个 `response_started`、若干 `chunk`，然后产生最终的完整结果。回调抛出的异常会使调用 Promise reject。

## `RequestBuilder`

以下方法都修改当前 builder 并返回 `this`，所以可以链式调用。

| 方法 | 行为 |
| --- | --- |
| `url(url)` | 设置 URL，不在 JavaScript facade 层校验。 |
| `method(method)` | 将方法转换为大写后保存；宿主仍会校验是否支持该方法。 |
| `header(name, value)` | 设置或覆盖一个请求头。 |
| `headers(headers)` | 用对象展开合并请求头；后面的同名字段覆盖前面的值。 |
| `body(body, type?)` | 保存请求体和类型，默认类型为 `text`。 |
| `jsonBody(data)` | 对 `data` 执行 `JSON.stringify`，设置类型为 `json`，并设置 `Content-Type: application/json`。 |
| `formParam(name, value)` | 保存表单字段，设置类型为 `form`，并设置 `Content-Type: application/x-www-form-urlencoded`。 |
| `multipartParam(name, value, contentType?)` | 保存 multipart 字段，设置类型为 `multipart`。 |
| `build()` | 生成可执行的 `HttpRequest`。 |

`body()` 的类型声明只有 `text`、`json`、`form` 和 `multipart`，但宿主 `http_request` 还实现了 `xml`。普通请求中的 `multipart` 会被宿主拒绝，因为 multipart 有单独的工具入口。

### `build()` 的实际限制

普通请求的 `build()` 结果包含声明中的请求字段和 `execute()` 方法。`formParam()` 在 JavaScript 层会先编码为类似 `name=value` 的字符串；当前宿主的 `body_type=form` 却把 body 当作 JSON 对象字符串解析，因此该组合在当前实现中通常会失败。需要表单请求时，应直接使用 `Tools.Net.http()` 并传入宿主要求的 `body_type` 与 body 格式。

`multipartParam()` 会返回一个只有 `execute()` 方法的特殊对象，并调用 `multipart_request`。当前 wrapper 将字段放在名为 `fields` 的参数中，但宿主实现读取的是 `form_data` 和 `files`；同时 wrapper 没有把字段转换为宿主需要的文件描述。因此该方法目前不能可靠地提交声明所描述的 multipart 字段或文件。文件上传应使用 `Tools.Net.uploadFile()`，其参数与宿主的 `form_data`/`files` 契约一致。

## `OkHttpResponse` 方法

### `json()`

对 `content` 调用 `JSON.parse` 并返回解析结果。内容不是有效 JSON 时抛出 `Error`，错误消息以 `Failed to parse response as JSON:` 开头。

### `text()`

直接返回 `content`。宿主会根据响应的 charset 解码响应字节；没有 charset 时使用 UTF-8。

### `bodyAsBase64()`

返回宿主 `HttpResponseData.contentBase64` 的值。该字段适合处理二进制响应；声明中的 `size` 是响应字节数。

### `isSuccessful()`

仅当 `statusCode >= 200 && statusCode < 300` 时返回 `true`。它不等同于 `toolCall` 是否成功：例如 404 会得到 `OkHttpResponse`，但 `isSuccessful()` 返回 `false`。

## 错误与异步语义

- 所有网络执行方法返回 Promise；宿主工具返回 `success: false` 时，底层 `toolCall` 会 reject，而不是返回一个失败的 `OkHttpResponse`。
- 空 URL、非法 URL、不支持的 HTTP 方法、无法解析的 body、连接异常、空响应体和 multipart 参数错误都会走 reject 路径。
- 拦截器抛出的同步异常、`jsonBody()` 的 `JSON.stringify` 异常以及 `Response.json()` 的解析异常由 JavaScript 直接抛出。
- 状态码本身不触发 reject；需要使用 `statusCode` 或 `isSuccessful()` 检查 HTTP 结果。
- `headers` 在宿主中按 JSON 对象解析；当前宿主返回的 headers 是 `Record<string, string>`，wrapper 对对象会直接保留，对字符串才执行按行解析。

## 示例

### JSON POST

```ts
const client = OkHttp.newBuilder()
  .connectTimeout(10_000)
  .readTimeout(20_000)
  .addInterceptor((request) => {
    request.headers['X-Trace-Id'] = String(Date.now());
    return request;
  })
  .build();

const request = client
  .newRequest()
  .url('https://example.com/api')
  .method('POST')
  .jsonBody({ hello: 'world' })
  .build();

const response = await client.execute(request);
if (response.isSuccessful()) {
  const data = response.json();
  console.log(data);
}
```

### 流式读取

```ts
const request = OkHttp.newClient()
  .newRequest()
  .url('https://example.com/stream')
  .build();

const response = await request.execute({
  onIntermediateResult(event) {
    if (event.type === 'chunk') {
      console.log(event.chunk);
    }
  }
});

console.log(response.text());
```

## 相关实现

- `examples/types/okhttp.d.ts`
- `app/src/main/assets/js/OkHttp3.js`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsAssetLoader.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsLibraries.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardHttpTools.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/ToolRegistration.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/ToolResultDataClasses.kt`
- [网络模块参考](./network.md)
- [结果类型参考](./results.md)
