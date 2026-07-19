package com.ai.assistance.operit.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingJsonXmlConverterFlushTest {

    @Test fun flush_afterCompletedString_emitsNothing() {
        val converter = StreamingJsonXmlConverter()
        converter.feed("{\"a\":\"b\"}")
        assertTrue(converter.flush().isEmpty())
    }

    @Test fun flush_afterPrimitiveEmitsClosingContent() {
        val converter = StreamingJsonXmlConverter()
        converter.feed("{\"a\":1")
        val output = render(converter.flush())
        assertEquals("1</param>", output)
    }

    private fun render(events: List<StreamingJsonXmlConverter.Event>): String {
        return events.joinToString("") {
            when (it) {
                is StreamingJsonXmlConverter.Event.Tag -> it.text
                is StreamingJsonXmlConverter.Event.Content -> it.text
            }
        }
    }
}
