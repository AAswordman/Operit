package com.ai.assistance.operit.ui.features.chat.components

import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode
import com.ai.assistance.operit.data.model.ChatMessageProcessMetadata
import org.junit.Assert.*
import org.junit.Test

class ResponseProcessMessagesTest {
    @Test fun completedProcessFoldsAcrossPagesIncludingCardOnlyWindow() {
        val full = listOf(
            intermediate(10).copy(timestamp = 1),
            ChatMessage(timestamp = 2, sender = "user", content = "mail",
                displayMode = ChatMessageDisplayMode.COLLABORATION_EVENT),
            intermediate(10).copy(timestamp = 3),
            final(10).copy(timestamp = 4),
        )
        val metadata = full.map(::metadata)
        val earlier = windowResponseProcessGroups(full.take(3), metadata)
        assertEquals(setOf(0, 1, 2), earlier.keys)
        assertEquals(-1, earlier.getValue(0).finalIndex)
        val cardOnly = windowResponseProcessGroups(listOf(full[1]), metadata).getValue(0)
        assertEquals(-1, cardOnly.headerIndex)
        assertEquals(-1, cardOnly.finalIndex)
        assertEquals(earlier.getValue(0).key, cardOnly.key)
        assertTrue(windowResponseProcessGroups(full.take(3), metadata.dropLast(1)).isEmpty())
    }

    @Test fun inMemoryCompletionOverridesPersistedUnfinishedMetadata() {
        val full = listOf(intermediate(10).copy(timestamp = 1), final(10).copy(timestamp = 2))
        val old = full.map(::metadata).map { it.copy(completedAt = 0) }
        assertEquals(setOf(0, 1), windowResponseProcessGroups(full, old).keys)
    }

    private fun metadata(message: ChatMessage) = ChatMessageProcessMetadata(
        message.timestamp, message.sender, message.displayMode.name, message.sentAt,
        message.completedAt, message.waitDurationMs, message.outputDurationMs,
    )

    @Test fun initialTaskAndMessagesFoldEvenWithoutIntermediateAssistantRows() {
        val messages = listOf(
            ChatMessage(sender = "user", content = "task", displayMode = ChatMessageDisplayMode.COLLABORATION_TASK),
            ChatMessage(sender = "user", content = "mail", displayMode = ChatMessageDisplayMode.COLLABORATION_EVENT),
            final(10),
        )
        val groups = responseProcessGroups(messages)
        assertEquals(setOf(0, 1, 2), groups.keys)
        assertEquals(0, groups.getValue(0).firstIndex)
        assertEquals(2, groups.getValue(0).headerIndex)
    }

    @Test fun humanMessageSeparatesLeadingAgentCardsFromNextReply() {
        val messages = listOf(
            ChatMessage(sender = "user", content = "mail", displayMode = ChatMessageDisplayMode.COLLABORATION_EVENT),
            ChatMessage(sender = "user", content = "new request"),
            final(10),
        )
        assertTrue(responseProcessGroups(messages).isEmpty())
    }

    @Test fun foldsAiSegmentsAndAgentInputsButKeepsHumanSteering() {
        val messages = listOf(
            intermediate(10),
            ChatMessage(sender = "user", content = "steer"),
            intermediate(10),
            ChatMessage(sender = "user", content = "mail", displayMode = ChatMessageDisplayMode.COLLABORATION_EVENT),
            final(10),
        )
        val groups = responseProcessGroups(messages)
        assertEquals(setOf(0, 2, 3, 4), groups.keys)
        assertEquals(1, groups.values.toSet().size)
        assertEquals(0, groups.getValue(4).firstIndex)
        assertEquals(4, groups.getValue(0).finalIndex)
        assertEquals(3000L, groups.getValue(0).durationMs)
    }

    @Test fun unfinishedOrDifferentTurnsAreNeverCollapsedTogether() {
        assertTrue(responseProcessGroups(listOf(intermediate(10))).isEmpty())
        assertTrue(responseProcessGroups(listOf(intermediate(10), final(20))).isEmpty())
        assertTrue(responseProcessGroups(listOf(intermediate(10), final(10).copy(completedAt = 0))).isEmpty())
        assertTrue(responseProcessGroups(listOf(final(10))).isEmpty())
    }

    @Test fun eachTurnHasItsOwnToggle() {
        val groups = responseProcessGroups(listOf(
            intermediate(10), final(10),
            intermediate(20), final(20),
        ))
        assertEquals(10L, groups.getValue(0).key)
        assertEquals(20L, groups.getValue(2).key)
        assertEquals(3, groups.getValue(2).finalIndex)
    }

    @Test fun emptySuffixAfterSteeringOrCancellationKeepsTheProcessVisible() {
        for (suffix in listOf("", " \n\t")) {
            val messages = listOf(
                intermediate(10),
                ChatMessage(sender = "user", content = "steer"),
                final(10).copy(content = suffix),
            )
            assertTrue(responseProcessGroups(messages).isEmpty())
        }
    }

    @Test fun paginationKeepsTheTurnKeyStable() {
        val messages = listOf(intermediate(10), intermediate(10), final(10))
        assertEquals(
            responseProcessGroups(messages).getValue(0).key,
            responseProcessGroups(messages.drop(1)).getValue(0).key,
        )
        assertTrue(responseProcessGroups(messages.take(2)).isEmpty())
    }

    private fun intermediate(sentAt: Long) = ChatMessage(
        sender = "ai", content = "work", sentAt = sentAt,
        displayMode = ChatMessageDisplayMode.ASSISTANT_INTERMEDIATE,
    )

    private fun final(sentAt: Long) = ChatMessage(
        sender = "ai", content = "answer", sentAt = sentAt, completedAt = 40,
        waitDurationMs = 1000, outputDurationMs = 2000,
    )
}
