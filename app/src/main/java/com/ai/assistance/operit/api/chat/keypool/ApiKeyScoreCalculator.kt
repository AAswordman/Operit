package com.ai.assistance.operit.api.chat.keypool

import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

internal data class ApiKeyScore(
    val keyId: String,
    val sampleCount: Int,
    val score: Double,
    val cold: Boolean,
)

internal object ApiKeyScoreCalculator {
    const val W_SUCCESS = 0.6
    const val W_LATENCY = 0.25
    const val W_THROUGHPUT = 0.15
    const val COLD_MAX_SAMPLES = 4
    const val WARM_MAX_SAMPLES = 14
    const val EPSILON_COLD = 0.10
    const val EPSILON_WARM = 0.08
    const val EPSILON_FULL = 0.04
    private const val Z = 1.96

    fun scores(stats: List<ApiKeyWindowStats>): List<ApiKeyScore> {
        val ttftMedian = median(stats.mapNotNull { it.meanTtftMs })
        val tpsMedian = median(stats.mapNotNull { it.meanTps })
        return stats.map { item -> scoreOne(item, ttftMedian, tpsMedian) }
    }

    fun pickInitial(
        keyIds: List<String>,
        scores: Map<String, ApiKeyScore>,
        stickyKeyId: String?,
        roundRobinIndex: Int,
        random: Random = Random.Default,
    ): Pair<String, Boolean> {
        if (keyIds.isEmpty()) {
            throw IllegalArgumentException("Cannot pick from an empty key list")
        }
        if (stickyKeyId != null && stickyKeyId in keyIds) {
            return stickyKeyId to true
        }
        val allCold = keyIds.all { scores[it]?.cold != false }
        val chosen =
            if (allCold) {
                roundRobin(keyIds, roundRobinIndex)
            } else {
                pickWeighted(keyIds, scores, random)
            }
        return chosen to false
    }

    fun pickFailover(
        keyIds: List<String>,
        scores: Map<String, ApiKeyScore>,
        roundRobinIndex: Int,
    ): String {
        if (keyIds.isEmpty()) {
            throw IllegalArgumentException("Cannot pick from an empty key list")
        }
        val ranked =
            keyIds.sortedWith(
                compareByDescending<String> { scores[it]?.cold == false }
                    .thenByDescending { scores[it]?.score ?: 0.0 }
            )
        val allCold = keyIds.all { scores[it]?.cold != false }
        return if (allCold) roundRobin(keyIds, roundRobinIndex) else ranked.first()
    }

    internal fun wilsonLowerBound(successes: Int, n: Int, z: Double = Z): Double {
        if (n <= 0) return 0.0
        val p = successes.toDouble() / n.toDouble()
        val z2 = z * z
        val denom = 1.0 + z2 / n
        val center = p + z2 / (2.0 * n)
        val margin = z * sqrt((p * (1.0 - p) + z2 / (4.0 * n)) / n)
        return ((center - margin) / denom).coerceIn(0.0, 1.0)
    }

    internal fun relativeLatency(ttftMs: Double, medianTtftMs: Double): Double {
        val denom = max(medianTtftMs * 2.0, ttftMs)
        if (denom <= 0.0) return 1.0
        return (1.0 - ttftMs / denom).coerceIn(0.0, 1.0)
    }

    internal fun relativeThroughput(tps: Double, medianTps: Double): Double {
        val denom = max(medianTps, tps)
        if (denom <= 0.0) return 0.0
        return (tps / denom).coerceIn(0.0, 1.0)
    }

    private fun scoreOne(
        stats: ApiKeyWindowStats,
        ttftMedian: Double?,
        tpsMedian: Double?,
    ): ApiKeyScore {
        val n = stats.sampleCount
        if (n <= COLD_MAX_SAMPLES) {
            return ApiKeyScore(
                keyId = stats.keyId,
                sampleCount = n,
                score = EPSILON_COLD,
                cold = true,
            )
        }
        val successRate = wilsonLowerBound(stats.successCount, n)
        val hasTtft = stats.meanTtftMs != null && ttftMedian != null
        val hasTps = stats.meanTps != null && tpsMedian != null
        val useFull = n > WARM_MAX_SAMPLES
        var weight = W_SUCCESS
        var total = W_SUCCESS * successRate
        if (useFull && hasTtft) {
            weight += W_LATENCY
            total += W_LATENCY * relativeLatency(stats.meanTtftMs!!, ttftMedian!!)
        }
        if (useFull && hasTps) {
            weight += W_THROUGHPUT
            total += W_THROUGHPUT * relativeThroughput(stats.meanTps!!, tpsMedian!!)
        }
        val raw = if (weight <= 0.0) 0.0 else total / weight
        val epsilon = if (useFull) EPSILON_FULL else EPSILON_WARM
        return ApiKeyScore(
            keyId = stats.keyId,
            sampleCount = n,
            score = max(raw, epsilon),
            cold = false,
        )
    }

    private fun pickWeighted(
        keyIds: List<String>,
        scores: Map<String, ApiKeyScore>,
        random: Random,
    ): String {
        val weights = keyIds.map { id -> max(scores[id]?.score ?: EPSILON_COLD, 0.0) }
        val sum = weights.sum()
        if (sum <= 0.0) return roundRobin(keyIds, 0)
        var remaining = random.nextDouble() * sum
        for (index in keyIds.indices) {
            remaining -= weights[index]
            if (remaining <= 0.0) return keyIds[index]
        }
        return keyIds.last()
    }

    private fun roundRobin(keyIds: List<String>, index: Int): String {
        val normalized = index % keyIds.size
        val start = if (normalized < 0) normalized + keyIds.size else normalized
        return keyIds[start]
    }

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        } else {
            sorted[mid]
        }
    }
}