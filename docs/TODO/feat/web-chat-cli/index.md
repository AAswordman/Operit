---
fork: https://github.com/3316891527/Operit
branch: feat/web-chat-cli
base: dev
---

# HTTP 服务终端管理与浏览器自动登录

用户决定保留当前网页聊天布局，将外部 HTTP 服务的管理迁到 Operit 自带终端，删除设置入口和整个旧页面。当前服务同时承载 HTTP Chat、Web Chat 与 A2A；已有端口、Token、启用状态和外部请求接口必须继续使用。

## 目标与范围

- Kotlin 提供仅供同一应用 UID 调用的本地控制通道，复用前台服务及现有配置。
- 新建独立 Rust 命令包，提供 `web-chat start-server [--port PORT] [--login]`、`stop-server`、`status`、`token show`、`token reset` 和 `examples [sync|async|health|intent]`。
- 未指定示例类型时连续打印四类示例，始终只展示、不执行请求。
- 普通启动输出带说明的 URL 和 Token，不写剪贴板；`--login` 在确认监听成功后打开 Android 默认浏览器，URL 携带当前 Token。
- 网页仅增加 URL Token 的消费和自动鉴权，保留已有手动登录与布局；消费后从地址栏移除 Token 参数。
- 删除外部 HTTP 设置卡片、页面路由、页面文件及专用多语言字符串，不保留简化服务卡片。
- 补充迁移说明、命令文档和完整回归清单；不发布 Release。

## 执行步骤

1. [命令与本地服务控制](01-command-and-control.md)
2. [登录链接与移除设置页](02-login-and-settings-removal.md)
3. [打包、文档与验证](03-delivery-and-verification.md)

## 兼容性

服务已有对外发布记录，见同分类 `a2a_server_20260807/index.md`。本次替换管理入口，保留 HTTP、A2A 与 Intent 的请求和响应协议、监听方式及持久化配置。升级后继续沿用原端口、Token 和启用状态，新命令不要求用户重新配置已有接入。

## 当前状态

命令、控制通道、登录、页面清理和文档已实现。构建、单元测试和设备验证均尚未执行；提交前静态检查与工作流待派发。迁移文案见 [更新日志](release-notes.md)。
