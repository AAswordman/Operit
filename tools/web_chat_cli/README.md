# web-chat 原生终端命令

这个独立 Rust 包提供命令解析和本地 IPC 客户端。服务启停、Token 管理、示例生成和浏览器跳转由 Android 应用的 Kotlin 控制器执行，共用已经发布的外部 HTTP 服务。

用户命令、登录链接和迁移说明见[命令文档](../../docs/doc-src/feature-protocol/web_chat_cli.md)。

## 构建与打包

从仓库根目录执行，工具链与现有 `apply_patch` 使用同一版本和 ARM64 GNU 目标：

```bash
export CARGO_TARGET_AARCH64_UNKNOWN_LINUX_GNU_LINKER=aarch64-linux-gnu-gcc
cargo +1.95.0 build --manifest-path tools/web_chat_cli/Cargo.toml \
  --release --target aarch64-unknown-linux-gnu --locked
python3 tools/web_chat_cli/package_assets.py \
  --binary tools/web_chat_cli/target/aarch64-unknown-linux-gnu/release/web-chat \
  --output app/build/generated/terminal-command-assets
```

交叉编译需要 `gcc-aarch64-linux-gnu` 与 `libc6-dev-arm64-cross`，Rust 目标为 `aarch64-unknown-linux-gnu`。资源使用独立的 `web_chat_command` 目录和 SHA-256 清单，APK 内的内容也必须与本轮生成目录一致。

现有本地 composite action 同时构建两个命令。Android Tests 与 PR Android JVM tests 会运行参数及 IPC 回归，并通过 QEMU 执行 ARM64 命令、安装、并发安装、引号路径和 APK 完整性回归。本轮本地未执行编译或测试。

## 本地控制协议

安装器将包装命令写入 `/usr/local/bin/web-chat`，并记录 Android 宿主 UID 对应的抽象 Unix socket 名称。内部二进制保存在同一目录的内容寻址文件，包装命令原子更新后清理旧副本。

一次连接只传递一条换行结尾的 JSON 请求和一条换行结尾的 JSON 响应。请求版本为 `1`，响应包含 `version`、`success` 和 `output`。请求上限为 16 KiB，响应上限为 64 KiB。参数错误不连接 App；断连、超时、截断和协议版本错误均返回非零状态。

控制器使用 Android peer credentials，仅允许应用 UID 与有应用沙箱访问权的真实 root。普通 proot 的虚拟 root 不参与 socket 寻址，真实 root chroot 使用相同安装入口。该控制通道不依赖网络 HTTP 服务已启用。
