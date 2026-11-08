package com.ai.assistance.operit.ui.error.report

import com.ai.assistance.operit.ui.features.management.databackup.logcat.LogExportFilter
import com.ai.assistance.operit.ui.features.management.databackup.logcat.LogExportOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 分类只表示异常特征，不把关键词匹配结果当成已确认的根因。 */
internal enum class CrashReason(val exceptionNames: Set<String>) {
    MEMORY(setOf("outofmemoryerror")),
    STACK_OVERFLOW(setOf("stackoverflowerror")),
    STORAGE_FULL(setOf("sqlitefullexception")),
    PERMISSION(setOf("securityexception", "accessdeniedexception")),
    NETWORK(
        setOf(
            "unknownhostexception",
            "sockettimeoutexception",
            "connectexception",
            "socketexception",
            "sslexception",
            "sslhandshakeexception",
            "sslpeerunverifiedexception",
        )
    ),
    DATABASE(
        setOf(
            "sqliteexception",
            "sqlitedatabasecorruptexception",
            "sqliteconstraintexception",
            "sqlitediskioexception",
            "sqlitelockedexception",
        )
    ),
    FILE_ACCESS(setOf("filenotfoundexception", "ioexception", "filesystemexception")),
    COMPONENT_LOADING(
        setOf(
            "unsatisfiedlinkerror",
            "noclassdeffounderror",
            "classnotfoundexception",
            "nosuchmethoderror",
            "nosuchfielderror",
            "verifyerror",
            "linkageerror",
        )
    ),
    REMOTE_PROCESS(
        setOf(
            "deadobjectexception",
            "deadserviceexception",
            "transactiontoolargeexception",
            "remoteexception",
        )
    ),
    NULL_REFERENCE(
        setOf(
            "nullpointerexception",
            "kotlinnullpointerexception",
            "uninitializedpropertyaccessexception",
        )
    ),
    INVALID_STATE(
        setOf(
            "illegalstateexception",
            "illegalargumentexception",
            "indexoutofboundsexception",
            "arrayindexoutofboundsexception",
            "stringindexoutofboundsexception",
            "classcastexception",
            "concurrentmodificationexception",
        )
    ),
    UNKNOWN(emptySet()),
}

internal object CrashReasonClassifier {
    private val exceptionHeader =
        Regex(
            """^\s*(?:(?:Caused by|Suppressed):\s*)?((?:[\w$]+\.)*[\w$]*(?:Exception|Error))(?=:|\s*$)""",
            setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE),
        )

    fun exceptionType(stackTrace: String): String? =
        exceptionHeader
            .findAll(stackTrace)
            .lastOrNull()
            ?.groupValues
            ?.get(1)
            ?.substringAfterLast('.')

    fun classify(stackTrace: String): CrashReason {
        val headers =
            stackTrace.lineSequence().filter { exceptionHeader.containsMatchIn(it) }.toList()
        val names =
            headers
                .mapNotNull {
                    exceptionHeader
                        .find(it)
                        ?.groupValues
                        ?.get(1)
                        ?.substringAfterLast('.')
                        ?.lowercase(Locale.ROOT)
                }
                .toSet()
        // 空间及权限错误常被包装为文件读写异常，只检查异常行而不匹配普通堆栈文本。
        val messages = headers.joinToString("\n").lowercase(Locale.ROOT)
        if ("outofmemoryerror" in names) return CrashReason.MEMORY
        if ("stackoverflowerror" in names) return CrashReason.STACK_OVERFLOW
        if (
            "sqlitefullexception" in names ||
                "enospc" in messages ||
                "no space left on device" in messages
        )
            return CrashReason.STORAGE_FULL
        if (
            "securityexception" in names ||
                "accessdeniedexception" in names ||
                "eacces" in messages ||
                "eperm" in messages ||
                "permission denial" in messages
        )
            return CrashReason.PERMISSION
        return CrashReason.entries.firstOrNull { reason ->
            reason.exceptionNames.any { it in names }
        } ?: CrashReason.UNKNOWN
    }
}

/** 页面和复制只接收过滤后的堆栈，错误报告文件使用同次过滤生成的日志文本。 */
internal data class CrashReport(
    val reason: CrashReason,
    val exceptionType: String?,
    val occurredAtMillis: Long,
    val options: LogExportOptions,
    val stackTrace: String,
    val exportText: String,
) {
    val showTime: Boolean
        get() = !options.stripTimestamp
}

internal fun prepareCrashReport(
    rawStackTrace: String,
    occurredAtMillis: Long,
    options: LogExportOptions,
): CrashReport {
    val timestamp =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(occurredAtMillis))
    val prefix = "$timestamp E/CrashReportActivity: "
    // 给独立堆栈补上错误级记录头，复用全部过滤规则，并使仅错误上下文保留本次崩溃。
    val filtered = LogExportFilter.filterText(prefix + rawStackTrace, options)
    val visiblePrefix = if (options.stripTimestamp) "E/CrashReportActivity: " else prefix
    val visible =
        filtered.lines
            .mapIndexed { index, line ->
                if (index == 0) line.removePrefix(visiblePrefix) else line
            }
            .joinToString("\n")
    return CrashReport(
        reason = CrashReasonClassifier.classify(rawStackTrace),
        exceptionType = CrashReasonClassifier.exceptionType(rawStackTrace),
        occurredAtMillis = occurredAtMillis,
        options = options,
        stackTrace = visible,
        exportText = filtered.lines.joinToString("\n"),
    )
}
