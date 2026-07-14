package com.ai.assistance.operit.data.model

import kotlinx.serialization.Serializable

/**
 * Metadata for an isolated long-term memory database.
 *
 * The identifier intentionally remains stable across the user-profile migration because it is
 * also the ObjectBox database name and may be referenced by character cards.
 */
@Serializable
data class MemorySpace(
    val id: String,
    val name: String
)
