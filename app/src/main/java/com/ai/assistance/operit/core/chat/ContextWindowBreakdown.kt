package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.util.ChatUtils
import java.util.Locale

/**
 * 当前请求窗口的估算快照。
 * 顶部圆环继续用 [windowTokens]；面板里的分类用同一套通用估算拆开。
 */
data class RequestWindowSnapshot(
        val windowTokens: Long,
        val history: List<PromptTurn>,
        val tools: List<ToolPrompt>?
)

data class ContextWindowBreakdown(
        val windowTokens: Long,
        val maxWindowTokens: Long,
        val autoSummaryThreshold: Float,
        val enableSummary: Boolean,
        val messageHistoryTokens: Long,
        val userMessageTokens: Long,
        val assistantMessageTokens: Long,
        val toolResultTokens: Long,
        val systemPromptTokens: Long,
        val residentToolTokens: Long,
        val mcpToolTokens: Long,
        val skillTokens: Long,
        val summaryTokens: Long,
        val otherTokens: Long,
        val autoSummaryBufferTokens: Long,
        val remainingTokens: Long
) {
    val usageRatio: Float
        get() =
                if (maxWindowTokens <= 0L) {
                    0f
                } else {
                    (windowTokens.toDouble() / maxWindowTokens.toDouble())
                            .coerceIn(0.0, 1.0)
                            .toFloat()
                }
}

object ContextWindowBreakdownEstimator {
    private const val AVAILABLE_PACKAGES_MARKER = "Available packages:"
    private val catalogEndMarkers = listOf("To use a package:", "PACKAGE SYSTEM", "包系统：")

    fun fromSnapshot(
            snapshot: RequestWindowSnapshot,
            maxWindowTokens: Long,
            autoSummaryThreshold: Float,
            enableSummary: Boolean
    ): ContextWindowBreakdown {
        val safeMax = maxWindowTokens.coerceAtLeast(0L)
        val windowTokens = snapshot.windowTokens.coerceAtLeast(0L)
        val systemPrompt = snapshot.history
                .filter { it.kind == PromptTurnKind.SYSTEM }
                .joinToString("\n") { it.content }
        val (systemWithoutCatalog, skillCatalog) = splitSystemPromptAndSkillCatalog(systemPrompt)
        val historyParts = estimateMessageHistoryParts(snapshot.history)
        val messageHistoryTokens =
                historyParts.user + historyParts.assistant + historyParts.toolResult
        val summaryTokens =
                estimateTurns(snapshot.history.filter { it.kind == PromptTurnKind.SUMMARY })
        val systemPromptTokens = ChatUtils.estimateTokenCount(systemWithoutCatalog)
        val skillTokens = ChatUtils.estimateTokenCount(skillCatalog)
        val (residentToolTokens, mcpToolTokens) = estimateToolTokens(snapshot.tools)
        val classified =
                messageHistoryTokens +
                        systemPromptTokens +
                        residentToolTokens +
                        mcpToolTokens +
                        skillTokens +
                        summaryTokens
        val otherTokens = (windowTokens - classified).coerceAtLeast(0L)
        val autoThresholdTokens =
                (safeMax.toDouble() * autoSummaryThreshold.toDouble().coerceIn(0.0, 1.0)).toLong()
        val autoSummaryBufferTokens = (autoThresholdTokens - windowTokens).coerceAtLeast(0L)
        val remainingTokens = (safeMax - windowTokens).coerceAtLeast(0L)
        return ContextWindowBreakdown(
                windowTokens = windowTokens,
                maxWindowTokens = safeMax,
                autoSummaryThreshold = autoSummaryThreshold.coerceIn(0f, 1f),
                enableSummary = enableSummary,
                messageHistoryTokens = messageHistoryTokens,
                userMessageTokens = historyParts.user,
                assistantMessageTokens = historyParts.assistant,
                toolResultTokens = historyParts.toolResult,
                systemPromptTokens = systemPromptTokens,
                residentToolTokens = residentToolTokens,
                mcpToolTokens = mcpToolTokens,
                skillTokens = skillTokens,
                summaryTokens = summaryTokens,
                otherTokens = otherTokens,
                autoSummaryBufferTokens = autoSummaryBufferTokens,
                remainingTokens = remainingTokens
        )
    }

    fun formatTokenCount(value: Long): String {
        val safe = value.coerceAtLeast(0L)
        return when {
            safe >= 1_000_000L -> {
                val millions = safe / 1_000_000.0
                if (millions >= 10.0) {
                    "${millions.toInt()}M"
                } else {
                    String.format(Locale.US, "%.1fM", millions)
                }
            }
            safe >= 10_000L -> "${safe / 1000L}k"
            safe >= 1000L -> String.format(Locale.US, "%.1fk", safe / 1000.0)
            else -> safe.toString()
        }
    }

    internal fun splitSystemPromptAndSkillCatalog(systemPrompt: String): Pair<String, String> {
        val start = systemPrompt.indexOf(AVAILABLE_PACKAGES_MARKER)
        if (start < 0) {
            return systemPrompt to ""
        }
        val afterStart = start + AVAILABLE_PACKAGES_MARKER.length
        val end =
                catalogEndMarkers
                        .map { systemPrompt.indexOf(it, afterStart) }
                        .filter { it >= 0 }
                        .minOrNull()
                        ?: systemPrompt.length
        val catalog = systemPrompt.substring(start, end).trim()
        val remaining =
                (systemPrompt.substring(0, start) + systemPrompt.substring(end)).trim()
        if (catalog.contains("No packages are currently available")) {
            return remaining to ""
        }
        return remaining to catalog
    }

    internal fun estimateToolTokens(tools: List<ToolPrompt>?): Pair<Long, Long> {
        if (tools.isNullOrEmpty()) {
            return 0L to 0L
        }
        var resident = 0L
        var mcp = 0L
        for (tool in tools) {
            val tokens = ChatUtils.estimateTokenCount(tool.toString())
            if (tool.name.contains(':')) {
                mcp += tokens
            } else {
                resident += tokens
            }
        }
        return resident to mcp
    }

    private fun estimateTurns(turns: List<PromptTurn>): Long {
        return turns.sumOf { ChatUtils.estimateTokenCount(it.content) }
    }

    internal data class MessageHistoryParts(
            val user: Long,
            val assistant: Long,
            val toolResult: Long
    )

    internal fun estimateMessageHistoryParts(history: List<PromptTurn>): MessageHistoryParts {
        var user = 0L
        var assistant = 0L
        var toolResult = 0L
        for (turn in history) {
            when (turn.kind) {
                PromptTurnKind.USER -> user += ChatUtils.estimateTokenCount(turn.content)
                PromptTurnKind.TOOL_CALL, PromptTurnKind.TOOL_RESULT ->
                        toolResult += ChatUtils.estimateTokenCount(turn.content)
                PromptTurnKind.ASSISTANT -> {
                    val split = splitAssistantContent(turn.content)
                    assistant += ChatUtils.estimateTokenCount(split.assistant)
                    toolResult += ChatUtils.estimateTokenCount(split.toolResult)
                }
                PromptTurnKind.SYSTEM, PromptTurnKind.SUMMARY -> Unit
            }
        }
        return MessageHistoryParts(user = user, assistant = assistant, toolResult = toolResult)
    }

    internal data class AssistantContentSplit(val assistant: String, val toolResult: String)

    internal fun splitAssistantContent(content: String): AssistantContentSplit {
        if (content.isEmpty() || !ChatMarkupRegex.containsToolResultTag(content)) {
            return AssistantContentSplit(assistant = content, toolResult = "")
        }
        val assistantParts = StringBuilder()
        val toolResultParts = StringBuilder()
        var lastEnd = 0
        for (match in ChatMarkupRegex.toolResultTag.findAll(content)) {
            if (match.range.first > lastEnd) {
                assistantParts.append(content.substring(lastEnd, match.range.first))
            }
            if (toolResultParts.isNotEmpty()) {
                toolResultParts.append('\n')
            }
            toolResultParts.append(match.value)
            lastEnd = match.range.last + 1
        }
        if (lastEnd < content.length) {
            assistantParts.append(content.substring(lastEnd))
        }
        return AssistantContentSplit(
                assistant = assistantParts.toString().trim(),
                toolResult = toolResultParts.toString().trim()
        )
    }
}