package com.ai.assistance.operit.util.stream.plugins

import com.ai.assistance.operit.util.ThinkingMarkup
import com.ai.assistance.operit.util.stream.StreamLogger
import org.junit.Before
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamXmlThinkingTokenTest {
    @Before fun disableAndroidLogging() { StreamLogger.setEnabled(false) }
    @After fun restoreAndroidLogging() { StreamLogger.setEnabled(true) }

    @Test fun tokenBlockOnlyFinishesAtMatchingClose() {
        val plugin = StreamXmlPlugin()
        val token = "aBc4"
        ThinkingMarkup.openTag(token).forEachIndexed { index, c -> plugin.processChar(c, index == 0) }
        val inner = "前半<think>标签示例</think></thinking></think token=\"Abc4\">后半"
        inner.forEach { c ->
            plugin.processChar(c, false)
            assertEquals(PluginState.PROCESSING, plugin.state)
        }
        val close = ThinkingMarkup.closeTag(token)
        close.dropLast(1).forEach { c ->
            plugin.processChar(c, false)
            assertEquals(PluginState.PROCESSING, plugin.state)
        }
        plugin.processChar(close.last(), false)
        assertEquals(PluginState.IDLE, plugin.state)
    }

    @Test fun tokenBlockAcceptsSingleQuotedMatchingClose() {
        val plugin = StreamXmlPlugin()
        val raw = "<think token='aBc4'>正文</think token='aBc4'>回答"
        raw.forEachIndexed { index, c -> plugin.processChar(c, index == 0) }
        assertEquals(PluginState.IDLE, plugin.state)
    }

    @Test fun resetDropsTokenFromPreviousBlock() {
        val plugin = StreamXmlPlugin()
        ThinkingMarkup.openTag("aBc4").forEachIndexed { index, c -> plugin.processChar(c, index == 0) }
        plugin.reset()
        "<think>草稿</think>".forEachIndexed { index, c -> plugin.processChar(c, index == 0) }
        assertEquals(PluginState.IDLE, plugin.state)
    }

    @Test fun unsupportedThinkingNameUsesOrdinaryXmlClosing() {
        val plugin = StreamXmlPlugin()
        val raw = "<operit_thinking token=\"aBc4\">普通内容</operit_thinking>"
        raw.forEachIndexed { index, c -> plugin.processChar(c, index == 0) }
        assertEquals(PluginState.IDLE, plugin.state)
    }

}
