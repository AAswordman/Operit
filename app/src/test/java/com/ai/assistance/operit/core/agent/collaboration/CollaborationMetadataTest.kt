package com.ai.assistance.operit.core.agent.collaboration

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class CollaborationMetadataTest {
    @Test fun persistedForkPreservesNestedValuesAndNumericTypes() {
        val original = mapOf(
            "role" to "reviewer",
            "sequence" to 4,
            "timestamp" to 1234567890123L,
            "extra" to listOf(null, true, mapOf("weight" to 0.5f)),
        )
        val saved = Json.encodeToString(CollaborationMetadata.from(original))
        assertEquals(original, Json.decodeFromString<CollaborationMetadata>(saved).value())
    }
}
