package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.ApiKeyAvailabilityStatus
import com.ai.assistance.operit.data.model.ApiKeyInfo
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelConfigData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatConfigReadinessTest {
    @Test
    fun deepSeekWithValidKey_isReady() {
        assertReady(remoteConfig(ApiProviderType.DEEPSEEK, apiKey = "sk-valid"))
    }

    @Test
    fun deepSeekWithChineseKey_isRejected() {
        assertIssue(
            ChatConfigReadinessIssue.API_KEY_INVALID,
            remoteConfig(ApiProviderType.DEEPSEEK, apiKey = "中文")
        )
    }

    @Test
    fun namedCloudProviderWithoutKey_isRejected() {
        assertIssue(
            ChatConfigReadinessIssue.API_KEY_MISSING,
            remoteConfig(ApiProviderType.OPENAI, apiKey = "")
        )
    }

    @Test
    fun genericProviderMayUseCustomHeaderAuthentication() {
        assertReady(remoteConfig(ApiProviderType.OPENAI_GENERIC, apiKey = ""))
    }

    @Test
    fun genericProviderWithEmptyKeyPool_isRejected() {
        assertIssue(
            ChatConfigReadinessIssue.API_KEY_MISSING,
            remoteConfig(ApiProviderType.OPENAI_GENERIC, apiKey = "").copy(
                useMultipleApiKeys = true
            )
        )
    }

    @Test
    fun localProviderDoesNotRequireEndpointOrKey() {
        assertReady(
            ModelConfigData(
                id = "mnn",
                name = "MNN",
                modelName = "local-model",
                apiProviderType = ApiProviderType.MNN,
                apiProviderTypeId = ApiProviderType.MNN.name,
                apiKey = "中文",
                useMultipleApiKeys = true
            )
        )
    }

    @Test
    fun registeredPluginOwnsItsConfigurationRequirements() {
        val config =
            ModelConfigData(
                id = "plugin",
                name = "Plugin",
                apiProviderType = ApiProviderType.OTHER,
                apiProviderTypeId = "example-provider"
            )

        val result =
            ChatConfigReadiness.evaluate(
                config = config,
                modelIndex = 0,
                registeredPluginProviderIds = setOf("example-provider")
            )

        assertTrue(result.isReady)
    }

    @Test
    fun invalidEndpoint_isRejected() {
        assertIssue(
            ChatConfigReadinessIssue.ENDPOINT_INVALID,
            remoteConfig(ApiProviderType.DEEPSEEK, endpoint = "not a url")
        )
    }

    @Test
    fun availableKeyPool_satisfiesNamedCloudProvider() {
        val config =
            remoteConfig(ApiProviderType.DEEPSEEK, apiKey = "").copy(
                useMultipleApiKeys = true,
                apiKeyPool =
                    listOf(
                        ApiKeyInfo(
                            id = "key-1",
                            key = "sk-pool",
                            availabilityStatus = ApiKeyAvailabilityStatus.AVAILABLE
                        )
                    )
            )

        assertReady(config)
    }

    private fun remoteConfig(
        providerType: ApiProviderType,
        apiKey: String = "sk-valid",
        endpoint: String = "https://api.example.com/v1/chat/completions"
    ): ModelConfigData =
        ModelConfigData(
            id = "remote",
            name = "Remote",
            apiKey = apiKey,
            apiEndpoint = endpoint,
            modelName = "model-1",
            apiProviderType = providerType,
            apiProviderTypeId = providerType.name
        )

    private fun assertReady(config: ModelConfigData) {
        assertTrue(
            ChatConfigReadiness.evaluate(
                config = config,
                modelIndex = 0,
                registeredPluginProviderIds = emptySet()
            ).isReady
        )
    }

    private fun assertIssue(expected: ChatConfigReadinessIssue, config: ModelConfigData) {
        assertEquals(
            expected,
            ChatConfigReadiness.evaluate(
                config = config,
                modelIndex = 0,
                registeredPluginProviderIds = emptySet()
            ).issue
        )
    }
}
