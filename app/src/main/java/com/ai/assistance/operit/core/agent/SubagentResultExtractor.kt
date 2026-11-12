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
    ): String = extractText(persistedAssistantContent, emptyResultText, includeEarlierProse = false)

    /** A read-only detail may show what the agent wrote before a final verification tool. */
    fun forTranscript(persistedAssistantContent: String): String =
        extractText(persistedAssistantContent, "", includeEarlierProse = true)

    private fun extractText(
        persistedAssistantContent: String,
        emptyResultText: String,
        includeEarlierProse: Boolean,
    ): String {
        val visibleContent = removeInternalNonToolMarkup(persistedAssistantContent)
        val toolRanges =
            sequenceOf(
                    ChatMarkupRegex.toolOrToolResultBlock,
                    ChatMarkupRegex.toolSelfClosingTag,
                    ChatMarkupRegex.toolResultSelfClosingTag,
                )
                .flatMap { regex -> regex.findAll(visibleContent) }
                .sortedBy { match -> match.range.first }
                .map { match -> match.range }
                .fold(mutableListOf<IntRange>()) { merged, range ->
                    val previous = merged.lastOrNull()
                    if (previous == null || range.first > previous.last) {
                        merged += range
                    } else if (range.last > previous.last) {
                        merged[merged.lastIndex] = previous.first..range.last
                    }
                    merged
                }

        val terminalText =
            toolRanges
                .lastOrNull()
                ?.let { range -> visibleContent.substring(range.last + 1) }
                ?.let(::removeInternalNonToolMarkup)
                ?.trim()
                .orEmpty()
        if (terminalText.isNotEmpty()) {
            return terminalText
        }

        if (toolRanges.isNotEmpty()) {
            if (!includeEarlierProse) return emptyResultText
            val proseSegments =
                buildList {
                        var cursor = 0
                        toolRanges.forEach { range ->
                            if (range.first > cursor) {
                                add(visibleContent.substring(cursor, range.first))
                            }
                            cursor = range.last + 1
                        }
                        if (cursor < visibleContent.length) {
                            add(visibleContent.substring(cursor))
                        }
                    }
                    .map(::removeInternalNonToolMarkup)
                    .map(String::trim)
                    .filter(String::isNotEmpty)

            // A Subagent can state its conclusion and then perform one final verification tool.
            // Preserve the latest visible prose even when it is the only prose segment; returning
            // a partial explanation is safer than replacing text the user already saw with an
            // artificial empty-result fallback.
            return proseSegments.lastOrNull() ?: emptyResultText
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
