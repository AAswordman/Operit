# apply_patch 命令行选项

默认参数补丁与 stdin 用法保留。扩展版本号读取移植包的 `CARGO_PKG_VERSION`，当前为 `0.1.0`；来源命令没有版本查询参数，来源工作区的 `0.0.0` 不作为独立命令发行号。

## 选项

| 选项 | 行为 |
| --- | --- |
| `--version`、`-v`、`-V` | 输出 `apply_patch 0.1.0` 并退出，不读取补丁 |
| `--help`、`-h` | 显示参数、补丁格式、调用示例与退出码 |
| `--check`、`-c`、`--dry-run` | 按原引擎顺序检查补丁，写入、建目录和删除只保存在内存 |
| `--no-overwrite` | 禁止新增和移动目标覆盖已有路径；普通更新与显式删除后重建仍允许 |
| `--diff`、`-D` | 只读检查并按操作顺序预览差异，不写盘 |
| `--quiet`、`-q` | 隐藏普通成功摘要；保留错误、差异、JSON、帮助和版本 |
| `--file PATH`、`-f PATH`、`--file=PATH` | 从 UTF-8 文件读取补丁；补丁内相对路径仍以当前工作目录为基准 |
| `-f -` | 显式从标准输入读取补丁 |
| `--json` | stdout 只输出一个 JSON 对象；成功、失败、帮助及版本查询均支持 |
| `--` | 结束选项解析，后一个参数按补丁正文处理 |

文件输入和位置参数补丁互斥，不能指定多个文件来源。使用 `-f` 时路径允许空格、引号和非 UTF-8 文件名；`--file=PATH` 使用可表示为 UTF-8 的参数。将 `--json` 作为文件名传给 `-f` 时，它仍是文件名，不会启用 JSON 输出。

```bash
apply_patch --version
apply_patch --help
apply_patch --check --file change.patch
apply_patch -c -f change.patch --json
apply_patch --file change.patch --json
apply_patch --diff --no-overwrite --file change.patch
apply_patch --quiet --no-overwrite --file change.patch
apply_patch --check <<'PATCH'
*** Begin Patch
*** Add File: example.txt
+hello
*** End Patch
PATCH
```

## 检查语义

检查模式使用原补丁引擎，按顺序模拟新增、更新、移动和删除，支持同一补丁先创建文件、再修改或移动、再继续操作目标路径。符号链接按原命令规则跟随，删除操作只移除链接本身；已有硬链接的内容变化在内存中共享，删除再新增的名称使用独立内容。检查成功与失败都不向磁盘写入目标文件、目录或暂存副本。

检查会读取实际文件和路径，不能保证实际执行时的写权限、可用磁盘空间或外部并发修改。检查失败时可列出在失败位置之前已模拟成功的操作，但这些不是已经写入磁盘的变更。普通应用模式仍是非事务式执行，后项失败不会撤销前项写入。

## 自定义规则

`--no-overwrite` 仅保护 `Add File` 与 `Move to` 的目标路径，已有文件、目录或符号链接均拒绝；实际执行使用独占创建，检查和预览模拟同一规则。同一补丁先创建的名称会阻止后续同名新增，明确删除后允许重建。不指定该选项时继续保留原有覆盖行为。

`--diff` 隐含只读检查，可与 `--check` 组合，输出按实际操作顺序组织，移动显示目标写入与源路径删除。二进制变化显示标记，空文件保留路径头，删除链接显示链接本身。预览失败时只报告错误，成功退出码为 `0`，存在差异也不返回 `1`。颜色由终端自动决定，重定向、`NO_COLOR` 和 `TERM=dumb` 禁用颜色。

`--quiet` 只隐藏普通成功摘要，明确请求的差异、JSON、帮助、版本和错误仍可见。`--help` 包含上述规则、组合行为及示例。[详细实现与验证边界](04-safe-preview.md)。

## JSON 契约

输出编码为 UTF-8，末尾包含换行，JSON 对象之外不混入文本摘要。普通错误写入 JSON 的 `error` 字段，保留进程退出码；系统无法写 stdout 等输出通道故障仍通过 stderr 报告。

| 字段 | 含义 |
| --- | --- |
| `schema_version` | 当前协议版本 `1` |
| `tool`、`version` | 命令名 `apply_patch` 和移植包版本 |
| `mode` | `apply`、`check`、`diff`、`help`、`version` 或参数解析失败时的 `usage` |
| `success`、`exit_code` | 操作结果及进程退出码：`0` 成功，`1` 补丁或 I/O 失败，`2` 用法错误 |
| `changes` | 按顺序记录的变更数组；成功时包含全部操作，失败时包含引擎已记录的操作 |
| `changes[].operation` | `add`、`update`、`delete` 或 `move` |
| `changes[].path` | 依据当前目录解析的绝对路径；保留补丁命名，不替换为符号链接最终目标 |
| `changes[].destination` | 移动操作的绝对目标路径 |
| `partial` | 实际应用失败且已有或可能已有写入时为 `true`；检查与预览模式始终为 `false` |
| `exact` | 原引擎的变化快照完整性标记；符号链接、无法读取文本等情况可能为 `false`，未执行补丁时为 `null` |
| `message` | 文本摘要、帮助内容或错误说明 |
| `diff` | 仅在 `mode=diff` 时提供；成功为无颜色预览字符串，失败为 `null`，其他模式不增加此字段 |
| `error` | 成功时为 `null`；失败时为 `{ "kind": "usage/input/io/patch", "message": "..." }` |

下面只是协议结构示例，路径与说明为演示值，不代表已执行记录：

```json
{
  "schema_version": 1,
  "tool": "apply_patch",
  "version": "0.1.0",
  "mode": "check",
  "success": true,
  "exit_code": 0,
  "changes": [{"operation": "add", "path": "/work/example.txt"}],
  "partial": false,
  "exact": true,
  "message": "Success. Checked the following files (no files written):\nA example.txt",
  "error": null
}
```

## 实现与验证

扩展代码位于 `tools/native_apply_patch/src/cli/`；禁止覆盖选项接入原引擎的新增/移动写入。补丁解析、上下文匹配和文本换行仍使用原实现，默认应用语义保留，`standalone_executable.rs` 只把既有入口转发到扩展模块。

原有 25 个场景继续按普通应用执行，另对全部场景执行检查及预览并验证状态一致和原目录字节不变。新增选项、顺序依赖、链接、二进制删除、失败后不写入及 JSON 输出回归；ARM64 QEMU 验证增加版本、帮助、文件输入、检查与 JSON，并对全部场景做检查/应用对照。

维护者已反馈旧入口在 Android 实机的 `Add File` 可用。该结果只证明所试新增文件路径；其他操作及本轮新选项尚待仓库检查与实机确认。本地未构建或运行测试，完整结果表见[回归清单](02-verification.md)。