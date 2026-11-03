package com.ai.assistance.operit.data.memory.profile

import kotlinx.serialization.Serializable

/**
 * Metadata for an isolated long-term memory database.
 *
 * The identifier intentionally remains stable across the user-profile migration because it is
 * also the ObjectBox database name and may be referenced by character cards.
 */
@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.MemorySpace")
@Serializable
data class MemorySpace(
    val id: String,
    val name: String,
    val profileAutoUpdateEnabled: Boolean = true,
    val profileAutoUpdateLocked: Boolean = false
)
