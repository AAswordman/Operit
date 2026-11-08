package com.ai.assistance.operit.core.chat.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingJsonXmlConverterChunkTest {

    @Test fun chunkedKeyName_isAccumulatedAcrossFeeds() {
        val converter = StreamingJsonXmlConverter()
        val events = converter.feed("{\"na") + converter.feed("me\":\"x\"}")
        assertEquals("\n  <param name=\"name\">x</param>", render(events))
    }

    @Test fun chunkedUnicodeEscape_isAccumulatedAcrossFeeds() {
        val converter = StreamingJsonXmlConverter()
        val events = converter.feed("{\"text\":\"\\u00") + converter.feed("41\"}")
        assertEquals("\n  <param name=\"text\">A</param>", render(events))
    }

    private fun render(events: List<StreamingJsonXmlConverter.Event>): String {
        return events.joinToString("") { when (it) {
            is StreamingJsonXmlConverter.Event.Tag -> it.text
            is StreamingJsonXmlConverter.Event.Content -> it.text
        } }
    }
}
