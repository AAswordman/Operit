package com.ai.nonoassistance.provider

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class OperitProviderAdapterTest {

    @Test
    fun `adapter exposes correct provider id and display name`() {
        val adapter = OperitProviderAdapter(
            id = "deepseek",
            displayName = "DeepSeek",
            underlyingServiceId = "DEEPSEEK",
            supportsToolCalling = true,
            supportsThinking = true
        )

        assertEquals("deepseek", adapter.id)
        assertEquals("DeepSeek", adapter.displayName)
        assertTrue(adapter.supportsToolCalling)
        assertTrue(adapter.supportsThinking)
    }

    @Test
    fun `adapter can be registered in ProviderRegistry`() {
        val registry = ProviderRegistry()
        val adapter = OperitProviderAdapter(
            id = "test",
            displayName = "Test Provider",
            underlyingServiceId = "TEST"
        )

        registry.register(adapter)
        assertTrue(registry.isRegistered("test"))
        assertEquals(adapter, registry.get("test"))
    }

    @Test
    fun `chatCompletion returns placeholder flow`() = runTest {
        val adapter = OperitProviderAdapter(
            id = "test",
            displayName = "Test",
            underlyingServiceId = "TEST"
        )

        val chunks = adapter.chatCompletion(
            ChatRequest(model = "test-model", messages = emptyList())
        ).toList()

        assertEquals(1, chunks.size)
        assertNotNull(chunks[0].content)
        assertEquals("stop", chunks[0].finishReason)
    }

    @Test
    fun `listModels returns empty list in skeleton`() = runTest {
        val adapter = OperitProviderAdapter(
            id = "test",
            displayName = "Test",
            underlyingServiceId = "TEST"
        )

        val models = adapter.listModels()
        assertTrue(models.isEmpty())
    }

    @Test
    fun `getPricing returns null in skeleton`() = runTest {
        val adapter = OperitProviderAdapter(
            id = "test",
            displayName = "Test",
            underlyingServiceId = "TEST"
        )

        val pricing = adapter.getPricing("test-model")
        assertNull(pricing)
    }
}
