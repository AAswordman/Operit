package com.ai.assistance.operit.data.model

import com.ai.assistance.operit.core.chat.tools.timing.ToolCallExecution
import com.ai.assistance.operit.core.chat.tools.timing.ToolTimingState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import com.ai.assistance.operit.core.chat.protocol.MessageSectionCodec
import com.ai.assistance.operit.data.chat.serialization.MessageSectionStorage

class ToolTimingPersistenceTest {
    private val content = "<tool name=\"read_file\"><param name=\"path\">/a</param></tool>"
    private val execution = ToolCallExecution(0, "saved-clock", 1_000L, 5_000L, 6_000L)

    @Test
    fun executionArrivingBeforeTextIsSavedWithoutChangingBodyOrSearch() {
        val state = ToolTimingState()
        state.update(execution)
        val message = ChatMessage("ai", toolTimingState = state)
        message.content = content
        val sections = message.resolvedSections()
        assertEquals(execution, (sections.single() as MessageSection.ToolCall).execution)
        assertEquals(content, MessageSectionCodec.render(sections))
        assertEquals("read_file /a", MessageSectionCodec.searchText(sections))
        assertFalse(message.displayContent().contains("durationMs"))
        assertFalse(message.content.contains("meta"))
    }

    @Test
    fun databaseVariantsAndJsonArchiveRetainExecution() {
        val state = ToolTimingState()
        state.update(execution)
        val message = ChatMessage("ai", content, toolTimingState = state)
        val loaded = MessageEntity.fromChatMessage("chat", message, 0).toChatMessage()
        assertEquals(execution, (loaded.resolvedSections().single() as MessageSection.ToolCall).execution)
        val variant = MessageVariantEntity.fromChatMessage("chat", message.timestamp, 1, message)
        val switched = variant.applyTo(message, 2)
        assertNull(switched.toolTimingState)
        assertEquals(execution, (switched.resolvedSections().single() as MessageSection.ToolCall).execution)
        val frozen = message.freezeToolTimings()
        state.update(execution.copy(durationMs = 9_000L))
        assertEquals(execution, (frozen.resolvedSections().single() as MessageSection.ToolCall).execution)
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val archived = json.decodeFromString<ChatMessage>(json.encodeToString(frozen))
        assertEquals(execution, (archived.resolvedSections().single() as MessageSection.ToolCall).execution)
        assertEquals(content, archived.content)
    }

    @Test
    fun loadedExecutionSurvivesLaterTextUpdatesAndUnknownHistoryStaysUnknown() {
        val sections = listOf((MessageSectionCodec.parse(content).single() as MessageSection.ToolCall).copy(execution = execution))
        val message = ChatMessage("ai", content, sections = sections)
        message.content += "后续正文"
        assertEquals(execution, (message.resolvedSections().first() as MessageSection.ToolCall).execution)
        val old = ChatMessage("ai", content)
        assertNull((old.resolvedSections().single() as MessageSection.ToolCall).execution)
    }

    @Test
    fun readingOldCandidateDropsOnlyItsLocalTimingProtocol() {
        val protocol = MessageSection.Protocol("operit:tool_timing", "", "<meta provider=\"operit:tool_timing\"/>")
        val vendor = MessageSection.Protocol("openai:responses_reasoning", "payload")
        val loaded = MessageSectionStorage.decode(MessageSectionStorage.encode(listOf(protocol, vendor)))
        assertEquals(listOf(vendor), loaded)
    }
}
