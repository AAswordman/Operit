package com.ai.assistance.operit.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.util.streamnative.NativeXmlSplitter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThinkingTokenNativeTest {
    @Test fun nativeSplitterAndStructuredParserKeepWholeThinkingBlock() {
        val thought = "前半<think>标签示例</think>后半</thinking></operit_thinking>尾部"
        val wrapped = ThinkingMarkup.openTag("aBc4") + thought + ThinkingMarkup.closeTag("aBc4")
        assertEquals(listOf(listOf("think", wrapped), listOf("text", "回答")), NativeXmlSplitter.splitXmlTag(wrapped + "回答"))
        val blocks = StructuredAssistantContentParser.parse(wrapped + "回答")
        assertEquals(2, blocks.size)
        assertEquals(thought, blocks[0].content)
        assertTrue(blocks[0].closed)
        assertEquals("回答", blocks[1].content)
    }

    @Test fun nativeSplitterAcceptsSingleQuotedMatchingClose() {
        val raw = "<think token='aBc4'>正文</think token='aBc4'>回答"
        assertEquals(
            listOf(
                listOf("think", "<think token='aBc4'>正文</think token='aBc4'>"),
                listOf("text", "回答"),
            ),
            NativeXmlSplitter.splitXmlTag(raw),
        )
    }

    @Test fun nativeSplitterKeepsUnclosedBlockAndResetAllowsLegacyNextMessage() {
        val raw = ThinkingMarkup.openTag("aBc4") + "前半</think>后半"
        val block = StructuredAssistantContentParser.parse(raw).single()
        assertEquals("前半</think>后半", block.content)
        assertFalse(block.closed)
        assertEquals(2, NativeXmlSplitter.splitXmlTag("<think>草稿</think>回答").size)
    }

    @Test fun unsupportedThinkingNameUsesOrdinaryNativeXmlClosing() {
        val raw = "<operit_thinking token=\"aBc4\">普通内容</operit_thinking>"
        assertEquals(
            listOf(listOf("operit_thinking", raw), listOf("text", "回答")),
            NativeXmlSplitter.splitXmlTag(raw + "回答"),
        )
        val blocks = StructuredAssistantContentParser.parse(raw + "回答")
        assertEquals(2, blocks.size)
        assertEquals("普通内容", blocks[0].content)
        assertTrue(blocks[0].closed)
    }

}
