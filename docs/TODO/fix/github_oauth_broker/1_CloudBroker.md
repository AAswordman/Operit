# 云端交换协议

## 旧实现

设备向 GitHub 直接提交 OAuth 授权码和编入 APK 的 client secret。

## 新实现

Worker 创建短期认证事务，生成 PKCE verifier 与一次性领取凭据。GitHub 回调由 Worker 接收并交换授权码，用户 token 加密暂存后重定向到浏览器回调 Host 预先注册的完成地址；Core 校验完成链接后以领取凭据 claim 一次，记录随即删除。

## 结果

- client secret 不离开 Cloudflare secret
- 授权码、token 和领取凭据不出现在浏览器回调 URL 中
- 完成通知不产生 Worker 轮询请求
- 新接口不改动旧客户端使用的 `/market/v2/auth/github`

[DONE]

[DONE]
