package com.ai.assistance.operit.data.repository

internal object MemorySearchCandidatePolicy {
    const val MAX_SEMANTIC_CANDIDATES_PER_KEYWORD = 50

    fun requestedNeighbors(indexSize: Int): Int =
        minOf(indexSize, MAX_SEMANTIC_CANDIDATES_PER_KEYWORD)

    fun <T> selectSemanticCandidates(
        candidates: Sequence<Pair<T, Float>>,
        minimumSimilarity: Float
    ): List<Pair<T, Float>> = candidates
        .filter { (_, similarity) -> similarity.isFinite() && similarity >= minimumSimilarity }
        .sortedByDescending { (_, similarity) -> similarity }
        .take(MAX_SEMANTIC_CANDIDATES_PER_KEYWORD)
        .toList()

    fun scopedScores(
        scores: Map<Long, Double>,
        allowedIds: Set<Long>,
        minimumScore: Double
    ): List<Map.Entry<Long, Double>> = scores.entries.filter {
        it.key in allowedIds && it.value >= minimumScore
    }
}
