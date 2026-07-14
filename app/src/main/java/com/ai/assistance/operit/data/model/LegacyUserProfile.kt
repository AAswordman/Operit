package com.ai.assistance.operit.data.model

import kotlinx.serialization.Serializable

/** Released structured profile payload, retained only so schema-v2 migration can preserve data. */
@Serializable
data class LegacyUserProfile(
    val id: String,
    val name: String,
    val birthDate: Long = 0L,
    val gender: String = "",
    val personality: String = "",
    val identity: String = "",
    val occupation: String = "",
    val aiStyle: String = "",
    val isInitialized: Boolean = false
)
