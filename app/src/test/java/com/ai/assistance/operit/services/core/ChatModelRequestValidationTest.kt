package com.ai.assistance.operit.services.core

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.keypool.ApiKeyAvailabilityStatus
import com.ai.assistance.operit.api.chat.keypool.ApiKeyInfo
import com.ai.assistance.operit.data.config.model.ApiProviderType
import com.ai.assistance.operit.data.config.model.ModelConfigData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ChatModelRequestValidationTest {
    private val context = mock<Context>().apply {
        whenever(getString(R.string.chat_model_configuration_invalid_key)).thenReturn("missing key")
        whenever(getString(R.string.chat_model_configuration_missing_model)).thenReturn("missing model")
    }
    private val config = ModelConfigData(
        id = "default", name = "默认配置", modelName = "deepseek-v4-flash",
        apiEndpoint = "https://api.deepseek.com/v1/chat/completions",
    )

    @Test
    fun freshConfigurationWithEmptyKey_isRejectedBeforeRequest() {
        assertEquals("missing key", chatModelConfigurationError(context, config))
    }

    @Test
    fun blankModelList_isRejectedEvenWithKey() {
        assertEquals("missing model", chatModelConfigurationError(context, config.copy(apiKey = "sk-test", modelName = " , , ")))
    }

    @Test
    fun remoteModelId_isNotRestrictedToBundledCatalog() {
        assertNull(chatModelConfigurationError(context, config.copy(apiKey = "sk-test", modelName = "custom-model-id")))
    }

    @Test
    fun usableKeyPool_isAcceptedWithoutSingleKey() {
        val key = ApiKeyInfo("key", "sk-test", availabilityStatus = ApiKeyAvailabilityStatus.AVAILABLE)
        assertNull(chatModelConfigurationError(context, config.copy(useMultipleApiKeys = true, apiKeyPool = listOf(key))))
        assertEquals("missing key", chatModelConfigurationError(context, config.copy(useMultipleApiKeys = true, apiKeyPool = listOf(key.copy(isEnabled = false)))))
    }

    @Test
    fun localEndpoint_doesNotRequireKey() {
        assertNull(chatModelConfigurationError(context, config.copy(apiEndpoint = "http://127.0.0.1:1234/v1/chat/completions")))
    }

    @Test
    fun accountAuthentication_doesNotRequireApiKey() {
        assertNull(chatModelConfigurationError(context, config.copy(apiProviderType = ApiProviderType.OPENAI_CODEX, apiProviderTypeId = "OPENAI_CODEX")))
    }

    @Test
    fun customAuthenticationHeader_isRespected() {
        assertNull(chatModelConfigurationError(context, config.copy(customHeaders = """{"authorization":"Bearer test-token"}""")))
        assertNull(chatModelConfigurationError(context, config.copy(customHeaders = """{"x-api-key":"test-token"}""")))
        assertEquals("missing key", chatModelConfigurationError(context, config.copy(customHeaders = """{"authorization":" "}""")))
    }

    @Test
    fun unrelatedHeaders_doNotCountAsCredentials() {
        assertEquals("missing key", chatModelConfigurationError(context, config.copy(customHeaders = """{"Content-Type":"application/json"}""")))
    }

    @Test
    fun pluginProvider_keepsItsOwnConfigurationContract() {
        assertNull(chatModelConfigurationError(context, config.copy(apiProviderTypeId = "plugin-provider", modelName = "")))
    }
}