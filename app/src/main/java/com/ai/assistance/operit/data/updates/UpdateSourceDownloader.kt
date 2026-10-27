package com.ai.assistance.operit.data.updates

import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/**
 * Fetches one update artifact, walking the ranked source list until a source delivers
 * a file that passes verification.
 *
 * A probe only samples the first bytes of a file, so any single source can still die
 * mid-transfer with 403/404 or a timeout. Committing to one probe winner therefore
 * turns a flaky mirror into a hard update failure, which is what issue #1177 reports.
 * Downloading through an ordered candidate list and recording why each rejected source
 * was rejected gives the user a completed update plus an accurate reason when nothing
 * works.
 */
object UpdateSourceDownloader {
    private const val TAG = "UpdateSourceDownloader"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** One source rejected during this artifact download, with the reason. */
    data class SourceFailure(val sourceKey: String, val reason: String)

    sealed class Outcome {
        data class Success(val file: File, val sourceKey: String) : Outcome()
        data class Failed(val failures: List<SourceFailure>) : Outcome()
    }

    data class Progress(
        val sourceKey: String,
        val readBytes: Long,
        val totalBytes: Long?,
        val speedBytesPerSec: Long
    )

    /**
     * Download [sources] in order into [out], verifying the result.
     *
     * @param sources candidate sources, already ordered by [UpdateSourceRanking].
     * @param expectedSha256 published checksum of the artifact; when present the file
     *   must match it before it is accepted.
     * @param expectedSize published artifact size; used when no checksum exists.
     * @param multiThread fetch the file in ranges, requires sources that answer 206.
     * @param onAttempt reports which source is about to be tried, so the UI can show
     *   that the update is still progressing through its candidate list.
     * @param onSourceFailed reports why a source died mid-transfer, before moving on.
     */
    suspend fun download(
        sources: List<SourceCandidate>,
        out: File,
        expectedSha256: String? = null,
        expectedSize: Long? = null,
        multiThread: Boolean = true,
        onAttempt: (attempt: Int, total: Int, source: SourceCandidate) -> Unit = { _, _, _ -> },
        onSourceFailed: (attempt: Int, total: Int, source: SourceCandidate, reason: String) -> Unit = { _, _, _, _ -> },
        onProgress: (Progress) -> Unit
    ): Outcome {
        val failures = ArrayList<SourceFailure>(sources.size)

        for ((index, source) in sources.withIndex()) {
            coroutineContext.ensureActive()

            onAttempt(index + 1, sources.size, source)

            val failure = runCatching {
                transfer(
                    source = source,
                    out = out,
                    multiThread = multiThread,
                    onProgress = onProgress
                )
                verifyArtifact(
                    file = out,
                    sourceKey = source.key,
                    expectedSha256 = expectedSha256,
                    expectedSize = expectedSize
                )
            }.exceptionOrNull()?.let { e ->
                // Cancellation is the user aborting the update, not a broken source;
                // reporting it as one would mask the real state of the job.
                if (e is CancellationException) throw e
                AppLogger.w(TAG, "source ${source.key} failed for ${out.name}", e)
                describeFailure(e)
            }

            if (failure == null) {
                return Outcome.Success(file = out, sourceKey = source.key)
            }

            failures.add(SourceFailure(sourceKey = source.key, reason = failure))
            onSourceFailed(index + 1, sources.size, source, failure)
        }

        return Outcome.Failed(failures = failures)
    }

    private fun describeFailure(e: Throwable): String {
        val message = e.message?.trim().orEmpty()
        return if (message.isEmpty()) e.javaClass.simpleName else message
    }

    private fun verifyArtifact(
        file: File,
        sourceKey: String,
        expectedSha256: String?,
        expectedSize: Long?
    ) {
        if (expectedSize != null && expectedSize > 0L) {
            val actualSize = file.length()
            if (actualSize != expectedSize) {
                throw IllegalStateException(
                    "size mismatch from $sourceKey: expected $expectedSize, got $actualSize"
                )
            }
        }

        if (expectedSha256 != null) {
            val actualSha256 = sha256Hex(file)
            if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                throw IllegalStateException(
                    "sha256 mismatch from $sourceKey: expected $expectedSha256, got $actualSha256"
                )
            }
        }
    }

    fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { ins ->
            val buf = ByteArray(1024 * 1024)
            while (true) {
                val r = ins.read(buf)
                if (r <= 0) break
                md.update(buf, 0, r)
            }
        }
        return md.digest().joinToString("") { b -> "%02x".format(b) }
    }

    private suspend fun transfer(
        source: SourceCandidate,
        out: File,
        multiThread: Boolean,
        onProgress: (Progress) -> Unit
    ) {
        out.parentFile?.mkdirs()
        if (out.exists()) {
            runCatching { out.delete() }
        }

        if (multiThread) {
            transferRanged(source = source, out = out, onProgress = onProgress)
        } else {
            transferSequential(source = source, out = out, onProgress = onProgress)
        }
    }

    private suspend fun transferSequential(
        source: SourceCandidate,
        out: File,
        onProgress: (Progress) -> Unit
    ) {
        val req = Request.Builder().url(source.url).build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code}: ${resp.message}")
            }
            val body = resp.body ?: throw IllegalStateException("Empty response body")
            val total = body.contentLength().takeIf { it > 0 }

            val buf = ByteArray(128 * 1024)
            var readTotal = 0L
            var lastNotifyAt = 0L
            var lastSampleAt = 0L
            var lastSampleBytes = 0L
            var speedBytesPerSec = 0L

            onProgress(Progress(source.key, 0L, total, 0L))

            FileOutputStream(out).use { fos ->
                body.byteStream().use { ins ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = ins.read(buf)
                        if (n <= 0) break
                        fos.write(buf, 0, n)
                        readTotal += n.toLong()

                        val now = System.currentTimeMillis()
                        if (lastSampleAt == 0L) {
                            lastSampleAt = now
                            lastSampleBytes = readTotal
                        } else {
                            val elapsed = now - lastSampleAt
                            if (elapsed >= 300) {
                                val delta = readTotal - lastSampleBytes
                                if (delta >= 0) {
                                    speedBytesPerSec = (delta * 1000L) / elapsed.coerceAtLeast(1L)
                                }
                                lastSampleAt = now
                                lastSampleBytes = readTotal
                            }
                        }

                        if (now - lastNotifyAt >= 300) {
                            lastNotifyAt = now
                            onProgress(Progress(source.key, readTotal, total, speedBytesPerSec))
                        }
                    }
                }
            }

            onProgress(Progress(source.key, readTotal, total ?: readTotal, speedBytesPerSec))
        }
    }

    private suspend fun transferRanged(
        source: SourceCandidate,
        out: File,
        onProgress: (Progress) -> Unit
    ) {
        val totalBytes = fetchContentLength(source.url)
        verifyRangeSupported(source.url)

        RandomAccessFile(out, "rw").use { raf ->
            raf.setLength(totalBytes)
        }

        val downloadedBytes = AtomicLong(0L)
        val finished = AtomicBoolean(false)

        onProgress(Progress(source.key, 0L, totalBytes, 0L))

        coroutineScope {
            val reporter = launch(Dispatchers.IO) {
                var lastBytes = 0L
                var lastAt = System.currentTimeMillis()
                while (isActive && !finished.get()) {
                    delay(300)
                    val now = System.currentTimeMillis()
                    val currentBytes = downloadedBytes.get()
                    val delta = (currentBytes - lastBytes).coerceAtLeast(0L)
                    val elapsed = (now - lastAt).coerceAtLeast(1L)
                    onProgress(
                        Progress(source.key, currentBytes, totalBytes, (delta * 1000L) / elapsed)
                    )
                    lastBytes = currentBytes
                    lastAt = now
                }
            }

            try {
                splitRanges(totalBytes, RANGED_THREAD_COUNT)
                    .map { (start, end) ->
                        async(Dispatchers.IO) {
                            downloadRange(
                                url = source.url,
                                out = out,
                                start = start,
                                end = end,
                                downloadedBytes = downloadedBytes
                            )
                        }
                    }
                    .awaitAll()
            } finally {
                finished.set(true)
                reporter.cancel()
            }
        }

        onProgress(Progress(source.key, totalBytes, totalBytes, 0L))
    }

    private const val RANGED_THREAD_COUNT = 6

    private fun fetchContentLength(url: String): Long {
        val req = Request.Builder().url(url).build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code}: ${resp.message}")
            }
            val body = resp.body ?: throw IllegalStateException("Empty response body")
            val totalBytes = body.contentLength()
            if (totalBytes <= 0L) {
                throw IllegalStateException("Server did not provide a valid Content-Length")
            }
            return totalBytes
        }
    }

    private fun verifyRangeSupported(url: String) {
        val req = Request.Builder()
            .url(url)
            .addHeader("Range", "bytes=0-0")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (resp.code != 206) {
                throw IllegalStateException("Server does not support HTTP Range (got ${resp.code})")
            }
        }
    }

    internal fun splitRanges(totalBytes: Long, threadCount: Int): List<Pair<Long, Long>> {
        val ranges = ArrayList<Pair<Long, Long>>(threadCount)
        val baseSize = totalBytes / threadCount
        val remainder = totalBytes % threadCount
        var start = 0L

        for (i in 0 until threadCount) {
            val chunkSize = baseSize + if (i < remainder) 1L else 0L
            if (chunkSize <= 0L) continue
            val end = start + chunkSize - 1L
            ranges.add(start to end)
            start = end + 1L
        }
        return ranges
    }

    private suspend fun downloadRange(
        url: String,
        out: File,
        start: Long,
        end: Long,
        downloadedBytes: AtomicLong
    ) {
        val req = Request.Builder()
            .url(url)
            .addHeader("Range", "bytes=$start-$end")
            .build()

        httpClient.newCall(req).execute().use { resp ->
            if (resp.code != 206) {
                throw IllegalStateException(
                    "HTTP ${resp.code}: Range request failed for bytes=$start-$end"
                )
            }
            val body = resp.body ?: throw IllegalStateException("Empty body for bytes=$start-$end")

            RandomAccessFile(out, "rw").use { raf ->
                raf.seek(start)
                val buffer = ByteArray(128 * 1024)
                body.byteStream().use { ins ->
                    while (true) {
                        // Blocking socket reads do not observe cancellation on their own,
                        // so the loop checks it to keep aborting an update responsive.
                        coroutineContext.ensureActive()
                        val n = ins.read(buffer)
                        if (n <= 0) break
                        raf.write(buffer, 0, n)
                        downloadedBytes.addAndGet(n.toLong())
                    }
                }
            }
        }
    }
}
