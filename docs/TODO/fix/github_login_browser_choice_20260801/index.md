---
fork: https://github.com/AAswordman/Operit
---

# GitHub 登录浏览器选择

## 原状

GitHub 登录直接打开应用内 WebView。此前的登录入口先让用户选择内嵌页面或外部浏览器，但旧外部流程依赖已移除的自定义 Scheme 和已废弃的设备端 OAuth secret。

## 目标

- 恢复内嵌页面与外部浏览器的选择弹窗
- 让两种方式继续使用 Broker 的 PKCE 与一次性领取凭据
- 让外部浏览器通过本机回调地址交付完成链接

## 范围

- Android GitHub 登录对话框、OAuth 协调器和本机回调监听器
- Android 中英文资源
- 本 TODO 记录
