package com.ai.assistance.operit.ui.features.settings.keypool

import com.ai.assistance.operit.data.model.ApiKeyAvailabilityStatus
import com.ai.assistance.operit.data.model.ApiKeyInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiKeyPoolSelectionTest {
    @Test
    fun presetsSelectMatchingKeysOnly() {
        val keys =
            listOf(
                key("a", enabled = true, ApiKeyAvailabilityStatus.AVAILABLE),
                key("b", enabled = true, ApiKeyAvailabilityStatus.UNAVAILABLE),
                key("c", enabled = true, ApiKeyAvailabilityStatus.UNTESTED),
                key("d", enabled = false, ApiKeyAvailabilityStatus.AVAILABLE),
            )

        assertEquals(setOf("a", "b", "c", "d"), keys.idsForPreset(ApiKeyPoolSelectionPreset.ALL))
        assertEquals(setOf("a"), keys.idsForPreset(ApiKeyPoolSelectionPreset.AVAILABLE))
        assertEquals(setOf("b"), keys.idsForPreset(ApiKeyPoolSelectionPreset.UNAVAILABLE))
        assertEquals(setOf("c"), keys.idsForPreset(ApiKeyPoolSelectionPreset.UNTESTED))
        assertEquals(setOf("d"), keys.idsForPreset(ApiKeyPoolSelectionPreset.DISABLED))
    }

    @Test
    fun disabledWinsOverAvailabilityMark() {
        val key = key("x", enabled = false, ApiKeyAvailabilityStatus.UNAVAILABLE)
        assertEquals(ApiKeyPoolSelectionPreset.DISABLED, key.poolSelectionPreset())
    }

    private fun key(
        id: String,
        enabled: Boolean,
        status: ApiKeyAvailabilityStatus,
    ): ApiKeyInfo =
        ApiKeyInfo(
            id = id,
            key = "sk-$id",
            name = "API Key $id",
            isEnabled = enabled,
            availabilityStatus = status,
        )
}
