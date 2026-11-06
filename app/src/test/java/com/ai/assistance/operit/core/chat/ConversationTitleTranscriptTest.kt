package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.data.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationTitleTranscriptTest {

    private fun userMessage(content: String, timestamp: Long) =
        ChatMessage(sender = "user", content = content, timestamp = timestamp)

    private fun aiMessage(content: String, timestamp: Long) =
        ChatMessage(sender = "ai", content = content, timestamp = timestamp)

    @Test
    fun build_ordersLinesOldestFirst() {
        val result =
            ConversationTitleTranscript.build(
                listOf(
                    userMessage("fix the gradle build", 1L),
                    aiMessage("checked the plugin block", 2L),
                )
            )

        assertEquals("User: fix the gradle build\nAssistant: checked the plugin block", result.transcript)
        assertEquals(2, result.messageCount)
        assertTrue(result.isUsable)
    }

    @Test
    fun build_skipsSummaryAndSystemMessages() {
        val result =
            ConversationTitleTranscript.build(
                listOf(
                    ChatMessage(sender = "summary", content = "previous summary text", timestamp = 1L),
                    ChatMessage(sender = "system", content = "system notice", timestamp = 2L),
                    userMessage("rename this chat", 3L),
                )
            )

        assertEquals("User: rename this chat", result.transcript)
        assertEquals(1, result.messageCount)
    }

    @Test
    fun build_stripsThinkingAndToolMarkup() {
        val result =
            ConversationTitleTranscript.build(
                listOf(
                    userMessage("what is in this folder", 1L),
                    aiMessage(
                        "<think>private reasoning</think>Let me look.<tool name=\"ls\">" +
                            "<param name=\"path\">/sdcard</param></tool>Done.",
                        2L
                    ),
                )
            )

        assertFalse(result.transcript.contains("private reasoning"))
        assertFalse(result.transcript.contains("<tool"))
        assertTrue(result.transcript.contains("User: what is in this folder"))
        assertTrue(result.transcript.contains("Assistant: Let me look."))
        assertTrue(result.transcript.contains("Done."))
    }

    @Test
    fun build_replacesImageOnlyMessageWithPlaceholder() {
        val imageLink = "<link type=\"image\" id=\"abc123\">image</link>"
        val result = ConversationTitleTranscript.build(listOf(userMessage(imageLink, 1L)))

        assertEquals("User: [image]", result.transcript)
        assertEquals(1, result.messageCount)
    }

    @Test
    fun build_dropsEmptyAndBlankContent() {
        val result =
            ConversationTitleTranscript.build(
                listOf(
                    userMessage("   ", 1L),
                    aiMessage("", 2L),
                    userMessage("keep me", 3L),
                )
            )

        assertEquals("User: keep me", result.transcript)
        assertEquals(1, result.messageCount)
    }

    @Test
    fun build_returnsUnusableResultWhenNothingToSummarize() {
        val result = ConversationTitleTranscript.build(listOf(userMessage("", 1L)))

        assertEquals("", result.transcript)
        assertEquals(0, result.messageCount)
        assertFalse(result.isUsable)
    }

    @Test
    fun build_prefersNewestMessagesWhenCharacterBudgetRunsOut() {
        val result =
            ConversationTitleTranscript.build(
                listOf(
                    userMessage("old topic about deployment", 1L),
                    aiMessage("old reply about deployment", 2L),
                    userMessage("new topic about fonts", 3L),
                    aiMessage("new reply about fonts", 4L),
                ),
                maxChars = 70,
            )

        assertFalse(result.transcript.contains("old topic"))
        assertTrue(result.transcript.contains("User: new topic about fonts"))
        assertTrue(result.transcript.contains("Assistant: new reply about fonts"))
    }

    @Test
    fun build_honoursMaxMessages() {
        val messages =
            (1..10).flatMap { index ->
                listOf(userMessage("user $index", index * 10L), aiMessage("ai $index", index * 10L + 1))
            }

        val result = ConversationTitleTranscript.build(messages, maxMessages = 3)

        assertEquals(3, result.messageCount)
        assertEquals("Assistant: ai 9\nUser: user 10\nAssistant: ai 10", result.transcript)
    }

    @Test
    fun build_truncatesSingleOversizedMessageToBudget() {
        val result = ConversationTitleTranscript.build(listOf(userMessage("x".repeat(500), 1L)), maxChars = 40)

        assertEquals(1, result.messageCount)
        assertEquals("User: ${"x".repeat(34)}", result.transcript)
    }

    @Test
    fun build_condensesLongMessages() {
        val longContent = "a".repeat(ConversationTitleTranscript.MAX_MESSAGE_CHARS + 50)
        val result = ConversationTitleTranscript.build(listOf(userMessage(longContent, 1L)))

        assertTrue(result.transcript.endsWith("…"))
        assertEquals(
            "User: " + "a".repeat(ConversationTitleTranscript.MAX_MESSAGE_CHARS) + "…",
            result.transcript
        )
    }

    @Test
    fun build_returnsEmptyForEmptyInput() {
        val result = ConversationTitleTranscript.build(emptyList())

        assertEquals("", result.transcript)
        assertEquals(0, result.messageCount)
    }
}
