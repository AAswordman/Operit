package com.ai.assistance.operit.core.performance

import android.app.ActivityManager
import android.content.Context
import android.net.TrafficStats
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.terminal.TerminalManager
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.util.ArrayDeque
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 采样实体的类别；软件=主进程，插件=ToolPkg 容器，终端=PTY 会话进程树。 */
enum class PerformanceEntityKind { APP, PLUGIN, TERMINAL }

/**
 * 单个实体在一次采样里的指标。
 * cpuPercent 口径为占整机全部核心的百分比（与 Windows 任务管理器一致），上限 100。
 * memoryKb 口径按类别不同：软件=PSS，插件=QuickJS JS 堆，终端=进程树 RSS 之和。
 * rxBytesPerSec/txBytesPerSec 只有软件（UID）有值：插件与终端和主进程共享同一
 * UID，Linux 没有按进程的网络计量，其流量天然计入软件。
 */
data class PerformanceEntitySample(
    val id: String,
    val kind: PerformanceEntityKind,
    val displayName: String,
    val cpuPercent: Double,
    val memoryKb: Long,
    val rxBytesPerSec: Long? = null,
    val txBytesPerSec: Long? = null,
    val detail: String? = null
)

/** 一次完整采样；历史窗口内的最小单元，界面三个分析页都从同一份快照序列取数。 */
data class PerformanceSnapshot(
    val timestampMs: Long,
    val cpuCoreCount: Int,
    val deviceCpuPercent: Double?,
    val deviceTotalMemMb: Long,
    val deviceAvailMemMb: Long,
    val deviceRxBytesPerSec: Long?,
    val deviceTxBytesPerSec: Long?,
    val entities: List<PerformanceEntitySample>
) {
    fun entity(kind: PerformanceEntityKind): PerformanceEntitySample? =
        entities.firstOrNull { it.kind == kind }

    fun sumCpuPercent(kind: PerformanceEntityKind): Double =
        entities.filter { it.kind == kind }.sumOf { it.cpuPercent }

    fun sumMemoryKb(kind: PerformanceEntityKind): Long =
        entities.filter { it.kind == kind }.sumOf { it.memoryKb }
}

data class PerformanceMonitorState(
    val running: Boolean = false,
    val paused: Boolean = false,
    val latest: PerformanceSnapshot? = null,
    val history: List<PerformanceSnapshot> = emptyList()
)

/**
 * 性能分析界面的采样引擎（singleton）。
 * 界面打开时 [start]，关闭时 [stop]；历史留在内存中供切页/重进后继续观察。
 * 所有 /proc 读取都限定在本进程或本 UID 的子进程上，无需任何特殊权限。
 */
object PerformanceMonitorManager {

    private const val TAG = "PerfMonitor"
    private const val SAMPLE_INTERVAL_MS = 1_000L
    private const val MAX_HISTORY = 180
    // Linux USER_HZ：Android/bionic 内核固定为 100，/proc/*/stat 的 utime/stime 以它为计时单位
    private const val CLOCK_TICKS_PER_SECOND = 100L
    // 采样间隔超过该值（暂停恢复、进程被冻结）时差分失去“当前值”意义，只做基数重置
    private const val MAX_VALID_INTERVAL_MS = 5_000L

    private const val KEY_APP = "app"
    private const val KEY_PLUGIN_PREFIX = "plugin:"
    private const val KEY_TERMINAL_PREFIX = "terminal:"

    private data class ProcStat(
        val ppid: Int,
        val utime: Long,
        val stime: Long,
        val cutime: Long,
        val cstime: Long,
        val rssPages: Long
    ) {
        val selfTicks: Long get() = utime + stime
        val withChildrenTicks: Long get() = utime + stime + cutime + cstime
    }

    private data class DeviceCpuTicks(val total: Long, val idle: Long)

    private data class NetworkCounters(
        val uidRx: Long,
        val uidTx: Long,
        val deviceRx: Long,
        val deviceTx: Long
    )

    private data class TerminalSessionRef(val id: String, val title: String, val pid: Int)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateMutex = Any()
    private val historyDeque = ArrayDeque<PerformanceSnapshot>()
    private val _stateFlow = MutableStateFlow(PerformanceMonitorState())
    val stateFlow: StateFlow<PerformanceMonitorState> = _stateFlow
    private var samplingJob: Job? = null

    @Volatile private var running = false
    @Volatile private var paused = false
    @Volatile private var appContext: Context? = null

    // —— 上一次采样的差分基数（仅采样协程访问，无需加锁） ——
    private var prevElapsedMs: Long = 0L
    private var prevDeviceTotalTicks: Long = 0L
    private var prevDeviceIdleTicks: Long = 0L
    private val prevEntityTicks = HashMap<String, Long>()
    private var prevNetwork: NetworkCounters? = null

    fun start(context: Context) {
        synchronized(stateMutex) {
            if (running) {
                return
            }
            appContext = context.applicationContext
            running = true
            paused = false
            samplingJob =
                scope.launch {
                    samplingLoop()
                }
            publishState()
        }
    }

    fun stop() {
        synchronized(stateMutex) {
            running = false
            paused = false
            samplingJob?.cancel()
            samplingJob = null
            appContext = null
            publishState()
        }
    }

    fun setPaused(value: Boolean) {
        paused = value
        synchronized(stateMutex) { publishState() }
    }

    private fun publishState() {
        val state =
            synchronized(stateMutex) {
                PerformanceMonitorState(
                    running = running,
                    paused = paused,
                    latest = historyDeque.lastOrNull(),
                    history = historyDeque.toList()
                )
            }
        _stateFlow.value = state
    }

    private suspend fun samplingLoop() {
        // delay 在协程被取消时抛出 CancellationException，自然结束循环
        while (running) {
            if (paused) {
                delay(200L)
                continue
            }
            try {
                val snapshot =
                    withContext(Dispatchers.IO) { sampleOnce() }
                appendSnapshot(snapshot)
            } catch (error: Exception) {
                AppLogger.e(TAG, "performance sampling failed: ${error.message}", error)
            }
            delay(SAMPLE_INTERVAL_MS)
        }
    }

    private fun appendSnapshot(snapshot: PerformanceSnapshot) {
        synchronized(stateMutex) {
            historyDeque.addLast(snapshot)
            while (historyDeque.size > MAX_HISTORY) {
                historyDeque.removeFirst()
            }
            publishState()
        }
    }

    // ————————————————————————————— 采样本体 —————————————————————————————

    private fun sampleOnce(): PerformanceSnapshot {
        val nowElapsed = SystemClock.elapsedRealtime()
        val coreCount = Runtime.getRuntime().availableProcessors()
        val deltaMs = nowElapsed - prevElapsedMs
        val deltaValid = prevElapsedMs > 0L && deltaMs in 1..MAX_VALID_INTERVAL_MS
        val seconds = deltaMs / 1000.0

        val deviceCpu = readDeviceCpu()
        val network = readNetworkCounters()

        // —— 网络（软件 UID 与整机，差分速率） ——
        var appRx: Long? = null
        var appTx: Long? = null
        var deviceRx: Long? = null
        var deviceTx: Long? = null
        val prevNet = prevNetwork
        if (deltaValid && network != null && prevNet != null) {
            appRx = ratePerSecond(network.uidRx - prevNet.uidRx, seconds)
            appTx = ratePerSecond(network.uidTx - prevNet.uidTx, seconds)
            deviceRx = ratePerSecond(network.deviceRx - prevNet.deviceRx, seconds)
            deviceTx = ratePerSecond(network.deviceTx - prevNet.deviceTx, seconds)
        }
        if (network != null) {
            prevNetwork = network
        }

        val entities = mutableListOf<PerformanceEntitySample>()

        // —— 软件（主进程） ——
        // /proc stat paths must be absolute: a relative "self"/PID is resolved against the
        // app working directory, so every read fails and the CPU delta is rendered as 0.0.
        val appStat = readProcStat("/proc/self/stat")
        val appCpuPercent =
            if (deltaValid && appStat != null) {
                ticksToPercent(appStat.selfTicks - (prevEntityTicks[KEY_APP] ?: 0L), deltaMs, coreCount)
            } else {
                0.0
            }
        if (appStat != null) {
            prevEntityTicks[KEY_APP] = appStat.selfTicks
        }

        val memInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memInfo)
        entities.add(
            PerformanceEntitySample(
                id = KEY_APP,
                kind = PerformanceEntityKind.APP,
                displayName = "",
                cpuPercent = appCpuPercent,
                memoryKb = memInfo.totalPss.toLong(),
                detail =
                    "Dalvik ${formatMb(memInfo.dalvikPss.toLong())} · Native ${formatMb(memInfo.nativePss.toLong())}",
                rxBytesPerSec = appRx,
                txBytesPerSec = appTx
            )
        )

        // —— 插件（ToolPkg 容器）：按引擎线程 tid 读 /proc/self/task/<tid>/stat，
        //    基数按 tid 记录，线程销毁/新建都不会把旧线程的累计量带进差分 ——
        PackageManager.peekInstance()?.getToolPkgRuntimeEngineOverviews()?.forEach { overview ->
            var currentTicks = 0L
            var previousTicks = 0L
            overview.engineThreadIds.forEach { tid ->
                val stat = readProcStat("/proc/self/task/$tid/stat") ?: return@forEach
                currentTicks += stat.selfTicks
                previousTicks += prevEntityTicks["${KEY_PLUGIN_PREFIX}${overview.containerPackageName}:$tid"] ?: 0L
                prevEntityTicks["${KEY_PLUGIN_PREFIX}${overview.containerPackageName}:$tid"] = stat.selfTicks
            }
            val cpuPercent =
                if (deltaValid) {
                    ticksToPercent(currentTicks - previousTicks, deltaMs, coreCount)
                } else {
                    0.0
                }
            entities.add(
                PerformanceEntitySample(
                    id = overview.containerPackageName,
                    kind = PerformanceEntityKind.PLUGIN,
                    displayName = overview.displayName,
                    cpuPercent = cpuPercent,
                    memoryKb = overview.quickJsMemoryUsedBytes / 1024L,
                    detail =
                        "QuickJS malloc ${formatKb(overview.quickJsMallocBytes / 1024L)} · ${overview.engineCount}T"
                )
            )
        }

        // —— 终端（PTY 子进程树：bash → proot → 命令） ——
        val terminalSessions = collectTerminalSessions()
        val procTree = if (terminalSessions.isEmpty()) emptyMap() else readAccessibleProcessTree()
        terminalSessions.forEach { session ->
            val tree = collectDescendants(session.pid, procTree)
            var currentTicks = 0L
            var rssPages = 0L
            tree.forEach { pid ->
                val stat = procTree[pid] ?: return@forEach
                currentTicks += stat.withChildrenTicks
                rssPages += stat.rssPages
            }
            val key = "${KEY_TERMINAL_PREFIX}${session.id}"
            val cpuPercent =
                if (deltaValid) {
                    ticksToPercent(currentTicks - (prevEntityTicks[key] ?: 0L), deltaMs, coreCount)
                } else {
                    0.0
                }
            prevEntityTicks[key] = currentTicks
            entities.add(
                PerformanceEntitySample(
                    id = session.id,
                    kind = PerformanceEntityKind.TERMINAL,
                    displayName = session.title,
                    cpuPercent = cpuPercent,
                    memoryKb = rssPages * pageSizeBytes / 1024L,
                    detail = "PID ${session.pid} · ${tree.size}P"
                )
            )
        }

        // —— 整机 ——
        var deviceCpuPercent: Double? = null
        if (deltaValid && deviceCpu != null) {
            val totalDelta = deviceCpu.total - prevDeviceTotalTicks
            val idleDelta = deviceCpu.idle - prevDeviceIdleTicks
            if (totalDelta > 0L) {
                deviceCpuPercent =
                    ((totalDelta - idleDelta).coerceAtLeast(0L).toDouble() / totalDelta) * 100.0
            }
        }
        if (deviceCpu != null) {
            prevDeviceTotalTicks = deviceCpu.total
            prevDeviceIdleTicks = deviceCpu.idle
        }
        val deviceMem = readDeviceMemory()

        prevElapsedMs = nowElapsed

        return PerformanceSnapshot(
            timestampMs = System.currentTimeMillis(),
            cpuCoreCount = coreCount,
            deviceCpuPercent = deviceCpuPercent,
            deviceTotalMemMb = deviceMem?.first ?: 0L,
            deviceAvailMemMb = deviceMem?.second ?: 0L,
            deviceRxBytesPerSec = deviceRx,
            deviceTxBytesPerSec = deviceTx,
            entities = entities.toList()
        )
    }

    private fun collectTerminalSessions(): List<TerminalSessionRef> {
        val context = appContext ?: return emptyList()
        val sessions =
            TerminalManager.getInstance(context).terminalState.value.sessions
        return sessions.mapNotNull { session ->
            val pid = session.pty?.pid ?: -1
            if (pid > 0) {
                TerminalSessionRef(id = session.id, title = session.title, pid = pid)
            } else {
                null
            }
        }
    }

    // ————————————————————————————— /proc 读取 —————————————————————————————

    /** 解析 stat 文件内容；comm 可能含空格，固定从最后一个 ')' 之后取字段。 */
    private fun readProcStat(statPath: String): ProcStat? {
        return runCatching {
            val text = File(statPath).readText()
            val closeParen = text.lastIndexOf(')')
            if (closeParen < 0) {
                return null
            }
            val fields = text.substring(closeParen + 2).split(' ')
            // fields[0] = 第 3 个字段 state；字段 N 对应索引 N-3
            val ppid = fields.getOrNull(1)?.toIntOrNull() ?: return null
            val utime = fields.getOrNull(11)?.toLongOrNull() ?: return null
            val stime = fields.getOrNull(12)?.toLongOrNull() ?: return null
            val cutime = fields.getOrNull(13)?.toLongOrNull() ?: 0L
            val cstime = fields.getOrNull(14)?.toLongOrNull() ?: 0L
            val rssPages = fields.getOrNull(21)?.toLongOrNull() ?: 0L
            ProcStat(
                ppid = ppid,
                utime = utime,
                stime = stime,
                cutime = cutime,
                cstime = cstime,
                rssPages = rssPages
            )
        }.getOrNull()
    }

    private fun readDeviceCpu(): DeviceCpuTicks? {
        return runCatching {
            File("/proc/stat").bufferedReader().use { reader ->
                val line = reader.readLine() ?: return null
                if (!line.startsWith("cpu ")) {
                    return null
                }
                val values = line.trim().split(Regex("\\s+")).drop(1).map { it.toLongOrNull() ?: 0L }
                // user nice system idle iowait …；idle + iowait 为空闲时间
                val idle = (values.getOrNull(3) ?: 0L) + (values.getOrNull(4) ?: 0L)
                DeviceCpuTicks(total = values.sum(), idle = idle)
            }
        }.getOrNull()
    }

    /** 枚举本 UID 可读的进程（其他应用的进程 stat 读取会失败并被跳过），建立 pid → stat 映射。 */
    private fun readAccessibleProcessTree(): Map<Int, ProcStat> {
        val result = HashMap<Int, ProcStat>()
        val dirs =
            File("/proc").listFiles { file -> file.name.all { it in '0'..'9' } } ?: return result
        dirs.forEach { dir ->
            val stat = readProcStat("/proc/${dir.name}/stat") ?: return@forEach
            result[dir.name.toInt()] = stat
        }
        return result
    }

    private fun collectDescendants(rootPid: Int, procTree: Map<Int, ProcStat>): List<Int> {
        val childrenByParent = HashMap<Int, MutableList<Int>>()
        procTree.forEach { (pid, stat) ->
            childrenByParent.getOrPut(stat.ppid) { mutableListOf() }.add(pid)
        }
        val visited = mutableListOf<Int>()
        val queue = ArrayDeque<Int>()
        queue.add(rootPid)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            visited.add(current)
            childrenByParent[current]?.forEach { child ->
                if (child != rootPid) {
                    queue.add(child)
                }
            }
        }
        return visited
    }

    private fun readNetworkCounters(): NetworkCounters? {
        val uidRx = TrafficStats.getUidRxBytes(Process.myUid())
        val uidTx = TrafficStats.getUidTxBytes(Process.myUid())
        val deviceRx = TrafficStats.getTotalRxBytes()
        val deviceTx = TrafficStats.getTotalTxBytes()
        if (uidRx < 0 || uidTx < 0 || deviceRx < 0 || deviceTx < 0) {
            // TrafficStats.UNSUPPORTED：内核未提供计数，上报不可用而不是捏造 0
            return null
        }
        return NetworkCounters(uidRx = uidRx, uidTx = uidTx, deviceRx = deviceRx, deviceTx = deviceTx)
    }

    private fun readDeviceMemory(): Pair<Long, Long>? {
        val context = appContext ?: return null
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return null
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return Pair(memInfo.totalMem / (1024L * 1024L), memInfo.availMem / (1024L * 1024L))
    }

    /** Android 上 _SC_PAGESIZE 恒可用；读取失败说明运行环境异常，应直接暴露错误。 */
    private val pageSizeBytes: Long by lazy { Os.sysconf(OsConstants._SC_PAGESIZE) }

    // ————————————————————————————— 数值换算 —————————————————————————————

    private fun ticksToPercent(deltaTicks: Long, deltaMs: Long, coreCount: Int): Double {
        if (deltaTicks <= 0L || deltaMs <= 0L || coreCount <= 0) {
            return 0.0
        }
        val cpuSeconds = deltaTicks.toDouble() / CLOCK_TICKS_PER_SECOND
        val wallSeconds = deltaMs / 1000.0
        return ((cpuSeconds / wallSeconds) / coreCount * 100.0).coerceIn(0.0, 100.0)
    }

    private fun ratePerSecond(deltaBytes: Long, seconds: Double): Long? {
        if (deltaBytes < 0L || seconds <= 0.0) {
            return null
        }
        return (deltaBytes / seconds).toLong()
    }

    private fun formatKb(kb: Long): String =
        if (kb >= 1024L) {
            String.format(Locale.US, "%.1fMB", kb / 1024.0)
        } else {
            "${kb}KB"
        }

    private fun formatMb(kb: Long): String = String.format(Locale.US, "%.1fMB", kb / 1024.0)
}
