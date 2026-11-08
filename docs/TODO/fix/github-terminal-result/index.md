---
fork: https://github.com/3316891527/Operit
status: implemented
---

# GitHub 工具包保留终端失败输出

同一条 stdout/stderr 后 exit 7 的命令，super_admin:terminal 完整返回原生 TerminalCommandResultData，而 github:terminal_exec 只返回错误说明。底层工具已提供 error.data，GitHub 包的通用 wrap 捕获异常后丢弃了该字段。

增加共享失败格式化函数，保留原始 data 引用、退出码、命令、完整输出及其他原生字段，success 继续为 false，不靠改成零退出码绕过失败。其他工具的结构化错误也按原样传递；没有原生数据的错误不制造终端结果。

新增六项 Node 回归源码及 JVM 调用入口，覆盖非零输出、大输出、超时/文件化输出元数据、传输错误、通用错误和字符串错误。已对当前安装工具做真实对照测试；新增回归尚未本地运行，未编译或重新打包 GitHub 包。

维护者要求与后续复杂问题一起处理，本轮仅本地提交、不推送、不派发工作流。此修改涉及实际 GitHub 包内容，后续必须构建分发脚本并更新对应固定资源与源码指纹；不能用旧资源目录冒充已交付修复，也不改写历史资源。
