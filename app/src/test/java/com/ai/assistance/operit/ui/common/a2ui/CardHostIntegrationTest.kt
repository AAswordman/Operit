package com.ai.assistance.operit.ui.common.a2ui

import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.MessageSection
import com.ai.assistance.operit.core.chat.protocol.MessageSectionCodec
import org.junit.Assert.*
import org.junit.Test

class CardHostIntegrationTest {
    private val document = """{"version":"v0.9","updateComponents":{"surfaceId":"card","components":[{"id":"root","component":"Text","text":"Visible"}]}}"""
    private val block = "```a2ui\n$document\n```"

    @Test fun summarizesTextSectionsWithoutTouchingThinkingOrToolResults() {
        val hidden = MessageSectionCodec.render(listOf(
            MessageSection.Thinking(block),
            MessageSection.ToolResult(name = "example", content = block),
        ))
        val original = "$hidden\nBefore\n$block\nAfter"
        val result = CardTranscript.forModel(original)
        assertTrue(result.startsWith(hidden))
        assertTrue(result.endsWith("Before\nHistorical A2UI summary (context only; do not output this text as a card). Visible content: Visible\nAfter"))
    }

    @Test fun outerCodeFenceAndIncompleteCardStayVerbatim() {
        val nested = "````markdown\n$block\n````"
        assertEquals(nested, CardTranscript.forModel(nested))
        val incomplete = "```a2ui\n$document"
        assertEquals(incomplete, CardTranscript.forModel(incomplete))
    }

    @Test fun interactionRequiresCurrentChatAndExactCandidateRevision() {
        val message = ChatMessage(sender = "ai", content = block, timestamp = 100, selectedVariantIndex = 2)
        val owner = A2uiOwner("a", 100, 2, block)
        assertTrue(matchesA2uiOwner("a", message, owner))
        assertFalse(matchesA2uiOwner("b", message, owner))
        assertFalse(matchesA2uiOwner("a", null, owner))
        assertFalse(matchesA2uiOwner("a", message.copy(timestamp = 101), owner))
        assertFalse(matchesA2uiOwner("a", message.copy(sender = "user"), owner))
        assertFalse(matchesA2uiOwner("a", message.copy(isVariantPreview = true), owner))
        assertFalse(matchesA2uiOwner("a", message.copy(selectedVariantIndex = 1), owner))
        assertFalse(matchesA2uiOwner("a", message.copy(content = "$block changed"), owner))
    }
}