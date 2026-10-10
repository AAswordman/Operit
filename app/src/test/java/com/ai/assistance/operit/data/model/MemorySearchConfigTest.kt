package com.ai.assistance.operit.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MemorySearchConfigTest {
    @Test
    fun defaultSimilarityIsConfigurableAndWithinCosineRange() {
        assertEquals(0.5f, MemorySearchConfig().minSemanticSimilarity, 0.0001f)
        assertEquals(0f, MemorySearchConfig(minSemanticSimilarity = -1f).normalized().minSemanticSimilarity, 0.0001f)
        assertEquals(1f, MemorySearchConfig(minSemanticSimilarity = 2f).normalized().minSemanticSimilarity, 0.0001f)
        assertEquals(0.5f, MemorySearchConfig(minSemanticSimilarity = Float.NaN).normalized().minSemanticSimilarity, 0.0001f)
    }
}