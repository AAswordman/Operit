package com.ai.assistance.operit.ui.features.toolbox.screens.logcat

/**
 * Parses AppLogger lines and applies export-only filters.
 * Does not change how logs are written.
 */
object LogExportFilter {
    private val headerRegex =
        Regex("^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+([VDIWEAF])/(.*?): (.*)$")

    private val systemTags = setOf(
        "androidruntime",
        "system",
        "system.err",
        "system.out",
        "webview",
        "chromium",
        "chromiumnet",
        "okhttp",
        "okhttpclient",
        "libc",
        "dalvikvm",
        "art",
        "activitymanager",
        "packagemanager",
        "windowmanager"
    )

    private val sensitiveHeaderPrefixes = listOf(
        "发现系统消息:",
        "请求体json:",
        "request body json:",
        "request body:",
        "json del cuerpo de la solicitud:",
        "json badan permintaan:",
        "요청 본문 json:",
        "corpo da solicitação json:",
        "corpul cereriijson:",
        "final deepseek reasoning mode request body:",
        "final kimi k2.5 request body:",
        "claude请求体:",
        "final prompt before llama generation:"
    )

    private val sensitiveContinuationPrefixes = listOf(
        "part "
    )

    private val sensitiveBodyTokens = listOf(
        "\"systeminstruction\"",
        "\"character_setting\"",
        "\"charactersetting\"",
        "\"advanced_custom_prompt\"",
        "\"tag_prompt\""
    )

    data class ExportRecord(
        val timestamp: String?,
        val level: Char?,
        val tag: String?,
        val message: String,
        val continuationLines: List<String> = emptyList()
    ) {
        val isHeader: Boolean get() = timestamp != null && level != null

        fun headerLine(): String {
            if (!isHeader) return message
            return "$timestamp $level/$tag: $message"
        }

        fun allLines(): List<String> {
            return if (isHeader) listOf(headerLine()) + continuationLines else listOf(message) + continuationLines
        }
    }

    data class FilterResult(
        val lines: List<String>,
        val originalRecordCount: Int,
        val exportedRecordCount: Int
    )

    fun parseRecords(rawLines: Sequence<String>): List<ExportRecord> {
        val records = mutableListOf<ExportRecord>()
        var current: ExportRecord? = null

        fun flush() {
            val record = current ?: return
            records += record
            current = null
        }

        for (raw in rawLines) {
            val line = raw.trimEnd('\r')
            if (line.isBlank()) continue
            val match = headerRegex.matchEntire(line)
            if (match != null) {
                flush()
                val (timestamp, level, tag, message) = match.destructured
                current = ExportRecord(
                    timestamp = timestamp,
                    level = level[0],
                    tag = tag.trim(),
                    message = message
                )
            } else {
                val existing = current
                if (existing != null) {
                    current = existing.copy(continuationLines = existing.continuationLines + line)
                } else {
                    records += ExportRecord(
                        timestamp = null,
                        level = null,
                        tag = null,
                        message = line
                    )
                }
            }
        }
        flush()
        return records
    }

    fun filter(rawLines: Sequence<String>, options: LogExportOptions): FilterResult {
        val parsed = parseRecords(rawLines)
        if (options.isIdentity) {
            return FilterResult(
                lines = parsed.flatMap { it.allLines() },
                originalRecordCount = parsed.size,
                exportedRecordCount = parsed.size
            )
        }

        var working = parsed
        if (options.excludeDebug) {
            working = working.filter { record ->
                val level = record.level ?: return@filter true
                level != 'V' && level != 'D'
            }
        }
        if (options.excludeSystem) {
            working = working.filter { record ->
                val tag = record.tag ?: return@filter true
                tag.lowercase() !in systemTags
            }
        }
        if (options.hideSensitive) {
            working = working.map { redactSensitive(it) }
        }
        if (options.errorContextOnly) {
            working = keepErrorContext(working)
        }
        val lines = working.flatMap { record ->
            formatRecord(record, options.stripTimestamp)
        }
        return FilterResult(
            lines = lines,
            originalRecordCount = parsed.size,
            exportedRecordCount = working.size
        )
    }

    fun filterText(rawText: String, options: LogExportOptions): FilterResult {
        return filter(rawText.lineSequence(), options)
    }

    private fun keepErrorContext(records: List<ExportRecord>): List<ExportRecord> {
        if (records.isEmpty()) return emptyList()
        val lastErrorIndex = records.indexOfLast { isErrorLevel(it.level) }
        if (lastErrorIndex < 0) return emptyList()
        val prefixCount = (records.size / 10).coerceAtLeast(0)
        val start = (lastErrorIndex - prefixCount).coerceAtLeast(0)
        return records.subList(start, records.size)
    }

    private fun isErrorLevel(level: Char?): Boolean {
        return level == 'E' || level == 'A' || level == 'F'
    }

    private fun redactSensitive(record: ExportRecord): ExportRecord {
        val headerSensitive = isSensitiveHeader(record)
        val redactedMessage = if (headerSensitive) {
            redactPayload(record.message)
        } else {
            record.message
        }
        val redactedContinuations = record.continuationLines.map { line ->
            if (headerSensitive || isSensitiveContinuation(line) || containsSensitiveToken(line)) {
                redactPayload(line)
            } else {
                line
            }
        }
        return record.copy(message = redactedMessage, continuationLines = redactedContinuations)
    }

    private fun isSensitiveHeader(record: ExportRecord): Boolean {
        val message = record.message.lowercase()
        if (sensitiveHeaderPrefixes.any { message.startsWith(it) }) return true
        if (containsSensitiveToken(record.message)) return true
        return record.continuationLines.any { containsSensitiveToken(it) }
    }

    private fun isSensitiveContinuation(line: String): Boolean {
        val lower = line.trimStart().lowercase()
        return sensitiveContinuationPrefixes.any { lower.startsWith(it) } ||
            Regex("^part\\s+\\d+/\\d+:", RegexOption.IGNORE_CASE).containsMatchIn(lower)
    }

    private fun containsSensitiveToken(text: String): Boolean {
        val lower = text.lowercase()
        return sensitiveBodyTokens.any { it in lower }
    }

    private fun redactPayload(text: String): String {
        val marker = findRedactMarker(text)
        val prefix = if (marker != null) {
            val index = text.indexOf(marker, ignoreCase = true)
            if (index >= 0) text.substring(0, index + marker.length).trimEnd() else text.substringBefore(':').let { if (it == text) "" else "$it:" }
        } else {
            text.substringBefore(':').let { if (it == text) "" else "$it:" }
        }
        val hiddenChars = (text.length - prefix.length).coerceAtLeast(0)
        val label = "[redacted, $hiddenChars chars]"
        return if (prefix.isBlank()) label else "$prefix $label"
    }

    private fun findRedactMarker(text: String): String? {
        val lower = text.lowercase()
        return sensitiveHeaderPrefixes.firstOrNull { lower.startsWith(it) }?.let { marker ->
            text.substring(0, marker.length)
        }
    }

    private fun formatRecord(record: ExportRecord, stripTimestamp: Boolean): List<String> {
        if (!record.isHeader) {
            return listOf(record.message) + record.continuationLines
        }
        val header = if (stripTimestamp) {
            "${record.level}/${record.tag}: ${record.message}"
        } else {
            record.headerLine()
        }
        return listOf(header) + record.continuationLines
    }
}
