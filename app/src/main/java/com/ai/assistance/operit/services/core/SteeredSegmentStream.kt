package com.ai.assistance.operit.services.core

import com.ai.assistance.operit.util.stream.MutableSharedStreamImpl
import com.ai.assistance.operit.util.stream.TextStreamEvent
import com.ai.assistance.operit.util.stream.TextStreamEventType
import com.ai.assistance.operit.util.stream.withEventChannel

/** Keeps the renderer's streaming lifetime independent of periodic database snapshots. */
internal class SteeredSegmentStream {
    private val text = MutableSharedStreamImpl<String>(replay = Int.MAX_VALUE)
    private val events = MutableSharedStreamImpl<TextStreamEvent>(replay = Int.MAX_VALUE)
    val stream = text.withEventChannel(events)
    private var content = ""
    private var emittedChars = 0
    private var closed = false

    init {
        events.tryEmit(TextStreamEvent(TextStreamEventType.SAVEPOINT, "segment-start", 0))
    }

    fun update(snapshot: String) {
        if (closed || snapshot == content) return
        val suffix =
            if (snapshot.startsWith(content)) {
                snapshot.substring(content.length)
            } else {
                events.tryEmit(TextStreamEvent(
                    TextStreamEventType.ROLLBACK, "segment-start", contentLength = 0, discardedLength = emittedChars,
                ))
                snapshot
            }
        // Empty text still delivers an otherwise trailing rollback to active subscribers.
        text.tryEmit(suffix)
        emittedChars += suffix.length
        content = snapshot
    }

    fun close() {
        if (closed) return
        closed = true
        events.close()
        text.close()
    }
}
