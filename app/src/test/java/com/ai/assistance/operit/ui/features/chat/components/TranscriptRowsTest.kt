package com.ai.assistance.operit.ui.features.chat.components

import com.ai.assistance.operit.data.model.ChatMessage
import org.junit.Assert.*
import org.junit.Test

class TranscriptRowsTest {
    @Test fun selectionKeepsIdentityWhenProcessRowsAreInserted() {
        val selected = TranscriptSelection()
        fun message(timestamp: Long) = ChatMessage(timestamp = timestamp, sender = "ai", content = "")
        var before by selected.forMessages(listOf(message(20)))
        before = setOf(0)
        var after by selected.forMessages(listOf(message(10), message(11), message(20)))
        assertEquals(setOf(2), after)
        assertEquals(setOf(20L), selected.timestamps)
        after = emptySet()
        assertTrue(before.isEmpty())
    }

    @Test fun finalOnlyWindowStillHasSummaryAndFinalBody() {
        val group = ResponseProcessGroup(10, 0, 0, 50, 0)
        val state = ResponseProcessState(mapOf(0 to group), { false }, {}, {})
        val rows = transcriptRows(listOf(ChatMessage(timestamp = 20, sender = "ai", content = "final")), state)
        assertEquals(listOf("process:10", "message:20"), rows.map { it.key })
        assertEquals(ResponseMessageSection.BODY, rows.last().section)
    }

    @Test fun expansionKeepsSummaryKeyAndPagesBeforeFinal() {
        val messages = listOf(11L, 20L).map { ChatMessage(timestamp = it, sender = "ai", content = "") }
        val group = ResponseProcessGroup(10, 0, 1, 50)
        val state = ResponseProcessState(
            mapOf(0 to group, 1 to group), { true }, {}, {}, { true },
        )
        assertEquals(
            listOf("process:10", "message:11", "more:10", "message:20"),
            transcriptRows(messages, state).map { it.key },
        )
    }
}
