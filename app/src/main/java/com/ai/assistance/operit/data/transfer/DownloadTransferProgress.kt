package com.ai.assistance.operit.data.transfer

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.ceil

/** 只记录实际传输数据；服务器没有给出总量时，百分比和剩余时间保持未知。 */
data class DownloadTransferProgress(
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val bytesPerSecond: Double?,
    val elapsedMillis: Long,
) {
    val fraction: Float?
        get() = totalBytes?.takeIf { it > 0L }?.let {
            (downloadedBytes.toDouble() / it).toFloat().coerceIn(0f, 1f)
        }

    val remainingSeconds: Long?
        get() {
            val total = totalBytes?.takeIf { it > 0L } ?: return null
            if (downloadedBytes >= total) return 0L
            val speed = bytesPerSecond?.takeIf { it > 0.0 && it.isFinite() } ?: return null
            return ceil((total - downloadedBytes) / speed).toLong()
        }
}

/** 使用单调时钟，限制界面更新频率，并按最近的采样区间计算传输速度。 */
internal class DownloadTransferTracker(
    totalBytes: Long?,
    private val clockNanos: () -> Long = System::nanoTime,
) {
    private val totalBytes = totalBytes?.takeIf { it > 0L }
    private val startedAt = clockNanos()
    private var lastReportedAt = startedAt
    private var windowStartedAt = startedAt
    private var windowStartedBytes = 0L
    private var speed: Double? = null

    fun sample(downloadedBytes: Long, force: Boolean = false): DownloadTransferProgress? {
        val now = clockNanos()
        val windowNanos = (now - windowStartedAt).coerceAtLeast(0L)
        if (windowNanos >= SPEED_WINDOW_NANOS || force && speed == null && downloadedBytes > 0L && windowNanos > 0L) {
            speed = (downloadedBytes - windowStartedBytes).coerceAtLeast(0L) * 1_000_000_000.0 / windowNanos
            windowStartedAt = now
            windowStartedBytes = downloadedBytes
        }
        if (!force && now - lastReportedAt < REPORT_INTERVAL_NANOS) return null
        lastReportedAt = now
        return DownloadTransferProgress(
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            bytesPerSecond = speed,
            elapsedMillis = (now - startedAt).coerceAtLeast(0L) / 1_000_000L,
        )
    }

    private companion object {
        const val REPORT_INTERVAL_NANOS = 200_000_000L
        const val SPEED_WINDOW_NANOS = 500_000_000L
    }
}

/** 初始与结束状态必定上报，中间按采样频率上报，避免每个缓冲区都触发重组。 */
internal fun InputStream.copyWithTransferProgress(
    output: OutputStream,
    totalBytes: Long?,
    onProgress: (DownloadTransferProgress) -> Unit,
    bufferSize: Int = DEFAULT_BUFFER_SIZE,
    clockNanos: () -> Long = System::nanoTime,
): Long {
    require(bufferSize > 0)
    val tracker = DownloadTransferTracker(totalBytes, clockNanos)
    tracker.sample(0L, force = true)?.let(onProgress)
    val buffer = ByteArray(bufferSize)
    var downloadedBytes = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        if (read == 0) continue
        output.write(buffer, 0, read)
        downloadedBytes += read
        tracker.sample(downloadedBytes)?.let(onProgress)
    }
    if (totalBytes != null && totalBytes > 0L && downloadedBytes != totalBytes) {
        throw EOFException("Download size mismatch: expected $totalBytes bytes, received $downloadedBytes bytes")
    }
    tracker.sample(downloadedBytes, force = true)?.let(onProgress)
    return downloadedBytes
}
