package com.ai.assistance.operit.core.chat.context

import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.tools.prompt.ToolPrompt
import com.ai.assistance.operit.core.chat.protocol.ChatMarkupRegex
import com.ai.assistance.operit.core.chat.context.ChatUtils
import java.util.Locale

/**
 * 当前请求窗口的估算快照。
 * 保留模型的窗口估算及实际参与请求准备的消息、工具，详情读取不会发布估算。
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
                    (windowTokens.toDouble() / maxWindowTokens.toDouble()).toFloat()
                }
}

object ContextWindowBreakdownEstimator {
    // 标记与系统提示词中的包系统模板耦合；调整模板时应同步这里及切分回归用例。
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
        val summaryTokens =
                estimateTurns(snapshot.history.filter { it.kind == PromptTurnKind.SUMMARY })
        val systemPromptTokens = ChatUtils.estimateTokenCount(systemWithoutCatalog)
        val skillTokens = ChatUtils.estimateTokenCount(skillCatalog)
        val (residentToolTokens, mcpToolTokens) = estimateToolTokens(snapshot.tools)
        val leafTokens = calibrateToWindow(
            longArrayOf(
                historyParts.user,
                historyParts.assistant,
                historyParts.toolResult,
                systemPromptTokens,
                residentToolTokens,
                mcpToolTokens,
                skillTokens,
                summaryTokens
            ),
            windowTokens
        )
        val otherTokens = windowTokens - leafTokens.sum()
        val autoThresholdTokens =
                (safeMax.toDouble() * autoSummaryThreshold.toDouble().coerceIn(0.0, 1.0)).toLong()
        val autoSummaryBufferTokens = (autoThresholdTokens - windowTokens).coerceAtLeast(0L)
        val remainingTokens = (safeMax - windowTokens).coerceAtLeast(0L)
        return ContextWindowBreakdown(
                windowTokens = windowTokens,
                maxWindowTokens = safeMax,
                autoSummaryThreshold = autoSummaryThreshold.coerceIn(0f, 1f),
                enableSummary = enableSummary,
                messageHistoryTokens = leafTokens[0] + leafTokens[1] + leafTokens[2],
                userMessageTokens = leafTokens[0],
                assistantMessageTokens = leafTokens[1],
                toolResultTokens = leafTokens[2],
                systemPromptTokens = leafTokens[3],
                residentToolTokens = leafTokens[4],
                mcpToolTokens = leafTokens[5],
                skillTokens = leafTokens[6],
                summaryTokens = leafTokens[7],
                otherTokens = otherTokens,
                autoSummaryBufferTokens = autoSummaryBufferTokens,
                remainingTokens = remainingTokens
        )
    }

    // 各类使用通用估算，校准到模型窗口预算后，分类合计与占用总数保持一致。
    internal fun calibrateToWindow(parts: LongArray, windowTokens: Long): LongArray {
        val total = parts.sum()
        if (total <= windowTokens) return parts
        val exact = parts.map { it.toDouble() / total.toDouble() * windowTokens.toDouble() }
        val calibrated = exact.map { it.toLong() }.toLongArray()
        val remainder = windowTokens - calibrated.sum()
        val indices = parts.indices.sortedByDescending { exact[it] - calibrated[it] }
        indices.take(remainder.toInt()).forEach { calibrated[it]++ }
        return calibrated
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
            safe >= 1000L -> String.format(Locale.US, "%.1fK", safe / 1000.0)
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
            // 包和 MCP 工具使用命名空间冒号，常驻工具名按约定不含冒号。
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
