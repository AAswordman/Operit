package com.ai.assistance.operit.services.core

import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.util.stream.MutableSharedStream
import com.ai.assistance.operit.util.stream.StreamLogger
import com.ai.assistance.operit.util.stream.TextStreamEventCarrier
import com.ai.assistance.operit.util.stream.TextStreamEventType
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class SteeredSegmentStreamTest {
    @Test fun successiveToolRoundsAppendWithoutRendererRollback() {
        org.mockito.Mockito.mockStatic(com.ai.assistance.operit.util.AppLogger::class.java).use {
            val rounds = com.ai.assistance.operit.api.chat.enhance.ConversationRoundManager()
            val segment = SteeredSegmentStream()
            val replay = StringBuilder()
            repeat(3) { round ->
                replay.append(rounds.startNewRound())
                segment.update(replay.toString())
                for (chunk in listOf("<think>round $round</think>", "reply", "<tool_result>done</tool_result>")) {
                    rounds.appendChunk(chunk)
                    replay.append(chunk)
                    segment.update(replay.toString())
                }
                assertEquals(rounds.getDisplayContent(), replay.toString())
                segment.update(rounds.getDisplayContent())
            }
            val events = (segment.stream as TextStreamEventCarrier).eventChannel.replayCache
            assertEquals(listOf(TextStreamEventType.SAVEPOINT), events.map { it.eventType })
            assertEquals(rounds.getDisplayContent(), segment.stream.replayCache.joinToString(""))
            segment.close()
        }
    }

    @Test fun periodicSnapshotsKeepTheSameLiveStreamUntilCompletion() = runBlocking {
        StreamLogger.setEnabled(false)
        try {
            val transcript = SteeredTranscript()
            transcript.add("before", 3, 1)
            val original = ChatMessage(
                sender = "ai", timestamp = 1, content = "before<think>working",
                contentStream = MutableSharedStream(replay = Int.MAX_VALUE),
            )
            val first = transcript.project(original).last()
            val received = mutableListOf<String>()
            val collection = launch(start = CoroutineStart.UNDISPATCHED) {
                first.contentStream!!.collect { received += it }
            }
            yield()
            assertFalse(collection.isCompleted)
            val next = transcript.project(original.copy(content = "before<think>working more")).last()
            assertSame(first.contentStream, next.contentStream)
            yield()
            assertEquals("<think>working more", received.joinToString(""))
            assertFalse(collection.isCompleted)
            val final = transcript.project(original.copy(
                content = "before<think>working more</think>done", contentStream = null,
            )).last()
            collection.join()
            assertNull(final.contentStream)
            assertEquals("<think>working more</think>done", received.joinToString(""))
        } finally {
            StreamLogger.setEnabled(true)
        }
    }

    @Test fun replacementSnapshotUsesAnOrderedRollbackInsteadOfClosingTheStream() {
        val segment = SteeredSegmentStream()
        segment.update("discarded")
        segment.update("replacement")
        val events = (segment.stream as TextStreamEventCarrier).eventChannel.replayCache
        assertEquals(listOf(TextStreamEventType.SAVEPOINT, TextStreamEventType.ROLLBACK),
            events.map { it.eventType })
        assertEquals(listOf(0, 0), events.map { it.contentLength })
        assertEquals(listOf(0, 9), events.map { it.discardedLength })
        assertEquals(listOf("discarded", "replacement"), segment.stream.replayCache)
        segment.close()
    }
}
