package com.ai.assistance.operit.core.chat.context

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatUtilsTokenEstimateTest {

    @Test fun estimateTokenCount_emptyText_isZero() {
        assertEquals(0L, ChatUtils.estimateTokenCount(""))
    }

    @Test fun estimateTokenCount_asciiOnlyRoundsDown() {
        assertEquals(2L, ChatUtils.estimateTokenCount("abcdefgh"))
    }

    @Test fun estimateTokenCount_chineseOnlyUsesHigherWeight() {
        assertEquals(4L, ChatUtils.estimateTokenCount("你好世"))
    }
}
