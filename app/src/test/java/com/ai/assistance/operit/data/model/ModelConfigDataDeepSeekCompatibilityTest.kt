package com.ai.assistance.operit.data.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelConfigDataDeepSeekCompatibilityTest {

    @Test
    fun releasedConfigWithoutDeepSeekSearchField_keepsSearchPreferenceEnabled() {
        val config =
            Json.decodeFromString<ModelConfigData>(
                """{"id":"released-config","name":"DeepSeek"}"""
            )

        assertTrue(config.enableDeepSeekWebSearch)
    }
}
