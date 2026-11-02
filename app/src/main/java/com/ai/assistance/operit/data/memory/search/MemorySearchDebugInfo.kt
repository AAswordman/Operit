package com.ai.assistance.operit.data.memory.search

data class MemorySearchDebugInfo(
    val query: String,
    val keywords: List<String>,
    val lexicalTokens: List<String>,
    val scoreMode: MemoryScoreMode,
    val relevanceThreshold: Double,
    val effectiveKeywordWeight: Double,
    val effectiveTagWeight: Double,
    val effectiveSemanticWeight: Float,
    val semanticKeywordNormFactor: Double,
    val effectiveEdgeWeight: Double,
    val memoriesInScopeCount: Int,
    val keywordMatchesCount: Int,
    val tagMatchesCount: Int,
    val reverseContainmentMatchesCount: Int,
    val semanticMatchesCount: Int,
    val graphEdgesTraversed: Int,
    val scoredCount: Int,
    val passedThresholdCount: Int,
    val candidates: List<MemorySearchDebugCandidate>,
    val finalResultIds: List<Long>
)

data class MemorySearchDebugCandidate(
    val memoryId: Long,
    val title: String,
    val folderPath: String?,
    val matchedKeywordTokenCount: Int,
    val keywordScore: Double,
    val tagScore: Double,
    val reverseContainmentScore: Double,
    val semanticScore: Double,
    val edgeScore: Double,
    val totalScore: Double,
    val passedThreshold: Boolean
)
