# 01 Runtime Host

## 原状

`sidebar_opencode` 已实现 Linux 运行目录、pnpm 环境、后台 Web 服务 PID、回环健康检查和日志末尾读取。

## 修改

- 建立 DSH 专用运行目录、日志、PID 与终端会话。
- 确保 Node 与 pnpm 可用后，安装 `@deepseek-ai/dsh@latest`。
- 启动 DSH Web Runtime，并由回环地址健康检查确认启动结果。

## 预期结果

DSH 在 Linux 终端中以原始 Node/Cordis Runtime 运行，Operit 只连接本机回环地址。

## 设备排查记录（2026-08-14）

手机端 `node-gyp` 的 C++ 编译已经成功，但 `node-pty@1.1.0` 的 `postinstall` 会清理 `build/Release/obj.target`。该容器上的 `COPY Release/pty.node` 是指向该目录的符号链接，因此清理后留下悬空链接，DSH 才会报告找不到 `pty.node`。

运行时由 pnpm 管理 DSH 依赖，并在冷启动或显式重启时更新至上游 `latest`。在运行 `pnpm add` 之前，它会先确保 `gcc`、`g++` 与 `make` 可用，缺失时安装 `build-essential`，以满足 `node-pty` 生命周期编译。随后定位 pnpm 自带的 `node-gyp.js` 直接执行 `rebuild --nodedir=/usr`；pnpm 的 `install` 命令会进入依赖安装和 build approval 流程，不能用来调用 `node-pty` 生命周期脚本。编译后会将 `pty.node` 解引用为普通文件；设备探针已验证 `require('./')` 能返回 `spawn`、`fork`、`createTerminal`、`open` 和 `native`。每次强制重启前会清空本次日志，避免旧的原生模块错误污染当前诊断。

[DONE] 根因定位、产物修复脚本验证和手机端重新启动均已完成；侧边栏已进入 DSH 官方 API Key 配置页，`127.0.0.1:3081` 返回 HTTP 200。

