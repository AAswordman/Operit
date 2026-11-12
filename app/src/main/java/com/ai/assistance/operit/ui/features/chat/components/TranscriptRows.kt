package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.runtime.compositionLocalOf
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.common.markdown.TranscriptMarkdownSlice

/** Whether the row being composed opens or closes its turn's card. */


internal data class TranscriptRow(
    val key: String,
    val messageIndex: Int,
    val section: ResponseMessageSection,
    val group: ResponseProcessGroup? = null,
    val loadMore: Boolean = false,
    val markdownSlice: TranscriptMarkdownSlice? = null,
    val preparingMarkdown: Boolean = false,
    /** Opens (closes) the card its turn shares; only those rows round the card off. */
    val cardFirst: Boolean = true,
    val cardLast: Boolean = true,
    val cardKey: String? = null,
)

/**
 * Mark which rows open and close the card their turn shares.
 *
 * One turn is drawn as one card: the rows carrying its content keep square corners and close spacing
 * so the whole reply reads as a single block, and only the first and last of those rows round the
 * card off. A row that belongs to no turn — a plain message, a card still waiting for a reply — is a
 * block of its own. The summary row stays out of this: it renders the identity, not the card.
 */
internal fun withCardEnds(rows: List<TranscriptRow>): List<TranscriptRow> =
    rows.mapIndexed { index, row ->
        if (row.section == ResponseMessageSection.HEADER) return@mapIndexed row
        val block = row.cardKey ?: row.group?.key?.toString() ?: "index:${row.messageIndex}"
        val previous =
            rows.getOrNull(index - 1)?.takeIf { it.section != ResponseMessageSection.HEADER }
        val next = rows.getOrNull(index + 1)?.takeIf { it.section != ResponseMessageSection.HEADER }
        row.copy(
            cardFirst = previous == null || (previous.cardKey ?: previous.group?.key?.toString() ?: "index:${previous.messageIndex}") != block,
            cardLast = next == null || (next.cardKey ?: next.group?.key?.toString() ?: "index:${next.messageIndex}") != block,
        )
    }

/** Drawing continuity also applies while the turn is streaming, before a collapse group exists. */
private fun transcriptCardKey(messages: List<ChatMessage>, index: Int): String? {
    val message = messages.getOrNull(index) ?: return null
    fun assistantKey(row: ChatMessage) =
        if (row.sentAt > 0) "turn:${row.sentAt}" else "message:${row.timestamp}"
    if (message.sender == "ai") return assistantKey(message)
    if (!message.displayMode.isCollaborationEvent) return null
    val before = (index - 1 downTo 0).firstOrNull { !messages[it].displayMode.isCollaborationEvent }
        ?.let { messages[it] }
    if (before?.sender == "ai" && before.displayMode != com.ai.assistance.operit.data.model.ChatMessageDisplayMode.NORMAL) {
        return assistantKey(before)
    }
    val after = (index + 1 until messages.size).firstOrNull { !messages[it].displayMode.isCollaborationEvent }
        ?.let { messages[it] }
    return after?.takeIf { it.sender == "ai" }?.let(::assistantKey)
}

/** Summary keys survive body paging and completion, unlike the first visible process message. */
internal fun transcriptRows(
    messages: List<ChatMessage>,
    state: ResponseProcessState,
): List<TranscriptRow> = buildList<TranscriptRow> {
        val occurrences = mutableMapOf<Long, Int>()
        messages.forEachIndexed { index, message ->
            val occurrence = occurrences.getOrDefault(message.timestamp, 0)
            occurrences[message.timestamp] = occurrence + 1
            val messageKey = "message:${message.timestamp}" + if (occurrence == 0) "" else ":$occurrence"
            val group = state.groups[index]
            if (group == null) {
                add(TranscriptRow(messageKey, index, ResponseMessageSection.ALL))
            } else {
                if (index == group.firstIndex && state.showSummary) {
                    add(TranscriptRow("process:${group.key}", group.headerIndex, ResponseMessageSection.HEADER, group))
                }
                if (state.isExpanded(group.key) || index == group.finalIndex) {
                    if (index == group.finalIndex && state.isExpanded(group.key) && state.hasMore(group.key)) {
                        add(TranscriptRow("more:${group.key}", -1, ResponseMessageSection.BODY, group, true))
                    }
                    add(TranscriptRow(messageKey, index,
                        if (state.showSummary) ResponseMessageSection.BODY else ResponseMessageSection.ALL, group))
                }
            }
        }
    }.map { row -> row.copy(cardKey = row.group?.key?.let { "group:$it" } ?: transcriptCardKey(messages, row.messageIndex)) }
