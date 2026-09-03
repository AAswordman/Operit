package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.collects.ModelThinkingConfigDefaults
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.OkHttpClient

class QwenThinkingConfigurationTest {
    private val provider = QwenAIProvider(
        apiEndpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
        apiKeyProvider = SingleApiKeyProvider("test-key"),
        modelName = "qwen3.6-27b",
        client = OkHttpClient(),
        thinkingConfigurations = ModelThinkingConfigDefaults.forProvider("ALIYUN"),
    )

    @Test
    fun aliyunProviderAppliesEnableThinkingToTheRequest() {
        val request = JSONObject()

        provider.applyQwenReasoningSettings(request, enableThinking = true)

        assertTrue(request.getBoolean("enable_thinking"))
    }

    @Test
    fun aliyunProviderAppliesDisableThinkingToTheRequest() {
        val request = JSONObject()

        provider.applyQwenReasoningSettings(request, enableThinking = false)

        assertFalse(request.getBoolean("enable_thinking"))
    }
}