# HTTP 服务管理迁移说明

外部 HTTP 服务改由 Operit 自带终端管理，设置中的“外部 HTTP 调用”入口及整个页面移除。升级会保留原来的端口、Bearer Token 和启用状态，已有 HTTP、A2A 与 Intent 调用继续使用原协议。

打开 App 内的终端，执行：

```bash
web-chat start-server --port 8080 --login
```

服务开始监听后，会直接请求 Android 默认浏览器打开携带 Token 的本机网页并自动登录。普通启动不带 `--login` 时输出带说明的 URL、当前网络地址和 Token，不自动复制到剪贴板。网页聊天布局保持原样，只增加链接登录。

日常管理命令：

```bash
web-chat status
web-chat stop-server
web-chat token show
web-chat token reset
```

普通启动沿用当前 Token。只有显式重置才更换 Token；更换后旧 Token 不能通过后续请求鉴权。停止命令同时关闭自动启动配置。

原页面的接入示例迁入命令：

```bash
web-chat examples
web-chat examples sync
web-chat examples async
web-chat examples health
web-chat examples intent
```

`examples` 默认连续显示四类示例；指定类型只显示一项。输出使用当前端口和 Token，示例仅显示、不自动执行。异步回调地址和 Intent 回传包名按输出说明替换。

查看完整帮助使用 `web-chat --help`。本说明记录已实现的迁移行为，构建和设备验证结果单独记录，不表示该功能已经正式发布。
