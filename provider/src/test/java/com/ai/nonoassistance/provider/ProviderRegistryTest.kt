package com.ai.nonoassistance.provider

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ProviderRegistryTest {

    private lateinit var registry: ProviderRegistry

    @Before
    fun setup() {
        registry = ProviderRegistry()
    }

    @Test
    fun `register and get provider by id`() {
        val provider = createDummyProvider("deepseek", "DeepSeek")
        registry.register(provider)

        val retrieved = registry.get("deepseek")
        assertNotNull(retrieved)
        assertEquals("deepseek", retrieved?.id)
        assertEquals("DeepSeek", retrieved?.displayName)
    }

    @Test
    fun `unregister provider`() {
        val provider = createDummyProvider("test-provider", "Test")
        registry.register(provider)
        assertTrue(registry.isRegistered("test-provider"))

        registry.unregister("test-provider")
        assertFalse(registry.isRegistered("test-provider"))
        assertNull(registry.get("test-provider"))
    }

    @Test
    fun `get all providers returns registered list`() {
        registry.register(createDummyProvider("provider-a", "A"))
        registry.register(createDummyProvider("provider-b", "B"))

        val all = registry.getAll()
        assertEquals(2, all.size)
        assertTrue(all.any { it.id == "provider-a" })
        assertTrue(all.any { it.id == "provider-b" })
    }

    @Test
    fun `register overwrites existing provider with same id`() {
        registry.register(createDummyProvider("dup", "Original"))
        registry.register(createDummyProvider("dup", "Updated"))

        val provider = registry.get("dup")
        assertEquals("Updated", provider?.displayName)
        assertEquals(1, registry.getAll().size)
    }

    @Test
    fun `get returns null for unregistered provider`() {
        assertNull(registry.get("nonexistent"))
    }

    private fun createDummyProvider(id: String, displayName: String) = object : AIProvider {
        override val id = id
        override val displayName = displayName
        override val supportsToolCalling = true
        override val supportsThinking = false
        override suspend fun listModels() = emptyList<ModelInfo>()
        override suspend fun getPricing(modelId: String) = null
        override suspend fun chatCompletion(request: ChatRequest) = flowOf<ChatChunk>()
    }
}
