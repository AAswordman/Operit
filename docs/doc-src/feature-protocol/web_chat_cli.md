# web-chat 终端命令

外部 HTTP 服务改由 Operit 自带终端管理。打开终端后，命令随应用内资源自动安装，无需额外下载脚本。该服务的同一端口同时提供网页聊天、HTTP Chat API 和 A2A。

## 启动与登录

```bash
web-chat start-server --port 8080
web-chat start-server --port 8080 --login
```

不指定 `--port` 时使用已保存端口，首次使用为 `8094`。端口范围为 `1-65535`；实际监听失败会报告错误并返回非零退出码。重复启动当前端口会使用正在运行的服务，切换端口会重新监听。

普通启动输出带注释的运行状态、本机 URL、当前网络 URL、A2A 地址和 Bearer Token，不修改剪贴板。`--login` 在确认服务已开始监听后，使用 Android 默认浏览器打开以下形式的本机链接：

```text
http://127.0.0.1:8080/?token=YOUR_TOKEN
```

网页读取链接中的 Token 自动鉴权，初始化成功后保存登录状态；读取时会从地址栏移除 `token`，保留其他查询参数与锚点。已保存的旧 Token 不会覆盖本次链接明确提供的 Token。网页布局、会话列表和原有手动输入 Token 的方式继续保留。

自动打开的是当前手机的浏览器，使用本机地址即可。其他设备应使用命令输出的当前网络 URL，网络可达时可在网页输入 Token，或使用同样的 `?token=YOUR_TOKEN` 登录参数。

## 状态、停止和 Token

```bash
web-chat status
web-chat stop-server
web-chat token show
web-chat token reset
web-chat --help
web-chat --version
```

- `status` 显示真实运行状态、启用配置、地址及最近的服务错误，不打印 Token。
- `stop-server` 停止共享 HTTP 服务，并关闭已保存的自动启动状态。
- `token show` 显示当前 Token；尚未生成时创建第一个 Token，但不启动网络服务。
- `token reset` 生成并保存新 Token，旧 Token 无法通过后续请求鉴权；不自动打开浏览器或写剪贴板。
- 普通启动沿用已有 Token，退出终端会话不会将 HTTP 服务当作终端子进程停止。
- `--help` 和 `--version` 不连接 Android 服务。

启动后的启用状态会保存，App 后续启动继续使用已有自动启动逻辑。只操作 Token 或输出示例不会启用网络监听。

## 接入示例

```bash
web-chat examples
web-chat examples sync
web-chat examples async
web-chat examples health
web-chat examples intent
```

不带类型时，按同步调用、异步回调、健康检查、Intent 的顺序连续输出四项；指定类型时只输出该项。示例使用当前配置端口和真实 Token，命令只打印文本，不执行示例请求。

HTTP 示例使用本机地址。远程调用时，将 `127.0.0.1` 换成 `status` 列出的设备网络地址。异步示例中的 `YOUR_PC` 是需要替换的回调接收地址；Intent 示例面向 adb 终端，`YOUR.APP.PACKAGE` 是需要替换的回传目标包名。

完整参数语义见 [HTTP Chat API](external_http_chat.md) 与 [Intent API](external_intent_chat.md)。A2A 的 Agent Card 为 `/.well-known/agent-card.json`，JSON-RPC 为 `/a2a`，共用同一端口和 Bearer Token。

## 升级迁移

设置中的“外部 HTTP 调用”入口和整个页面已移除，不再提供独立的设置卡片。升级保留原端口、Bearer Token、启用状态以及 HTTP、A2A、Intent 接入协议；已配置的外部调用无需重新设置。

重新打开 Operit 自带终端后，可用 `web-chat status` 查看原有服务，使用 `web-chat start-server --login` 打开浏览器，或用 `web-chat stop-server` 关闭服务。原页面中的同步、异步、健康检查与 Intent 示例由 `examples` 提供。

## 退出码

- `0`：命令成功，或正常输出帮助和版本。
- `1`：本地控制通道不可用、服务操作失败、浏览器无法打开或结果协议错误。
- `2`：命令或参数无效。

命令安装和本地控制通道面向 Operit 自带终端。App 进程结束后，旧终端进程中的控制通道可能已不可用；重新在 App 中打开终端会建立当前进程的控制通道。
