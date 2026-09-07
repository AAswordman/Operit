package com.ai.assistance.operit.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderIdentityUtilsTest {

    @Test
    fun `known legacy provider display value uses built in provider id`() {
        assertEquals("DEEPSEEK", normalizeProviderTypeId("DEEPSEEK/old configuration"))
        assertEquals("OPENAI", normalizeProviderTypeId(" openai/legacy configuration "))
    }

    @Test
    fun `canonical and custom provider values keep their identities`() {
        assertEquals("DEEPSEEK", normalizeProviderTypeId("DEEPSEEK"))
        assertEquals("custom/provider", normalizeProviderTypeId("custom/provider"))
    }

    @Test
    fun `provider model normalization changes only the provider part`() {
        assertEquals(
            "DEEPSEEK:deepseek-chat",
            normalizeProviderModel("DEEPSEEK/old configuration:deepseek-chat"),
        )
        assertEquals("custom/provider:model", normalizeProviderModel("custom/provider:model"))
    }

    @Test
    fun `only independently billed providers keep missing cache write unknown`() {
        assertEquals(false, hasIndependentCacheWriteBilling("DEEPSEEK/legacy"))
        assertEquals(true, hasIndependentCacheWriteBilling("ANTHROPIC/legacy"))
        assertEquals(true, hasIndependentCacheWriteBilling("custom/provider"))
    }
}
