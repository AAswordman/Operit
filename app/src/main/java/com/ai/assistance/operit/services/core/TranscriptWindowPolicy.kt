package com.ai.assistance.operit.services.core

import com.ai.assistance.operit.data.model.ChatMessage

/** Paging expands the loaded range; concurrent in-memory updates win over the fetched page. */
internal fun mergeLoadedTranscriptMessages(
    current: List<ChatMessage>,
    incoming: List<ChatMessage>,
): List<ChatMessage> =
    (incoming + current).associateBy { it.timestamp }.values.sortedBy { it.timestamp }

/** Keep loaded history across refreshes, without restoring deleted bodies from stale memory. */
internal fun retainedTranscriptReloadTimestamps(
    structure: TranscriptStructure,
    loaded: List<Long>,
    hasNewer: Boolean,
): List<Long> {
    if (loaded.isEmpty()) return emptyList()
    val loadedSet = loaded.toHashSet()
    val start = loaded.min()
    val end = if (hasNewer) loaded.max() else Long.MAX_VALUE
    val topLevel = structure.topLevelRows.asSequence()
        .filter { it.timestamp in start..end }.map { it.timestamp }.toSet()
    return structure.rows.filter { it.timestamp in loadedSet || it.timestamp in topLevel }
        .map { it.timestamp }
}
