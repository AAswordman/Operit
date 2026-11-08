package com.ai.assistance.operit.core.chat.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProtocolMetaDisplayFilterCheckpointTest {
    private fun tag(text: String) = "<meta provider='openai:responses_reasoning'>$text</meta>"

    @Test fun openingCheckpointSurvivesRejectionAndMultipleRestores() {
        val filter = ProtocolMetaDisplayFilter()
        assertEquals("前", filter.append("前<me"))
        val saved = filter.checkpoint()
        filter.append("x>错误")
        repeat(3) {
            filter.restore(saved)
            assertEquals("正确", filter.append("ta provider='openai:responses_reasoning'>hidden</meta>正确"))
        }
    }

    @Test fun fencedExampleCheckpointSurvivesDiscardedClosureAcrossAllChunks() {
        val rawPrefix = "```xml\n" + tag("example") + "\n"
        val suffix = "```\n" + tag("secret") + "正确"
        for (cut in 0..suffix.length) {
            val filter = ProtocolMetaDisplayFilter()
            val visiblePrefix = filter.append(rawPrefix)
            val checkpoint = filter.checkpoint()
            filter.append("```\n错误")
            filter.restore(checkpoint)
            val result = visiblePrefix + filter.append(suffix.substring(0, cut)) + filter.append(suffix.substring(cut)) + filter.finish()
            assertEquals("```xml\n" + tag("example") + "\n```\n正确", result)
        }
    }

    @Test fun xmlQuoteCommentAndCdataStatesSurviveCheckpoints() {
        for (prefix in listOf("<tool title='a>", "<tool><!-- </tool", "<tool><![CDATA[`</tool")) {
            val ending = when {
                prefix.contains("CDATA") -> ">]]>"
                prefix.contains("<!--") -> ">-->"
                else -> "b'>"
            }
            val filter = ProtocolMetaDisplayFilter()
            filter.append(prefix)
            val checkpoint = filter.checkpoint()
            filter.append(ending + "</tool>`错误`")
            filter.restore(checkpoint)
            assertEquals(ending + "`内部`</tool>正确", filter.append(ending + "`内部`</tool>" + tag("secret") + "正确") + filter.finish())
        }
    }

    @Test fun allCharacterBoundariesKeepNestedXmlAndEscapedMarkersStable() {
        val xml = "<tool a='>'><tool/><node>`内部`</node><!--`</tool>--><![CDATA[`</tool>]]></tool>"
        val escaped = "\\``说明"
        val expected = xml + escaped + "正确"
        val raw = xml + escaped + tag("secret") + "正确"
        for (size in 1..17) {
            val filter = ProtocolMetaDisplayFilter()
            val actual = raw.chunked(size).joinToString("") { filter.append(it) } + filter.finish()
            assertEquals("chunk=$size", expected, actual)
            assertFalse(actual.contains("secret"))
        }
    }

    @Test(timeout = 20_000) fun manyProtocolBoundariesDoNotRescanTheGrowingXmlPrefix() {
        val filter = ProtocolMetaDisplayFilter()
        val segment = "<tool note='a>b'><tool/>`内部`<!-- </tool> --><![CDATA[</tool>]]></tool>\n"
        repeat(8_000) {
            assertEquals(segment, filter.append(segment))
            assertEquals("", filter.append(tag("secret")))
        }
        assertEquals("结束", filter.append("结束") + filter.finish())
    }
}
