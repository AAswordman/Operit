package com.ai.assistance.operit.util.logging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import com.ai.assistance.operit.util.logging.DailyLogWritePolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.util.Log
import com.ai.assistance.operit.core.application.OperitApplication
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.regex.Pattern
import com.ai.assistance.operit.core.diagnostics.ThrowableTextFormatter
import com.ai.assistance.operit.util.files.OperitPaths

/**
 * App-wide logger with an API closely mirroring [com.ai.assistance.operit.util.logging.AppLogger].
 *
 * It forwards all logs to the system Log and also persists them to
 * an internal file so that the app can export logs for debugging.
 */
object AppLogger {

    // Mirror com.ai.assistance.operit.util.logging.AppLogger priority constants
    const val VERBOSE: Int = Log.VERBOSE
    const val DEBUG: Int = Log.DEBUG
    const val INFO: Int = Log.INFO
    const val WARN: Int = Log.WARN
    const val ERROR: Int = Log.ERROR
    const val ASSERT: Int = Log.ASSERT

    // Log file configuration
    private const val LOG_DIR_NAME = "logs"
    private const val LOG_FILE_NAME = "operit.log"
    private const val PACKAGE_LOG_DIR_NAME = "packageLogs"
    private const val MAX_PACKAGE_LOG_FILES = 20
    private const val TOOLPKG_LOG_TAG = "ToolPkg"
    private const val MAX_LOG_MESSAGE_CHARS = 12_000
    private const val MAX_LOG_THROWABLE_CHARS = 24_000

    // Simple date formatter for log lines
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val startupFileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
    private val packageLogFileNamePattern = Pattern.compile("\\d{8}_\\d{6}_\\d{3}\\.log")
    private val packageIdRegexes = listOf(
        Pattern.compile("""\btoolPkgId=([A-Za-z0-9._:-]+)\b"""),
        Pattern.compile("""\bpackage(?:/subpackage)?=([A-Za-z0-9._:-]+)\b"""),
        Pattern.compile("""\bcontainer=([A-Za-z0-9._:-]+)\b"""),
        Pattern.compile("""\btarget=([A-Za-z0-9._:-]+)\b""")
    )
    private val scriptRegexes = listOf(
        Pattern.compile("""\bscript=([^\s,]+)"""),
        Pattern.compile("""\bpath=([^\s,]+)"""),
        Pattern.compile("""\bscreen=([^\s,]+)"""),
        Pattern.compile("""\bfunction=([A-Za-z0-9_.$:-]+)\b""")
    )
    private val pluginRegexes = listOf(
        Pattern.compile("""\bplugin=([A-Za-z0-9._:-]+)\b"""),
        Pattern.compile("""\bpluginId=([A-Za-z0-9._:-]+)\b"""),
        Pattern.compile("""\bhookId=([A-Za-z0-9._:-]+)\b""")
    )

    /**
     * Optional external switch to completely disable file logging if needed.
     * System AppLogger.* calls will still be performed.
     */
    @Volatile
    var enableFileLogging: Boolean = true

    /**
     * JVM 单元测试开关：关闭对 [android.util.Log] 的调用（返回 0/false），避免
     * 纯 JVM 环境抛 "not mocked" 异常。与 [enableFileLogging] 独立——文件日志照常。
     */
    @Volatile
    var enableSystemLog: Boolean = true

    @Volatile
    private var logFile: File? = null
    @Volatile
    private var packageLogFile: File? = null

    @Volatile
    private var boundContext: Context? = null
    private val fileWriteLock = Any()
    private val logPolicyLock = Any()
    private val dailyWritePolicy = DailyLogWritePolicy()
    private val _logWritingEnabled = MutableStateFlow(true)
    val logWritingEnabled = _logWritingEnabled.asStateFlow()
    private var logWritePreferences: SharedPreferences? = null
    @Volatile
    private var logWriteGeneration = 0L
    private val fileLogExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "OperitAppLogger").apply {
            isDaemon = true
        }
    }

    @JvmStatic
    fun bindContext(context: Context) {
        synchronized(fileWriteLock) {
            if (boundContext != null) return
            val appContext = context.applicationContext
            boundContext = appContext
            val preferences = appContext.getSharedPreferences("app_log_writing", Context.MODE_PRIVATE)
            logWritePreferences = preferences
            synchronized(logPolicyLock) {
                dailyWritePolicy.restore(preferences.getString("disabled_date", null))
                refreshLogWritingEnabled()
            }
            ContextCompat.registerReceiver(
                appContext,
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        refreshLogWritingEnabled()
                    }
                },
                IntentFilter().apply {
                    addAction(Intent.ACTION_DATE_CHANGED)
                    addAction(Intent.ACTION_TIME_CHANGED)
                    addAction(Intent.ACTION_TIMEZONE_CHANGED)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    @JvmStatic
    fun refreshLogWritingEnabled(): Boolean = synchronized(logPolicyLock) {
        dailyWritePolicy.isEnabled().also { _logWritingEnabled.value = it }
    }

    @JvmStatic
    fun setLogWritingEnabled(enabled: Boolean) {
        synchronized(fileWriteLock) {
            synchronized(logPolicyLock) {
                val preferences = requireNotNull(logWritePreferences)
                dailyWritePolicy.setEnabled(enabled)
                // 让关闭之前排队的任务失效，重新开启也不补写这些旧日志。
                logWriteGeneration += 1
                preferences.edit().putString("disabled_date", dailyWritePolicy.disabledDate).apply()
                refreshLogWritingEnabled()
            }
        }
    }

    private fun resolveLogFile(): File? {
        val existing = logFile
        if (existing != null) return existing

        return try {
            val appContext: Context = try {
                OperitApplication.instance.applicationContext
            } catch (_: Throwable) {
                boundContext ?: return null
            }
            val dir = File(appContext.filesDir, LOG_DIR_NAME)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            File(dir, LOG_FILE_NAME).also { file ->
                logFile = file
            }
        } catch (e: Throwable) {
            null
        }
    }

    private fun resolvePackageLogFile(): File? {
        val existing = packageLogFile
        if (existing != null) return existing

        return try {
            val appContext: Context = try {
                OperitApplication.instance.applicationContext
            } catch (_: Throwable) {
                boundContext ?: return null
            }
            val dir = File(OperitPaths.operitRootDir(), PACKAGE_LOG_DIR_NAME)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val startupMs = OperitApplication.appStartupTimeMs.takeIf { it > 0L } ?: System.currentTimeMillis()
            val fileName = startupFileDateFormat.format(Date(startupMs)) + ".log"
            File(dir, fileName).also { file ->
                // Prune before the first write so the new startup file fits within the cap.
                if (!file.exists()) {
                    prunePackageLogFiles(dir)
                }
                packageLogFile = file
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun prunePackageLogFiles(dir: File) {
        val files = dir.listFiles { file ->
            file.isFile && packageLogFileNamePattern.matcher(file.name).matches()
        }?.sortedBy { it.name } ?: return

        var remainingFiles = files.size
        for (file in files) {
            if (remainingFiles < MAX_PACKAGE_LOG_FILES) {
                break
            }
            if (file.delete()) {
                remainingFiles -= 1
            }
        }
    }

    // --- Public API mirroring com.ai.assistance.operit.util.logging.AppLogger ---

    @JvmStatic
    fun v(tag: String, msg: String): Int {
        writeToFile(VERBOSE, tag, msg, null)
        return if (enableSystemLog && refreshLogWritingEnabled()) Log.v(tag, msg) else 0
    }

    @JvmStatic
    fun v(tag: String, msg: String, tr: Throwable): Int {
        writeToFile(VERBOSE, tag, msg, tr)
        return if (enableSystemLog && refreshLogWritingEnabled()) Log.v(tag, msg, tr) else 0
    }

    @JvmStatic
    fun d(tag: String, msg: String): Int {
        writeToFile(DEBUG, tag, msg, null)
        return if (enableSystemLog && refreshLogWritingEnabled()) Log.d(tag, msg) else 0
    }

    @JvmStatic
    fun d(tag: String, msg: String, tr: Throwable): Int {
        writeToFile(DEBUG, tag, msg, tr)
        return if (enableSystemLog && refreshLogWritingEnabled()) Log.d(tag, msg, tr) else 0
    }

    @JvmStatic
    fun i(tag: String, msg: String): Int {
        writeToFile(INFO, tag, msg, null)
        return if (enableSystemLog && refreshLogWritingEnabled()) Log.i(tag, msg) else 0
    }

    @JvmStatic
    fun i(tag: String, msg: String, tr: Throwable): Int {
        writeToFile(INFO, tag, msg, tr)
        return if (enableSystemLog && refreshLogWritingEnabled()) Log.i(tag, msg, tr) else 0
    }

    @JvmStatic
    fun w(tag: String, msg: String): Int {
        writeToFile(WARN, tag, msg, null)
        return if (enableSystemLog && refreshLogWritingEnabled()) Log.w(tag, msg) else 0
    }

    @JvmStatic
    fun w(tag: String, msg: String, tr: Throwable): Int {
        writeToFile(WARN, tag, msg, tr)
        return if (enableSystemLog && refreshLogWritingEnabled()) Log.w(tag, msg, tr) else 0
    }

    @JvmStatic
    fun w(tag: String, tr: Throwable): Int {
        writeToFile(WARN, tag, "", tr)
        return if (enableSystemLog && refreshLogWritingEnabled()) Log.w(tag, tr) else 0
    }

    @JvmStatic
    fun e(tag: String, msg: String): Int {
        writeToFile(ERROR, tag, msg, null)
        return if (enableSystemLog && refreshLogWritingEnabled()) Log.e(tag, msg) else 0
    }

    @JvmStatic
    fun e(tag: String, msg: String, tr: Throwable): Int {
        writeToFile(ERROR, tag, msg, tr)
        return if (enableSystemLog && refreshLogWritingEnabled()) Log.e(tag, msg, tr) else 0
    }

    @JvmStatic
    fun wtf(tag: String, msg: String): Int {
        writeToFile(ASSERT, tag, msg, null)
        return if (enableSystemLog && refreshLogWritingEnabled()) Log.wtf(tag, msg) else 0
    }

    @JvmStatic
    fun wtf(tag: String, msg: String, tr: Throwable): Int {
        writeToFile(ASSERT, tag, msg, tr)
        return if (enableSystemLog && refreshLogWritingEnabled()) Log.wtf(tag, msg, tr) else 0
    }

    @JvmStatic
    fun wtf(tag: String, tr: Throwable): Int {
        writeToFile(ASSERT, tag, "", tr)
        return if (enableSystemLog && refreshLogWritingEnabled()) Log.wtf(tag, tr) else 0
    }

    @JvmStatic
    fun isLoggable(tag: String, level: Int): Boolean {
        return enableSystemLog && refreshLogWritingEnabled() && Log.isLoggable(tag, level)
    }

    @JvmStatic
    fun println(priority: Int, tag: String, msg: String): Int {
        writeToFile(priority, tag, msg, null)
        return if (enableSystemLog && refreshLogWritingEnabled()) Log.println(priority, tag, msg) else 0
    }

    @JvmStatic
    fun getStackTraceString(tr: Throwable): String {
        return ThrowableTextFormatter.format(tr, MAX_LOG_THROWABLE_CHARS)
    }

    /**
     * Returns the current log file (if available) so that callers can export it.
     */
    @JvmStatic
    fun getLogFile(): File? = resolveLogFile()

    @JvmStatic
    fun resetLogFile() {
        try {
            val appContext: Context = OperitApplication.instance.applicationContext
            val dir = File(appContext.filesDir, LOG_DIR_NAME)
            val file = File(dir, LOG_FILE_NAME)
            if (file.exists()) {
                file.delete()
            }
            logFile = null
            packageLogFile = null
        } catch (e: Throwable) {
            // Ignore errors during reset to avoid crashing on startup
        }
    }

    // --- Internal helpers ---

    private fun writeToFile(priority: Int, tag: String, msg: String, tr: Throwable?) {
        val generation = synchronized(logPolicyLock) {
            if (!enableFileLogging || !refreshLogWritingEnabled()) return
            logWriteGeneration
        }
        try {
            fileLogExecutor.execute {
                writeToFileSync(priority, tag, msg, tr, generation)
            }
        } catch (_: RejectedExecutionException) {
        }
    }

    private fun writeToFileSync(priority: Int, tag: String, msg: String, tr: Throwable?, generation: Long) {
        // 切换开关与实际文件追加互斥，关闭返回后主日志和工具包日志都不会继续写入。
        synchronized(fileWriteLock) {
            if (!enableFileLogging || generation != logWriteGeneration || !refreshLogWritingEnabled()) return
            val file = resolveLogFile() ?: return

            val time = dateFormat.format(Date())
            val normalizedMessage = normalizeLogMessage(msg)
            val throwableText = tr?.let { ThrowableTextFormatter.format(it, MAX_LOG_THROWABLE_CHARS) }
            val levelChar = when (priority) {
                VERBOSE -> 'V'
                DEBUG -> 'D'
                INFO -> 'I'
                WARN -> 'W'
                ERROR -> 'E'
                ASSERT -> 'A'
                else -> '?'
            }

            val builder = StringBuilder()
            builder.append(time)
                .append(" ")
                .append(levelChar)
                .append("/")
                .append(tag)
                .append(": ")
                .append(normalizedMessage)

            if (throwableText != null) {
                builder.append("\n").append(throwableText)
            }

            builder.append('\n')

            try {
                FileWriter(file, true).use { writer ->
                    writer.write(builder.toString())
                }
            } catch (e: IOException) {
                // 写盘失败时不递归记录日志，避免影响应用运行
            }

            writeToPackageLogIfNeeded(
                tag = tag,
                msg = normalizedMessage,
                throwableText = throwableText,
                time = time,
                levelChar = levelChar
            )
        }
    }
    private fun writeToPackageLogIfNeeded(
        tag: String,
        msg: String,
        throwableText: String?,
        time: String,
        levelChar: Char
    ) {
        if (!shouldMirrorToPackageLog(tag, msg)) {
            return
        }
        val file = resolvePackageLogFile() ?: return
        val packageId = extractFirstMatch(msg, packageIdRegexes)
        val scriptId = extractFirstMatch(msg, scriptRegexes)
        val pluginId = extractFirstMatch(msg, pluginRegexes)

        val builder = StringBuilder()
        builder.append(time)
            .append(" ")
            .append(levelChar)
            .append("/")
            .append(TOOLPKG_LOG_TAG)
            .append(" ")

        if (!packageId.isNullOrBlank()) {
            builder.append("[PKG:")
                .append(packageId)
                .append("]")
        }
        if (!scriptId.isNullOrBlank()) {
            builder.append("[SCRIPT:")
                .append(scriptId)
                .append("]")
        }
        if (!pluginId.isNullOrBlank()) {
            builder.append("[PLUGIN:")
                .append(pluginId)
                .append("]")
        }
        if (builder.isNotEmpty() && builder[builder.length - 1] != ' ') {
            builder.append(" ")
        }
        builder
            .append(msg)

        if (throwableText != null) {
            builder.append("\n").append(throwableText)
        }
        builder.append('\n')

        try {
            FileWriter(file, true).use { writer ->
                writer.write(builder.toString())
            }
        } catch (_: IOException) {
        }
    }

    private fun shouldMirrorToPackageLog(tag: String, msg: String): Boolean {
        if (!tag.equals(TOOLPKG_LOG_TAG, ignoreCase = true)) {
            return false
        }
        return true
    }

    private fun normalizeLogMessage(msg: String): String {
        return ThrowableTextFormatter.truncateText(msg, MAX_LOG_MESSAGE_CHARS)
    }

    private fun extractFirstMatch(text: String, patterns: List<Pattern>): String? {
        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val value = matcher.group(1)?.trim().orEmpty()
                if (value.isNotEmpty()) {
                    return value
                }
            }
        }
        return null
    }
}
