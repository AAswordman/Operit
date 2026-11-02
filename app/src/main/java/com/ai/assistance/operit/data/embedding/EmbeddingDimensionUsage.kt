package com.ai.assistance.operit.data.embedding

data class DimensionCount(
    val dimension: Int,
    val count: Int
)

data class EmbeddingDimensionUsage(
    val memoryTotal: Int = 0,
    val memoryMissing: Int = 0,
    val memoryDimensions: List<DimensionCount> = emptyList(),
    val chunkTotal: Int = 0,
    val chunkMissing: Int = 0,
    val chunkDimensions: List<DimensionCount> = emptyList()
)

data class EmbeddingRebuildProgress(
    val total: Int = 0,
    val processed: Int = 0,
    val failed: Int = 0,
    val currentStage: String = ""
) {
    val fraction: Float
        get() = if (total <= 0) 0f else processed.toFloat() / total.toFloat()

    val isFinished: Boolean
        get() = total > 0 && processed >= total
}
