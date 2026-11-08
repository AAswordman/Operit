package com.ai.assistance.operit.util.stream.plugins

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamMarkdownFencedCodeBlockPluginTest {
    @Test
    fun inlineBackticksNeverStartFencedCodeBlock() {
        listOf(
            "停止条件比如 ``` 收尾、工具调用的结束标记。\n后续正文。",
            "句中 ``` 第一处，后面 ``` 第二处。\n尾部正文。",
        ).forEach { content ->
            assertFalse(content, states(content).contains(PluginState.PROCESSING))
        }
    }

    @Test
    fun openingFenceAllowsAtMostThreeLeadingSpaces() {
        for (indent in 0..4) {
            val content = "${" ".repeat(indent)}```text\n代码内容\n"
            val expected = if (indent <= 3) PluginState.PROCESSING else PluginState.IDLE
            assertEquals("前导空格数量：$indent", expected, states(content).last())
        }
    }

    @Test
    fun invalidInfoStringCannotOpenFencedCodeBlock() {
        val content = "```text `说明`\n后续正文。"
        assertFalse(states(content).contains(PluginState.PROCESSING))
    }

    @Test
    fun closingFenceRequiresFullLineAndEnoughBackticks() {
        val opening = "````text\n代码内容\n"
        listOf("```", "````正文", "    ````", "```` \t`", "```` ````").forEach { closing ->
            assertEquals(
                closing,
                PluginState.PROCESSING,
                states("$opening$closing\n").last(),
            )
        }
        for (indent in 0..3) {
            val closing = "${" ".repeat(indent)}````` \t\r\n"
            assertEquals(PluginState.IDLE, states(opening + closing).last())
        }
    }

    @Test
    fun legitimateUnclosedFenceKeepsStreamingContentInCodeBlock() {
        val opening = "```text\n"
        val content = opening + "代码中的 ``` 标记\n还有正文"
        assertTrue(states(content).drop(opening.length - 1).all { it == PluginState.PROCESSING })
    }

    @Test
    fun inlineFenceDoesNotShiftFollowingValidFence() {
        val prefix = "说明 ``` 标记。\n"
        val opening = "```text\n"
        val content = prefix + opening + "代码内容\n```\n后续正文。"
        val result = states(content)
        assertFalse(result.take(prefix.length).contains(PluginState.PROCESSING))
        assertEquals(PluginState.PROCESSING, result[prefix.length + opening.length - 1])
        assertEquals(PluginState.IDLE, result.last())
    }

    private fun states(content: String): List<PluginState> {
        val plugin = StreamMarkdownFencedCodeBlockPlugin()
        return content.mapIndexed { index, char ->
            plugin.processChar(char, index == 0 || content[index - 1] == '\n')
            plugin.state
        }
    }
}
