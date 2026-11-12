package com.ai.assistance.operit.core.agent.collaboration

import com.ai.assistance.operit.data.collects.ModelThinkingConfigDefaults
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelConfigData
import org.junit.Assert.*
import org.junit.Test

class CollaborationModelParametersTest {
    @Test fun usesTheConfiguredProviderMappingWithoutChangingTheSavedConfig() {
        val config = ModelConfigData(
            id = "config", name = "configured model",
            apiProviderType = ApiProviderType.OPENAI,
            modelName = "gpt-5.6-luna",
            thinkingConfigurations = ModelThinkingConfigDefaults.forProvider(ApiProviderType.OPENAI.name),
            thinkingOptionId = "low",
        )
        val result = CollaborationModelParameters.overrideConfig(config, 0, "high")
        assertEquals("high", result.thinkingOptionId)
        assertEquals("low", config.thinkingOptionId)
        assertSame(config, CollaborationModelParameters.overrideConfig(config, 0, null))
    }

    @Test fun unsupportedEffortIsRejectedBeforeBuildingAProviderRequest() {
        val config = ModelConfigData(id = "config", name = "unconfigured", modelName = "model")
        val failure = runCatching { CollaborationModelParameters.overrideConfig(config, 0, "high") }
        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
        assertTrue(failure.exceptionOrNull()!!.message!!.contains("not configured"))
    }
}
