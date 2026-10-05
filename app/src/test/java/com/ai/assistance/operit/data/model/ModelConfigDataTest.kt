package com.ai.assistance.operit.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelConfigDataTest {
    @Test
    fun apiKeyPoolSupportExcludesNonApiKeyProviders() {
        assertFalse(ApiProviderType.ANTIGRAVITY.supportsApiKeyPool())
        assertFalse(ApiProviderType.OPENAI_CODEX.supportsApiKeyPool())
        assertFalse(ApiProviderType.LMSTUDIO.supportsApiKeyPool())
        assertFalse(ApiProviderType.OLLAMA.supportsApiKeyPool())
        assertFalse(ApiProviderType.OPENAI_LOCAL.supportsApiKeyPool())
        assertFalse(ApiProviderType.MNN.supportsApiKeyPool())
        assertFalse(ApiProviderType.LLAMA_CPP.supportsApiKeyPool())
    }

    @Test
    fun apiKeyPoolSupportIncludesStandardApiKeyProviders() {
        assertTrue(ApiProviderType.OPENAI.supportsApiKeyPool())
        assertTrue(ApiProviderType.ANTHROPIC.supportsApiKeyPool())
        assertTrue(ApiProviderType.GOOGLE.supportsApiKeyPool())
        assertTrue(ApiProviderType.OTHER.supportsApiKeyPool())
    }
}
