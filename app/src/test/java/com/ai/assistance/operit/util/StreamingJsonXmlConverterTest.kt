package com.ai.assistance.operit.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingJsonXmlConverterTest {

    @Test fun stringValue_emitsParamTagAndContent() {
        val converter = StreamingJsonXmlConverter()
        assertEquals("\n  <param name=\"name\">value</param>", render(converter.feed("{\"name\":\"value\"}")))
    }

    @Test fun multipleValues_emitMultipleParams() {
        val converter = StreamingJsonXmlConverter()
        assertEquals(
            "\n  <param name=\"a\">1</param>\n  <param name=\"b\">2</param>",
            render(converter.feed("{\"a\":1,\"b\":2}"))
        )
    }

    @Test fun numericValue_isCapturedAsPrimitive() {
        val converter = StreamingJsonXmlConverter()
        assertEquals("\n  <param name=\"score\">42</param>", render(converter.feed("{\"score\":42}")))
    }

    @Test fun booleanValue_isCapturedAsPrimitive() {
        val converter = StreamingJsonXmlConverter()
        assertEquals("\n  <param name=\"enabled\">true</param>", render(converter.feed("{\"enabled\":true}")))
    }

    @Test fun nullValue_isCapturedAsPrimitive() {
        val converter = StreamingJsonXmlConverter()
        assertEquals("\n  <param name=\"value\">null</param>", render(converter.feed("{\"value\":null}")))
    }

    @Test fun arrayValue_isCapturedAsSinglePrimitiveBlock() {
        val converter = StreamingJsonXmlConverter()
        assertEquals("\n  <param name=\"items\">[1,2]</param>", render(converter.feed("{\"items\":[1,2]}")))
    }

    @Test fun objectValue_isCapturedAsSinglePrimitiveBlock() {
        val converter = StreamingJsonXmlConverter()
        assertEquals(
            "\n  <param name=\"obj\">{&quot;a&quot;:1}</param>",
            render(converter.feed("{\"obj\":{\"a\":1}}"))
        )
    }

    @Test fun escapedXmlCharacters_areEncoded() {
        val converter = StreamingJsonXmlConverter()
        assertEquals(
            "\n  <param name=\"text\">&lt;&amp;&gt;</param>",
            render(converter.feed("{\"text\":\"<&>\"}"))
        )
    }

    @Test fun unicodeEscape_isDecoded() {
        val converter = StreamingJsonXmlConverter()
        assertEquals("\n  <param name=\"text\">A</param>", render(converter.feed("{\"text\":\"\\u0041\"}")))
    }

    @Test fun escapedNewline_isDecoded() {
        val converter = StreamingJsonXmlConverter()
        assertEquals("\n  <param name=\"text\">line1\nline2</param>", render(converter.feed("{\"text\":\"line1\\nline2\"}")))
    }

    @Test fun escapedQuote_isDecodedAndEscapedForXml() {
        val converter = StreamingJsonXmlConverter()
        assertEquals("\n  <param name=\"text\">&quot;</param>", render(converter.feed("{\"text\":\"\\\"\"}")))
    }

    @Test fun escapedBackslash_isDecoded() {
        val converter = StreamingJsonXmlConverter()
        assertEquals("\n  <param name=\"path\">\\</param>", render(converter.feed("{\"path\":\"\\\\\"}")))
    }

    @Test fun chunkedStringInput_accumulatesAcrossFeeds() {
        val converter = StreamingJsonXmlConverter()
        val events = converter.feed("{\"name\":\"va") + converter.feed("lue\"}")
        assertEquals("\n  <param name=\"name\">value</param>", render(events))
    }

    @Test fun chunkedPrimitiveInput_flushesAtEnd() {
        val converter = StreamingJsonXmlConverter()
        val events = converter.feed("{\"score\":4") + converter.flush()
        assertEquals("\n  <param name=\"score\">4</param>", render(events))
    }

    @Test fun chunkedComplexValue_staysOpenUntilClosed() {
        val converter = StreamingJsonXmlConverter()
        val openingEvents = converter.feed("{\"items\":[1")
        assertTrue(converter.hasUnfinishedParam())
        val events = openingEvents + converter.feed(",2]}")
        assertEquals("\n  <param name=\"items\">[1,2]</param>", render(events))
    }

    @Test fun flushWithoutPendingPrimitive_returnsNoEvents() {
        val converter = StreamingJsonXmlConverter()
        assertTrue(converter.flush().isEmpty())
    }

    @Test fun unfinishedString_keepsParamOpen() {
        val converter = StreamingJsonXmlConverter()
        converter.feed("{\"name\":\"va")
        assertTrue(converter.hasUnfinishedParam())
    }

    @Test fun finishedString_closesParam() {
        val converter = StreamingJsonXmlConverter()
        converter.feed("{\"name\":\"value\"}")
        assertFalse(converter.hasUnfinishedParam())
    }

    @Test fun nestedObjectInsideArray_isPreserved() {
        val converter = StreamingJsonXmlConverter()
        assertEquals(
            "\n  <param name=\"items\">[{&quot;a&quot;:1}]</param>",
            render(converter.feed("{\"items\":[{\"a\":1}]}"))
        )
    }

    @Test fun whitespaceAroundValue_isIgnoredForPrimitiveParsing() {
        val converter = StreamingJsonXmlConverter()
        assertEquals("\n  <param name=\"score\">1</param>", render(converter.feed("{\"score\" : 1 }")))
    }

    private fun render(events: List<StreamingJsonXmlConverter.Event>): String {
        return events.joinToString("") {
            when (it) {
                is StreamingJsonXmlConverter.Event.Content -> it.text
                is StreamingJsonXmlConverter.Event.Tag -> it.text
            }
        }
    }
}
