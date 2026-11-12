package com.ai.assistance.operit.ui.features.chat.components

import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.common.markdown.*
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType

internal data class TranscriptDocumentEntry(val content: String, val document: TranscriptMarkdownDocument)

// Reprojecting a neighboring message must not invalidate every visible block through a new lambda.
private data class TranscriptExpansionAction(
    val id: String,
    val toggle: (String) -> Unit,
) : () -> Unit {
    override fun invoke() = toggle(id)
}

internal fun canSplitTranscriptMessage(message: ChatMessage): Boolean =
    message.sender == "ai" && message.contentStream == null &&
        !message.displayMode.isCollaborationEvent && message.content.length >= 4096

internal fun transcriptMarkdownRows(
    baseRows: List<TranscriptRow>,
    messages: List<ChatMessage>,
    documents: Map<Long, TranscriptDocumentEntry>,
    enabled: Boolean,
    collapse: Boolean,
    isExpanded: (String) -> Boolean,
    toggle: (String) -> Unit,
    processExpanded: (Long) -> Boolean = { true },
): List<TranscriptRow> = withCardEnds(baseRows.flatMap { row ->
    val message = messages.getOrNull(row.messageIndex)
    if (!enabled || message == null || row.section == ResponseMessageSection.HEADER ||
        !canSplitTranscriptMessage(message)) return@flatMap listOf(row)
    val entry = documents[message.timestamp]?.takeIf { it.content == message.content }
        ?: return@flatMap listOf(row.copy(preparingMarkdown = true))
    val document = entry.document
    val slices = mutableListOf<Pair<String, TranscriptMarkdownSlice>>()
    val messageKey = "message:${message.timestamp}"
    val processId = "$messageKey:activity"
    val internalProcess = row.group == null && collapse && document.processEnd >= 0
    val hideProcess = (internalProcess && !isExpanded(processId)) ||
        (row.group != null && !processExpanded(row.group.key))
    if (internalProcess) {
        slices += "activity" to TranscriptMarkdownSlice(document, null, messageKey, false, false,
            isExpanded(processId), TranscriptExpansionAction(processId, toggle), processHeader = true)
    }
    document.items.forEach { item ->
        val index = when (item) {
            is MarkdownGroupedItem.Single -> item.index
            is MarkdownGroupedItem.Group -> item.startIndex
        }
        val end = when (item) {
            is MarkdownGroupedItem.Single -> item.index
            is MarkdownGroupedItem.Group -> item.endIndexInclusive
        }
        if (hideProcess && end <= document.processEnd) return@forEach
        if (item is MarkdownGroupedItem.Single) {
            if (index in document.hiddenNodes) return@forEach
            val node = document.nodes[index]
            if (node.type == MarkdownProcessorType.PLAIN_TEXT && node.content.isBlank() &&
                node.children.isEmpty()) return@forEach
        }
        val id = "$messageKey:group:$index"
        slices += "node:$index" to TranscriptMarkdownSlice(document, item, messageKey, false, false,
            isExpanded(id), TranscriptExpansionAction(id, toggle))
        if (item is MarkdownGroupedItem.Group && isExpanded(id)) {
            (item.startIndex..item.endIndexInclusive).forEach { child ->
                val node = document.nodes[child]
                if (node.type == MarkdownProcessorType.XML_BLOCK && child !in document.hiddenNodes) {
                    slices += "child:$child" to TranscriptMarkdownSlice(
                        document, MarkdownGroupedItem.Single(child), messageKey, false, false,
                        indented = true)
                }
            }
        }
    }
    if (slices.isEmpty()) return@flatMap listOf(row)
    // Amortize the message chrome/preferences across a few small nodes, without
    // making an entire tool run one lazy item. Group headers remain independent.
    val batches = mutableListOf<Pair<String, TranscriptMarkdownSlice>>()
    var batchChars = 0
    slices.forEach { (key, slice) ->
        val single = slice.item as? MarkdownGroupedItem.Single
        val chars = single?.let { document.nodeChars[it.index] } ?: Int.MAX_VALUE
        val previous = batches.lastOrNull()?.second
        if (single != null && previous?.item is MarkdownGroupedItem.Single &&
            previous.indented == slice.indented && previous.extraItems.size < 3 &&
            chars <= 2048 - batchChars) {
            batches[batches.lastIndex] = batches.last().first to
                previous.copy(extraItems = previous.extraItems + single)
            batchChars += chars
        } else {
            batches += key to slice
            batchChars = chars
        }
    }
    batches.mapIndexed { index, (key, slice) ->
        row.copy(
            key = if (index == 0) row.key else "${row.key}:$key",
            markdownSlice = slice.copy(first = index == 0, last = index == batches.lastIndex),
        )
    }
})
