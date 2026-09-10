package com.ai.assistance.operit.core.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class AIMessageManagerDialogueReviewTest {
    @Test
    fun formatDialogueReviewHeader_usesDefaultHeaderWhenTitleIsBlank() {
        val defaultHeader = "\n\n对话回顾：\n"

        assertEquals(defaultHeader, formatDialogueReviewHeader(defaultHeader, "  "))
    }

    @Test
    fun formatDialogueReviewHeader_normalizesCustomTitleAndKeepsLocaleSeparator() {
        val header = formatDialogueReviewHeader("\n\n对话回顾：\n", "  执行回顾：  ")

        assertEquals("\n\n执行回顾：\n", header)
    }
}
