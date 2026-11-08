package com.ai.assistance.operit.core.chat.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProtocolMetaToolPayloadTest {
    private val providers = listOf(
        "gemini:thought_signature", "openai:responses_reasoning", "openai:responses_output_item",
    )

    private fun meta(provider: String = providers.last(), payload: String = "secret-payload") =
        """<meta provider="$provider">$payload</meta>"""

    private fun block(tag: String, body: String) =
        """<$tag name="read_file"><content>$body</content></$tag>"""

    @Test
    fun xmlPayloadCodeMarkersCannotDelayFollowingText() {
        val tags = listOf("tool", "tool_Ab12", "tool_result", "tool_result_Ab12", "think", "thinking", "search", "status", "plan")
        val bodies = listOf("`", "``", "```kotlin\nval value = 1", "~~~text\n源码内容")
        for (provider in providers) for (tag in tags) for (body in bodies) {
            val prefix = block(tag, body)
            val filter = ProtocolMetaDisplayFilter()
            assertEquals(prefix, filter.append(prefix))
            // 必须在 finish 前出现，避免最终文本正确掩盖整轮流式停更。
            assertEquals("后续正文", filter.append(meta(provider) + "后续正文"))
            assertEquals("", filter.finish())
        }
    }

    @Test
    fun everyChunkBoundaryPublishesTheTailBeforeFinish() {
        for (body in listOf("`", "```text\n未配对围栏", "~~~text\n未配对围栏")) {
            val prefix = block("tool_result", body)
            val raw = prefix + meta() + "正文继续"
            for (cut in 0..raw.length) {
                val filter = ProtocolMetaDisplayFilter()
                val visible = filter.append(raw.substring(0, cut)) + filter.append(raw.substring(cut))
                assertEquals("切分位置 $cut", prefix + "正文继续", visible)
                assertFalse(visible.contains("secret-payload"))
                assertEquals("", filter.finish())
            }
        }
    }

    @Test
    fun repeatedToolRoundsContinuePublishingWithoutClosingTheStream() {
        val filter = ProtocolMetaDisplayFilter()
        for ((index, body) in listOf("`", "```text\n源码", "~~~text\n源码").withIndex()) {
            val prefix = block("tool_result", body)
            assertEquals(prefix, filter.append(prefix))
            assertEquals("第 $index 轮正文", filter.append(meta() + "第 $index 轮正文"))
        }
        assertEquals("", filter.finish())
    }

    @Test
    fun xmlAttributesCommentsAndCdataCannotLeakTheirMarkersIntoMarkdown() {
        val prefixes = listOf(
            """<tool_result note="> `"><content>源码</content></tool_result>""",
            "<tool_result><content><![CDATA[源码 ` </tool_result>]]></content></tool_result>",
            "<tool_result><!-- ` </tool_result> --><content>源码</content></tool_result>",
            "<tool_result><tool_result>嵌套 `</tool_result>后续 ``</tool_result>",
            """<status note="> `"/>""",
            "<!-- ` -->",
            "<![CDATA[`]]>",
        )
        for (prefix in prefixes) {
            val filter = ProtocolMetaDisplayFilter()
            assertEquals(prefix + "正常正文", filter.append(prefix + meta() + "正常正文"))
            assertEquals("", filter.finish())
        }
    }

    @Test
    fun incompleteXmlStillHidesProtocolAndPublishesFollowingCharacters() {
        val prefix = "<tool_result><content>源码 `\n"
        val filter = ProtocolMetaDisplayFilter()
        assertEquals(prefix, filter.append(prefix + meta() + ""))
        assertEquals("后续结果</content></tool_result>正文", filter.append("后续结果</content></tool_result>正文"))
        assertEquals("", filter.finish())
    }

    @Test
    fun realMarkdownExamplesAfterXmlRemainVisible() {
        val prefix = block("tool_result", "源码 `")
        val example = "说明 `" + meta(payload = "示例") + "`"
        val filter = ProtocolMetaDisplayFilter()
        assertEquals(prefix + example + "正文", filter.append(prefix + example + meta() + "正文"))
        assertEquals("", filter.finish())
    }

    @Test
    fun xmlWrittenInsideMarkdownCodeIsStillAnExample() {
        val example = "`<tool>" + meta(payload = "示例") + "</tool>`"
        val filter = ProtocolMetaDisplayFilter()
        assertEquals(example + "正文", filter.append(example + meta() + "正文"))
        assertEquals("", filter.finish())
    }

    @Test
    fun anActualUnclosedMarkdownExampleKeepsItsExistingProtection() {
        val prefix = block("tool_result", "源码 `") + "\n```xml\n"
        val filter = ProtocolMetaDisplayFilter()
        assertEquals(prefix, filter.append(prefix + meta() + "正文"))
        assertEquals("正文", filter.finish())
    }

    @Test
    fun replayAndRollbackRestoreTheSameVisiblePrefix() {
        val rawPrefix = block("tool_result", "源码 `") + meta() + "保留正文"
        val expected = block("tool_result", "源码 `") + "保留正文"
        val filter = ProtocolMetaDisplayFilter()
        assertEquals(expected, filter.append(rawPrefix))
        val checkpoint = filter.checkpoint()
        filter.append("应撤回的内容")
        filter.restore(checkpoint)
        assertEquals("重试后正文", filter.append(meta() + "重试后正文"))
        assertEquals("", filter.finish())
        val replay = ProtocolMetaDisplayFilter()
        assertEquals(expected + "重试后正文", replay.append(rawPrefix + meta() + "重试后正文"))
        assertEquals("", replay.finish())
    }
}