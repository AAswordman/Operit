package com.ai.assistance.operit.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the capture rate limit that stops a long main-thread stall from
 * starting a new full thread dump on every 100 ms watchdog sample.
 */
class AnrCaptureThrottleTest {

    @Test
    fun firstCaptureIsAlwaysAllowed() {
        val throttle = AnrCaptureThrottle(cooldownMs = 5_000L)

        assertTrue(throttle.tryAcquire(nowMs = 1_000L))
    }

    @Test
    fun samplesInsideTheCooldownAreRejected() {
        val throttle = AnrCaptureThrottle(cooldownMs = 5_000L)
        assertTrue(throttle.tryAcquire(nowMs = 1_000L))

        // A stalled main thread is resampled every 100 ms for the whole stall.
        val attempts = (1..40).map { offset -> 1_000L + offset * 100L }
        val allowed = attempts.count { throttle.tryAcquire(it) }

        assertEquals(0, allowed)
    }

    @Test
    fun captureIsAllowedAgainAfterTheCooldownElapses() {
        val throttle = AnrCaptureThrottle(cooldownMs = 5_000L)
        assertTrue(throttle.tryAcquire(nowMs = 1_000L))

        assertFalse(throttle.tryAcquire(nowMs = 5_999L))
        assertTrue(throttle.tryAcquire(nowMs = 6_000L))
    }

    @Test
    fun cooldownIsMeasuredFromTheLastAllowedCapture() {
        val throttle = AnrCaptureThrottle(cooldownMs = 5_000L)
        assertTrue(throttle.tryAcquire(nowMs = 0L))
        assertTrue(throttle.tryAcquire(nowMs = 5_000L))

        // The rejected attempts in between must not push the window forward.
        assertFalse(throttle.tryAcquire(nowMs = 9_999L))
        assertTrue(throttle.tryAcquire(nowMs = 10_000L))
    }

    @Test
    fun aSixtySecondStallProducesABoundedNumberOfCaptures() {
        val cooldownMs = AnrCaptureBudget.CAPTURE_COOLDOWN_MS
        val throttle = AnrCaptureThrottle(cooldownMs)

        val captures = (0..600).count { step -> throttle.tryAcquire(step * 100L) }

        // 60 s of stall at a 100 ms sampling interval, capped by the cooldown window.
        assertEquals((60_000L / cooldownMs).toInt() + 1, captures)
    }
}
