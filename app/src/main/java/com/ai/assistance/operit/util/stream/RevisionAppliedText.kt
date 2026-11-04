package com.ai.assistance.operit.util.stream

/** 按生产者记录的修订历史收集最终正文，避免保留失败尝试的输出。 */
suspend fun Stream<String>.collectRevisionAppliedText(
    onChunk: suspend (String) -> Unit = {},
): String {
    val content = StringBuilder()
    collect { chunk ->
        content.append(chunk)
        onChunk(chunk)
    }

    val carrier = this as? TextStreamEventCarrier ?: return content.toString()
    return applyRevisionEvents(content.toString(), carrier.eventChannel.replayCache)
}

fun applyRevisionEvents(rawContent: String, events: List<TextStreamEvent>): String {
    val tracker = TextStreamRevisionTracker(rawContent)
    tracker.applyEventHistory(events)
    return tracker.currentContent().toString()
}
