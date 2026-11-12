package com.ai.assistance.operit.ui.features.chat.components

import com.ai.assistance.operit.data.model.ChatMessageLocatorPreview

internal fun locatorVisibleEntries(
    entries: List<ChatMessageLocatorPreview>,
): List<ChatMessageLocatorPreview> =
    entries.mapIndexedNotNull { index, entry ->
        if (entry.resolvedDisplayMode.isCollaborationEvent) null
        else entry.copy(messageIndex = entry.messageIndex ?: index)
    }

/** Hidden collaboration rows locate the closest ordinary row; ties prefer the following reply. */
internal fun locatorCurrentVisiblePosition(
    entries: List<ChatMessageLocatorPreview>,
    timestamp: Long,
): Int {
    val visible = locatorVisibleEntries(entries)
    val exact = visible.indexOfFirst { it.timestamp == timestamp }
    if (exact >= 0 || visible.isEmpty()) return exact
    val sourceIndex = entries.indexOfFirst { it.timestamp == timestamp }
    if (sourceIndex >= 0) {
        val nearest = entries.indices
            .filter { !entries[it].resolvedDisplayMode.isCollaborationEvent }
            .minWithOrNull(compareBy<Int> { kotlin.math.abs(it - sourceIndex) }.thenByDescending { it })
        return visible.indexOfFirst { it.timestamp == nearest?.let { index -> entries[index].timestamp } }
    }
    // The visible anchor may have just been removed or not yet persisted in the preview snapshot.
    return visible.indices.minByOrNull {
        kotlin.math.abs(visible[it].timestamp.toDouble() - timestamp.toDouble())
    } ?: -1
}
