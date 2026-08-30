package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.collects.ModelThinkingConfigDefaults
import com.ai.assistance.operit.data.model.ApiProviderType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OtherProviderThinkingPresetTest {
    private val configurations = ModelThinkingConfigDefaults.forProvider(ApiProviderType.OTHER.name)

    @Test
    fun resolvesEveryGatewayThinkingPresetRule() {
        val chatEndpoint = "https://gateway.example/v1/chat/completions"
        val responsesEndpoint = "https://gateway.example/v1/responses"

        assertEquals("reasoning_effort", mapping("openai/gpt-5.6", chatEndpoint).parameterLabel)
        assertEquals("reasoning.effort", mapping("openai/gpt-5.6", responsesEndpoint).parameterLabel)
        assertEquals("reasoning_effort", mapping("deepseek/deepseek-v4-pro", chatEndpoint).parameterLabel)
        assertEquals("reasoning.effort", mapping("deepseek/deepseek-v4-pro", responsesEndpoint).parameterLabel)
        assertEquals("reasoning_effort", mapping("zai-org/glm-5.3", chatEndpoint).parameterLabel)
        assertEquals("reasoning_effort", mapping("zai-org/glm-5.2", chatEndpoint).parameterLabel)
        assertEquals("thinking.type", mapping("zai-org/glm-4.7", chatEndpoint).parameterLabel)
        assertEquals("reasoning_effort", mapping("moonshot/kimi-k3", chatEndpoint).parameterLabel)
        assertEquals(ThinkingQualityControl.UNSUPPORTED, mapping("moonshot/kimi-k2.7", chatEndpoint).control)
        assertEquals("thinking.type", mapping("moonshot/kimi-k2.6", chatEndpoint).parameterLabel)
        assertEquals("reasoning_effort", mapping("xai/grok-4.6", chatEndpoint).parameterLabel)
        assertEquals("reasoning_effort", mapping("xai/grok-4.5", chatEndpoint).parameterLabel)
        assertEquals("thinking.budget_tokens", mapping("anthropic/claude-sonnet-4-5", chatEndpoint).parameterLabel)
        assertEquals("output_config.effort", mapping("anthropic/claude-sonnet-4-6", chatEndpoint).parameterLabel)
        assertEquals("thinkingBudget", mapping("google/gemini-2.5-flash", chatEndpoint).parameterLabel)
        assertEquals("thinkingLevel", mapping("google/gemini-3-flash", chatEndpoint).parameterLabel)
    }

    @Test
    fun writesGatewayModelFamilyParametersIntoTheRequestBody() {
        val chatEndpoint = "https://gateway.example/v1/chat/completions"
        val gpt = request("openai/gpt-5.6", chatEndpoint, "medium")
        val gptResponses = request("openai/gpt-5.6", "https://gateway.example/v1/responses", "medium")
        val deepseek = request("deepseek/deepseek-v4-pro", chatEndpoint, "high")
        val deepseekResponses = request("deepseek/deepseek-v4-pro", "https://gateway.example/v1/responses", "high")
        val glm53 = request("zai-org/glm-5.3", chatEndpoint, "max")
        val glmCurrent = request("zai-org/glm-5.2", chatEndpoint, "high")
        val glmLegacy = request("zai-org/glm-4.7", chatEndpoint, "unused")
        val kimiK3 = request("moonshot/kimi-k3", chatEndpoint, "max")
        val kimiK26 = request("moonshot/kimi-k2.6", chatEndpoint, "unused")
        val kimiK27 = request("moonshot/kimi-k2.7", chatEndpoint, "max")
        val grok46 = request("xai/grok-4.6", chatEndpoint, "high")
        val grok45 = request("xai/grok-4.5", chatEndpoint, "medium")
        val claude45 = request("anthropic/claude-sonnet-4-5", chatEndpoint, "4096")
        val claude46 = request("anthropic/claude-sonnet-4-6", chatEndpoint, "medium")
        val gemini25 = request("google/gemini-2.5-flash", chatEndpoint, "8192")
        val gemini3 = request("google/gemini-3-flash", chatEndpoint, "MEDIUM")

        assertEquals("medium", gpt.getString("reasoning_effort"))
        assertEquals("medium", gptResponses.getJSONObject("reasoning").getString("effort"))
        assertEquals("enabled", deepseek.getJSONObject("thinking").getString("type"))
        assertEquals("high", deepseek.getString("reasoning_effort"))
        assertEquals("high", deepseekResponses.getJSONObject("reasoning").getString("effort"))
        assertEquals("enabled", glm53.getJSONObject("thinking").getString("type"))
        assertEquals("max", glm53.getString("reasoning_effort"))
        assertEquals("enabled", glmCurrent.getJSONObject("thinking").getString("type"))
        assertEquals("high", glmCurrent.getString("reasoning_effort"))
        assertEquals("enabled", glmLegacy.getJSONObject("thinking").getString("type"))
        assertEquals("max", kimiK3.getString("reasoning_effort"))
        assertEquals("enabled", kimiK26.getJSONObject("thinking").getString("type"))
        assertEquals(0, kimiK27.length())
        assertEquals("high", grok46.getString("reasoning_effort"))
        assertEquals("medium", grok45.getString("reasoning_effort"))
        assertEquals("enabled", claude45.getJSONObject("thinking").getString("type"))
        assertEquals(4096, claude45.getJSONObject("thinking").getInt("budget_tokens"))
        assertEquals("adaptive", claude46.getJSONObject("thinking").getString("type"))
        assertEquals("medium", claude46.getJSONObject("output_config").getString("effort"))
        assertTrue(gemini25.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getBoolean("includeThoughts"))
        assertEquals(8192, gemini25.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getInt("thinkingBudget"))
        assertTrue(gemini3.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getBoolean("includeThoughts"))
        assertEquals("MEDIUM", gemini3.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getString("thinkingLevel"))
    }

    @Test
    fun preservesRequiredGatewayThinkingAndDisablesOptionalGatewayThinking() {
        val chatEndpoint = "https://gateway.example/v1/chat/completions"
        val requiredGlm = request("zai-org/glm-5.3", chatEndpoint, "max", enableThinking = false)
        val optionalDeepseek = request("deepseek/deepseek-v4-pro", chatEndpoint, "high", enableThinking = false)
        val optionalGemini = request("google/gemini-2.5-flash", chatEndpoint, "8192", enableThinking = false)

        assertEquals("enabled", requiredGlm.getJSONObject("thinking").getString("type"))
        assertEquals("max", requiredGlm.getString("reasoning_effort"))
        assertEquals("disabled", optionalDeepseek.getJSONObject("thinking").getString("type"))
        assertEquals(false, optionalGemini.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getBoolean("includeThoughts"))
        assertEquals(0, optionalGemini.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getInt("thinkingBudget"))
    }

    private fun mapping(modelName: String, apiEndpoint: String): ThinkingQualityMapping =
        ThinkingQualityMappingRegistry.resolve(
            providerTypeId = ApiProviderType.OTHER.name,
            modelName = modelName,
            apiEndpoint = apiEndpoint,
            thinkingConfigurations = configurations,
        )

    private fun request(
        modelName: String,
        apiEndpoint: String,
        optionId: String,
        enableThinking: Boolean = true,
    ): JSONObject =
        JSONObject().also { request ->
            ThinkingConfigurationApplier.apply(
                requestJson = request,
                providerTypeId = ApiProviderType.OTHER.name,
                modelName = modelName,
                apiEndpoint = apiEndpoint,
                thinkingConfigurations = configurations,
                enableThinking = enableThinking,
                optionId = optionId,
            )
        }
}
