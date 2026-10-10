package com.ai.assistance.operit.data.model

enum class MemoryScoreMode {
    BALANCED,
    KEYWORD_FIRST,
    SEMANTIC_FIRST
}

data class MemorySearchConfig(
    val scoreMode: MemoryScoreMode = MemoryScoreMode.BALANCED,
    val keywordWeight: Float = 10.0f,
    val tagWeight: Float = 0.0f,
    val vectorWeight: Float = 0.0f,
    val edgeWeight: Float = 0.4f,
    val minSemanticSimilarity: Float = DEFAULT_MIN_SEMANTIC_SIMILARITY
) {
    companion object {
        const val DEFAULT_MIN_SEMANTIC_SIMILARITY = 0.5f
    }

    fun normalized(): MemorySearchConfig {
        return copy(
            keywordWeight = keywordWeight.coerceAtLeast(0.0f),
            tagWeight = tagWeight.coerceAtLeast(0.0f),
            vectorWeight = vectorWeight.coerceAtLeast(0.0f),
            edgeWeight = edgeWeight.coerceAtLeast(0.0f),
            minSemanticSimilarity = minSemanticSimilarity.takeIf { it.isFinite() }
                ?.coerceIn(0.0f, 1.0f) ?: DEFAULT_MIN_SEMANTIC_SIMILARITY
        )
    }
}
