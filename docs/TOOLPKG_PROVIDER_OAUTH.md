# ToolPkg AI Provider OAuth

ToolPkg Provider 可声明 `auth.type = "oauth2"`，由 Operit 管理浏览器授权、回调、令牌保存和刷新。省略 `auth` 的现有 Provider 保持 API Key 行为；内置 Codex 和 GitHub 登录流程不变。

## 插件声明

```js
if (typeof ToolPkg.supportsAiProviderAuth !== "function" ||
    !ToolPkg.supportsAiProviderAuth("oauth2")) {
    throw new Error("This provider requires a newer Operit build with OAuth support");
}

ToolPkg.registerAiProvider({
    id: "my_oauth_provider",
    displayName: "My provider",
    auth: {
        type: "oauth2",
        clientId: "YOUR_REGISTERED_PUBLIC_CLIENT_ID",
        authorizationEndpoint: "https://identity.example/authorize",
        tokenEndpoint: "https://identity.example/token",
        scopes: ["offline_access"],
        redirectPort: 0,
        redirectHost: "127.0.0.1",
        redirectPath: "/oauth/callback"
    },
    listModels: { function: listModels },
    sendMessage: { function: sendMessage },
    testConnection: { function: testConnection },
    calculateInputTokens: { function: calculateInputTokens }
});
```

四个 handler 仍须从 ToolPkg 模块导出。完整的文本请求示例见 `examples/oauth_ai_provider`。

**能力检测不可省略。** 较旧的宿主会忽略未知注册字段；仅声明 `auth` 或 `api_version: "1.0.1"` 不能防止它把插件当成 API Key Provider。示例在注册前检查 `supportsAiProviderAuth`，不满足就明确拒绝加载。本变更不虚构已发布应用版本与新的 ToolPkg API 版本之间的映射。

## 支持范围

当前接口支持公共客户端的 OAuth 2.0 授权码流程，强制 S256 PKCE。宿主使用外部浏览器，不把登录页面嵌入插件 WebView。

授权端点和令牌端点必须使用 HTTPS，不能带用户名、密码、查询串或片段。附加授权参数使用 `authorizationParameters`，例如 `audience`、`prompt` 或服务方支持的 `access_type`。协议字段、凭据字段和 `response_mode` 不能被覆盖；回调采用查询参数，不接受 fragment/form_post。

回调监听器只绑定 `127.0.0.1`，默认临时端口。服务方需要允许已注册公共客户端使用对应 loopback 回调。仅当已有客户端要求固定地址时，配置 `redirectPort`、`redirectPath`，以及可选的 `redirectHost: "localhost"`。`localhost` 兼容模式仍只绑定 IPv4 loopback；其他主机名、LAN 地址、自定义 scheme 和 HTTPS App Links 均不支持。

`issuer` 可配置预期授权服务器标识。配置后，授权响应必须包含完全相同的 `iss`；缺失或不符都拒绝。未配置时仍严格绑定本次授权的端点、回调地址、随机 state 和 PKCE verifier。

不接受客户端密钥；不能把机密客户端的 `client_secret` 打包进插件。需要设备授权流程、私有令牌交换协议或其他回调方式的供应商，需要另外扩展宿主协议。本功能没有预装供应商的 client ID，也不表示任意订阅账号已经能够接入。

## 登录与使用

在模型设置中选择该插件 Provider，填写其 HTTPS API Endpoint，然后点击浏览器登录。成功后可获取模型列表、测试连接和聊天；OAuth 分支不显示 API Key 输入框。过期且可刷新的令牌在请求前刷新，也提供手动刷新和退出入口。

网络类 Provider handler 收到：

```ts
const { config, auth } = event.eventPayload;
if (!auth) throw new Error("Sign in in model settings first");
const headers = { Authorization: `${auth.tokenType} ${auth.accessToken}` };
```

`auth` 包含 `type`、`accessToken`、`tokenType`。不会把 OAuth token 写入持久化的 `ModelConfigData.apiKey`，OAuth handler 中的 `config.apiKey` 为空。刷新令牌、授权码和 PKCE verifier 不会通过这个接口传给插件。

插件负责自身 AI 请求协议，不能只因示例使用 OpenAI-compatible JSON 就假定所有 Provider 必须兼容该协议。插件必须自行处理流式输出、工具调用、多模态和计量等供应商差异。本示例仅演示文本/非流式请求，不是完整的生产供应商适配器。

`calculateInputTokens` 是本地估算，不注入 `auth`，不会为了估算触发登录或网络刷新。没有 `expires_in` 的 Bearer 令牌可使用，但宿主无法预知其过期时间；服务方拒绝后可手动刷新或重新登录。不会在收到 401 后自动重放聊天请求。

## 凭据和会话边界

凭据按 ToolPkg 包、Provider ID、模型配置 ID、完整 API Endpoint 和认证配置绑定。切换模型名称不要求重新登录；更改绑定项会使用新的凭据命名空间。这样可以在不同模型配置中使用不同账号，也避免把一个端点的已保存令牌自动交给另一个端点。

令牌保存在 `noBackupFilesDir/toolpkg_oauth`，使用 Android Keystore 的 AES-256-GCM 密钥加密，命名空间作为附加认证数据。写入通过 `AtomicFile` 完成；Android Auto Backup 不包含此目录。没有明文降级路径。设备密钥缺失或加密数据不可读时，旧凭据不再使用，需要重新授权。

同一凭据的并发请求串行协调刷新。退出登录和更新登录会使旧会话失效，延迟返回的刷新结果不能把已退出的会话重新保存。令牌响应省略 refresh_token 时保留原值；返回新值时保存轮换后的值。invalid_grant 只清理对应的旧会话，不误删同时完成的新登录。

退出登录删除**当前绑定命名空间**的本地凭据，不代表服务端撤销授权，也不能使已经发出去的 token 立即失效。模型配置删除、插件卸载以及历史命名空间的批量清理暂未接入本变更；更改认证配置前可先退出原会话。

这不是不可信插件的沙箱。被授权的插件会获得 access token，能够发送自己的网络请求；具备其他宿主工具权限的插件也不能被视为受到本接口隔离。只应授权可信插件，不能记录、回显、持久化或跨配置缓存 handler 收到的认证对象。

授权等待上限为五分钟。取消、页面销毁或进程退出会终止当前授权；不会在后台自动恢复未完成的浏览器登录。

## 验证

不依赖 Android SDK 的核心回归测试：

```sh
bash tools/toolpkg_oauth/run_core_tests.sh
node --test examples/oauth_ai_provider/tests/provider.test.js
```

前者要求 Kotlin CLI 1.9+、JDK，以及 `kotlinx-coroutines-core` JVM jar；可通过 `COROUTINES_JAR` 显式指定。JUnit 包装器复用同一组核心测试，可在项目 Android 构建环境运行：

```sh
./gradlew :app:testDebugUnitTest --tests '*ProviderOAuthTest'
```

核心测试覆盖 PKCE、参数校验、回调身份与单次消费、过期、取消、真实 loopback socket、令牌轮换、命名空间和并发竞态。示例测试使用模拟 HTTP，不代表真实供应商登录通过。Android Keystore、Compose 页面和真实账号端到端授权仍须在设备或相应测试环境验收。

协议参考：RFC 7636（PKCE）、RFC 8252（原生应用 OAuth）、RFC 9700（OAuth 安全实践）、RFC 9207（授权响应 issuer 标识）。
