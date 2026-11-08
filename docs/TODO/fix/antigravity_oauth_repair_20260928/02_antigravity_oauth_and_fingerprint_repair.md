# Antigravity OAuth 修复与指纹伪装

## 目标

彻底解决登录后无法获取额度及请求 403 的根因，补充指纹伪装，杜绝任何兜底伪造代码。

## 详细改造

- [DONE] OAuth 协议：
  - 补充 `https://www.googleapis.com/auth/cloud-platform` Scope。
  - 将 UserInfo 端点更新为 `v2`。
  - OAuth Token 交换与刷新请求发送 `User-Agent: Go-http-client/2.0` 与 `Host: oauth2.googleapis.com`。
- [DONE] GCP 项目发现与激活：
  - 修正字段提取兼容 `cloudaicompanionProject`（支持字符串或包含 `id` 的对象）。
  - 若未激活，调用 `daily-cloudcode-pa.googleapis.com/v1internal:onboardUser` 轮询最多 5 次完成激活。
  - 彻底删除 `fallbackProjectId(email)`，若项目仍未发现直接抛出明确异常。
- [DONE] 指纹伪装：
  - 全流程 HTTP 客户端强制使用 HTTP/1.1，匹配原生 Antigravity 单连接模型。
  - `User-Agent` 统一采用 `antigravity/hub/2.9.1 darwin/arm64`（onboard 时追加 node client 标识）。
  - 请求时移除多余的 `X-Goog-Api-Client: google-cloud-sdk vscode_cloudshelleditor/0.1` 与 `Client-Metadata` 头。
  - 请求体信封：补齐 `userAgent: antigravity`，去除 `requestId` 中的 `operit-` 前缀，基于首个用户消息哈希派生稳定的 `request.sessionId`，清除 `safetySettings`。
  - Claude 模型自动声明 `request.toolConfig.functionCallingConfig.mode = VALIDATED`。
- [DONE] 额度查询与非流式解包：
  - `retrieveUserQuotaSummary` 请求体补全 `{"project": projectId}`。
  - `GeminiProvider.processNonStreamingResponse` 增加 `unwrapStreamingPayload` 调用。

[DONE]
