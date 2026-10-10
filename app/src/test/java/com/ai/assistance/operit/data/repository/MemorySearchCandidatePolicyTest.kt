package com.ai.assistance.operit.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemorySearchCandidatePolicyTest {
    @Test
    fun semanticCandidatesExcludeUnrelatedAndNonFiniteScores() {
        val candidates = sequenceOf(
            "low" to 0.39f,
            "match" to 0.56f,
            "nan" to Float.NaN,
            "edge" to 0.5f,
            "negative" to -0.1f
        )

        assertEquals(
            listOf("match", "edge"),
            MemorySearchCandidatePolicy.selectSemanticCandidates(candidates, 0.5f).map { it.first }
        )
    }

    @Test
    fun topCandidatesAreRankedAndCapped() {
        val candidates = (1..200).asSequence().map { it to (it / 200f) }
        val result = MemorySearchCandidatePolicy.selectSemanticCandidates(candidates, 0f)

        assertEquals(50, result.size)
        assertEquals(200, result.first().first)
        assertEquals(151, result.last().first)
    }

    @Test
    fun globalIndexQueriesRequestOnlyBoundedCandidates() {
        assertEquals(50, MemorySearchCandidatePolicy.requestedNeighbors(500))
        assertEquals(4, MemorySearchCandidatePolicy.requestedNeighbors(4))
        assertEquals(0, MemorySearchCandidatePolicy.requestedNeighbors(0))
    }

    @Test
    fun zeroSimilarityFloorAllowsExplicitBroadSearch() {
        val result = MemorySearchCandidatePolicy.selectSemanticCandidates(
            sequenceOf("weak" to 0.1f, "negative" to -0.1f),
            0f
        )
        assertEquals(listOf("weak"), result.map { it.first })
        assertTrue(MemorySearchCandidatePolicy.selectSemanticCandidates(emptySequence<Pair<String, Float>>(), 0f).isEmpty())
    }

    @Test
    fun scopedResultsNeverIncludeLinkedMemoriesOutsideTheFolder() {
        val scores = linkedMapOf(1L to 0.2, 2L to 0.9, 3L to 0.01)
        val results = MemorySearchCandidatePolicy.scopedScores(scores, setOf(1L, 3L), 0.025)

        assertEquals(listOf(1L), results.map { it.key })
        assertTrue(MemorySearchCandidatePolicy.scopedScores(scores, emptySet(), 0.0).isEmpty())
    }
}
