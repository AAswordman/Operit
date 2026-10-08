package com.ai.assistance.operit.util.toolmarkup

import com.ai.assistance.operit.util.ChatMarkupRegex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ToolResultMarkupTest {
    @Test fun incompleteResultAndMultipleLegacyBlocksAreNotACompleteBlock() {
        assertEquals(false, ToolResultMarkup.isCompleteBlock("<tool_result_exec><content><br/>"))
        val raw = "<tool_result><content>done</content></tool_result>"
        assertEquals(false, ToolResultMarkup.isCompleteBlock(raw + raw))
        assertThrows(IllegalArgumentException::class.java) { ToolResultMarkup.contentFromBlock(raw + raw) }
    }

    @Test fun retainsClosingTagExamplesAndTail() {
        val payload = "开头</content>中间<content>内部</content>尾部"
        assertEquals(payload, ToolResultMarkup.contentFromBody("<content>$payload</content>"))
    }

    @Test fun retainsSourceCdataWhitespaceAndXmlEntities() {
        val payload = "\n<file-diff><![CDATA[<content>示例</content>]]></file-diff>\n&lt;原文&gt; &amp;\n"
        assertEquals(payload, ToolResultMarkup.contentFromBody("  <content>$payload</content>\n"))
    }

    @Test fun adjacentResultsRemainIndependent() {
        val payloads = listOf("前</content>后", "<content>二</content>尾")
        val blocks = payloads.mapIndexed { index, body ->
            "<tool_result_x$index name=\"run\" status=\"success\"><content>$body</content></tool_result_x$index>"
        }
        val parsed = ChatMarkupRegex.toolResultAnyPattern.findAll(blocks.joinToString("\n"))
            .map { ToolResultMarkup.contentFromBlock(it.value) }.toList()
        assertEquals(payloads, parsed)
        assertThrows(IllegalArgumentException::class.java) {
            ToolResultMarkup.contentFromBlock(blocks.joinToString("\n"))
        }
    }

    @Test fun legacyBareBodyAndEmptyResultsKeepTheirMeaning() {
        assertEquals("直接正文</content>尾部", ToolResultMarkup.contentFromBody("直接正文</content>尾部"))
        assertEquals("", ToolResultMarkup.contentFromBody("<content></content>"))
        assertEquals("", ToolResultMarkup.contentFromBlock("<tool_result_exec name=\"run\" />"))
    }

    @Test fun doesNotExtractAContentExampleFromAnUnwrappedBody() {
        val body = "说明<content>示例</content>说明尾部"
        assertEquals(body, ToolResultMarkup.contentFromBody(body))
    }

    @Test fun errorBodyAndCaseInsensitiveWrapperRemainComplete() {
        val body = "<error>错误</content>后续</error>"
        assertEquals(body, ToolResultMarkup.contentFromBody("<CONTENT>$body</CONTENT>"))
    }
}
