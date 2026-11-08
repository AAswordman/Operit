---
fork: https://github.com/AAswordman/Operit
---

# Operit 2 客户端迁移

## 旧实现

Rust CLI 使用 GitHub Device Flow，并要求操作者设置 GitHub OAuth client ID 环境变量。Flutter 市场页要求用户创建并粘贴 GitHub Token。

## 新实现

Rust CLI 与 Flutter 都调用 Core 的类型化 `GitHubOAuthBrokerService`。Flutter 使用生成的 Dart proxy，CLI 使用生成的 Rust proxy；两者都不传递市场认证命令字符串、不解析命令 stdout，也不手写 CoreLink 请求。应用自己准备完成地址，Core 用该地址调用 Worker 的 `/oauth/github/start` 并私有保存 delivery credential；应用展示授权页并交回完成链接。CLI 使用临时 loopback 并在终端打印授权链接；Flutter 市场登录对话框拦截其 WebView 的完成导航。Core 只在收到与当前事务、目标地址都匹配的完成链接后 claim 一次并保存 Worker 返回的 GitHub token。

## 协议责任

- Worker 持有 OAuth client secret、生成 PKCE 和处理 GitHub 回调
- 客户端不包含 client ID 或 client secret
- Flutter 市场页不持有 OAuth HTTP、平台 Intent、EventChannel 或 loopback receiver；它只管理自己的可见 WebView 导航
- 客户端不向 Worker 反复查询授权状态
- Rust 解析和校验 Broker 响应，并以单元测试固定协议契约

[DONE]
