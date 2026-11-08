# 03 Delivery

## 原状

`sync_example_packages.py` 会编译 `examples/` 内含 `manifest.json` 的 TypeScript ToolPkg 并生成 `.toolpkg`。

## 修改

- 添加示例 manifest、TypeScript 配置与运行时文件。
- 让 Runtime 目录和网络监听规则在 package 描述中明确可见。

## 预期结果

示例可进入既有 ToolPkg 打包链路，且没有改动 OpenCode 容器。

## 当前验证

- 已使用调试安装广播重新安装 `com.operit.sidebar_deepseek_harness`，没有卸载现有容器。
- 已在手机 Linux 容器中确认 `node-pty` 的 ARM64 编译产物可以被 Node 24 加载。
- 已用修复后的 `dist` 再次安装；侧边栏进入 DSH 官方 API Key 配置页，`dsh web` 监听 `127.0.0.1:3081`。

