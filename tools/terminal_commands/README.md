# 公共终端命令构建与校验

本目录保存应用内终端命令共用的 Rust 工具链声明与资源汇总校验。CI 构建步骤位于 [terminal-commands action](../../.github/actions/terminal-commands/action.yml)。

## 职责划分

- `tools/terminal_commands/`：固定公共编译工具链，调用全部组件的资源或 APK 校验器。
- [native_apply_patch](../native_apply_patch/README.md)：补丁引擎、CLI、安装器、许可文件、独立打包及校验。
- [web_chat_cli](../web_chat_cli/README.md)：HTTP 服务管理命令、安装器、独立打包及校验。

两个组件的 `package_assets.py` 都只处理自身资源。新增命令在自己的目录维护打包规则，再注册到公共校验入口及共享构建步骤；无需修改其他命令的打包脚本。

## 汇总校验

两个命令均准备好资源后，从仓库根目录执行：

```bash
python3 tools/terminal_commands/verify_assets.py \
  --verify --output app/build/generated/terminal-command-assets
python3 tools/terminal_commands/verify_assets.py \
  --verify-apks app/build/outputs/apk \
  --output app/build/generated/terminal-command-assets
```

Gradle 的 `verifyTerminalCommandAssets` 调用公共资源校验入口，Build 和 PR 工作流调用公共 APK 校验入口。每个组件继续验证自己的 ELF 架构、SHA-256、安装脚本、清单及许可要求；任何组件缺失、损坏或 APK 与生成目录不一致都会使汇总校验失败。

Build、Android Tests 与 PR 检查都使用共享 action。共享目录、命令源码和 action 的变更均包含在相应工作流与 PR 检查范围内。
