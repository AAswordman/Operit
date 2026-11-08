package com.ai.assistance.operit.core.chat.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingJsonXmlConverterStringTest {

    @Test fun stringValue_withSlashEscape_isDecoded() {
        val converter = StreamingJsonXmlConverter()
        assertEquals("\n  <param name=\"url\">/a</param>", render(converter.feed("{\"url\":\"\\/a\"}")))
    }

    @Test fun stringValue_withFormFeedEscape_isDecoded() {
        val converter = StreamingJsonXmlConverter()
        assertEquals("\n  <param name=\"text\">\u000c</param>", render(converter.feed("{\"text\":\"\\f\"}")))
    }

    @Test fun stringValue_withBackspaceEscape_isDecoded() {
        val converter = StreamingJsonXmlConverter()
        assertEquals("\n  <param name=\"text\">\b</param>", render(converter.feed("{\"text\":\"\\b\"}")))
    }

    private fun render(events: List<StreamingJsonXmlConverter.Event>): String {
        return events.joinToString("") { if (it is StreamingJsonXmlConverter.Event.Tag) it.text else (it as StreamingJsonXmlConverter.Event.Content).text }
    }
}
