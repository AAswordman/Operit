package com.ai.assistance.operit.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatUtilsThinkingTest {

    @Test fun removeThinkingContent_trimsWhitespaceAroundRemovedBlocks() {
        assertEquals("answer", ChatUtils.removeThinkingContent("  <think>x</think> answer  "))
    }

    @Test fun extractThinkingContent_returnsEmptyThinkingWhenAbsent() {
        val result = ChatUtils.extractThinkingContent("answer")
        assertEquals("answer", result.first)
        assertEquals("", result.second)
    }

    @Test fun extractThinkingContent_trimsEachThinkingSegment() {
        val result = ChatUtils.extractThinkingContent("<think>  a  </think><thinking> b </thinking>c")
        assertEquals("c", result.first)
        assertEquals("a\nb", result.second)
    }

    @Test fun removeThinkingContent_preservesMiddleText() {
        assertEquals("ab", ChatUtils.removeThinkingContent("a<think>x</think>b"))
    }

    @Test fun removeThinkingContent_tokenBlockIgnoresInnerClose() {
        val raw = "<think token=\"aBc4\">前半</think>后半</think token=\"aBc4\">回答"
        assertEquals("回答", ChatUtils.removeThinkingContent(raw))
        assertEquals("回答" to "前半</think>后半", ChatUtils.extractThinkingContent(raw))
    }

    @Test fun extractThinkingContent_unclosedTokenNeverReturnsThoughtAsAnswer() {
        val raw = "前文<think token=\"aBc4\">前半</think>后半"
        assertEquals("前文", ChatUtils.removeThinkingContent(raw))
        assertEquals("前文" to "前半</think>后半", ChatUtils.extractThinkingContent(raw))
    }
}
