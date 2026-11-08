package com.ai.assistance.operit.core.chat.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingJsonXmlConverterPrimitiveTest {

    @Test fun primitiveFalse_followedByObjectEnd_isClosed() {
        val converter = StreamingJsonXmlConverter()
        assertEquals("\n  <param name=\"ok\">false</param>", render(converter.feed("{\"ok\":false}")))
    }

    @Test fun primitiveWithWhitespaceBeforeComma_isClosed() {
        val converter = StreamingJsonXmlConverter()
        assertEquals(
            "\n  <param name=\"a\">1</param>\n  <param name=\"b\">2</param>",
            render(converter.feed("{\"a\":1 ,\"b\":2}"))
        )
    }

    @Test fun nestedComplexPrimitive_flushesOnlyAfterClosure() {
        val converter = StreamingJsonXmlConverter()
        converter.feed("{\"a\":{\"b\":[1")
        assertEquals("", render(converter.flush()))
    }

    private fun render(events: List<StreamingJsonXmlConverter.Event>): String {
        return events.joinToString("") { if (it is StreamingJsonXmlConverter.Event.Tag) it.text else (it as StreamingJsonXmlConverter.Event.Content).text }
    }
}
