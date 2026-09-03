package com.ai.assistance.operit.api.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatRuntimeHolderTest {
    @Test
    fun countCurrentTurnTools_includesEveryTrackedTurn() {
        val counts = mapOf(
            "main-chat" to 3,
            "floating-chat" to 4,
        )

        assertEquals(
            7,
            ChatRuntimeHolder.countCurrentTurnTools(counts),
        )
    }

    @Test
    fun countCurrentTurnTools_doesNotAllowNegativeCounts() {
        val counts = mapOf(
            "main-chat" to 2,
            "invalid-chat" to -100,
        )

        assertEquals(
            2,
            ChatRuntimeHolder.countCurrentTurnTools(counts),
        )
    }
}
