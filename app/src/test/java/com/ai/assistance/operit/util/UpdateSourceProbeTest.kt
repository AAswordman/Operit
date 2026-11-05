package com.ai.assistance.operit.util

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The speed probe decides which sources the updater is allowed to download from, so it has
 * to apply the same conditions as the ranged downloader. Issue #1177 is caused by exactly
 * this gap: a mirror answered `200` with its own HTML page, the probe counted it as usable
 * and even fast, and the download then died on the first range request.
 */
class UpdateSourceProbeTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        // Keep AppLogger off Android APIs while running on a plain JVM.
        AppLogger.enableSystemLog = false
        AppLogger.enableFileLogging = false
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun probe(timeoutMs: Int = 2_000): GithubReleaseUtil.ProbeResult = runBlocking {
        val url = server.url("/asset.zip").toString()
        GithubReleaseUtil.probeMirrorUrls(mapOf("source" to url), timeoutMs = timeoutMs)
            .getValue("source")
    }

    @Test
    fun `partial content is a usable source`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", "application/octet-stream")
                .setBody("payload-bytes")
        )

        val result = probe()

        assertTrue(result.ok)
        assertEquals(206, result.httpStatus)
        assertNull(result.error)
        assertTrue((result.sampledBytes ?: 0L) > 0L)
    }

    @Test
    fun `forbidden source is rejected with its status`() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("<html>forbidden</html>"))

        val result = probe()

        assertFalse(result.ok)
        assertEquals("HTTP 403", result.error)
    }

    @Test
    fun `missing asset is rejected with its status`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))

        val result = probe()

        assertFalse(result.ok)
        assertEquals("HTTP 404", result.error)
    }

    @Test
    fun `server that ignores Range is rejected instead of being ranked as fast`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody("<!DOCTYPE html><html><body>proxy error</body></html>")
        )

        val result = probe()

        assertFalse(result.ok)
        assertEquals("HTTP 200 (no Range support)", result.error)
    }

    @Test
    fun `partial content carrying an html error page is rejected`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody("<!DOCTYPE html>")
        )

        val result = probe()

        assertFalse(result.ok)
        assertTrue(result.error.orEmpty().contains("text/html"))
    }

    @Test
    fun `unresponsive source is reported as a timeout`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val result = probe(timeoutMs = 300)

        assertFalse(result.ok)
        assertEquals("SocketTimeoutException", result.error)
    }
}
