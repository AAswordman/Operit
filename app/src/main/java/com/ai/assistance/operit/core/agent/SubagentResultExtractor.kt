package com.ai.assistance.operit.core.agent

import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.util.ChatUtils

/**
 * Extracts the terminal prose returned to the parent task call.
 *
 * The child chat keeps its complete persisted transcript. Only the parent-facing result drops
 * internal thinking, tool calls, tool results, status updates, and provider metadata.
 */
object SubagentResultExtractor {
    fun extract(
        persistedAssistantContent: String,
        emptyResultText: String,
    ): String {
        val visibleContent = removeInternalNonToolMarkup(persistedAssistantContent)
        val toolMatches =
            sequenceOf(
                    ChatMarkupRegex.toolOrToolResultBlock,
                    ChatMarkupRegex.toolSelfClosingTag,
                    ChatMarkupRegex.toolResultSelfClosingTag,
                )
                .flatMap { regex -> regex.findAll(visibleContent) }
                .sortedBy { match -> match.range.first }
                .toList()

        val terminalText =
            toolMatches
                .lastOrNull()
                ?.let { match -> visibleContent.substring(match.range.last + 1) }
                ?.let(::removeInternalNonToolMarkup)
                ?.trim()
                .orEmpty()
        if (terminalText.isNotEmpty()) {
            return terminalText
        }

        if (toolMatches.isNotEmpty()) {
            return emptyResultText
        }
        return visibleContent.ifEmpty { emptyResultText }
    }

    private fun removeInternalNonToolMarkup(content: String): String =
        ChatUtils.removeThinkingContent(content)
            .let(ChatMarkupRegex::removeGeminiThoughtSignatureMeta)
            .let(ChatMarkupRegex::removeOpenAiResponsesReasoningMeta)
            .replace(ChatMarkupRegex.statusTag, "")
            .replace(ChatMarkupRegex.statusSelfClosingTag, "")
            .replace(ChatMarkupRegex.emotionTag, "")
            .trim()
}
