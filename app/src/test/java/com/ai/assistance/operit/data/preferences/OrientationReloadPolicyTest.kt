package com.ai.assistance.operit.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class OrientationReloadPolicyTest {
    @Test
    fun defaultsToAskWhenNotConfiguredOrInvalid() {
        assertEquals(OrientationReloadPolicy.ASK, OrientationReloadPolicy.fromValue(null))
        assertEquals(OrientationReloadPolicy.ASK, OrientationReloadPolicy.fromValue("unknown"))
    }

    @Test
    fun restoresEachSavedMode() {
        OrientationReloadPolicy.entries.forEach { policy ->
            assertEquals(policy, OrientationReloadPolicy.fromValue(policy.name))
        }
    }
}
