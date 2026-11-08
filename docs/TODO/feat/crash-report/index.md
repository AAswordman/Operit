---
fork: https://github.com/3316891527/Operit
status: implemented-pending-ci
---

# 崩溃报告页面与共享日志过滤

在 `feat/data-and-backup` 上落实已确认的 HTML 设计。页面使用项目现有 `ic_launcher_simple_foreground`，跟随工具页浅深色，不增加独立主题按钮；致命状态点为红色，错误详情首次打开时展开。

## 页面与动作

`ui/error/report/` 分离报告模型、异常分类、配色、摘要和堆栈组件。主按钮重启应用；复制、保存和日志导出保留真实动作。堆栈支持换行、滚动、选取、行号及折叠，复制不含行号。小高度或大字体时正文滚动，保留现有错误报告目录和应用重启流程。

## 共用过滤

从 `LogExportPreferences` 读取日志查看器保存的排除调试、排除系统、仅错误上下文、隐藏敏感内容、不保留时间五项选项。报告生成时一次调用 `LogExportFilter`，页面与复制使用过滤后的堆栈，错误文件使用同次过滤后的日志文本；应用日志导出显式传入相同选项，默认导出路径也读取已保存选项。

独立堆栈以应用错误级记录参与过滤，使仅错误上下文保留当前崩溃。敏感前缀识别支持异常类型包装和续行，仍与普通日志共用同一规则表。原始堆栈不再由崩溃页重复写回日志。选择不保留时间时隐藏摘要时间，并省去导出内容中的时间戳和导出时间行；文件名仍用时间区分文件。从设置返回报告时重新读取选项。

## 分类样例

分类按异常头、Caused by 或 Suppressed 的异常名，以及异常行中的存储和权限关键词匹配，是简短提示，不代表完成根因诊断。

| 分类 | 示例 |
| --- | --- |
| 内存不足 | OutOfMemoryError |
| 调用栈溢出 | StackOverflowError |
| 存储空间不足 | SQLiteFullException、ENOSPC |
| 权限受限 | SecurityException、EACCES |
| 网络连接异常 | UnknownHostException、SSLHandshakeException |
| 数据库异常 | SQLiteDatabaseCorruptException |
| 文件读写异常 | FileNotFoundException |
| 组件加载异常 | UnsatisfiedLinkError、ClassNotFoundException |
| 进程通信异常 | DeadObjectException、RemoteException |
| 对象未初始化 | NullPointerException、UninitializedPropertyAccessException |
| 运行状态异常 | IllegalStateException、IllegalArgumentException |
| 未识别异常 | 不匹配规则的自定义异常或无异常头文本 |

## 验证

新增 11 项报告与分类 JVM 用例，并在共享过滤器补两项异常消息与续行遮盖回归；交给仓库工作流执行，不在本地运行 Android 构建或测试。完整设备操作、预期及独立结果表见[数据与备份验收](../data-and-backup/verification.md)，保留原有编号并扩到 77 项。设备视觉和真实崩溃恢复结果待填写。
