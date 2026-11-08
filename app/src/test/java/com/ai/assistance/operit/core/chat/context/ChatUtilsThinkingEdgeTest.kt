package com.ai.assistance.operit.core.chat.context

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatUtilsThinkingEdgeTest {

    @Test fun removeThinkingContent_handlesThinkingTagVariant() {
        assertEquals("answer", ChatUtils.removeThinkingContent("<thinking>x</thinking>answer"))
    }

    @Test fun extractThinkingContent_handlesNoSearchTag() {
        val result = ChatUtils.extractThinkingContent("<think>x</think>")
        assertEquals("", result.first)
        assertEquals("x", result.second)
    }

    @Test fun removeThinkingContent_handlesSearchOnlyContent() {
        assertEquals("", ChatUtils.removeThinkingContent("<search>x</search>"))
    }
}
