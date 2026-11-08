package com.ai.assistance.operit.util.stream.plugins

import com.ai.assistance.operit.util.stream.StreamLogger
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamXmlToolBoundaryTest {
    @Before fun disableAndroidLogging() { StreamLogger.setEnabled(false) }
    @After fun restoreAndroidLogging() { StreamLogger.setEnabled(true) }

    @Test fun toolTagsStartImmediatelyAfterOrdinaryText() {
        for (tag in listOf("tool", "tool_exec", "tool_result", "tool_result_aBc4")) {
            val plugin = StreamXmlPlugin()
            ("说明<$tag name=\"run\">").forEachIndexed { index, char -> plugin.processChar(char, index == 0) }
            assertEquals(PluginState.PROCESSING, plugin.state)
            "正文</content>尾部".forEach { char -> plugin.processChar(char, false) }
            assertEquals(PluginState.PROCESSING, plugin.state)
            ("</$tag>").forEach { char -> plugin.processChar(char, false) }
            assertEquals(PluginState.IDLE, plugin.state)
        }
    }

    @Test fun ordinaryInlineXmlKeepsItsOriginalBoundaryRule() {
        val plugin = StreamXmlPlugin()
        "说明<details>".forEachIndexed { index, char -> plugin.processChar(char, index == 0) }
        assertEquals(PluginState.IDLE, plugin.state)
    }

    @Test fun resetAllowsAnOrdinaryXmlTagAtLineStart() {
        val plugin = StreamXmlPlugin()
        "说明<tool_exec name=\"run\">".forEachIndexed { index, char -> plugin.processChar(char, index == 0) }
        plugin.reset()
        "<details>".forEachIndexed { index, char -> plugin.processChar(char, index == 0) }
        assertEquals(PluginState.PROCESSING, plugin.state)
    }
}
