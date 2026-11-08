# Android 登录替换

## 旧实现

GitHub 登录界面提供内嵌 WebView 和外部浏览器两条路径，应用接收 `operit://github-oauth-callback` 后直接向 GitHub 交换 token。

## 新实现

Android 通过通用浏览器回调组件注册完成地址、展示 Worker 返回的授权页，并拦截 `https://api.operit.app/oauth/github/complete` 的最终导航。协调器验证当前事务后 claim 一次并保存 GitHub token 与用户信息。

用户明确退出 GitHub 登录时，应用会清除自身 WebView 的 Cookie 和 WebStorage，再删除本地认证信息。下一次登录不会静默复用之前的 GitHub Web 会话；这不会影响系统浏览器或 Chrome 的 GitHub 登录状态。

## 结果

- 删除旧自定义 scheme、外部浏览器回调和 Activity Intent 接管
- 浏览器回调组件不包含 GitHub 协议、token 或领取凭据
- 删除 Android 的 client ID 与 client secret BuildConfig 字段
- 认证版本升级，旧 APK 数据不会被新版认证代码继续使用

[DONE]

[DONE]
