package com.ai.assistance.operit.core.chat.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingJsonXmlConverterObjectTest {

    @Test fun nestedObjectWithString_preservesQuotesInsideBody() {
        val converter = StreamingJsonXmlConverter()
        val output = render(converter.feed("{\"obj\":{\"name\":\"x\"}}"))
        assertEquals("\n  <param name=\"obj\">{&quot;name&quot;:&quot;x&quot;}</param>", output)
    }

    @Test fun nestedArrayWithString_preservesQuotesInsideBody() {
        val converter = StreamingJsonXmlConverter()
        val output = render(converter.feed("{\"arr\":[\"x\"]}"))
        assertEquals("\n  <param name=\"arr\">[&quot;x&quot;]</param>", output)
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
