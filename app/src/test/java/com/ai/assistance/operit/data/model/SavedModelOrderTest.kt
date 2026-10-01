package com.ai.assistance.operit.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedModelOrderTest {
    @Test
    fun `keeps saved models first and appends newly available models`() {
        val result = applySavedModelOrder(
            models = listOf("new-model", "model-b", "model-a"),
            savedOrder = listOf("model-a", "model-b")
        )

        assertEquals(listOf("model-a", "model-b", "new-model"), result)
    }

    @Test
    fun `drops unavailable and duplicate model ids`() {
        val result = applySavedModelOrder(
            models = listOf("model-b", "model-a", "model-b"),
            savedOrder = listOf("removed", "model-a", "model-a")
        )

        assertEquals(listOf("model-a", "model-b"), result)
    }
}
