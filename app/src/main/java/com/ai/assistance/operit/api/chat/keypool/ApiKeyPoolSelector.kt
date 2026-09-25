package com.ai.assistance.operit.api.chat.keypool

import com.ai.assistance.operit.data.model.ApiKeyAvailabilityStatus
import com.ai.assistance.operit.data.model.ApiKeyInfo
import kotlin.random.Random

sealed class ApiKeyPick {
    data class Selected(
        val id: String,
        val key: String,
        val name: String,
        val nextIndex: Int,
        val candidateCount: Int,
        val stickyHit: Boolean = false,
    ) : ApiKeyPick()

    data class FallbackSingle(val key: String) : ApiKeyPick()

    data class Exhausted(val reason: String) : ApiKeyPick()
}

internal object ApiKeyPoolSelector {
    fun select(
        pool: List<ApiKeyInfo>,
        fallbackSingleKey: String,
        currentIndex: Int,
        excludedIds: Set<String>,
        nowMs: Long,
        model: String,
        cooldownUntilMs: (keyId: String, model: String) -> Long,
        stickyKeyId: String? = null,
        scores: Map<String, ApiKeyScore> = emptyMap(),
        failover: Boolean = false,
        random: Random = Random.Default,
    ): ApiKeyPick {
        val enabledKeys = pool.filter { it.isEnabled && it.key.isNotBlank() }
        val hasAvailabilityMark =
            enabledKeys.any { it.availabilityStatus != ApiKeyAvailabilityStatus.UNTESTED }
        val markedKeys =
            if (hasAvailabilityMark) {
                enabledKeys.filter { it.availabilityStatus == ApiKeyAvailabilityStatus.AVAILABLE }
            } else {
                enabledKeys
            }

        val candidates =
            markedKeys.filter { key ->
                key.id !in excludedIds && cooldownUntilMs(key.id, model) <= nowMs
            }

        if (candidates.isNotEmpty()) {
            val candidateIds = candidates.map { it.id }
            val chosenId =
                if (failover) {
                    ApiKeyScoreCalculator.pickFailover(
                        keyIds = candidateIds,
                        scores = scores,
                        roundRobinIndex = currentIndex,
                    )
                } else {
                    ApiKeyScoreCalculator.pickInitial(
                        keyIds = candidateIds,
                        scores = scores,
                        stickyKeyId = stickyKeyId,
                        roundRobinIndex = currentIndex,
                        random = random,
                    ).first
                }
            val stickyHit = !failover && stickyKeyId != null && chosenId == stickyKeyId
            val selected = candidates.first { it.id == chosenId }
            val selectedIndex = candidates.indexOf(selected).coerceAtLeast(0)
            val nextIndex = (selectedIndex + 1) % candidates.size
            return ApiKeyPick.Selected(
                id = selected.id,
                key = selected.key,
                name = selected.name,
                nextIndex = nextIndex,
                candidateCount = candidates.size,
                stickyHit = stickyHit,
            )
        }

        if (hasAvailabilityMark && markedKeys.isEmpty()) {
            return ApiKeyPick.Exhausted("no_available_marks")
        }
        if (enabledKeys.isEmpty() && fallbackSingleKey.isNotBlank()) {
            return ApiKeyPick.FallbackSingle(fallbackSingleKey)
        }
        if (markedKeys.isNotEmpty() && candidates.isEmpty()) {
            return ApiKeyPick.Exhausted("all_cooling_or_excluded")
        }
        if (fallbackSingleKey.isNotBlank() && enabledKeys.isEmpty()) {
            return ApiKeyPick.FallbackSingle(fallbackSingleKey)
        }
        return ApiKeyPick.Exhausted("empty_or_disabled")
    }

    fun candidateCount(
        pool: List<ApiKeyInfo>,
        excludedIds: Set<String>,
        nowMs: Long,
        model: String,
        cooldownUntilMs: (keyId: String, model: String) -> Long,
    ): Int {
        val enabledKeys = pool.filter { it.isEnabled && it.key.isNotBlank() }
        val hasAvailabilityMark =
            enabledKeys.any { it.availabilityStatus != ApiKeyAvailabilityStatus.UNTESTED }
        val markedKeys =
            if (hasAvailabilityMark) {
                enabledKeys.filter { it.availabilityStatus == ApiKeyAvailabilityStatus.AVAILABLE }
            } else {
                enabledKeys
            }
        return markedKeys.count { key ->
            key.id !in excludedIds && cooldownUntilMs(key.id, model) <= nowMs
        }
    }
}