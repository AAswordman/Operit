package com.ai.assistance.operit.api.chat.llmprovider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmRetryPolicyTest {
    @Test
    fun retryableClientStatusIncludes408And429() {
        assertTrue(LlmRetryPolicy.isRetryableClientStatus(408))
        assertTrue(LlmRetryPolicy.isRetryableClientStatus(429))
    }

    @Test
    fun nonRetryableClientStatusExcludesDeterministic4xx() {
        assertFalse(LlmRetryPolicy.isRetryableClientStatus(400))
        assertFalse(LlmRetryPolicy.isRetryableClientStatus(401))
        assertFalse(LlmRetryPolicy.isRetryableClientStatus(403))
        assertFalse(LlmRetryPolicy.isRetryableClientStatus(404))
        assertFalse(LlmRetryPolicy.isRetryableClientStatus(422))
    }
}
