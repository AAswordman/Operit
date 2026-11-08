package com.ai.assistance.operit.core.chat.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingJsonXmlConverterEscapeTest {

    @Test fun apostropheInString_isEscapedForXml() {
        val converter = StreamingJsonXmlConverter()
        assertEquals("\n  <param name=\"text\">&apos;</param>", render(converter.feed("{\"text\":\"'\"}")))
    }

    @Test fun quoteInString_isEscapedForXml() {
        val converter = StreamingJsonXmlConverter()
        assertEquals("\n  <param name=\"text\">&quot;</param>", render(converter.feed("{\"text\":\"\\\"\"}")))
    }

    private fun render(events: List<StreamingJsonXmlConverter.Event>): String {
        return events.joinToString("") { when (it) {
            is StreamingJsonXmlConverter.Event.Tag -> it.text
            is StreamingJsonXmlConverter.Event.Content -> it.text
        } }
    }
}
