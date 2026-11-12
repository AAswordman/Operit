package com.ai.assistance.operit.core.agent.collaboration

import kotlinx.serialization.Serializable

@Serializable
data class CollaborationLimits(
    val maxActive: Int = 3,
    val maxDepth: Int = 1,
    val minWaitMs: Long = 10_000,
    val defaultWaitMs: Long = 30_000,
    val maxWaitMs: Long = 3_600_000,
) {
    fun validate(): CollaborationLimits = apply {
        require(maxActive in 1..64 && maxDepth in 1..64) {
            "Invalid agent capacity"
        }
        require(minWaitMs >= 0 && minWaitMs <= defaultWaitMs &&
            defaultWaitMs <= maxWaitMs && maxWaitMs <= 3_600_000) { "Invalid wait time limits" }
    }
}
