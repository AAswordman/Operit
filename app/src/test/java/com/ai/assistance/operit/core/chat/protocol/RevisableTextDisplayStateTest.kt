package com.ai.assistance.operit.core.chat.protocol

import com.ai.assistance.operit.util.stream.TextStreamEvent
import com.ai.assistance.operit.util.stream.TextStreamEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RevisableTextDisplayStateTest {
    private fun tag(text: String) = "<meta provider='openai:responses_reasoning'>$text</meta>"
    private fun save(id: String, at: Int) = TextStreamEvent(TextStreamEventType.SAVEPOINT, id, at)
    private fun undo(id: String, at: Int, count: Int) = TextStreamEvent(TextStreamEventType.ROLLBACK, id, at, count)

    @Test fun rollbackRestoresHiddenPayloadStateAndPartialOpening() {
        for (prefix in listOf("前<me", "前<meta provider='openai:responses_reasoning'>hidden")) {
            val state = RevisableTextDisplayState()
            assertEquals("前", state.append(prefix))
            state.apply(save("retry", prefix.length))
            val suffix = if (prefix.endsWith("<me")) "ta provider='openai:responses_reasoning'>secret</meta>错误" else "secret</meta>错误"
            state.append(suffix)
            assertEquals("前", state.apply(undo("retry", prefix.length, suffix.length)))
            val next = if (prefix.endsWith("<me")) "ta provider='openai:responses_reasoning'>hidden</meta>正确" else "hidden</meta>正确"
            assertEquals("正确", state.append(next))
            state.finish()
            assertEquals("前正确", state.visibleContent())
        }
    }

    @Test fun rollbackRestoresBufferedCodeExampleAfterItsDiscardedClosingMarker() {
        val state = RevisableTextDisplayState()
        val example = tag("example")
        val prefix = "说明 `" + example
        assertEquals("说明 `", state.append(prefix))
        state.apply(save("code", prefix.length))
        val discarded = "`错误"
        state.append(discarded)
        assertEquals("说明 `", state.apply(undo("code", prefix.length, discarded.length)))
        assertEquals(example + "`正确", state.append("`正确"))
        state.finish()
        assertEquals("说明 `" + example + "`正确", state.visibleContent())
    }

    @Test fun everyTextAndEventInterleavingKeepsProducerCoordinatesAndHidesPayloads() {
        val example = "`" + tag("example") + "`\n"
        val prefix = example + "前" + tag("hidden")
        val bad = "错误" + tag("discarded-secret")
        val good = "正确"
        val secondAt = prefix.length + bad.length + good.length
        val raw = prefix + bad + good + bad + "结束"
        val cuts = listOf(0, prefix.length - 5, prefix.length + 2, secondAt + 3, raw.length)
        val chunks = cuts.zipWithNext { a, b -> raw.substring(a, b) }
        val events = listOf(save("a", prefix.length), undo("a", prefix.length, bad.length), save("b", secondAt), undo("b", secondAt, bad.length))
        var checked = 0
        fun visit(chunk: Int, event: Int, order: List<Boolean>) {
            if (chunk == chunks.size && event == events.size) {
                val state = RevisableTextDisplayState()
                var ci = 0
                var ei = 0
                for (isText in order) {
                    if (isText) state.append(chunks[ci++]) else state.apply(events[ei++])
                    assertFalse(state.visibleContent().contains("secret"))
                }
                state.reconcile(events)
                state.finish()
                assertEquals("order=$order", example + "前正确结束", state.visibleContent())
                checked++
                return
            }
            if (chunk < chunks.size) visit(chunk + 1, event, order + true)
            if (event < events.size) visit(chunk, event + 1, order + false)
        }
        visit(0, 0, emptyList())
        assertEquals(70, checked)
    }

    @Test fun completionReconcilesUndeliveredEventsExactlyOnce() {
        val state = RevisableTextDisplayState()
        val prefix = "前" + tag("hidden")
        val discarded = "错误"
        state.append(prefix + discarded + "正确")
        val events = listOf(save("late", prefix.length), undo("late", prefix.length, discarded.length))
        assertEquals("前正确", state.reconcile(events))
        assertNull(state.reconcile(events))
        state.finish()
        assertEquals("前正确", state.visibleContent())
    }

    @Test fun outerRollbackInvalidatesAnInnerCheckpoint() {
        val state = RevisableTextDisplayState()
        state.append("前")
        state.apply(save("outer", 1))
        state.append("中")
        state.apply(save("inner", 2))
        state.append("后")
        assertEquals("前", state.apply(undo("outer", 1, 2)))
        assertNull(state.apply(undo("inner", 2, 0)))
        assertEquals("新", state.append("新"))
        assertEquals("前新", state.visibleContent())
    }

    @Test(timeout = 20_000) fun longPrefixAndRepeatedRetriesKeepOnlyTheSuccessfulTail() {
        val prefix = "<tool><node/>`代码`</tool>".repeat(20_000)
        val state = RevisableTextDisplayState()
        state.append(prefix)
        var rawOffset = prefix.length
        repeat(40) { attempt ->
            val id = "attempt-$attempt"
            state.apply(save(id, rawOffset))
            val bad = tag("hidden") + "错误"
            state.append(bad)
            assertEquals(prefix, state.apply(undo(id, rawOffset, bad.length)))
            rawOffset += bad.length
        }
        state.append(tag("hidden") + "正确")
        state.finish()
        assertEquals(prefix + "正确", state.visibleContent())
    }
}
