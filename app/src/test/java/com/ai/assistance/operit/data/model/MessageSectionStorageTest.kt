package com.ai.assistance.operit.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.ai.assistance.operit.core.chat.protocol.MessageSectionCodec
import com.ai.assistance.operit.core.tools.protocol.ToolResult
import com.ai.assistance.operit.data.chat.serialization.MessageSectionStorage

class MessageSectionStorageTest {
    @Test fun encode_writesReadableSectionsArray() {
        val message = ChatMessage(sender = "ai", content = sample())
        val entity = MessageEntity.fromChatMessage("chat", message, orderIndex = 0)
        assertTrue(entity.sections.startsWith("[{\"type\":\"thinking\""))
        assertFalse(entity.sections.contains("operit-sections:"))
        assertFalse(entity.searchText.contains("cGF5bG9hZA=="))
        assertTrue(entity.searchText.contains("answer"))
    }

    @Test fun entityRoundTrip_keepsProtocolAndHidesItFromDisplay() {
        val original = ChatMessage(sender = "ai", content = sample())
        val restored = MessageEntity.fromChatMessage("chat", original, 0).toChatMessage()
        assertEquals(original.resolvedSections(), restored.resolvedSections())
        assertTrue(restored.sections.any { it is MessageSection.Protocol })
        assertEquals(listOf(MessageSection.Thinking("draft"), MessageSection.Text("answer")), restored.displaySections())
    }

    @Test fun archiveRoundTrip_exportsSectionsWithoutContent() {
        val json = Json { encodeDefaults = true }
        val message = ChatMessage(sender = "ai", content = sample(), timestamp = 7L)
        val encoded = json.encodeToString(ChatMessage.serializer(), message)
        assertFalse(json.parseToJsonElement(encoded).jsonObject.containsKey("content"))
        assertTrue(encoded.contains("\"sections\""))
        val decoded = json.decodeFromString(ChatMessage.serializer(), encoded)
        assertEquals(message.resolvedSections(), decoded.resolvedSections())
        assertEquals(listOf(MessageSection.Thinking("draft"), MessageSection.Text("answer")), decoded.displaySections())
    }
    @Test fun archiveImport_convertsLegacyContent() {
        val json = Json { ignoreUnknownKeys = true }
        val legacy = """{"sender":"ai","content":"<think>draft</think>answer","timestamp":7}"""
        val decoded = json.decodeFromString(ChatMessage.serializer(), legacy)
        assertEquals(2, decoded.sections.size)
        assertEquals("draft", (decoded.sections[0] as MessageSection.Thinking).content)
        assertEquals(listOf(MessageSection.Thinking("draft"), MessageSection.Text("answer")), decoded.displaySections())
    }
    @Test fun decode_migratedDevelopmentEnvelopeFromLegacyContent() {
        val legacy = "operit-sections:1:{\"sections\":[{\"type\":\"com.ai.assistance.operit.data.model.MessageSection.Text\",\"content\":\"answer\"}]}"
        assertEquals(
            listOf(MessageSection.Text("answer")),
            MessageSectionStorage.decode("", legacy),
        )
    }
    @Test fun decode_acceptsEnvelopeAlreadyStoredInSections() {
        val stored = "{\"sections\":[{\"type\":\"com.ai.assistance.operit.data.model.MessageSection.Text\",\"content\":\"answer\"}]}"
        assertEquals(listOf(MessageSection.Text("answer")), MessageSectionStorage.decode(stored))
    }
    @Test fun decode_recoversCorruptSectionsFromLegacyText() {
        assertEquals(
            listOf(MessageSection.Text("legacy answer")),
            MessageSectionStorage.decode("{broken", "legacy answer"),
        )
    }
    @Test fun decode_corruptEntitySectionsFallsBackToLegacyText() {
        val entity = MessageEntity(
            chatId = "chat",
            sender = "ai",
            sections = "{broken",
            searchText = "legacy answer",
            orderIndex = 0,
        )
        assertEquals("legacy answer", entity.toChatMessage().content)
    }
    @Test fun decode_emptySectionsUsesLegacyTextWhileBackfillIsPending() {
        assertEquals(
            listOf(MessageSection.Text("legacy answer")),
            MessageSectionStorage.decode("[]", "legacy answer"),
        )
    }


    @Test fun decode_directArrayPreservesLongEscapedToolPayload() {
        val payload = "换行\n引号\"反斜杠\\和中文".repeat(8_192)
        val sections = listOf(
            MessageSection.Text("说明\n"),
            MessageSection.ToolResult("read_file", "success", payload),
            MessageSection.Protocol("provider", "hidden"),
        )
        val stored = MessageSectionStorage.encode(sections)
        assertEquals(sections, MessageSectionStorage.decode(" \n\t$stored\r\n"))
    }
    @Test fun decode_directArrayIgnoresUnknownFields() {
        val stored = """[{"type":"text","content":"回答","future":{"items":[1,2,3]}}]"""
        assertEquals(listOf(MessageSection.Text("回答")), MessageSectionStorage.decode(stored))
    }
    private fun sample(): String =
        "<think>draft</think>answer<meta provider=\"openai:responses_reasoning\">cGF5bG9hZA==</meta>"

    @Test fun thinkingRoundTripDoesNotPersistRuntimeBoundary() {
        val thought = "前半</think>后半"
        val sections = listOf(MessageSection.Thinking(thought), MessageSection.Text("回答"))
        val stored = MessageSectionStorage.encode(sections)
        assertFalse(stored.contains("token="))
        assertFalse(stored.contains("markup"))
        val restored = MessageSectionStorage.decode(stored)
        assertEquals(sections, restored)
        val rendered = MessageSectionCodec.render(restored)
        assertEquals(rendered, MessageSectionCodec.render(restored))
        assertEquals(sections, MessageSectionCodec.parse(rendered))
    }
}
