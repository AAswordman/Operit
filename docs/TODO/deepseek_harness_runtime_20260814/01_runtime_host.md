# 01 Runtime Host

## 原状

`sidebar_opencode` 已实现 Linux 运行目录、pnpm 环境、后台 Web 服务 PID、回环健康检查和日志末尾读取。

## 修改

- 建立 DSH 专用运行目录、日志、PID 与终端会话。
- 确保 Node 与 pnpm 可用后，固定安装 `@deepseek-ai/dsh@0.1.0-rc.6`。
- 启动 DSH Web Runtime，并由回环地址健康检查确认启动结果。

## 预期结果

DSH 在 Linux 终端中以原始 Node/Cordis Runtime 运行，Operit 只连接本机回环地址。

