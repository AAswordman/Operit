package com.ai.assistance.operit.util

import android.content.Context
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.StringWriter
import java.io.PrintWriter
import java.lang.reflect.Field
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * ANR监控器，用于跟踪和记录可能导致ANR的主线程阻塞
 * 
 * @param context 应用上下文
 * @param coroutineScope 用于启动监控协程的作用域
 * @param tag 日志标签
 */
class AnrMonitor(
    private val context: Context, 
    private val coroutineScope: CoroutineScope,
    private val tag: String = "AnrMonitor"
) {
    companion object {
        // 默认阈值设置
        private const val ANR_THRESHOLD_MS = 1000L     // 1秒，标准ANR阈值
        private const val WARNING_THRESHOLD_MS = 500L // 0.5秒，警告阈值
        private const val SAMPLING_INTERVAL_MS = 100L  // 100毫秒采样间隔
        private const val MAX_STACK_TRACES = 10        // 最大堆栈跟踪数

        // 主线程名称
        private const val MAIN_THREAD_NAME = "main"
    }
    
    private val running = AtomicBoolean(false)
    private val lastResponseTime = AtomicLong(System.currentTimeMillis())
    private var monitoringJob: Job? = null
    private val mainThreadHandler = Handler(Looper.getMainLooper())

    // 捕获节流：持续ANR时每100ms的采样只允许按冷却窗口触发一次完整转储
    private val captureThrottle = AnrCaptureThrottle(AnrCaptureBudget.CAPTURE_COOLDOWN_MS)
    
    // 后备方案：如果协程有问题，使用ScheduledExecutorService
    private var scheduledExecutor: ScheduledExecutorService? = null
    
    // 记录ANR次数和严重程度
    private val anrCount = AtomicInteger(0)
    private val warningCount = AtomicInteger(0)
    private val maxBlockDuration = AtomicLong(0)
    
    // 堆栈跟踪历史
    private val stackTraces = mutableListOf<Pair<Long, String>>()
    
    // 跟踪调用者信息
    private val callerInfo = ConcurrentHashMap<String, String>()

    // 当前这轮卡顿是否已经计过数：一次阻塞只折算成一次ANR，而不是每100ms一次
    private val stallReported = AtomicBoolean(false)

    // 最后一次获取到的主线程引用
    private var mainThread: Thread? = null
    
    // 上次ANR的堆栈签名，用于去重（只比较栈顶若干帧，与转储文本大小无关）
    private var lastAnrSignature: String? = null
    
    /**
     * 开始ANR监控
     */
    fun start() {
        if (running.getAndSet(true)) {
            AppLogger.w(tag, "ANR监控器已经在运行中")
            return
        }
        
        AppLogger.d(tag, "启动ANR监控器")
        lastResponseTime.set(System.currentTimeMillis())
        stallReported.set(false)
        lastAnrSignature = null
        
        // 尝试获取主线程引用
        try {
            mainThread = getMainThread()
            AppLogger.d(tag, "已获取主线程引用: $mainThread")
        } catch (e: Exception) {
            AppLogger.e(tag, "获取主线程引用失败", e)
        }
        
        try {
            // 尝试启动协程监控
            monitoringJob = coroutineScope.launch(Dispatchers.Default) {
                while (running.get()) {
                    checkMainThreadHealth()
                    delay(SAMPLING_INTERVAL_MS)
                }
            }
        } catch (e: Exception) {
            // 如果协程启动失败，使用线程池作为备选方案
            AppLogger.e(tag, "协程启动失败，使用备选线程池监控", e)
            startUsingExecutor()
        }
    }
    
    /**
     * 使用ScheduledExecutorService开始监控（备选方案）
     */
    private fun startUsingExecutor() {
        if (scheduledExecutor == null || scheduledExecutor?.isShutdown == true) {
            scheduledExecutor = Executors.newSingleThreadScheduledExecutor { r ->
                val t = Thread(r, "AnrMonitor-Watchdog")
                t.priority = Thread.MAX_PRIORITY
                t.isDaemon = true
                t
            }
        }
        
        scheduledExecutor?.scheduleAtFixedRate({
            if (running.get()) {
                checkMainThreadHealth()
            } else {
                scheduledExecutor?.shutdown()
            }
        }, 0, SAMPLING_INTERVAL_MS, TimeUnit.MILLISECONDS)
    }
    
    /**
     * 停止ANR监控
     */
    fun stop() {
        if (!running.getAndSet(false)) {
            return
        }
        
        AppLogger.d(tag, "停止ANR监控器，监控结果：ANR次数=${anrCount.get()}, 警告次数=${warningCount.get()}, 最长阻塞时间=${maxBlockDuration.get()}ms")
        monitoringJob?.cancel()
        scheduledExecutor?.shutdown()
        
        // 如果有记录到ANR，保存报告
        if (anrCount.get() > 0 || warningCount.get() > 0) {
            saveAnrReport()
        }
    }
    
    /**
     * 报告主线程正常响应
     */
    fun reportThreadHealthy() {
        lastResponseTime.set(System.currentTimeMillis())
    }
    
    /**
     * 报告主线程响应缓慢
     */
    fun reportSlowResponse(responseTime: Long) {
        if (responseTime > WARNING_THRESHOLD_MS) {
            warningCount.incrementAndGet()
            if (responseTime > maxBlockDuration.get()) {
                maxBlockDuration.set(responseTime)
            }
            
            if (responseTime > ANR_THRESHOLD_MS) {
                val anrCount = anrCount.incrementAndGet()
                AppLogger.e(tag, "检测到可能的ANR! 响应时间: ${responseTime}ms, 这是第${anrCount}次ANR")
                captureFullThreadDump()
            } else {
                AppLogger.w(tag, "主线程响应缓慢: ${responseTime}ms")
            }
        }
    }
    
    /**
     * 添加调用者信息，帮助跟踪ANR来源
     */
    fun addCallerInfo(key: String, info: String) {
        callerInfo[key] = "[$key] $info (${SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())})"
    }
    
    /**
     * 检查主线程健康状态
     */
    private fun checkMainThreadHealth() {
        mainThreadHandler.post {
            reportThreadHealthy()
        }

        val now = System.currentTimeMillis()
        val lastResponse = lastResponseTime.get()
        val timeSinceLastResponse = now - lastResponse

        if (timeSinceLastResponse <= WARNING_THRESHOLD_MS) {
            // 主线程已恢复响应：本轮卡顿结束，让下一次卡顿重新采集，而不是一直被当成重复ANR
            if (stallReported.getAndSet(false)) {
                lastAnrSignature = null
            }
            return
        }

        // 主线程可能被阻塞
        val message = context.getString(R.string.anr_main_thread_not_responding, timeSinceLastResponse)

        if (timeSinceLastResponse > ANR_THRESHOLD_MS) {
            // 每次卡顿只计一次ANR：100ms采样否则会把一次卡顿刷成几十次
            if (stallReported.compareAndSet(false, true)) {
                AppLogger.e(tag, "$message - 可能发生ANR!")
                anrCount.incrementAndGet()
            }

            // 记录堆栈跟踪 - 使用增强的堆栈捕获
            captureFullThreadDump()

            if (timeSinceLastResponse > maxBlockDuration.get()) {
                maxBlockDuration.set(timeSinceLastResponse)
            }
        } else {
            // 超过警告阈值但未到ANR阈值
            AppLogger.w(tag, "$message - 警告")
            warningCount.incrementAndGet()
        }
    }
    
    /**
     * 捕获主线程堆栈
     */
    private fun captureMainThreadStack() {
        mainThreadHandler.post {
            try {
                val stackTrace = AnrCaptureBudget.formatFrames(Thread.currentThread().stackTrace, skipFrames = 3)

                val timeStamp = System.currentTimeMillis()
                val trace = Pair(timeStamp, stackTrace)

                synchronized(stackTraces) {
                    stackTraces.add(trace)
                    // 限制堆栈历史数量
                    if (stackTraces.size > MAX_STACK_TRACES) {
                        stackTraces.removeAt(0)
                    }
                }

                // 分析堆栈
                val analysis = analyzeStackTrace(stackTrace)

                AppLogger.e(tag, "主线程堆栈跟踪:\n$stackTrace\n$analysis")
            } catch (e: Exception) {
                AppLogger.e(tag, "捕获堆栈失败", e)
            }
        }
    }
    
    /**
     * 获取主线程实例的引用
     */
    private fun getMainThread(): Thread? {
        try {
            // 尝试方法1：通过Looper的对应线程
            Looper.getMainLooper().thread?.let { return it }
            
            // 尝试方法2：遍历所有线程查找main线程
            val threadGroup = Thread.currentThread().threadGroup ?: return null
            val threadCount = threadGroup.activeCount()
            val threads = arrayOfNulls<Thread>(threadCount)
            threadGroup.enumerate(threads)
            
            return threads.filterNotNull().find { it.name == MAIN_THREAD_NAME }
        } catch (e: Exception) {
            AppLogger.e(tag, "获取主线程失败", e)
            return null
        }
    }
    
    /**
     * 捕获完整的线程转储信息，包括主线程和其他重要线程
     */
    private fun captureFullThreadDump() {
        // 冷却期内不再重复采集：主线程持续阻塞时每100ms的采样只会走到这里一次
        if (!captureThrottle.tryAcquire(SystemClock.elapsedRealtime())) {
            return
        }

        try {
            val stack = readMainThreadStack()
            if (stack == null) {
                AppLogger.e(tag, "检测到ANR，但主线程引用不可用\n${context.getString(R.string.anr_cannot_get_main_thread_ref)}")
                return
            }

            // 先做重复ANR判断，避免在字符串构建和日志落盘上重复付出代价
            if (stack.signature == lastAnrSignature) {
                AppLogger.w(tag, "检测到重复的ANR，跳过输出")
                return
            }
            lastAnrSignature = stack.signature

            val sbDump = StringBuilder(AnrCaptureBudget.MAX_DUMP_CHARS / 4)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

            sbDump.append(context.getString(R.string.anr_thread_dump_header, dateFormat.format(Date())))
            sbDump.append(context.getString(R.string.anr_main_thread_section, stack.dump))

            // 添加主线程分析
            val analysis = analyzeStackTrace(stack.dump)
            sbDump.append(context.getString(R.string.anr_analysis_section, analysis))

            // 获取并添加调用者信息
            if (callerInfo.isNotEmpty()) {
                sbDump.append(context.getString(R.string.anr_recent_call_info))
                callerInfo.forEach { (_, info) -> sbDump.append("$info\n") }
                sbDump.append("\n")
            }

            val dumpText = AnrCaptureBudget.clampDump(sbDump)

            // 保存线程转储
            val timestamp = System.currentTimeMillis()
            val trace = Pair(timestamp, dumpText)

            // 更新堆栈跟踪历史
            synchronized(stackTraces) {
                stackTraces.add(trace)
                if (stackTraces.size > MAX_STACK_TRACES) {
                    stackTraces.removeAt(0)
                }
            }

            // 输出到日志
            AppLogger.e(tag, "检测到ANR! 完整线程转储:\n$dumpText")

        } catch (oom: OutOfMemoryError) {
            // OutOfMemoryError是Error不是Exception，不接住会让监控器把进程一起带走
            AppLogger.e(tag, "捕获线程转储时内存不足，已跳过本次转储")
        } catch (e: Exception) {
            AppLogger.e(tag, "捕获线程转储失败", e)
            // 失败时尝试旧方法
            captureMainThreadStack()
        }
    }

    /**
     * 一次性读取主线程堆栈：同一次采样只取一份 stackTrace 数组，供去重签名和转储共用。
     *
     * @return null 表示主线程引用不可用。
     */
    private fun readMainThreadStack(): MainThreadStack? {
        val thread = mainThread ?: getMainThread() ?: return null
        mainThread = thread

        val frames = try {
            thread.stackTrace
        } catch (e: Exception) {
            AppLogger.e(tag, "无法获取主线程堆栈", e)
            return MainThreadStack(
                signature = "unreadable:${e.message.orEmpty()}",
                dump = context.getString(R.string.anr_cannot_get_main_thread_stack, e.message ?: ""),
            )
        }

        val stackText = AnrCaptureBudget.formatFrames(frames)
        return MainThreadStack(
            signature = AnrCaptureBudget.stackSignature(frames),
            dump = context.getString(R.string.anr_main_thread_with_state, thread.name, thread.state, stackText),
        )
    }

    private class MainThreadStack(val signature: String, val dump: String)
    
    /**
     * 分析堆栈跟踪，提取并列出堆栈中的包名（仅保留com.ai.assistance.operit包）
     */
    private fun analyzeStackTrace(stackTrace: String): String {
        val analysis = StringBuilder()
        val targetPackage = "com.ai.assistance.operit"
        val lines = mutableListOf<String>()
        
        for (line in stackTrace.lines()) {
            // 匹配堆栈行格式: at package.Class.method(File.java:line)
            val atIndex = line.indexOf("at ")
            if (atIndex >= 0) {
                val stackPart = line.substring(atIndex + 3).trim()
                // 只保留com.ai.assistance.operit包的堆栈
                if (stackPart.startsWith(targetPackage)) {
                    lines.add(line.trim())
                }
            }
        }
        
        // 输出捕捉到的堆栈行
        if (lines.isNotEmpty()) {
            analysis.append(context.getString(R.string.anr_package_calls, targetPackage, lines.size))
            lines.forEach { line ->
                analysis.append("$line\n")
            }
        } else {
            analysis.append(context.getString(R.string.anr_cannot_extract_package_info, targetPackage))
        }
        
        return analysis.toString()
    }
    
    /**
     * 保存ANR报告到文件
     */
    private fun saveAnrReport() {
        try {
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val timestamp = dateFormat.format(Date())
            val fileName = "anr_report_${timestamp}.txt"
            
            val file = File(context.getExternalFilesDir("anr_reports"), fileName)
            file.parentFile?.mkdirs()
            
            FileOutputStream(file).use { fos ->
                OutputStreamWriter(fos).use { writer ->
                    writer.write(context.getString(R.string.anr_report_header))
                    writer.write(context.getString(R.string.anr_report_time, SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())))
                    writer.write(context.getString(R.string.anr_report_anr_count, anrCount.get()))
                    writer.write(context.getString(R.string.anr_report_warning_count, warningCount.get()))
                    writer.write(context.getString(R.string.anr_report_max_block, maxBlockDuration.get()))

                    writer.write(context.getString(R.string.anr_system_info_header))
                    writer.write(context.getString(R.string.anr_android_version, android.os.Build.VERSION.SDK_INT))
                    writer.write(context.getString(R.string.anr_device, android.os.Build.MANUFACTURER, android.os.Build.MODEL))
                    writer.write(context.getString(R.string.anr_memory_info))
                    val rt = Runtime.getRuntime()
                    writer.write(context.getString(R.string.anr_max_memory, rt.maxMemory() / 1024 / 1024))
                    writer.write(context.getString(R.string.anr_allocated_memory, rt.totalMemory() / 1024 / 1024))
                    writer.write(context.getString(R.string.anr_free_memory, rt.freeMemory() / 1024 / 1024))

                    writer.write(context.getString(R.string.anr_stack_trace_history_header))
                    synchronized(stackTraces) {
                        stackTraces.forEach { (time, stack) ->
                            val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(time))
                            writer.write(context.getString(R.string.anr_time, timeStr))
                            writer.write("$stack\n\n")
                        }
                    }

                    writer.write(context.getString(R.string.anr_caller_info_header))
                    callerInfo.forEach { (_, info) -> 
                        writer.write("$info\n") 
                    }
                }
            }
            
            AppLogger.i(tag, "ANR报告已保存到: ${file.absolutePath}")
        } catch (e: Exception) {
            AppLogger.e(tag, "保存ANR报告失败", e)
        }
    }
} 