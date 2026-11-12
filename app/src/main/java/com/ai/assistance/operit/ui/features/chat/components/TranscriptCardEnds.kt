package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode

internal data class TranscriptCardEnds(val first: Boolean = true, val last: Boolean = true)
internal val LocalTranscriptCardEnds = staticCompositionLocalOf { TranscriptCardEnds() }

/** Compute neighbors over visible bodies, excluding folded rows and respecting turn boundaries. */
internal fun visibleCardEnds(messages: List<ChatMessage>, state: ResponseProcessState): Map<Int, TranscriptCardEnds> {
    val following = arrayOfNulls<String>(messages.size)
    var next: String? = null
    fun key(row: ChatMessage) = if (row.sentAt > 0L) "turn:${row.sentAt}" else "row:${row.timestamp}"
    for (i in messages.indices.reversed()) {
        val row = messages[i]
        following[i] = next
        if (!row.displayMode.isCollaborationEvent) next = if (row.sender == "ai") key(row) else null
    }
    val rows = mutableListOf<Pair<Int,String?>>()
    var previous: String? = null
    messages.forEachIndexed { i, row ->
        val group = state.groups[i]
        val k = group?.let { "group:${it.key}" } ?: when {
            row.displayMode.isCollaborationEvent -> previous ?: following[i]
            row.sender == "ai" -> key(row)
            else -> null
        }
        if (!row.displayMode.isCollaborationEvent) previous =
            if (row.sender == "ai" && row.displayMode != ChatMessageDisplayMode.NORMAL) key(row) else null
        if (group == null || i == group.finalIndex || state.isExpanded(group.key)) rows += i to k
    }
    return rows.mapIndexed { i, (index, k) -> index to TranscriptCardEnds(k == null || rows.getOrNull(i-1)?.second != k, k == null || rows.getOrNull(i+1)?.second != k) }.toMap()
}
