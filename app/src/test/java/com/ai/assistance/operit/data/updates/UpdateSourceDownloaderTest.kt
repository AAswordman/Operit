package com.ai.assistance.operit.data.updates

import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Covers the failure switch that issue #1177 asks for: a source that returns 403/404, times
 * out, or serves tampered bytes must not end the update while other sources are still
 * available, and the reason for each dropped source has to survive into the report.
 */
class UpdateSourceDownloaderTest {

    private lateinit var server: MockWebServer
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        AppLogger.enableSystemLog = false
        AppLogger.enableFileLogging = false
        server = MockWebServer()
        server.start()
        tempDir = Files.createTempDirectory("operit-update-test").toFile()
    }

    @After
    fun tearDown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    private fun source(key: String, path: String) =
        SourceCandidate(key = key, url = server.url(path).toString())

    private fun outFile(name: String = "update.apk") = File(tempDir, name)

    @Test
    fun `moves to the next source when the first answers 403`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("forbidden"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("apk-bytes"))

        val out = outFile()
        val outcome = UpdateSourceDownloader.download(
            sources = listOf(source("blocked", "/blocked"), source("working", "/working")),
            out = out,
            multiThread = false,
            onProgress = {}
        )

        val success = outcome as UpdateSourceDownloader.Outcome.Success
        assertEquals("working", success.sourceKey)
        assertEquals("apk-bytes", out.readText())
    }

    @Test
    fun `moves to the next source when the first answers 404`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(200).setBody("apk-bytes"))

        val outcome = UpdateSourceDownloader.download(
            sources = listOf(source("missing", "/missing"), source("working", "/working")),
            out = outFile(),
            multiThread = false,
            onProgress = {}
        )

        assertEquals(
            "working",
            (outcome as UpdateSourceDownloader.Outcome.Success).sourceKey
        )
    }

    @Test
    fun `reports every source and its reason when all of them fail`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        server.enqueue(MockResponse().setResponseCode(404))

        val outcome = UpdateSourceDownloader.download(
            sources = listOf(source("blocked", "/blocked"), source("missing", "/missing")),
            out = outFile(),
            multiThread = false,
            onProgress = {}
        )

        val failed = outcome as UpdateSourceDownloader.Outcome.Failed
        assertEquals(listOf("blocked", "missing"), failed.failures.map { it.sourceKey })
        assertTrue(failed.failures[0].reason.contains("403"))
        assertTrue(failed.failures[1].reason.contains("404"))
    }

    @Test
    fun `rejects a payload whose checksum does not match the published digest`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("tampered-bytes"))

        val outcome = UpdateSourceDownloader.download(
            sources = listOf(source("mirror", "/tampered")),
            out = outFile(),
            expectedSha256 = "0".repeat(64),
            multiThread = false,
            onProgress = {}
        )

        val failed = outcome as UpdateSourceDownloader.Outcome.Failed
        assertTrue(failed.failures.single().reason.contains("sha256 mismatch"))
    }

    @Test
    fun `rejects a truncated payload whose size does not match the release asset`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("short"))

        val outcome = UpdateSourceDownloader.download(
            sources = listOf(source("mirror", "/truncated")),
            out = outFile(),
            expectedSize = 1024L,
            multiThread = false,
            onProgress = {}
        )

        val failed = outcome as UpdateSourceDownloader.Outcome.Failed
        assertTrue(failed.failures.single().reason.contains("size mismatch"))
    }

    @Test
    fun `accepts a payload whose checksum matches the published digest`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("genuine-apk-bytes"))

        val expected = File(tempDir, "expected.bin").apply { writeText("genuine-apk-bytes") }
        val outcome = UpdateSourceDownloader.download(
            sources = listOf(source("mirror", "/genuine")),
            out = outFile(),
            expectedSha256 = UpdateSourceDownloader.sha256Hex(expected),
            expectedSize = expected.length(),
            multiThread = false,
            onProgress = {}
        )

        val success = outcome as UpdateSourceDownloader.Outcome.Success
        assertEquals("mirror", success.sourceKey)
        assertEquals("genuine-apk-bytes", success.file.readText())
    }

    @Test
    fun `ranged download reassembles the asset from partial responses`() = runBlocking {
        val payload = ByteArray(24) { it.toByte() }
        server.dispatcher = rangedDispatcher(payload)

        val out = outFile()
        val outcome = UpdateSourceDownloader.download(
            sources = listOf(source("ranged", "/release.apk")),
            out = out,
            expectedSha256 = UpdateSourceDownloader.sha256Hex(
                File(tempDir, "payload.bin").apply { writeBytes(payload) }
            ),
            multiThread = true,
            onProgress = {}
        )

        assertTrue(outcome is UpdateSourceDownloader.Outcome.Success)
        assertEquals(payload.toList(), out.readBytes().toList())
    }

    @Test
    fun `ranged download refuses a source that does not answer 206`() = runBlocking {
        // Full body on every request, including the Range probe: exactly what a proxy that
        // strips range support does, and what broke the download in issue #1177.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setResponseCode(200).setBody("full-body")
        }

        val outcome = UpdateSourceDownloader.download(
            sources = listOf(source("noRange", "/release.apk")),
            out = outFile(),
            multiThread = true,
            onProgress = {}
        )

        val failed = outcome as UpdateSourceDownloader.Outcome.Failed
        assertTrue(failed.failures.single().reason.contains("byte ranges"))
    }


    @Test
    fun `hash mismatch rejects only that source and accepts a verified successor`() = runBlocking {
        val payload = "verified-apk"
        server.enqueue(MockResponse().setBody("tampered-apk"))
        server.enqueue(MockResponse().setBody(payload))
        val expected = File(tempDir, "expected.bin").apply { writeText(payload) }
        val failures = mutableListOf<String>()
        val outcome = UpdateSourceDownloader.download(
            sources = listOf(source("bad", "/bad"), source("good", "/good")),
            out = outFile(), expectedSha256 = UpdateSourceDownloader.sha256Hex(expected),
            expectedSize = expected.length(), multiThread = false,
            onSourceFailed = { _, _, candidate, _ -> failures += candidate.key },
            onProgress = {},
        ) as UpdateSourceDownloader.Outcome.Success
        assertEquals(listOf("bad"), failures)
        assertEquals("good", outcome.sourceKey)
        assertEquals(payload, outcome.file.readText())
    }

    @Test
    fun `mismatched Content-Range total never reaches another workers byte interval`() = runBlocking {
        assertRejectedRangeThenVerifiedSource("wrong-total")
    }

    @Test
    fun `short range bodies are rejected before accepting another source`() = runBlocking {
        assertRejectedRangeThenVerifiedSource("short")
    }

    @Test
    fun `excess range bodies are rejected before accepting another source`() = runBlocking {
        assertRejectedRangeThenVerifiedSource("excess")
    }

    private suspend fun assertRejectedRangeThenVerifiedSource(mode: String) {
        val payload = ByteArray(24) { (it + 1).toByte() }
        val failures = mutableListOf<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val match = Regex("bytes=(\\d+)-(\\d+)").matchEntire(requireNotNull(request.getHeader("Range")))!!
                val start = match.groupValues[1].toInt()
                val end = minOf(match.groupValues[2].toInt(), payload.lastIndex)
                var slice = payload.copyOfRange(start, end + 1)
                var total = payload.size
                // Keep the length probe valid, corrupt only actual chunks from the first source.
                if (request.path == "/bad" && end > start) {
                    when (mode) {
                        "wrong-total" -> total++
                        "short" -> slice = slice.copyOf(slice.size - 1)
                        "excess" -> slice += byteArrayOf(99)
                    }
                }
                return MockResponse().setResponseCode(206)
                    .setHeader("Content-Range", "bytes $start-$end/$total")
                    .setBody(Buffer().write(slice))
            }
        }
        val expected = File(tempDir, "expected.bin").apply { writeBytes(payload) }
        val result = UpdateSourceDownloader.download(
            sources = listOf(source("bad", "/bad"), source("good", "/good")), out = outFile(),
            expectedSha256 = UpdateSourceDownloader.sha256Hex(expected), multiThread = true,
            onSourceFailed = { _, _, candidate, _ -> failures += candidate.key }, onProgress = {},
        ) as UpdateSourceDownloader.Outcome.Success
        assertEquals(listOf("bad"), failures)
        assertEquals("good", result.sourceKey)
        assertEquals(payload.toList(), result.file.readBytes().toList())
    }

    @Test
    fun `cancellation stops the candidate list without recording a source failure`() = runBlocking {
        server.enqueue(MockResponse().setBody("payload"))
        val failures = mutableListOf<String>()
        val error = runCatching {
            UpdateSourceDownloader.download(
                sources = listOf(source("first", "/first"), source("second", "/second")),
                out = outFile(), multiThread = false,
                onSourceFailed = { _, _, candidate, _ -> failures += candidate.key },
                onProgress = { throw CancellationException("user cancelled") },
            )
        }.exceptionOrNull()
        assertTrue(error is CancellationException)
        assertTrue(failures.isEmpty())
        assertEquals(1, server.requestCount)
        assertTrue(!outFile().exists())
    }

    /** Serves whole bodies for plain GETs and byte slices for ranged requests. */
    private fun rangedDispatcher(payload: ByteArray) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val range = request.getHeader("Range")
                ?: return MockResponse()
                    .setResponseCode(200)
                    .setBody(Buffer().write(payload))

            val match = Regex("bytes=(\\d+)-(\\d+)").find(range)
                ?: return MockResponse().setResponseCode(416)
            val start = match.groupValues[1].toInt()
            val end = match.groupValues[2].toInt().coerceAtMost(payload.lastIndex)
            val slice = payload.copyOfRange(start, end + 1)

            return MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes $start-$end/${payload.size}")
                .setBody(Buffer().write(slice))
        }
    }
}
