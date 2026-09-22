package com.ai.assistance.operit.data.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenStatsOrphanedConfigsTest {

    @Test
    fun `blank and default ids are not orphaned`() {
        val orphaned = TokenStatsOrphanedConfigs.orphanedConfigIds(
            recordedConfigIds = listOf("", "  ", "default", "alive"),
            activeConfigIds = listOf("default", "alive"),
        )
        assertTrue(orphaned.isEmpty())
    }

    @Test
    fun `empty active list does not treat recorded ids as orphaned`() {
        val orphaned = TokenStatsOrphanedConfigs.orphanedConfigIds(
            recordedConfigIds = listOf("ff4c4be5-3c86-4334-93a8-27b88f429caa"),
            activeConfigIds = emptyList(),
        )
        assertTrue(orphaned.isEmpty())
    }

    @Test
    fun `deleted config ids are orphaned`() {
        val orphaned = TokenStatsOrphanedConfigs.orphanedConfigIds(
            recordedConfigIds = listOf(
                "ff4c4be5-3c86-4334-93a8-27b88f429caa",
                "alive",
                "ff4c4be5-3c86-4334-93a8-27b88f429caa",
            ),
            activeConfigIds = listOf("alive", "default"),
        )
        assertEquals(
            listOf("ff4c4be5-3c86-4334-93a8-27b88f429caa"),
            orphaned,
        )
    }
}
