package com.ai.assistance.operit.core.chat.protocol

import com.ai.assistance.operit.util.stream.StreamLogger
import com.ai.assistance.operit.util.stream.TextStreamRevisionTracker
import com.ai.assistance.operit.util.stream.plugins.PluginState
import com.ai.assistance.operit.util.stream.plugins.StreamXmlPlugin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class ProtocolMetaDisplayFilterTest {
    private val provider = "openai:responses_reasoning"
    private fun tag(payload: String = "secret-payload", type: String = provider) =
        "<meta provider=\"$type\">$payload</meta>"

    @Before fun disableAndroidLogging() { StreamLogger.setEnabled(false) }
    @After fun restoreAndroidLogging() { StreamLogger.setEnabled(true) }

    @Test fun unfinishedOuterXmlDoesNotExposeProtocolWhileStreaming() {
        val prefix = "<unfinished>正文\n"
        val raw = prefix + tag() + "尾"
        val oldClassifier = StreamXmlPlugin()
        raw.forEachIndexed { index, char ->
            oldClassifier.processChar(char, index == 0 || raw[index - 1] == '\n')
        }
        assertEquals(PluginState.PROCESSING, oldClassifier.state)

        val filter = ProtocolMetaDisplayFilter()
        assertEquals(prefix, filter.append(prefix + "<meta provider=\"$provider\">secret"))
        assertEquals("", filter.append("-payload"))
        assertEquals("尾", filter.append("</meta>尾"))
        assertEquals("", filter.finish())
    }

    @Test fun allProtocolProvidersAreHiddenAfterOrdinaryText() {
        for (type in listOf("gemini:thought_signature", provider, "openai:responses_output_item")) {
            assertEquals("前后", protocolMetaDisplayText("前" + tag(type = type) + "后"))
        }
    }

    @Test fun everyChunkBoundaryProducesTheSameVisibleContent() {
        val raw = "<unfinished>前" + tag() + "后"
        for (cut in 0..raw.length) {
            val filter = ProtocolMetaDisplayFilter()
            val first = filter.append(raw.substring(0, cut))
            val second = filter.append(raw.substring(cut))
            assertFalse(first.contains("secret"))
            assertFalse(second.contains("secret"))
            assertEquals("<unfinished>前后", first + second + filter.finish())
        }
    }

    @Test fun characterByCharacterOutputNeverPublishesProtocolPayload() {
        val filter = ProtocolMetaDisplayFilter()
        val visible = StringBuilder()
        for (char in "前" + tag() + "后") {
            visible.append(filter.append(char.toString()))
            assertFalse(visible.contains("<meta"))
            assertFalse(visible.contains("secret"))
        }
        visible.append(filter.finish())
        assertEquals("前后", visible.toString())
    }

    @Test fun longPayloadStaysHiddenBeforeItsClosingTagArrives() {
        val filter = ProtocolMetaDisplayFilter()
        assertEquals("正文", filter.append("正文<meta provider=\"$provider\">"))
        val payload = "A".repeat(256 * 1024)
        for (chunk in payload.chunked(37)) assertEquals("", filter.append(chunk))
        assertEquals("尾", filter.append("</meta>尾"))
        assertEquals("", filter.finish())
    }

    @Test fun unfinishedProtocolAndKnownOpeningRemainHiddenAtEnd() {
        assertEquals("正文", protocolMetaDisplayText("正文<meta provider=\"$provider\">secret"))
        assertEquals("正文", protocolMetaDisplayText("正文<meta provider=\"$provider\""))
        assertEquals("正文<me", protocolMetaDisplayText("正文<me"))
    }

    @Test fun ordinaryMetaAndUnrelatedAttributesRemainVisible() {
        for (sample in listOf(
            "<meta charset=\"utf-8\">",
            "<meta provider=\"custom\">example</meta>",
            "<meta data-provider=\"$provider\">example</meta>",
            "<meta title='provider=\"$provider\"'>example</meta>",
            "<metadata>example</metadata>",
            "<meta-name>example</meta-name>",
            "&lt;meta provider=\"$provider\"&gt;",
        )) assertEquals(sample, protocolMetaDisplayText(sample))
    }

    @Test fun mixedCaseQuotesAndQuotedAngleBracketsAreHandled() {
        val raw = "前<META note='a>b' PROVIDER = 'OPENAI:RESPONSES_REASONING'>secret</MeTa>后"
        assertEquals("前后", protocolMetaDisplayText(raw))
    }

    @Test fun overlappingOpeningAndAdjacentProtocolBlocksDoNotLeak() {
        assertEquals("<尾", protocolMetaDisplayText("<" + tag() + tag() + "尾"))
        assertEquals("前后", protocolMetaDisplayText("前<meta provider=\"$provider\"/>后"))
    }

    @Test fun inlineCodeExampleIsPreservedAndFollowingProtocolIsHidden() {
        val sample = "说明 `" + tag("example") + "`"
        assertEquals(sample + "正文", protocolMetaDisplayText(sample + tag() + "正文"))
        assertEquals(sample, protocolMetaDisplayText(sample))
    }

    @Test fun fencedCodeExamplesAndClosingFenceAtEndRemainVisible() {
        for (marker in listOf("```", "````", "~~~", "~~~~")) {
            for (ending in listOf("", "\n正文")) {
                val raw = "  $marker xml\n" + tag("example") + "\n  $marker" + ending
                val filter = ProtocolMetaDisplayFilter()
                val visible = raw.chunked(3).joinToString("") { filter.append(it) } + filter.finish()
                assertEquals(raw, visible)
            }
        }
    }

    @Test fun unfinishedCodeCannotPublishProtocolDuringOrAfterTheStream() {
        for (prefix in listOf("`代码", "```xml\n", "~~~xml\n")) {
            val filter = ProtocolMetaDisplayFilter()
            assertEquals(prefix, filter.append(prefix + tag() + "\n尾"))
            assertEquals("\n尾", filter.finish())
            assertEquals(prefix + "\n尾", protocolMetaDisplayText(prefix + tag() + "\n尾"))
        }
    }

    @Test fun rollbackUsesRawSavepointAndRebuildsHiddenState() {
        val tracker = TextStreamRevisionTracker()
        val filter = ProtocolMetaDisplayFilter()
        val rawPrefix = "前<meta provider=\"$provider\">saved-secret"
        tracker.append(rawPrefix)
        assertEquals("前", filter.append(rawPrefix))
        tracker.savepoint("retry", rawPrefix.length)
        val checkpoint = filter.checkpoint()
        tracker.append("discarded</meta>错误")
        filter.append("discarded</meta>错误")

        val restored = tracker.rollback("retry", rawPrefix.length, "discarded</meta>错误".length)!!.toString()
        assertEquals(rawPrefix, restored)
        filter.restore(checkpoint)
        assertEquals("正确", filter.append("continued-secret</meta>正确"))
        assertEquals("", filter.finish())
    }

    @Test fun rollbackToAPartialOpeningContinuesWithoutLeaking() {
        val filter = ProtocolMetaDisplayFilter()
        assertEquals("前", filter.append("前<me"))
        val checkpoint = filter.checkpoint()
        filter.append("x>错误")
        filter.restore(checkpoint)
        assertEquals("后", filter.append("ta provider=\"$provider\">secret</meta>后"))
        assertEquals("", filter.finish())
    }

    @Test fun escapedCodeMarkerDoesNotTurnProtocolIntoAnExample() {
        assertEquals("\\`前后", protocolMetaDisplayText("\\`前" + tag() + "后"))
    }

    @Test fun largeNestedXmlKeepsCodeExamplesWithoutExposingFollowingProtocol() {
        val xml = buildString {
            append("<tool name='render' note='a>b'>")
            repeat(2_048) {
                append("<tool>`内部代码`<!-- </tool> --><![CDATA[</tool>]]></tool>")
            }
            append("</tool>")
        }
        val example = "\n```xml\n" + tag("example") + "\n```\n"
        val raw = xml + example + tag() + "正文"
        assertEquals(xml + example + "正文", protocolMetaDisplayText(raw))
    }

    @Test fun unfinishedXmlWithInternalBackticksStillHidesProtocolAcrossChunks() {
        val prefix = "<tool note='a>b'><!-- </tool> --><![CDATA[`</tool>]]><node/>`内部代码"
        val filter = ProtocolMetaDisplayFilter()
        val raw = prefix + tag() + "正文"
        val visible = raw.chunked(11).joinToString("") { filter.append(it) } + filter.finish()
        assertEquals(prefix + "正文", visible)
    }

    @Test fun ordinaryTextIsPublishedWithoutWaitingForCompletion() {
        val filter = ProtocolMetaDisplayFilter()
        assertEquals("正常正文", filter.append("正常正文"))
        assertEquals("继续", filter.append("继续"))
        assertEquals("", filter.finish())
    }
}
