package com.ai.assistance.operit.core.chat.context

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatUtilsProviderModelTest {

    @Test fun providerModel_acceptsGoogleWithoutSuffix() {
        assertTrue(ChatUtils.isGeminiProviderModel("google"))
    }

    @Test fun providerModel_acceptsGeminiGenericWithoutSuffix() {
        assertTrue(ChatUtils.isGeminiProviderModel("gemini_generic"))
    }

    @Test fun providerModel_rejectsEmptyString() {
        assertFalse(ChatUtils.isGeminiProviderModel(""))
    }

    @Test fun providerModel_rejectsAnthropic() {
        assertFalse(ChatUtils.isGeminiProviderModel("anthropic:claude"))
    }
}
