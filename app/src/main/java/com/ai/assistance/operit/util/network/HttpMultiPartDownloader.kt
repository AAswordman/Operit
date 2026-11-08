package com.ai.assistance.operit.util.network

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong

object HttpMultiPartDownloader {
    data class ProbeResult(
        val contentLength: Long,
        val acceptRanges: Boolean
    )

    data class SegmentPlan(
        val index: Int,
        val startInclusive: Long,
        val endInclusive: Long
    )

    fun download(
        url: String,
        dest: File,
        headers: Map<String, String> = emptyMap(),
        threadCount: Int = 4,
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null
    ) {
        val safeThreads = threadCount.coerceIn(1, 8)

        val meta = probeDownload(url, headers)
        val total = meta.contentLength
        val supportsRanges = meta.acceptRanges

        if (total <= 0L || !supportsRanges || safeThreads == 1) {
            downloadSingle(url, dest, headers, total, onProgress)
            return
        }

        downloadMulti(url, dest, headers, total, safeThreads, onProgress)
    }

    fun probeDownload(url: String, headers: Map<String, String> = emptyMap()): ProbeResult {
        // Prefer HEAD, but some servers don't allow it.
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                applyHeaders(this, headers)
                setRequestProperty("Accept-Encoding", "identity")
                instanceFollowRedirects = true
                connectTimeout = 15000
                readTimeout = 15000
            }
            val code = conn.responseCode
            if (code in 200..399) {
                val len = conn.getHeaderFieldLong("Content-Length", -1L)
                val acceptRanges = conn.getHeaderField("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
                return ProbeResult(len, acceptRanges)
            }
        } catch (_: Exception) {
            // ignore
        } finally {
            conn?.disconnect()
        }

        // Fallback GET with Range 0-0 to detect range support.
        var conn2: HttpURLConnection? = null
        try {
            conn2 = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                applyHeaders(this, headers)
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("Range", "bytes=0-0")
                instanceFollowRedirects = true
                connectTimeout = 15000
                readTimeout = 15000
            }
            val code = conn2.responseCode
            val acceptRangesHeader = conn2.getHeaderField("Accept-Ranges")
            val acceptRanges = (code == HttpURLConnection.HTTP_PARTIAL) ||
                (acceptRangesHeader?.contains("bytes", ignoreCase = true) == true)

            val contentRange = conn2.getHeaderField("Content-Range")
            val totalFromContentRange = parseTotalFromContentRange(contentRange)
            val total = when {
                totalFromContentRange > 0L -> totalFromContentRange
                else -> conn2.getHeaderFieldLong("Content-Length", -1L)
            }
            return ProbeResult(total, acceptRanges)
        } catch (_: Exception) {
            return ProbeResult(-1L, false)
        } finally {
            conn2?.disconnect()
        }
    }

    fun buildSegmentPlan(totalBytes: Long, threadCount: Int): List<SegmentPlan> {
        if (totalBytes <= 0L) {
            return emptyList()
        }
        val safeThreads = threadCount.coerceIn(1, 8)
        val partSize = (totalBytes + safeThreads - 1) / safeThreads
        return buildList(safeThreads) {
            for (part in 0 until safeThreads) {
                val start = part * partSize
                val end = minOf(totalBytes - 1, (part + 1) * partSize - 1)
                if (start <= end) {
                    add(
                        SegmentPlan(
                            index = part,
                            startInclusive = start,
                            endInclusive = end
                        )
                    )
                }
            }
        }
    }

    fun downloadSegment(
        url: String,
        dest: File,
        headers: Map<String, String> = emptyMap(),
        startInclusive: Long = 0L,
        endInclusive: Long? = null,
        append: Boolean = false,
        onChunk: ((chunkBytes: Int) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null
    ) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                applyHeaders(this, headers)
                setRequestProperty("Accept-Encoding", "identity")
                instanceFollowRedirects = true
                connectTimeout = 15000
                readTimeout = 30000
                if (startInclusive > 0L || endInclusive != null) {
                    val rangeValue =
                        if (endInclusive != null) {
                            "bytes=$startInclusive-$endInclusive"
                        } else {
                            "bytes=$startInclusive-"
                        }
                    setRequestProperty("Range", rangeValue)
                }
            }

            val code = conn.responseCode
            val expectedPartial = startInclusive > 0L || endInclusive != null
            if (expectedPartial) {
                if (code != HttpURLConnection.HTTP_PARTIAL && code != HttpURLConnection.HTTP_OK) {
                    throw RuntimeException("HTTP $code")
                }
            } else if (code !in 200..299) {
                throw RuntimeException("HTTP $code")
            }

            dest.parentFile?.mkdirs()
            conn.inputStream.use { input ->
                FileOutputStream(dest, append).buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (isCancelled?.invoke() == true) {
                            throw InterruptedException("Download cancelled")
                        }
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        onChunk?.invoke(read)
                    }
                    output.flush()
                }
            }
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseTotalFromContentRange(contentRange: String?): Long {
        // format: bytes 0-0/12345
        if (contentRange.isNullOrBlank()) return -1L
        val slash = contentRange.lastIndexOf('/')
        if (slash <= 0 || slash >= contentRange.length - 1) return -1L
        return contentRange.substring(slash + 1).trim().toLongOrNull() ?: -1L
    }

    private fun downloadSingle(
        url: String,
        dest: File,
        headers: Map<String, String>,
        totalBytes: Long,
        onProgress: ((Long, Long) -> Unit)?
    ) {
        val total = AtomicLong(totalBytes)
        val downloaded = AtomicLong(0L)
        downloadSegment(
            url = url,
            dest = dest,
            headers = headers,
            startInclusive = 0L,
            endInclusive = null,
            append = false,
            onChunk = { chunk ->
                val now = downloaded.addAndGet(chunk.toLong())
                onProgress?.invoke(now, total.get())
            }
        )
    }

    private fun downloadMulti(
        url: String,
        dest: File,
        headers: Map<String, String>,
        totalBytes: Long,
        threadCount: Int,
        onProgress: ((Long, Long) -> Unit)?
    ) {
        dest.parentFile?.mkdirs()

        // Pre-allocate file
        RandomAccessFile(dest, "rw").use { raf ->
            raf.setLength(totalBytes)
        }

        val downloaded = AtomicLong(0L)
        val firstError = AtomicReference<Throwable?>(null)
        val pool = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        for (segment in buildSegmentPlan(totalBytes, threadCount)) {
            val start = segment.startInclusive
            val end = segment.endInclusive

            pool.execute {
                try {
                    val partFile = File(dest.parentFile, "${dest.name}.part.${segment.index}")
                    partFile.delete()
                    downloadSegment(
                        url = url,
                        dest = partFile,
                        headers = headers,
                        startInclusive = start,
                        endInclusive = end,
                        append = false,
                        onChunk = { chunk ->
                            val now = downloaded.addAndGet(chunk.toLong())
                            onProgress?.invoke(now, totalBytes)
                        }
                    )
                    RandomAccessFile(dest, "rw").use { raf ->
                        raf.seek(start)
                        partFile.inputStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                raf.write(buffer, 0, read)
                            }
                        }
                    }
                    partFile.delete()
                } catch (t: Throwable) {
                    firstError.compareAndSet(null, t)
                } finally {
                    latch.countDown()
                }
            }
        }

        try {
            latch.await()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            firstError.compareAndSet(null, e)
            throw e
        } finally {
            val err = firstError.get()
            if (err != null) {
                pool.shutdownNow()
            } else {
                pool.shutdown()
            }
        }

        val err = firstError.get()
        if (err != null) {
            try {
                dest.delete()
            } catch (_: Exception) {
            }
            throw RuntimeException("Multi-part download failed", err)
        }

        onProgress?.invoke(totalBytes, totalBytes)
    }

    private fun applyHeaders(conn: HttpURLConnection, headers: Map<String, String>) {
        if (headers.isEmpty()) return
        headers.forEach { (rawName, rawValue) ->
            val name = sanitizeHeaderName(rawName) ?: return@forEach
            val value = sanitizeHeaderValue(rawValue)
            conn.setRequestProperty(name, value)
        }
    }

    private fun sanitizeHeaderName(name: String?): String? {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (trimmed.contains("\r") || trimmed.contains("\n")) return null
        if (trimmed.contains(":")) return null
        return trimmed
    }

    private fun sanitizeHeaderValue(value: String?): String {
        return value
            ?.replace("\r", "")
            ?.replace("\n", "")
            ?: ""
    }
}
