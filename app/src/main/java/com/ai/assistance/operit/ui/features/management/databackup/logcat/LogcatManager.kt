package com.ai.assistance.operit.ui.features.management.databackup.logcat

import android.content.Context
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.*

/**
 * 日志管理器 - 从AppLogger的日志文件读取日志
 */
class LogcatManager(private val context: Context) {
    private val TAG = "LogcatManager"

    // 日志格式解析
    private val logPattern = "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+([VDIWEAF])/(.*?): (.*)".toRegex()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())


    /**
     * 加载所有历史日志
     */
    suspend fun loadInitialLogs(): List<LogRecord> = withContext(Dispatchers.IO) {
        val logFile = AppLogger.getLogFile()
        if (logFile == null || !logFile.exists()) {
            return@withContext emptyList()
        }
        try {
            logFile.readLines().mapNotNull { parseLogLine(it) }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to load initial logs", e)
            emptyList()
        }
    }

    /**
     * 清除日志文件
     */
    fun clearLogs() {
        AppLogger.resetLogFile()
    }

    /**
     * 解析AppLogger格式的日志行
     * 如果不匹配标准格式，则退化为UNKNOWN级别的原始行，避免丢日志
     */
    private fun parseLogLine(line: String): LogRecord? {
        val match = logPattern.matchEntire(line)
        if (match != null) {
            val (timestampStr, levelStr, tag, message) = match.destructured
            val level = when (levelStr) {
                "V" -> LogLevel.VERBOSE
                "D" -> LogLevel.DEBUG
                "I" -> LogLevel.INFO
                "W" -> LogLevel.WARNING
                "E" -> LogLevel.ERROR
                "A" -> LogLevel.FATAL // Assert as Fatal
                else -> LogLevel.UNKNOWN
            }
            val timestamp = try {
                dateFormat.parse(timestampStr)?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }

            return LogRecord(
                timestamp = timestamp,
                level = level,
                tag = tag.trim(),
                message = message.trim()
            )
        }

        // 非首行或异常格式（如堆栈跟踪），仍然显示出来
        return if (line.isNotBlank()) {
            LogRecord(
                message = line,
                level = LogLevel.UNKNOWN,
                timestamp = System.currentTimeMillis(),
                tag = null
            )
        } else {
            null
        }
    }
}
