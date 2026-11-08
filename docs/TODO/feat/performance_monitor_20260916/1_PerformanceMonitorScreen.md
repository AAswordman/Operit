---
For_Agent: 性能分析界面实现记录
---

# 性能分析界面

## 旧实现
- 设置中没有性能相关界面；插件侧只有 `ToolPkgRuntimeMonitor`（调用次数/耗时/QuickJS 内存增量，展示在插件详情弹窗）
- QuickJS 引擎线程与内核线程无对应关系；`Pty` 丢弃了 JNI 返回的子进程 pid
- 无 CPU/网络采样基础设施

## 新实现
- 新增 `core/performance/PerformanceMonitorManager`：
  - 1s 周期采样，产出 `PerformanceSnapshot`（时间戳、核心数、实体列表、整机 CPU/内存/网络）
  - CPU：`/proc/self/stat`、`/proc/self/task/<tid>/stat`（按 tid 归属引擎）、终端会话 pid 的 `/proc/<pid>/stat` 进程树聚合（utime+stime+cutime+cstime，父子不重复计入）；整机来自 `/proc/stat`
  - 内存：软件 = `Debug.MemoryInfo.totalPss`（明细含 Dalvik/Native）；插件 = QuickJS `memoryUsedBytes` 按容器聚合；终端 = 进程树 RSS 求和；设备 = `ActivityManager.MemoryInfo`
  - 网络：`TrafficStats` UID 收发与整机收发的差分速率；速率由真实 Δt 计算，暂停恢复后的第一个大间隔样本不产生速率点
  - 滚动窗口 180 个样本，singleton 持有，界面关闭后停止采样但保留历史
- quickjs：`OperitQuickJsEngine` 在运行线程启动时记录 `Process.myTid()`，暴露 `getRuntimeTid()`
- app：`JsEngine.getRuntimeThreadId()` → `ToolPkgManager.getToolPkgEngineRuntimeInfoInternal()`（按容器聚合引擎 tid 与 QuickJS 内存）→ `PackageManager.peekInstance()` + `getToolPkgEngineRuntimeInfo()`（附显名解析）；包管理器未初始化时插件列表为空
- terminal：`Pty` 新增 `pid` 构造参数（默认 -1，`Pty.start` 传入真实值），性能界面经 `TerminalManager.terminalState` 读取会话 pid
- 新增 `ui/features/performance/PerformanceMonitorScreen.kt`（TabRow：CPU/内存/网络；头部当前值；Canvas 多曲线历史图；实体明细列表；暂停/继续）与 `PerformanceMonitorCharts.kt`（折线/面积图、图例）；页面使用 app 级统一 AppBar，通过 `LocalTopBarActions` 申请暂停/继续按钮
- 导航：`Screen.PerformanceMonitor`（`NavItem.Settings`，`screen_title_performance_monitor`），设置页“数据和权限”分组新增入口
- 字符串：`values/strings.xml` + `values-en/strings.xml` 新增 perf_* / settings_performance_monitor* / screen_title_performance_monitor

## CPU 采样路径修正

- `readProcStat` 接收的是文件路径；主进程、QuickJS 线程和终端进程树的调用点此前传入了相对的 `self`、tid 或 PID，导致 `/proc/*/stat` 始终读取失败并被转换成 0.0
- 调用点统一传入 `/proc/self/stat`、`/proc/self/task/<tid>/stat` 和 `/proc/<pid>/stat`，恢复软件、插件与终端的 CPU 差分采样

## 验收点
- 设置 → 性能分析可见，三个 Tab 可切换且共享同一份滚动历史
- 打开/运行插件后 CPU、内存页出现对应插件行，数值随脚本执行变化
- 创建终端会话后 CPU、内存页出现对应会话行；会话内执行占用 CPU 的命令时曲线抬升
- 网络页展示软件与整机收发速率；插件/终端流量归属有明确说明
- 暂停后曲线冻结，恢复后继续；离开界面采样停止
