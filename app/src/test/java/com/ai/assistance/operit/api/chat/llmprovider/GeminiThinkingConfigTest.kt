package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.collects.ModelThinkingConfigDefaults
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class GeminiThinkingConfigTest {
    private val thinkingConfigurations =
        ModelThinkingConfigDefaults.forProvider(ApiProviderType.GOOGLE.name)

    @Test
    fun mapsModelOptionToGeminiLevel() {
        val config = GeminiThinkingConfig.fromOption("gemini-3-pro", "HIGH", thinkingConfigurations)
        assertEquals("HIGH", config.thinkingLevel)
    }

    @Test
    fun requestsThoughtSummariesForAnEnabledOption() {
        val config = GeminiThinkingConfig.fromOption("gemini-3-pro", "HIGH", thinkingConfigurations)
        assertTrue(config.includeThoughts)
    }

    @Test
    fun `required Gemini reasoning hides thought text when caller disables thinking`() {
        val requestJson = JSONObject().put("generationConfig", JSONObject())

        applyGeminiThinkingConfiguration(
            requestJson = requestJson,
            providerTypeId = ApiProviderType.GOOGLE.name,
            modelName = "gemini-3-pro",
            apiEndpoint = "https://generativelanguage.googleapis.com",
            thinkingConfigurations = thinkingConfigurations,
            enableThinking = false,
            optionId = "HIGH",
        )

        val thinkingConfig = requestJson
            .getJSONObject("generationConfig")
            .getJSONObject("thinkingConfig")
        assertFalse(thinkingConfig.getBoolean("includeThoughts"))
        assertEquals("HIGH", thinkingConfig.getString("thinkingLevel"))
    }

    @Test
    fun `Gemini function call parts use the thoughtSignature wire field`() {
        val part =
            buildGeminiFunctionCallPart(
                functionCall = JSONObject().put("name", "package_proxy"),
                thoughtSignature = "signature",
            )

        assertEquals("signature", part.getString("thoughtSignature"))
        assertFalse(part.has("thought_signature"))
    }

    @Test
    fun `package proxy history returns concrete tool result without a cancellation`() {
        val provider =
            GeminiProvider(
                apiEndpoint = "https://example.test",
                apiKeyProvider = SingleApiKeyProvider("test-key"),
                modelName = "gemini-test",
                client = OkHttpClient(),
                enableToolCall = true,
            )
        val contents = buildGeminiContents(
            provider,
            listOf(
                PromptTurn(
                    kind = PromptTurnKind.TOOL_CALL,
                    content =
                        "<tool name=\"package_proxy\">" +
                            "<param name=\"tool_name\">extended_http_tools:http_request</param>" +
                            "<param name=\"params\">{&quot;url&quot;:&quot;https://example.test&quot;}</param>" +
                            "</tool>"
                ),
                PromptTurn(
                    kind = PromptTurnKind.TOOL_RESULT,
                    content =
                        "<tool_result name=\"extended_http_tools:http_request\" status=\"success\">" +
                            "<content>done</content></tool_result>"
                )
            )
        )

        val functionCall =
            contents
                .getJSONObject(0)
                .getJSONArray("parts")
                .getJSONObject(0)
                .getJSONObject("functionCall")
        val functionResponse =
            contents
                .getJSONObject(1)
                .getJSONArray("parts")
                .getJSONObject(0)
                .getJSONObject("functionResponse")

        assertEquals("package_proxy", functionCall.getString("name"))
        assertEquals("package_proxy", functionResponse.getString("name"))
        assertEquals("done", functionResponse.getJSONObject("response").getString("result"))
    }

    private fun buildGeminiContents(
        provider: GeminiProvider,
        history: List<PromptTurn>,
    ): org.json.JSONArray {
        val method =
            GeminiProvider::class.java.declaredMethods.single {
                it.name == "buildContentsAndCountTokens" && it.parameterCount == 3
            }
        method.isAccessible = true
        val result = method.invoke(provider, history, null, false) as Pair<*, *>
        val contentsAndSystem = result.first as Pair<*, *>
        return contentsAndSystem.first as org.json.JSONArray
    }
}
