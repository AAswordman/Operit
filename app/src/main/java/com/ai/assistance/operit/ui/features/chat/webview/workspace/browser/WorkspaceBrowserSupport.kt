package com.ai.assistance.operit.ui.features.chat.webview.workspace.browser

import java.io.File
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.Year
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoField
import java.util.Locale

/** 创建与重命名只接受当前目录中的单个名称，不能夹带路径段。 */
internal fun isValidWorkspaceEntryName(name: String): Boolean =
    name.isNotBlank() && name != "." && name != ".." &&
        '/' !in name && '\\' !in name && '\u0000' !in name

/** 将文件工具已提供的不同时间表示转换为同一比较值；未知格式保持无时间状态。 */
internal fun directoryModifiedTime(value: String): Long? {
    value.toLongOrNull()?.let { return it }
    return try {
        when {
            Regex("\\d{4}-\\d{2}-\\d{2}T.+(?:Z|[+-]\\d{2}:\\d{2})").matches(value) ->
                OffsetDateTime.parse(value).toInstant().toEpochMilli()
            Regex("\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?").matches(value) ->
                LocalDateTime.parse(value.replace(' ', 'T')).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            Regex("[A-Za-z]{3} \\d{1,2} \\d{2}:\\d{2}").matches(value) -> {
                // 本机文件工具的短格式没有年份，使用当前年份比较同一列表中的月日。
                val format = DateTimeFormatterBuilder().appendPattern("MMM d HH:mm")
                    .parseDefaulting(ChronoField.YEAR, Year.now().value.toLong())
                    .toFormatter(Locale.US)
                LocalDateTime.parse(value, format).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
            else -> null
        }
    } catch (_: DateTimeParseException) {
        null
    }
}

private const val WORKSPACE_SHARE_RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000

/** 只清理应用分享缓存中过期的目录，最近共享的 URI 所需文件继续保留。 */
internal fun pruneWorkspaceShareCache(directory: File, now: Long = System.currentTimeMillis()) {
    val expiration = now - WORKSPACE_SHARE_RETENTION_MILLIS
    directory.listFiles()?.filter { it.isDirectory }?.forEach { entry ->
        val lastUse = maxOf(entry.lastModified(), entry.listFiles()?.maxOfOrNull { it.lastModified() } ?: 0L)
        if (lastUse < expiration) entry.deleteRecursively()
    }
}