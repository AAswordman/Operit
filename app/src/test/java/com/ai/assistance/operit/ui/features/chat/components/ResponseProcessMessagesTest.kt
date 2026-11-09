package com.ai.assistance.operit.ui.features.chat.components

import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode
import org.junit.Assert.*
import org.junit.Test

class ResponseProcessMessagesTest {
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
