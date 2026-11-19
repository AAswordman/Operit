package com.ai.assistance.operit.ui.features.chat.components

import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode

/**
 * One collaboration row a reply's memory edit covers: which message it is, and what it says.
 *
 * Such a row is its own message in storage, so editing it here writes it back where it came from
 * instead of folding its words into the reply. The model keeps reading it as something another
 * agent said, which is what the row is.
 */
internal data class MemoryEditFragment(
    val messageIndex: Int,
    val timestamp: Long,
    val sender: String,
    val kind: String,
    /** The row's own content, envelope and all: what a write-back has to preserve. */
    val content: String,
    /** What the agent said, as it stands now. */
    val body: String,
    val deleted: Boolean = false,
    val isAssistant: Boolean = false,
) {
    /** Nothing to write back while the fragment still says what it said. */
    val changed: Boolean
        get() = !deleted && body != if (isAssistant) content else collaborationDisplayMessages(content, sender).first().body
}

/**
 * The words a fragment wears: the same ones its row wears in the transcript, so the message reads
 * the same in both places.
 */
internal fun collaborationFragmentLabelRes(kind: String): Int =
    collaborationResultLabelRes(kind)
        ?: collaborationMidwayLabelRes(kind)
        ?: collaborationKindLabelRes(kind)

/**
 * The collaboration rows a memory edit of [index] covers: everything the turn of that row produced,
 * in the order it happened.
 *
 * Membership is the transcript's own rule (see TranscriptStructure): a turn is the collaboration
 * rows plus the assistant rows sharing its `sentAt`, and the turn ends at its final reply. A message
 * the user slipped in while the reply was being written is not a boundary there either, so the walk
 * passes over it the way the folded transcript does. Rows from an earlier turn are not this reply's
 * memory, so the walk stops at the first of them.
 *
 * An intermediate row opens the same editor, and the rows this turn produced after it are the same
 * memory, so the walk also runs forward to the turn's final reply. A row with no `sentAt` of its own
 * (an older transcript) keeps only the run of collaboration rows sitting right before it.
 */
internal fun collaborationMemoryFragments(
    messages: List<ChatMessage>,
    index: Int,
    includeAssistant: Boolean = false,
): List<MemoryEditFragment> {
    val anchor = messages.getOrNull(index) ?: return emptyList()
    if (anchor.sender != "ai") return emptyList()
    val turnSentAt = anchor.sentAt
    val found = mutableListOf<Int>()
    if (includeAssistant) found += index

    var cursor = index - 1
    while (cursor >= 0) {
        val row = messages[cursor]
        if (row.displayMode.isCollaborationEvent) {
            found += cursor
        } else if (belongsToTurn(row, turnSentAt)) {
            if (includeAssistant) found += cursor
        } else if (!belongsToTurn(row, turnSentAt) && !interjectedWhileWriting(row, turnSentAt)) {
            break
        }
        cursor--
    }

    // 最终回复本身就是这一轮的结尾，它之后的行一律不属于它——所以只有中间段才需要向后看。
    var ahead = index + 1
    while (
        turnSentAt > 0L &&
            anchor.displayMode != ChatMessageDisplayMode.NORMAL &&
            ahead < messages.size
    ) {
        val row = messages[ahead]
        val sameTurnIntermediate =
            belongsToTurn(row, turnSentAt) && row.displayMode != ChatMessageDisplayMode.NORMAL
        if (row.displayMode.isCollaborationEvent) {
            found += ahead
        } else if (belongsToTurn(row, turnSentAt)) {
            if (includeAssistant) found += ahead
            if (!sameTurnIntermediate) break
        } else if (!sameTurnIntermediate && !interjectedWhileWriting(row, turnSentAt)) {
            // 走到这一轮的最终回复（或别的行）为止，此后不再属于它。
            break
        }
        ahead++
    }

    return found.sorted().map { rowIndex ->
        val row = messages[rowIndex]
        if (!row.displayMode.isCollaborationEvent) {
            return@map MemoryEditFragment(
                rowIndex, row.timestamp, row.roleName, "", row.content, row.content,
                isAssistant = true,
            )
        }
        val event = collaborationDisplayMessages(row.content, row.roleName).first()
        MemoryEditFragment(
            messageIndex = rowIndex,
            timestamp = row.timestamp,
            sender = row.roleName,
            kind = event.kind,
            content = row.content,
            body = event.body,
        )
    }
}

/** Another row of the same reply: the assistant rows that share the turn's request. */
private fun belongsToTurn(row: ChatMessage, turnSentAt: Long): Boolean =
    turnSentAt > 0L && row.sender == "ai" && row.sentAt == turnSentAt

/**
 * A message the user slipped in while the reply was being written. It carries no request of its own,
 * which is what tells it apart from the message that opened a turn.
 */
private fun interjectedWhileWriting(row: ChatMessage, turnSentAt: Long): Boolean =
    turnSentAt > 0L && row.sender != "ai" && row.sentAt <= 0L
