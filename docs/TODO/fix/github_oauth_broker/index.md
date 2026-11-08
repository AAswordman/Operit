---
fork: https://github.com/AAswordman/Operit
---

# GitHub OAuth Broker

## 背景

Android 客户端曾将 GitHub OAuth client secret 写入 BuildConfig，并在设备上直接交换授权码。该 secret 随已发布 APK 分发，不能继续作为可信凭据。

## 目标

- 新版 Android 只通过 `api.operit.app` 的受保护 Worker 完成 GitHub 授权码交换
- 新 APK 不再包含 client ID、client secret 或 `operit://` OAuth 回调
- 已发布的旧 APK 继续使用原 OAuth App，直至发布公告规定的迁移截止日
- 新 OAuth App 的 client secret 只存在于 Cloudflare Worker secret

## 范围

- 后端：`D:\Code\prog\assistance_web\workers\market`
- Android：GitHub 登录协调器、通用浏览器回调组件、认证偏好和 Android Manifest
- Operit 2：Rust CLI 与 Flutter 市场登录
- 开发：直接在两个仓库的 `main` 工作区修改，部署顺序为后端先于 Android

## 状态

- 云端密钥已配置，新 OAuth App 已创建
- D1 迁移已应用
- Worker 部署待后端现有未提交市场改动整理后执行
- Operit 2 客户端迁移进行中，CLI 包现有编译问题与本协议无关
