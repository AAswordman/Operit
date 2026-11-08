---
For_Agent: 性能分析界面计划
---

# 性能分析界面（设置）

## 现状
- 设置页各子界面以密封类 `Screen` + 回调导航组织（`OperitScreens.kt` / `SettingsScreen.kt`），无 NavHost
- 插件运行在主进程内的 QuickJS 线程（`OperitQuickJsRuntime`）上，`ToolPkgManager` 维护 contextKey → 引擎的映射，但引擎与内核线程 ID 的对应关系没有暴露
- 终端会话通过 `:terminal` 模块的 `Pty.start` fork 出独立 PTY 子进程，JNI 已返回 pid 但 `Pty` 没有保存
- 项目内没有任何 CPU / 网络采样代码；内存仅有 `ActivityManager.MemoryInfo`（设备级）和 QuickJS 分配量（插件级）
- 已有 Canvas 图表先例：`ui/features/tokenstats/TokenStatsCharts.kt`

## 意图
- 在设置中新增“性能分析”界面，类似 Windows 任务管理器：CPU / 内存 / 网络 三个分析项切换
- 每个分析项都按“软件（主进程） / 插件（按容器聚合） / 终端（按会话聚合）”拆分，方便用户横向对比
- 曲线图 + 按实体的实时明细列表，采样在界面打开期间持续进行

## 作用域
- 新增 `core/performance/PerformanceMonitorManager`：单例采样器，/proc CPU、Debug.MemoryInfo、QuickJS 内存、TrafficStats 网络
- QuickJS 引擎暴露其运行线程 tid（Java `Thread.id` 不是内核 tid，必须在引擎线程内取 `Process.myTid()`）
- `JsEngine` / `ToolPkgManager` / `PackageManager` 逐层暴露每插件的引擎线程 ID 与 QuickJS 内存
- `Pty` 保存并暴露子进程 pid；终端会话 pid 用于 /proc 进程树（bash → proot → 命令）聚合
- 新增 `ui/features/performance` 界面（TabRow 三页 + Canvas 曲线 + 实体明细），接入设置导航
- 补充中英文字符串资源（与既有功能一致的覆盖范围）

## 不做的事
- 网络不做插件/终端级归属：它们与主进程共享同一 UID，Linux/Android 无按进程流量计量，界面中明确说明其流量计入“软件”
- 不把 `:repair` / `:crash` 辅助进程计入“软件”（它们空闲且与用户感知无关）
- 不做持久化历史，仅保留内存中的滚动窗口
