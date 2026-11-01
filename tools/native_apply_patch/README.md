# Operit 终端 apply_patch

这里提供派生自 OpenAI Codex 的独立 `apply_patch` 命令，发布目标为 Operit 本地 Ubuntu 的 `aarch64-unknown-linux-gnu`。它通过 APK 资源更新安装到 `/usr/local/bin/apply_patch`，无需更换 Ubuntu 安装归档或删除已有环境。

## 使用

打开更新后的本地终端即可使用，也适用于工具发起的隐藏执行。补丁可作为一个完整参数传入，或从标准输入读取。推荐使用带引号的 heredoc，避免 Bash 展开补丁中的美元符号、反引号和反斜杠。

```bash
apply_patch <<'PATCH'
*** Begin Patch
*** Add File: example.txt
+hello
*** Update File: example.txt
@@
-hello
+world
*** End Patch
PATCH
```

相对路径以命令当前目录为基准，也接受绝对路径。输出保留上游的 `Success. Updated the following files:` 及 `A`、`M`、`D` 摘要。退出码为 `0` 成功、`1` 补丁或文件操作失败、`2` 用法错误。

## 扩展选项

```bash
apply_patch --version
apply_patch --help
apply_patch --check --file change.patch
apply_patch -c -f change.patch --json
apply_patch --file change.patch --json
apply_patch --no-overwrite --file change.patch
apply_patch --diff --no-overwrite --file change.patch
apply_patch --quiet --file change.patch
```

版本读取当前移植包的 `0.1.0`，支持 `-v/-V`；帮助支持 `-h`。`--check/-c/--dry-run` 使用同一补丁引擎在内存里按顺序检查，不写入目标文件或目录。`--file/-f` 读取 UTF-8 补丁文件，`-f -` 读取 stdin，补丁内相对路径仍以当前目录为准。`--json` 对成功、错误、帮助和版本均输出单个 JSON 对象，不混入普通文本摘要。

`--no-overwrite` 禁止新增文件和移动目标覆盖已有路径，包括符号链接；普通更新以及显式删除后重建仍允许。`--diff/-D` 在内存中按操作顺序预览变化，不写盘，终端自动显示颜色，重定向或 `NO_COLOR` 禁用颜色。`--diff --json` 把无颜色差异放入 `diff` 字段。`--quiet/-q` 隐藏成功摘要，保留错误和明确请求的差异、JSON、帮助与版本输出。完整自定义规则和示例也直接列在 `--help` 中。

检查不能保证实际执行时的写权限、磁盘空间或无并发修改。参数组合、JSON 字段和检查语义见[命令行契约](../../docs/TODO/feat/terminal-apply-patch/03-cli-options.md)。

## 保留的行为

- 支持新增、修改、删除文件和修改时移动路径，保留多段更新及 `*** End of File` 标记
- 上下文搜索依次使用精确、忽略尾空白、忽略首尾空白和部分 Unicode 标点归一化匹配
- 保留目标文件已有的 LF、CRLF、CR 及混合换行处理，以及更新时补齐末尾换行的规则
- 独立命令默认跟随符号链接，保留原版文件覆盖和移动目标覆盖行为
- 多文件补丁逐项执行，后续失败时先前写入仍可能保留；失败信息和退出码沿用原版
- 文件读取保留原版 512 MiB 上限、普通文件检查及 UTF-8 错误处理

## 安装与升级

主应用在创建终端实例前注册本地会话初始化钩子。终端核心在普通交互会话和隐藏执行共用的 Ubuntu 登录流程中运行安装脚本，成功后才输出登录就绪标记。SSH 会话使用远端环境，不会把本地资源装到远端。

Android 侧按清单校验二进制及安装脚本，并在应用文件目录原子更新缓存。Ubuntu 侧再次校验二进制，使用 `flock` 串行化，在目标目录暂存、校验、设定权限后原子替换。相同版本保留已安装文件；损坏资源会阻止本次登录继续执行任务。真实 chroot 安装会恢复应用 UID/GID 归属，使随后 proot 会话仍可更新命令。

升级只管理 `apply_patch` 命令及其安装锁，不清理工作目录、软件包或 Ubuntu 文件。重新安装 Ubuntu 后，下次登录会重新安装命令。应用升级后新开的本地会话使用更新的命令；现有会话状态不迁移。

## 源码组成

```text
tools/native_apply_patch/
	src/
	compat/fs/
	vendor/absolute-path/
	vendor/path-uri/
	tests/
		fixtures/scenarios/
	Cargo.toml
	Cargo.lock
	rust-toolchain.toml
	install.sh
	package_assets.py
	UPSTREAM.json
	LICENSE
	NOTICE
```

[UPSTREAM.json](UPSTREAM.json) 记录来源快照与逐文件 SHA-256。提供的归档没有 Git 提交元数据，因此不填未经核实的上游提交号。初次移植对 26 个 Rust 来源文件完成静态代码与字面量对照。命令行扩展调整库选项和独立入口，并为新增/移动目标提供可选的独占创建；Unix 文件描述符写入增加可选 `O_EXCL`。另修正原生路径拼接对制表符、换行和回车的字节保留，共 5 个来源文件显式记录适配，其余 21 个来源文件保持原哈希；25 个场景 fixture 保持字节一致。默认补丁行为保持来源语义。

`compat/fs` 仅实现补丁引擎需要的本地文件系统方法，沿用原版有界读取和 Unix 文件描述符操作。`invocation.rs` 用于在 Codex 执行框架中识别外层 shell 调用，独立 CLI 已由 Bash 提供参数和标准输入，因而未携带该模块及 Tree-sitter Bash 依赖。与之配套的验证器专用用例不属于此命令入口；直接补丁引擎、CLI、综合命令、场景和符号链接用例均保留；扩展另外覆盖新选项及全部场景的内存检查。

## 构建接入

[仓库复用步骤](../../.github/actions/native-apply-patch/action.yml) 使用固定 Rust 工具链和 `Cargo.lock`，先交叉构建 ARM64 Linux ELF，再生成 `app/build/generated/terminal-command-assets/terminal_commands/`。该目录包含二进制、安装脚本、清单、LICENSE 和 NOTICE。Gradle 的 `syncMainAssets` 校验并合并这些资源，缺少资源或校验失败会终止构建。

开发环境准备同一资源可执行：

```bash
rustup toolchain install 1.95.0 --profile minimal
rustup target add --toolchain 1.95.0 aarch64-unknown-linux-gnu
export CARGO_TARGET_AARCH64_UNKNOWN_LINUX_GNU_LINKER=aarch64-linux-gnu-gcc
cargo +1.95.0 build --manifest-path tools/native_apply_patch/Cargo.toml \
  --release --target aarch64-unknown-linux-gnu --locked
python3 tools/native_apply_patch/package_assets.py \
  --binary tools/native_apply_patch/target/aarch64-unknown-linux-gnu/release/apply_patch \
  --output app/build/generated/terminal-command-assets
```

以上命令从仓库根目录执行，需预先安装 `gcc-aarch64-linux-gnu` 和 `libc6-dev-arm64-cross`，后者提供交叉链接所需的 glibc 启动对象与开发文件；使用 `--no-install-recommends` 时也必须显式安装。当前构建环境为 Ubuntu 24.04，与内置 Ubuntu Noble 对应。这里固定 `1.95.0` 以对应来源工作区，未声称这是最低可用 Rust 版本。

Build 与 PR Android build 会核对最终 APK 中的命令、清单和许可文件。Android Tests 与 PR Android JVM tests 另运行 Rust 工作区回归、QEMU ARM64 的全部 25 个场景的检查/预览/应用对照以及安装升级回归。该功能源码与复用步骤的改动均触发完整 Android 检查。

## 验证状态与许可

维护者已在 Android 实机确认旧入口的 `Add File` 可用，未据此宣称其他操作或新选项通过。本轮完成来源文件哈希、资源与会话入口静态核对、Python/TOML/YAML 解析及 Git 差异检查。本地未编译或运行测试；仓库工作流结果待确认。完整项目与可填写记录见[回归清单](../../docs/TODO/feat/terminal-apply-patch/02-verification.md)。

派生代码遵循 [Apache-2.0](LICENSE)，保留上游 [NOTICE](NOTICE)。打包脚本将许可和 NOTICE 一并写入 APK，应用的开源许可页增加 Codex apply_patch 来源入口。