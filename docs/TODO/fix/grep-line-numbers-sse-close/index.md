---
repo: https://github.com/3316891527/Operit
branch: fix/grep-line-numbers-sse-close
status: implemented-awaiting-validation
---
# GREP 上下文行号与 SSE 关闭异常

## 范围与依据

只处理用户确认的两项修复，不整体合并上游集成 PR，不引入其他同步内容。

- GREP：当前应用读取真实源码文件开头时，`grep_code` 返回了 `-1`、`0` 行号，并把命中标记放到实际第 3 行。Rust 返回上下文时未携带行号，Kotlin 按上下文中心推算；文件首尾和不连续匹配窗口不满足该假设
- SSE：Web Chat 和 external-chat 在 writer 的 `use` 内捕获异常，隐式关闭发生在捕获范围之外；客户端断连后，缓冲数据在关闭时写入失效管道可能抛出 `IOException`

来源：[上游 #1355](https://github.com/AAswordman/Operit/pull/1355)、[上游 #1186](https://github.com/AAswordman/Operit/pull/1186)，参考集成 [#1469](https://github.com/AAswordman/Operit/pull/1469) 的 `0b21ef92308dbf299731cc74c91466f7f7db61c0`。实现按本仓库现有目录、修订归并、取消及资源清理链路适配。

## 实现计划

- [x] Rust 上下文携带真实行号及命中标记，在完整行边界截断；保持现有 4000 Unicode 字符预算
- [x] Kotlin 按生产者携带的行号输出上下文，移除中心推算；保留片段读取产生的编号和截断标记
- [x] 两个 SSE 入口共用关闭保护，限定处理关闭阶段的 IO 异常，保留正文异常、取消、提前退出及清理语义
- [x] 补充 Rust 与 JVM 回归源码，并接入 PR 检查的 Rust 主机单测命令
- [ ] 完成静态检查、草稿 PR 和远程 Debug 构建派发，分别记录已确认结果与待验证项

## 边界

GREP 工具参数、语义检索轮次、输出数量与逐行文本预算沿用现有规则。Rust 和 Kotlin 作为一组交付，原生库从本次源码重新构建。

SSE 保留当前请求体处理、最终正文修订、结构化历史和客户端取消链路。关闭阶段的正常断连不得掩盖正文异常，也不改变协程取消状态。

终端及 TerminalCore、模型服务生命周期、TTS/A2A、配置界面、本地化、子代理示例和改名重构均不在本次范围。

## 验证记录

本地进行源码审阅和静态检查，不执行项目编译、构建或测试。远程 Rust、JVM、Debug 构建和新包真机验证分别记录，不将用例源码视为已执行结果。


## 实现记录

SSE 两条入口共用生产代码 `withSseWriter`。关闭保护只捕获 `BufferedWriter.close()` 的 `IOException`，外层 `use` 继续维护正文异常、协程取消以及非 IO 关闭异常的 suppressed 关系。两条入口原有流式正文、修订归并、请求解析和清理代码保持现有语义；回归直接调用生产包装器。

GREP 配套修改 Rust 生产者和 Kotlin 展示层，编号和命中标记由源文件位置生成。保留 4000 Unicode 字符预算，避免将中文上下文预算误改为 UTF-8 字节数。补充多字节文本回归，并在现有 PR 原生构建步骤中接入主机单测，Android 原生库仍从同一份源码构建。

新增 7 项 Rust、18 项 JVM 回归源码。覆盖文件首尾、中部、不连续多匹配、整行截断、多字节预算、无上下文输出，以及正常关闭、断连、提前退出、正文异常、取消和非 IO 关闭异常。

[DONE] 两项源码修复及回归覆盖已完成。静态检查、远程构建和设备验证按候选提交分别记录。
