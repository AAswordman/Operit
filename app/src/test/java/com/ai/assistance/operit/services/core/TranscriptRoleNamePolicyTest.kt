package com.ai.assistance.operit.services.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptRoleNamePolicyTest {
    @Test
    fun explicitSubagentTranscriptNamesOverrideOrdinaryChatFallbacks() {
        assertEquals(
            "Rainy",
            resolveTranscriptRoleName(override = " Rainy ", fallback = "用户"),
        )
        assertEquals(
            "Explore",
            resolveTranscriptRoleName(override = "Explore", fallback = "Operit"),
        )
    }

    @Test
    fun blankOverrideKeepsOrdinaryChatRoleName() {
        assertEquals(
            "Operit",
            resolveTranscriptRoleName(override = " ", fallback = "Operit"),
        )
    }
}
