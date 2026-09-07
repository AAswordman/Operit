package com.ai.assistance.operit.ui.features.settings.sections

import com.ai.assistance.operit.data.model.ApiProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderRegionWarningTypeTest {
    @Test
    fun `generic and responses providers do not force an overseas warning`() {
        listOf(
            ApiProviderType.OPENAI_GENERIC,
            ApiProviderType.OPENAI_RESPONSES,
            ApiProviderType.OPENAI_RESPONSES_GENERIC,
            ApiProviderType.ANTHROPIC_GENERIC,
            ApiProviderType.GEMINI_GENERIC
        ).forEach { provider ->
            assertNull(providerRegionWarningType(provider))
        }
    }

    @Test
    fun `router providers use the international proxy warning`() {
        listOf(
            ApiProviderType.OPENROUTER,
            ApiProviderType.FOUR_ROUTER
        ).forEach { provider ->
            assertEquals(
                ProviderRegionWarningType.INTERNATIONAL_PROXY,
                providerRegionWarningType(provider)
            )
        }
    }

    @Test
    fun `known overseas official providers keep the overseas warning`() {
        listOf(
            ApiProviderType.OPENAI,
            ApiProviderType.GOOGLE,
            ApiProviderType.ANTHROPIC,
            ApiProviderType.MISTRAL,
            ApiProviderType.NVIDIA,
            ApiProviderType.NOUS_PORTAL
        ).forEach { provider ->
            assertEquals(
                ProviderRegionWarningType.OVERSEAS,
                providerRegionWarningType(provider)
            )
        }
    }
}
