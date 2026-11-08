package com.ai.assistance.operit.data.embedding

import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.config.model.ModelConfigData
import com.ai.assistance.operit.data.preferences.FunctionConfigMapping
import com.ai.assistance.operit.data.preferences.remapDeletedConfigReferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class MemoryEmbeddingModelTest {
    @Test
    fun chatEndpointAndVersionBaseResolveToEmbeddingEndpoint() {
        val endpoints = mapOf(
            "https://example.com" to "https://example.com/v1/embeddings",
            "https://example.com/v1/" to "https://example.com/v1/embeddings",
            "https://example.com/v1/chat/completions" to "https://example.com/v1/embeddings",
            "https://example.com/api/v1/responses?tenant=memory" to "https://example.com/api/v1/embeddings?tenant=memory",
            "https://example.com/v1/embeddings" to "https://example.com/v1/embeddings",
            "https://example.com/api%20path/v1/responses?tenant=a%26b&key=a%2Bb" to
                "https://example.com/api%20path/v1/embeddings?tenant=a%26b&key=a%2Bb",
        )
        endpoints.forEach { (source, expected) -> assertEquals(expected, memoryEmbeddingEndpoint(source)) }
    }

    @Test
    fun explicitlyPinnedEndpointIsNotRewritten() {
        assertEquals("https://example.com/custom/vector", memoryEmbeddingEndpoint("https://example.com/custom/vector#"))
    }

    @Test
    fun invalidEndpointIsRejected() {
        listOf("file:///vector", "file:///vector#", "https:///v1", "https://example.com/v1#fragment").forEach { endpoint ->
            assertThrows(IllegalArgumentException::class.java) { memoryEmbeddingEndpoint(endpoint) }
        }
    }

    @Test
    fun selectedModelUsesBoundIndexAndRetainsConnectionSettings() {
        val config = ModelConfigData(id = "models", name = "向量服务", modelName = "small, , large",
            apiEndpoint = "https://example.com/v1", apiKey = "test-key",
            customHeaders = "{\"X-Tenant\":\"memory\"}")
        assertEquals(config.copy(modelName = "large"), selectMemoryEmbeddingModel(config, 1))
        assertThrows(IllegalArgumentException::class.java) { selectMemoryEmbeddingModel(config, 2) }
    }

    @Test
    fun unassignedOrIncompleteModelDoesNotEnableVectors() {
        assertFalse((null as ModelConfigData?).isEmbeddingReady())
        assertFalse(ModelConfigData(id = "empty", name = "未完成配置").isEmbeddingReady())
    }

    @Test
    fun deletingEmbeddingModelClearsItsBindingWithoutChangingChatBinding() {
        val chat = FunctionConfigMapping("chat-model", 2)
        val repair = remapDeletedConfigReferences(mapOf(
            FunctionType.CHAT to chat,
            FunctionType.MEMORY_EMBEDDING to FunctionConfigMapping("vector-model", 1),
        ), "vector-model")
        assertEquals(chat, repair.mapping[FunctionType.CHAT])
        assertEquals(FunctionConfigMapping("", 0), repair.mapping[FunctionType.MEMORY_EMBEDDING])
        assertEquals(listOf(FunctionType.MEMORY_EMBEDDING), repair.affectedFunctions)
    }
}