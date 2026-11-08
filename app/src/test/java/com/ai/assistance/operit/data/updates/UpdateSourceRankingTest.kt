package com.ai.assistance.operit.data.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ranking decides which source the downloader tries first and which ones it refuses to
 * touch at all, so these tests pin the policy described on [UpdateSourceRanking]:
 * probe-verified sources only, fastest first, deterministic tie-breaking, and the user's
 * pick moved to the front without dropping the others.
 */
class UpdateSourceRankingTest {

    private fun candidate(
        key: String,
        speed: Long? = null,
        latency: Long? = null,
        error: String? = null
    ) = SourceCandidate(
        key = key,
        url = "https://example.invalid/$key",
        speedBytesPerSec = speed,
        latencyMs = latency,
        probeError = error
    )

    @Test
    fun `usable sources are ordered by measured throughput`() {
        val ranked = UpdateSourceRanking.rank(
            listOf(
                candidate("slow", speed = 10),
                candidate("fast", speed = 900),
                candidate("medium", speed = 100)
            )
        )

        assertEquals(listOf("fast", "medium", "slow"), ranked.usable.map { it.key })
        assertTrue(ranked.rejected.isEmpty())
        assertTrue(ranked.hasUsable)
    }

    @Test
    fun `rejected sources never become download candidates`() {
        val ranked = UpdateSourceRanking.rank(
            listOf(
                candidate("ok", speed = 5),
                candidate("forbidden", error = "HTTP 403"),
                candidate("missing", error = "HTTP 404"),
                candidate("timedOut", error = "SocketTimeoutException")
            )
        )

        assertEquals(listOf("ok"), ranked.usable.map { it.key })
        assertEquals(
            listOf("forbidden", "missing", "timedOut"),
            ranked.rejected.map { it.key }
        )
    }

    @Test
    fun `latency breaks speed ties and key keeps the order stable between runs`() {
        val ranked = UpdateSourceRanking.rank(
            listOf(
                candidate("b", speed = 100, latency = 5),
                candidate("a", speed = 100, latency = 5),
                candidate("quick", speed = 100, latency = 1)
            )
        )

        assertEquals(listOf("quick", "a", "b"), ranked.usable.map { it.key })
    }

    @Test
    fun `sources without a measured speed rank after measured ones`() {
        val ranked = UpdateSourceRanking.rank(
            listOf(
                candidate("unmeasured", latency = 3),
                candidate("measured", speed = 1, latency = 900)
            )
        )

        assertEquals(listOf("measured", "unmeasured"), ranked.usable.map { it.key })
    }

    @Test
    fun `all sources rejected leaves no candidate to try`() {
        val ranked = UpdateSourceRanking.rank(
            listOf(
                candidate("dead", error = "SocketTimeoutException"),
                candidate("html", error = "HTTP 200 (no Range support)")
            )
        )

        assertFalse(ranked.hasUsable)
        assertTrue(ranked.usable.isEmpty())
        assertEquals(2, ranked.rejected.size)
    }

    @Test
    fun `prioritized puts the user pick first and keeps every other usable source`() {
        val ranked = UpdateSourceRanking.rank(
            listOf(
                candidate("fast", speed = 900),
                candidate("picked", speed = 10),
                candidate("medium", speed = 100)
            )
        )

        val ordered = ranked.prioritized(preferredKey = "picked", failedKeys = emptySet())

        assertEquals(listOf("picked", "fast", "medium"), ordered.map { it.key })
    }

    @Test
    fun `remaining drops sources that already failed during this update`() {
        val ranked = UpdateSourceRanking.rank(
            listOf(
                candidate("a", speed = 30),
                candidate("b", speed = 20),
                candidate("c", speed = 10)
            )
        )

        assertEquals(listOf("a", "c"), ranked.remaining(setOf("b")).map { it.key })
    }

    @Test
    fun `failure lines pair every source with the reason it was dropped`() {
        val lines = UpdateSourceRanking.failureLines(
            rejected = listOf(
                candidate("h233", error = "HTTP 403"),
                candidate("xx9527", error = "UnknownHostException")
            ),
            downloadFailures = mapOf("moeyy" to "SocketTimeoutException")
        )

        assertEquals(
            listOf(
                "h233: HTTP 403",
                "xx9527: UnknownHostException",
                "moeyy: SocketTimeoutException"
            ),
            lines
        )
    }
}
