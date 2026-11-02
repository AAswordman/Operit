# 终端生命周期与托管任务回归

本轮本地仅执行静态检查，JVM 用例由仓库 Actions 执行，Android 设备结果留空待填。以下清单包含既有行为与新增路径。

## JVM 用例

应用 `TerminalCommandFlowTest` 覆盖即时完成的订阅握手、忽略其他命令事件和取消后退订；`TerminalTaskRegistryTest` 覆盖查询超时、查询方取消、执行期限、取消结果、调度前取消、退出码、画面替换、输出截断、容量淘汰、失效 ID 与缺失完成事件。

CI 的 `FixedCatalogSourceSnapshotTest` 另覆盖开发构建与当前索引分离、发布源码不匹配、快照标签不匹配和文件摘要被改动四项校验。

核心 `SessionProcessStatTest` 覆盖带括号的进程名和无效 stat；`TerminalExitMonitorTest` 覆盖主进程退出但读取仍不结束，以及读取已排空；`ClosedSessionCompletionTest` 覆盖运行与多个排队命令恰好完成一次。

CI 入口为 `./gradlew :app:testDebugUnitTest --stacktrace --no-daemon`，该任务通过 Gradle 依赖同时执行 `:terminal:testDebugUnitTest`。Debug 构建由工作分支上的 Build 工作流执行。

## Android 设备检查

- T01：新建会话执行 `pwd`、`true`、`printf 'ok\n'`，重复快速调用；及时返回并保留正确退出码
- T02：执行中文、跨字节块输出和目录切换；中文完整，提示符协议不混入结果
- T03：执行 `false`、`exit 7` 和会话主进程退出；命令非零或会话丢失明确失败，不伪报命令成功
- T04：A 运行时排队 B、C，取消 B；A 不被中断，B 结束为取消，C 按序执行
- T05：A 执行时取消 A，再排队 B；B 不收到 A 的 Ctrl+C，取消未完成前不推进队列
- T06：执行不响应 Ctrl+C 的程序；限定时间后退役会话，结果说明清理完成或未确认，后续创建得到新会话
- T07：显式关闭包含运行和多个排队任务的会话；每个调用结束一次，不继续等待原会话
- T08：启动会话后立刻取消或关闭；晚创建的 PTY 被清理，不留下可复用的失效会话
- T09：主进程退出且后代仍持有输出描述符；输出最多等待排空期限后结束，调用不永久挂起
- T10：启动长任务并用较小 yieldMs 返回；保存 runId，后续查询同一任务得到最终结果且命令只执行一次
- T11：取消一次 poll 请求或让 yieldMs 到期；任务继续，下一次 poll 可继续查询
- T12：执行期限到期与显式 cancel；分别得到 timed_out 和取消结果，检查 cancellationOutcome
- T13：任务输出进度行、无换行文本和全屏画面；screen 替换更新，查询结果不重复追加日志
- T14：任务输出超过 12000 字符、任务记录满额和应用重启后旧 ID；分别标记截断、拒绝超额或明确 ID 失效
- T15：旧 exec、execStreaming、super_admin terminal、terminal_wait 与隐藏执行器；旧结果结构可用，排队等待超时不误伤前一命令
- T16：并行使用两个会话，在一个会话制造写通道阻塞；另一个会话的输入和简单命令仍能处理
- T17：交互提示、Ctrl+C、组合按键和方向键；输入进入目标会话，文本与回车保持顺序
- T18：人工本地会话、工具本地会话及 SSH；自动化环境只作用于新建本地工具会话，SSH 不使用本地 PID 清理
- T19：宿主初始化、环境删除及重新初始化；初始化仍先于就绪，维护期间不得新建终端

## 结果填写

```text
设备 / Android 版本：
构建提交 / 构建变体：
JVM Actions 链接与结果：
Debug Actions 链接与结果：
T01：
T02：
T03：
T04：
T05：
T06：
T07：
T08：
T09：
T10：
T11：
T12：
T13：
T14：
T15：
T16：
T17：
T18：
T19：
```
