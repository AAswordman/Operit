package com.ai.assistance.operit.core.tools.builtin.web.session.browser

import com.ai.assistance.operit.ui.common.oktoast.OKToast

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.ai.assistance.operit.core.application.ActivityLifecycleManager
import com.ai.assistance.operit.core.tools.builtin.web.StandardBrowserSessionTools
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.util.network.HttpMultiPartDownloader
import java.io.File
import java.io.FileOutputStream
import java.util.LinkedHashMap
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private const val DOWNLOAD_SUPPORT_TAG = "BrowserDownloadSupport"
private const val BROWSER_DOWNLOAD_STATE_FILE = "browser_download_tasks.json"
private const val DEFAULT_BROWSER_DOWNLOAD_THREADS = 4
private const val DOWNLOAD_TYPE_HTTP = "http"

internal enum class BrowserDownloadStatus(val wireName: String) {
    QUEUED("queued"),
    CONNECTING("connecting"),
    DOWNLOADING("downloading"),
    PAUSED("paused"),
    COMPLETED("completed"),
    FAILED("failed"),
    CANCELED("canceled");

    companion object {
        fun fromWireName(value: String?): BrowserDownloadStatus =
            entries.firstOrNull { it.wireName == value } ?: FAILED
    }
}

internal enum class BrowserDownloadAction {
    PAUSE,
    RESUME,
    CANCEL,
    RETRY,
    DELETE_RECORD,
    DELETE_WITH_FILE
}

internal data class BrowserDownloadSegmentRecord(
    val index: Int,
    val startInclusive: Long,
    val endInclusive: Long,
    val tempPath: String
) {
    fun expectedLength(): Long =
        if (endInclusive >= startInclusive) {
            endInclusive - startInclusive + 1L
        } else {
            -1L
        }

    fun toJson(): JSONObject =
        JSONObject()
            .put("index", index)
            .put("start", startInclusive)
            .put("end", endInclusive)
            .put("temp_path", tempPath)

    companion object {
        fun fromJson(json: JSONObject): BrowserDownloadSegmentRecord =
            BrowserDownloadSegmentRecord(
                index = json.optInt("index"),
                startInclusive = json.optLong("start"),
                endInclusive = json.optLong("end"),
                tempPath = json.optString("temp_path")
            )
    }
}

internal data class BrowserDownloadTaskRecord(
    val id: String,
    val sessionId: String?,
    val type: String,
    val sourceUrl: String?,
    val destinationPath: String,
    val fileName: String,
    val headers: Map<String, String>,
    val createdAt: Long,
    var updatedAt: Long,
    var mimeType: String?,
    var status: BrowserDownloadStatus,
    var totalBytes: Long,
    var downloadedBytes: Long,
    var speedBytesPerSecond: Long,
    var supportsResume: Boolean,
    var threadCount: Int,
    var errorMessage: String?,
    var completedAt: Long?,
    val segments: MutableList<BrowserDownloadSegmentRecord> = mutableListOf()
) {
    fun snapshot(): BrowserDownloadTaskRecord =
        copy(
            headers = LinkedHashMap(headers),
            segments = segments.map { it.copy() }.toMutableList()
        )

    fun activeOrPending(): Boolean =
        status == BrowserDownloadStatus.QUEUED ||
            status == BrowserDownloadStatus.CONNECTING ||
            status == BrowserDownloadStatus.DOWNLOADING

    fun supportsRetry(): Boolean = status == BrowserDownloadStatus.FAILED

    fun supportsResumeAction(): Boolean =
        status == BrowserDownloadStatus.PAUSED || status == BrowserDownloadStatus.CANCELED

    fun toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("session_id", sessionId)
            .put("type", type)
            .put("source_url", sourceUrl)
            .put("destination_path", destinationPath)
            .put("file_name", fileName)
            .put("created_at", createdAt)
            .put("updated_at", updatedAt)
            .put("mime_type", mimeType)
            .put("status", status.wireName)
            .put("total_bytes", totalBytes)
            .put("downloaded_bytes", downloadedBytes)
            .put("speed_bytes_per_second", speedBytesPerSecond)
            .put("supports_resume", supportsResume)
            .put("thread_count", threadCount)
            .put("error_message", errorMessage)
            .put("completed_at", completedAt)
            .put(
                "headers",
                JSONObject().also { json ->
                    headers.forEach { (name, value) ->
                        json.put(name, value)
                    }
                }
            ).put(
                "segments",
                JSONArray().also { array ->
                    segments.forEach { segment ->
                        array.put(segment.toJson())
                    }
                }
            )

    companion object {
        fun fromJson(json: JSONObject): BrowserDownloadTaskRecord {
            val headersJson = json.optJSONObject("headers") ?: JSONObject()
            val headers = LinkedHashMap<String, String>()
            headersJson.keys().forEach { key ->
                headers[key] = headersJson.optString(key)
            }
            val segmentsJson = json.optJSONArray("segments") ?: JSONArray()
            val segments =
                MutableList(segmentsJson.length()) { index ->
                    BrowserDownloadSegmentRecord.fromJson(segmentsJson.getJSONObject(index))
                }
            return BrowserDownloadTaskRecord(
                id = json.optString("id"),
                sessionId = json.optString("session_id").ifBlank { null },
                type = json.optString("type", DOWNLOAD_TYPE_HTTP),
                sourceUrl = json.optString("source_url").ifBlank { null },
                destinationPath = json.optString("destination_path"),
                fileName = json.optString("file_name"),
                headers = headers,
                createdAt = json.optLong("created_at"),
                updatedAt = json.optLong("updated_at"),
                mimeType = json.optString("mime_type").ifBlank { null },
                status = BrowserDownloadStatus.fromWireName(json.optString("status")),
                totalBytes = json.optLong("total_bytes"),
                downloadedBytes = json.optLong("downloaded_bytes"),
                speedBytesPerSecond = json.optLong("speed_bytes_per_second"),
                supportsResume = json.optBoolean("supports_resume"),
                threadCount = json.optInt("thread_count", DEFAULT_BROWSER_DOWNLOAD_THREADS),
                errorMessage = json.optString("error_message").ifBlank { null },
                completedAt = json.optLong("completed_at").takeIf { it > 0L },
                segments = segments
            )
        }
    }
}

private data class BrowserDownloadActiveControl(
    val job: Job,
    @Volatile var stopAction: BrowserDownloadAction? = null
)

private data class BrowserDownloadInlinePayload(
    val bytes: ByteArray
)

internal class BrowserDownloadManager private constructor(
    private val appContext: Context
) {
    companion object {
        @Volatile
        private var instance: BrowserDownloadManager? = null

        fun getInstance(context: Context): BrowserDownloadManager =
            instance ?: synchronized(this) {
                instance ?: BrowserDownloadManager(context.applicationContext).also {
                    instance = it
                }
            }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tasks = LinkedHashMap<String, BrowserDownloadTaskRecord>()
    private val activeControls = ConcurrentHashMap<String, BrowserDownloadActiveControl>()
    private val inlinePayloads = ConcurrentHashMap<String, BrowserDownloadInlinePayload>()
    private val stateFile = File(appContext.filesDir, BROWSER_DOWNLOAD_STATE_FILE)

    @Volatile private var taskListener: ((BrowserDownloadTaskRecord, WebDownloadEvent) -> Unit)? = null
    @Volatile private var uiRefreshListener: (() -> Unit)? = null
    @Volatile private var lastUiDispatchAt: Long = 0L
    @Volatile private var lastPersistAt: Long = 0L
    @Volatile private var lastEventAt: Long = 0L
    @Volatile private var lastEvent: WebDownloadEvent? = null

    init {
        loadState()
        normalizeRestoredTasks()
    }

    fun setTaskListener(listener: ((BrowserDownloadTaskRecord, WebDownloadEvent) -> Unit)?) {
        taskListener = listener
    }

    fun setUiRefreshListener(listener: (() -> Unit)?) {
        uiRefreshListener = listener
    }

    fun snapshotTasks(): List<BrowserDownloadTaskRecord> =
        synchronized(tasks) {
            tasks.values
                .map { it.snapshot() }
                .sortedWith(compareByDescending<BrowserDownloadTaskRecord> { it.updatedAt }.thenByDescending { it.createdAt })
        }

    fun latestSessionUpdateAt(sessionId: String): Long =
        synchronized(tasks) {
            tasks.values
                .asSequence()
                .filter { it.sessionId == sessionId }
                .maxOfOrNull { it.updatedAt }
                ?: 0L
        }

    fun latestEventAt(): Long = lastEventAt

    fun latestEventAfter(marker: Long): WebDownloadEvent? =
        if (lastEventAt > marker) {
            lastEvent
        } else {
            null
        }

    fun startHttpDownload(
        sessionId: String?,
        url: String,
        suggestedFileName: String,
        mimeType: String?,
        headers: Map<String, String>
    ): BrowserDownloadTaskRecord {
        val destination = resolveUniqueDestinationFile(suggestedFileName)
        val now = System.currentTimeMillis()
        val task =
            BrowserDownloadTaskRecord(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                type = DOWNLOAD_TYPE_HTTP,
                sourceUrl = url,
                destinationPath = destination.absolutePath,
                fileName = destination.name,
                headers = LinkedHashMap(headers),
                createdAt = now,
                updatedAt = now,
                mimeType = mimeType,
                status = BrowserDownloadStatus.QUEUED,
                totalBytes = -1L,
                downloadedBytes = 0L,
                speedBytesPerSecond = 0L,
                supportsResume = false,
                threadCount = DEFAULT_BROWSER_DOWNLOAD_THREADS,
                errorMessage = null,
                completedAt = null
            )
        synchronized(tasks) {
            tasks[task.id] = task
        }
        persistState(force = true)
        notifyTaskChanged(task, buildTaskEvent(task, "started"), forceUi = true)
        startWorker(task.id)
        return task.snapshot()
    }

    fun startInlineDownload(
        sessionId: String?,
        type: String,
        suggestedFileName: String,
        mimeType: String?,
        bytes: ByteArray,
        sourceUrl: String? = null
    ): BrowserDownloadTaskRecord {
        val destination = resolveUniqueDestinationFile(suggestedFileName)
        val now = System.currentTimeMillis()
        val task =
            BrowserDownloadTaskRecord(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                type = type,
                sourceUrl = sourceUrl,
                destinationPath = destination.absolutePath,
                fileName = destination.name,
                headers = emptyMap(),
                createdAt = now,
                updatedAt = now,
                mimeType = mimeType,
                status = BrowserDownloadStatus.QUEUED,
                totalBytes = bytes.size.toLong(),
                downloadedBytes = 0L,
                speedBytesPerSecond = 0L,
                supportsResume = false,
                threadCount = 1,
                errorMessage = null,
                completedAt = null,
                segments =
                    mutableListOf(
                        BrowserDownloadSegmentRecord(
                            index = 0,
                            startInclusive = 0L,
                            endInclusive = bytes.size.toLong() - 1L,
                            tempPath = buildSinglePartPath(destination).absolutePath
                        )
                    )
            )
        inlinePayloads[task.id] = BrowserDownloadInlinePayload(bytes)
        synchronized(tasks) {
            tasks[task.id] = task
        }
        persistState(force = true)
        notifyTaskChanged(task, buildTaskEvent(task, "started"), forceUi = true)
        startWorker(task.id)
        return task.snapshot()
    }

    fun performAction(taskId: String, action: BrowserDownloadAction) {
        when (action) {
            BrowserDownloadAction.PAUSE -> pauseTask(taskId)
            BrowserDownloadAction.RESUME -> resumeTask(taskId)
            BrowserDownloadAction.CANCEL -> cancelTask(taskId)
            BrowserDownloadAction.RETRY -> retryTask(taskId)
            BrowserDownloadAction.DELETE_RECORD -> deleteTask(taskId, deleteFile = false)
            BrowserDownloadAction.DELETE_WITH_FILE -> deleteTask(taskId, deleteFile = true)
        }
    }

    fun renderDownloads(marker: Long = 0L, includeAll: Boolean = false): String? {
        val visibleTasks =
            snapshotTasks()
                .filter { includeAll || it.updatedAt > marker || it.activeOrPending() }
        if (visibleTasks.isEmpty()) {
            return null
        }
        return visibleTasks.joinToString("\n\n") { task ->
            buildString {
                appendLine("- File: ${task.fileName}")
                appendLine("- Status: ${task.status.wireName}")
                appendLine("- Type: ${task.type}")
                if (!task.sourceUrl.isNullOrBlank()) {
                    appendLine("- URL: ${task.sourceUrl}")
                }
                if (task.totalBytes > 0L) {
                    appendLine("- Progress: ${formatTaskProgress(task)}")
                } else if (task.downloadedBytes > 0L) {
                    appendLine("- Downloaded: ${formatBytes(task.downloadedBytes)}")
                }
                if (task.speedBytesPerSecond > 0L) {
                    appendLine("- Speed: ${formatBytes(task.speedBytesPerSecond)}/s")
                }
                appendLine("- Saved path: ${task.destinationPath}")
                appendLine("- Resume supported: ${if (task.supportsResume) "yes" else "no"}")
                if (!task.errorMessage.isNullOrBlank()) {
                    append("- Error: ${task.errorMessage}")
                }
            }.trim()
        }
    }

    private fun pauseTask(taskId: String) {
        val control = activeControls[taskId]
        if (control != null) {
            control.stopAction = BrowserDownloadAction.PAUSE
            control.job.cancel(CancellationException("pause requested"))
            return
        }
        mutateTask(taskId, eventStatus = "paused") { task ->
            if (task.activeOrPending()) {
                task.status = BrowserDownloadStatus.PAUSED
                task.speedBytesPerSecond = 0L
                task.errorMessage = null
            }
        }
    }

    private fun cancelTask(taskId: String) {
        val control = activeControls[taskId]
        if (control != null) {
            control.stopAction = BrowserDownloadAction.CANCEL
            control.job.cancel(CancellationException("cancel requested"))
            return
        }
        mutateTask(taskId, eventStatus = "canceled") { task ->
            if (task.status != BrowserDownloadStatus.COMPLETED) {
                task.status = BrowserDownloadStatus.CANCELED
                task.speedBytesPerSecond = 0L
                task.errorMessage = null
            }
        }
    }

    private fun resumeTask(taskId: String) {
        val task = synchronized(tasks) { tasks[taskId]?.snapshot() } ?: return
        if (!task.supportsResumeAction()) {
            return
        }
        if (!task.supportsResume) {
            clearTemporaryArtifacts(task)
        }
        mutateTask(taskId, eventStatus = "queued") { current ->
            current.status = BrowserDownloadStatus.QUEUED
            current.errorMessage = null
            current.speedBytesPerSecond = 0L
            current.completedAt = null
            current.downloadedBytes = if (current.supportsResume) current.downloadedBytes else 0L
            resetSegmentsForRestart(current)
        }
        startWorker(taskId)
    }

    private fun retryTask(taskId: String) {
        val task = synchronized(tasks) { tasks[taskId]?.snapshot() } ?: return
        if (!task.supportsRetry()) {
            return
        }
        if (!task.supportsResume) {
            clearTemporaryArtifacts(task)
        }
        mutateTask(taskId, eventStatus = "queued") { current ->
            current.status = BrowserDownloadStatus.QUEUED
            current.errorMessage = null
            current.speedBytesPerSecond = 0L
            current.completedAt = null
            current.downloadedBytes = if (current.supportsResume) current.downloadedBytes else 0L
            resetSegmentsForRestart(current)
        }
        startWorker(taskId)
    }

    private fun deleteTask(taskId: String, deleteFile: Boolean) {
        val control = activeControls[taskId]
        if (control != null) {
            control.stopAction =
                if (deleteFile) {
                    BrowserDownloadAction.DELETE_WITH_FILE
                } else {
                    BrowserDownloadAction.DELETE_RECORD
                }
            scope.launch {
                runCatching {
                    control.job.cancel(CancellationException("delete requested"))
                    control.job.join()
                }
                deleteTaskArtifacts(taskId, deleteFile)
            }
            return
        }
        deleteTaskArtifacts(taskId, deleteFile)
    }

    private fun startWorker(taskId: String) {
        if (activeControls.containsKey(taskId)) {
            return
        }
        val job =
            scope.launch {
                val task = synchronized(tasks) { tasks[taskId]?.snapshot() } ?: return@launch
                try {
                    when (task.type) {
                        DOWNLOAD_TYPE_HTTP -> runHttpTask(taskId)
                        else -> runInlineTask(taskId)
                    }
                } finally {
                    activeControls.remove(taskId)
                }
            }
        activeControls[taskId] = BrowserDownloadActiveControl(job = job)
    }

    private suspend fun runInlineTask(taskId: String) {
        val payload = inlinePayloads[taskId]?.bytes
        if (payload == null) {
            mutateTask(taskId, eventStatus = "failed") { task ->
                task.status = BrowserDownloadStatus.FAILED
                task.errorMessage = "Inline download payload is no longer available."
                task.speedBytesPerSecond = 0L
            }
            return
        }
        updateTaskStatus(taskId, BrowserDownloadStatus.CONNECTING)
        updateTaskStatus(taskId, BrowserDownloadStatus.DOWNLOADING)
        try {
            val task = synchronized(tasks) { tasks[taskId]?.snapshot() } ?: return
            val segment = task.segments.firstOrNull()
                ?: throw IllegalStateException("Inline download segment is missing.")
            val partFile = File(segment.tempPath)
            partFile.parentFile?.mkdirs()
            FileOutputStream(partFile, false).use { output ->
                output.write(payload)
                output.flush()
            }
            currentCoroutineContext().ensureActive()
            inlinePayloads.remove(taskId)
            mutateTask(taskId, forcePersist = false, forceUi = false) { current ->
                current.downloadedBytes = payload.size.toLong()
                current.totalBytes = payload.size.toLong()
            }
            mergeCompletedTask(taskId)
        } catch (cancelled: CancellationException) {
            handleTaskCancellation(taskId, cancelled)
        } catch (error: Throwable) {
            handleTaskError(taskId, error)
        }
    }

    private suspend fun runHttpTask(taskId: String) {
        updateTaskStatus(taskId, BrowserDownloadStatus.CONNECTING)
        val originalTask = synchronized(tasks) { tasks[taskId]?.snapshot() } ?: return
        try {
            val probe = HttpMultiPartDownloader.probeDownload(originalTask.sourceUrl.orEmpty(), originalTask.headers)
            currentCoroutineContext().ensureActive()
            val supportsResume = probe.acceptRanges && probe.contentLength > 0L
            configureTaskSegments(taskId, probe.contentLength, supportsResume)
            initializeDownloadedBytes(taskId)
            updateTaskStatus(taskId, BrowserDownloadStatus.DOWNLOADING)

            val speedLock = Any()
            var bytesAtLastSample = currentDownloadedBytes(taskId)
            var sampledAt = System.currentTimeMillis()
            val onChunk: (Int) -> Unit = { chunkBytes ->
                incrementDownloadedBytes(taskId, chunkBytes.toLong())
                val now = System.currentTimeMillis()
                synchronized(speedLock) {
                    if (now - sampledAt >= 500L) {
                        val downloadedNow = currentDownloadedBytes(taskId)
                        val delta = max(0L, downloadedNow - bytesAtLastSample)
                        val speed = (delta * 1000L) / max(1L, now - sampledAt)
                        updateTaskSpeed(taskId, speed)
                        bytesAtLastSample = downloadedNow
                        sampledAt = now
                    }
                }
            }

            val task = synchronized(tasks) { tasks[taskId]?.snapshot() } ?: return
            if (task.supportsResume && task.segments.size > 1) {
                coroutineScope {
                    task.segments.map { segment ->
                        async(Dispatchers.IO) {
                            downloadSegment(taskId, task, segment, onChunk)
                        }
                    }.awaitAll()
                }
            } else {
                downloadSegment(taskId, task, task.segments.first(), onChunk)
            }

            currentCoroutineContext().ensureActive()
            updateTaskSpeed(taskId, 0L)
            mergeCompletedTask(taskId)
        } catch (cancelled: CancellationException) {
            handleTaskCancellation(taskId, cancelled)
        } catch (error: Throwable) {
            handleTaskError(taskId, error)
        }
    }

    private suspend fun downloadSegment(
        taskId: String,
        task: BrowserDownloadTaskRecord,
        segment: BrowserDownloadSegmentRecord,
        onChunk: (Int) -> Unit
    ) {
        val currentTask = synchronized(tasks) { tasks[taskId]?.snapshot() } ?: return
        val segmentFile = File(segment.tempPath)
        val expectedLength = segment.expectedLength()
        val existingBytes =
            if (segmentFile.exists()) {
                if (expectedLength > 0L) {
                    segmentFile.length().coerceAtMost(expectedLength)
                } else {
                    segmentFile.length()
                }
            } else {
                0L
            }

        if (expectedLength > 0L && existingBytes >= expectedLength) {
            return
        }

        if (!currentTask.supportsResume && segmentFile.exists()) {
            segmentFile.delete()
        }

        val startInclusive =
            if (currentTask.supportsResume) {
                segment.startInclusive + existingBytes
            } else {
                segment.startInclusive
            }
        val append = currentTask.supportsResume && existingBytes > 0L
        val endInclusive = segment.endInclusive.takeIf { it >= 0L }
        val coroutineContext = currentCoroutineContext()

        HttpMultiPartDownloader.downloadSegment(
            url = task.sourceUrl.orEmpty(),
            dest = segmentFile,
            headers = task.headers,
            startInclusive = startInclusive,
            endInclusive = endInclusive,
            append = append,
            onChunk = onChunk,
            isCancelled = {
                activeControls[taskId]?.stopAction != null || !coroutineContext.isActive
            }
        )
    }

    private fun configureTaskSegments(taskId: String, totalBytes: Long, supportsResume: Boolean) {
        mutateTask(taskId, forcePersist = true, forceUi = true) { task ->
            task.totalBytes = totalBytes
            task.supportsResume = supportsResume
            task.errorMessage = null
            task.completedAt = null
            val destination = File(task.destinationPath)
            if (!supportsResume || totalBytes <= 0L) {
                task.segments.clear()
                task.segments +=
                    BrowserDownloadSegmentRecord(
                        index = 0,
                        startInclusive = 0L,
                        endInclusive = -1L,
                        tempPath = buildSinglePartPath(destination).absolutePath
                    )
                task.threadCount = 1
                return@mutateTask
            }

            val currentByIndex = task.segments.associateBy { it.index }
            task.segments.clear()
            HttpMultiPartDownloader.buildSegmentPlan(totalBytes, DEFAULT_BROWSER_DOWNLOAD_THREADS).forEach { plan ->
                task.segments +=
                    BrowserDownloadSegmentRecord(
                        index = plan.index,
                        startInclusive = plan.startInclusive,
                        endInclusive = plan.endInclusive,
                        tempPath =
                            currentByIndex[plan.index]?.tempPath
                                ?: buildSegmentPartPath(destination, plan.index).absolutePath
                    )
            }
            task.threadCount = task.segments.size
        }
    }

    private fun initializeDownloadedBytes(taskId: String) {
        mutateTask(taskId, forcePersist = false, forceUi = false) { task ->
            task.downloadedBytes = computeDownloadedBytes(task)
        }
    }

    private fun currentDownloadedBytes(taskId: String): Long =
        synchronized(tasks) {
            tasks[taskId]?.downloadedBytes ?: 0L
        }

    private fun incrementDownloadedBytes(taskId: String, delta: Long) {
        mutateTask(taskId, forcePersist = false, forceUi = false) { task ->
            task.downloadedBytes += delta
        }
    }

    private fun updateTaskSpeed(taskId: String, speedBytesPerSecond: Long) {
        mutateTask(taskId, forcePersist = false, forceUi = true) { task ->
            task.speedBytesPerSecond = speedBytesPerSecond
        }
    }

    private fun updateTaskStatus(taskId: String, status: BrowserDownloadStatus) {
        mutateTask(taskId, forcePersist = true, forceUi = true, eventStatus = status.wireName) { task ->
            task.status = status
            task.errorMessage = null
            task.speedBytesPerSecond = 0L
        }
    }

    private fun mergeCompletedTask(taskId: String) {
        val task = synchronized(tasks) { tasks[taskId]?.snapshot() } ?: return
        val destinationFile = File(task.destinationPath)
        destinationFile.parentFile?.mkdirs()
        if (task.segments.size == 1) {
            val partFile = File(task.segments.first().tempPath)
            if (!partFile.exists()) {
                throw IllegalStateException("Download part file is missing for ${task.fileName}")
            }
            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            if (!partFile.renameTo(destinationFile)) {
                partFile.copyTo(destinationFile, overwrite = true)
                partFile.delete()
            }
        } else {
            FileOutputStream(destinationFile, false).use { output ->
                task.segments.sortedBy { it.index }.forEach { segment ->
                    val partFile = File(segment.tempPath)
                    if (!partFile.exists()) {
                        throw IllegalStateException("Missing segment ${segment.index} for ${task.fileName}")
                    }
                    partFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                output.flush()
            }
            task.segments.forEach { segment ->
                File(segment.tempPath).delete()
            }
        }
        inlinePayloads.remove(taskId)
        MediaScannerConnection.scanFile(
            appContext,
            arrayOf(destinationFile.absolutePath),
            task.mimeType?.let { arrayOf(it) },
            null
        )
        mutateTask(taskId, eventStatus = "completed") { current ->
            current.status = BrowserDownloadStatus.COMPLETED
            current.completedAt = System.currentTimeMillis()
            current.errorMessage = null
            current.speedBytesPerSecond = 0L
            current.downloadedBytes =
                if (current.totalBytes > 0L) {
                    current.totalBytes
                } else {
                    destinationFile.length()
                }
        }
    }

    private fun failTask(taskId: String, error: Throwable) {
        AppLogger.w(DOWNLOAD_SUPPORT_TAG, "Browser download failed: ${error.message}")
        mutateTask(taskId, eventStatus = "failed") { task ->
            task.status = BrowserDownloadStatus.FAILED
            task.errorMessage = error.message ?: error::class.java.simpleName
            task.speedBytesPerSecond = 0L
        }
    }

    private suspend fun handleTaskError(taskId: String, error: Throwable) {
        if (error is CancellationException) {
            handleTaskCancellation(taskId, error)
            return
        }

        if (activeControls[taskId]?.stopAction != null && !currentCoroutineContext().isActive) {
            val cancellation = CancellationException(error.message ?: "Download cancelled")
            cancellation.initCause(error)
            handleTaskCancellation(taskId, cancellation)
            return
        }

        failTask(taskId, error)
    }

    private fun handleTaskCancellation(taskId: String, error: CancellationException) {
        when (activeControls[taskId]?.stopAction) {
            BrowserDownloadAction.PAUSE ->
                mutateTask(taskId, eventStatus = "paused") { task ->
                    task.status = BrowserDownloadStatus.PAUSED
                    task.errorMessage = null
                    task.speedBytesPerSecond = 0L
                }

            BrowserDownloadAction.CANCEL ->
                mutateTask(taskId, eventStatus = "canceled") { task ->
                    task.status = BrowserDownloadStatus.CANCELED
                    task.errorMessage = null
                    task.speedBytesPerSecond = 0L
                }

            BrowserDownloadAction.DELETE_RECORD,
            BrowserDownloadAction.DELETE_WITH_FILE -> Unit
            BrowserDownloadAction.RESUME,
            BrowserDownloadAction.RETRY -> Unit
            null -> failTask(taskId, error)
        }
    }

    private fun deleteTaskArtifacts(taskId: String, deleteFile: Boolean) {
        val task = synchronized(tasks) { tasks[taskId]?.snapshot() } ?: return
        if (deleteFile) {
            clearTaskFiles(task, includeDestinationFile = true)
        }
        synchronized(tasks) {
            tasks.remove(taskId)
        }
        inlinePayloads.remove(taskId)
        persistState(force = true)
        dispatchUiRefresh(force = true)
    }

    private fun clearTemporaryArtifacts(task: BrowserDownloadTaskRecord) {
        clearTaskFiles(task, includeDestinationFile = task.status != BrowserDownloadStatus.COMPLETED)
    }

    private fun clearTaskFiles(task: BrowserDownloadTaskRecord, includeDestinationFile: Boolean) {
        task.segments.forEach { segment ->
            runCatching { File(segment.tempPath).delete() }
        }
        if (includeDestinationFile) {
            runCatching { File(task.destinationPath).delete() }
        }
    }

    private fun computeDownloadedBytes(task: BrowserDownloadTaskRecord): Long =
        task.segments.sumOf { segment ->
            val file = File(segment.tempPath)
            if (!file.exists()) {
                0L
            } else {
                val expected = segment.expectedLength()
                if (expected > 0L) {
                    file.length().coerceAtMost(expected)
                } else {
                    file.length()
                }
            }
        }

    private fun resetSegmentsForRestart(task: BrowserDownloadTaskRecord) {
        val destination = File(task.destinationPath)
        if (task.type != DOWNLOAD_TYPE_HTTP) {
            task.segments.clear()
            task.segments +=
                BrowserDownloadSegmentRecord(
                    index = 0,
                    startInclusive = 0L,
                    endInclusive = task.totalBytes - 1L,
                    tempPath = buildSinglePartPath(destination).absolutePath
                )
            task.threadCount = 1
            return
        }
        if (!task.supportsResume) {
            task.segments.clear()
            task.segments +=
                BrowserDownloadSegmentRecord(
                    index = 0,
                    startInclusive = 0L,
                    endInclusive = -1L,
                    tempPath = buildSinglePartPath(destination).absolutePath
                )
            task.threadCount = 1
        }
    }

    private fun mutateTask(
        taskId: String,
        forcePersist: Boolean = true,
        forceUi: Boolean = true,
        eventStatus: String? = null,
        block: (BrowserDownloadTaskRecord) -> Unit
    ): BrowserDownloadTaskRecord? {
        val snapshot =
            synchronized(tasks) {
                tasks[taskId]?.let { task ->
                    block(task)
                    task.updatedAt = System.currentTimeMillis()
                    task.snapshot()
                }
            } ?: return null
        if (forcePersist) {
            persistState(force = false)
        }
        notifyTaskChanged(
            task = snapshot,
            event = eventStatus?.let { buildTaskEvent(snapshot, it) },
            forceUi = forceUi
        )
        return snapshot
    }

    private fun notifyTaskChanged(
        task: BrowserDownloadTaskRecord,
        event: WebDownloadEvent?,
        forceUi: Boolean
    ) {
        if (event != null) {
            lastEvent = event
            lastEventAt = System.currentTimeMillis()
            taskListener?.invoke(task.snapshot(), event)
        }
        dispatchUiRefresh(
            force =
                forceUi ||
                    task.status == BrowserDownloadStatus.COMPLETED ||
                    task.status == BrowserDownloadStatus.FAILED
        )
        persistState(
            force =
                forceUi ||
                    task.status == BrowserDownloadStatus.COMPLETED ||
                    task.status == BrowserDownloadStatus.FAILED
        )
    }

    private fun dispatchUiRefresh(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastUiDispatchAt < 250L) {
            return
        }
        lastUiDispatchAt = now
        uiRefreshListener?.invoke()
    }

    private fun buildTaskEvent(task: BrowserDownloadTaskRecord, status: String): WebDownloadEvent =
        WebDownloadEvent(
            status = status,
            type = task.type,
            fileName = task.fileName,
            url = task.sourceUrl,
            mimeType = task.mimeType,
            savedPath = task.destinationPath,
            error = task.errorMessage
        )

    private fun persistState(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPersistAt < 500L) {
            return
        }
        lastPersistAt = now
        val payload =
            JSONArray().also { array ->
                synchronized(tasks) {
                    tasks.values.forEach { task ->
                        array.put(task.toJson())
                    }
                }
            }
        runCatching {
            stateFile.writeText(payload.toString())
        }.onFailure {
            AppLogger.w(DOWNLOAD_SUPPORT_TAG, "Failed to persist browser downloads: ${it.message}")
        }
    }

    private fun loadState() {
        if (!stateFile.exists()) {
            return
        }
        runCatching {
            val raw = stateFile.readText()
            if (raw.isBlank()) {
                return@runCatching
            }
            val array = JSONArray(raw)
            synchronized(tasks) {
                tasks.clear()
                for (index in 0 until array.length()) {
                    val task = BrowserDownloadTaskRecord.fromJson(array.getJSONObject(index))
                    tasks[task.id] = task
                }
            }
        }.onFailure {
            AppLogger.w(DOWNLOAD_SUPPORT_TAG, "Failed to load browser downloads: ${it.message}")
        }
    }

    private fun normalizeRestoredTasks() {
        val now = System.currentTimeMillis()
        var changed = false
        synchronized(tasks) {
            tasks.values.forEach { task ->
                if (task.type != DOWNLOAD_TYPE_HTTP && task.status != BrowserDownloadStatus.COMPLETED) {
                    task.status = BrowserDownloadStatus.FAILED
                    task.errorMessage = "Inline download could not be resumed after app restart."
                    task.updatedAt = now
                    task.speedBytesPerSecond = 0L
                    changed = true
                    return@forEach
                }
                if (task.activeOrPending()) {
                    task.status = BrowserDownloadStatus.PAUSED
                    task.errorMessage = null
                    task.speedBytesPerSecond = 0L
                    task.updatedAt = now
                    task.downloadedBytes = computeDownloadedBytes(task)
                    changed = true
                }
            }
        }
        if (changed) {
            persistState(force = true)
        }
    }
}

internal data class BrowserDownloadSummary(
    val activeCount: Int = 0,
    val failedCount: Int = 0,
    val overallProgress: Float? = null,
    val latestCompletedFileName: String? = null
)

internal data class PendingExternalOpenRequest(
    val requestId: String,
    val sessionId: String,
    val intent: Intent,
    val title: String,
    val target: String,
    val createdAt: Long = System.currentTimeMillis()
)

internal fun StandardBrowserSessionTools.browserDownloadManager(): BrowserDownloadManager =
    BrowserDownloadManager.getInstance(context.applicationContext)

internal fun StandardBrowserSessionTools.initializeBrowserDownloadSupport() {
    browserDownloadManager().setTaskListener { task, event ->
        task.sessionId?.let { sessionId ->
            sessionById(sessionId)?.let { session ->
                session.lastDownloadEvent = event
                session.lastDownloadEventAt = System.currentTimeMillis()
            }
        }
    }
    browserDownloadManager().setUiRefreshListener {
        StandardBrowserSessionTools.mainHandler.post {
            refreshSessionUiOnMain()
        }
    }
}

internal fun StandardBrowserSessionTools.startBrowserManagedDownload(
    session: StandardBrowserSessionTools.WebSession,
    url: String,
    userAgent: String,
    contentDisposition: String?,
    mimeType: String?
) {
    val fileName = sanitizeFileName(android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType))
    val headers = linkedMapOf<String, String>()
    if (userAgent.isNotBlank()) {
        headers["User-Agent"] = userAgent
    }
    android.webkit.CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let {
        headers["Cookie"] = it
    }
    session.currentUrl.takeIf { it.isNotBlank() }?.let {
        headers["Referer"] = it
    }
    browserDownloadManager().startHttpDownload(
        sessionId = session.id,
        url = url,
        suggestedFileName = fileName,
        mimeType = mimeType,
        headers = headers
    )
    OKToast.info(context, context.getString(com.ai.assistance.operit.R.string.download_started, fileName)).show()
}

internal fun StandardBrowserSessionTools.startInlineManagedDownload(
    session: StandardBrowserSessionTools.WebSession,
    type: String,
    base64Data: String,
    fileName: String,
    mimeType: String,
    sourceUrl: String? = null
) {
    val normalizedMimeType = mimeType.ifBlank { guessMimeTypeFromDataUrl(base64Data) }
    val resolvedFileName = resolveInlineDownloadFileName(fileName, normalizedMimeType)
    val bytes = decodeInlineDownloadBytes(base64Data)
    browserDownloadManager().startInlineDownload(
        sessionId = session.id,
        type = type,
        suggestedFileName = resolvedFileName,
        mimeType = normalizedMimeType,
        bytes = bytes,
        sourceUrl = sourceUrl
    )
    OKToast.info(context, context.getString(com.ai.assistance.operit.R.string.download_started, resolvedFileName)).show()
}

internal fun StandardBrowserSessionTools.buildBrowserDownloadSummary(): BrowserDownloadSummary {
    val tasks = browserDownloadManager().snapshotTasks()
    val active = tasks.filter { it.activeOrPending() }
    val failed = tasks.count { it.status == BrowserDownloadStatus.FAILED }
    val latestCompleted =
        tasks.filter { it.status == BrowserDownloadStatus.COMPLETED && it.completedAt != null }
            .maxByOrNull { it.completedAt ?: 0L }
            ?.fileName
    val progress =
        if (active.isEmpty()) {
            null
        } else {
            val progressTasks = active.filter { it.totalBytes > 0L }
            if (progressTasks.isEmpty()) {
                null
            } else {
                val downloaded = progressTasks.sumOf { it.downloadedBytes.toDouble() }
                val total = progressTasks.sumOf { it.totalBytes.toDouble() }
                if (total > 0.0) {
                    (downloaded / total).toFloat()
                } else {
                    null
                }
            }
        }
    return BrowserDownloadSummary(
        activeCount = active.size,
        failedCount = failed,
        overallProgress = progress,
        latestCompletedFileName = latestCompleted
    )
}

internal fun StandardBrowserSessionTools.buildBrowserDownloadUiState(): BrowserDownloadUiState {
    val tasks =
        browserDownloadManager().snapshotTasks().map { task ->
            BrowserDownloadItem(
                id = task.id,
                fileName = task.fileName,
                status = task.status.wireName,
                type = task.type,
                progress = task.progressOrNull(),
                downloadedBytes = task.downloadedBytes,
                totalBytes = task.totalBytes,
                speedBytesPerSecond = task.speedBytesPerSecond,
                destinationPath = task.destinationPath,
                errorMessage = task.errorMessage,
                canPause = task.status == BrowserDownloadStatus.CONNECTING || task.status == BrowserDownloadStatus.DOWNLOADING,
                canResume = task.supportsResumeAction(),
                canCancel =
                    task.status == BrowserDownloadStatus.CONNECTING ||
                        task.status == BrowserDownloadStatus.DOWNLOADING ||
                        task.status == BrowserDownloadStatus.PAUSED,
                canRetry = task.supportsRetry(),
                canDelete = true,
                canDeleteFile = task.destinationPath.isNotBlank(),
                canOpenFile = task.status == BrowserDownloadStatus.COMPLETED,
                canOpenLocation = task.status == BrowserDownloadStatus.COMPLETED
            )
        }
    return BrowserDownloadUiState(tasks = tasks)
}

internal fun StandardBrowserSessionTools.renderManagedDownloads(
    session: StandardBrowserSessionTools.WebSession,
    marker: Long,
    includeAll: Boolean = false
): String? = browserDownloadManager().renderDownloads(marker, includeAll)

internal fun StandardBrowserSessionTools.latestBrowserDownloadEventAt(): Long =
    browserDownloadManager().latestEventAt()

internal fun StandardBrowserSessionTools.latestBrowserDownloadEventAfter(
    marker: Long
): WebDownloadEvent? = browserDownloadManager().latestEventAfter(marker)

internal fun StandardBrowserSessionTools.performBrowserDownloadAction(
    taskId: String,
    action: BrowserDownloadAction
) {
    browserDownloadManager().performAction(taskId, action)
}

internal fun StandardBrowserSessionTools.performBrowserDownloadDelete(
    taskId: String,
    deleteFile: Boolean
) {
    browserDownloadManager().performAction(
        taskId,
        if (deleteFile) BrowserDownloadAction.DELETE_WITH_FILE else BrowserDownloadAction.DELETE_RECORD
    )
}

internal fun StandardBrowserSessionTools.openDownloadedFile(taskId: String): Boolean {
    val task = browserDownloadManager().snapshotTasks().firstOrNull { it.id == taskId } ?: return false
    val file = File(task.destinationPath)
    if (!file.exists()) {
        return false
    }
    val uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    val mimeType =
        task.mimeType
            ?: MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.extension.lowercase(Locale.ROOT))
            ?: "application/octet-stream"
    val intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    return launchBrowserExternalIntent(intent)
}

internal fun StandardBrowserSessionTools.openDownloadLocation(taskId: String? = null): Boolean {
    if (taskId != null && browserDownloadManager().snapshotTasks().none { task -> task.id == taskId }) {
        return false
    }
    val intent =
        Intent("android.intent.action.VIEW_DOWNLOADS").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    return launchBrowserExternalIntent(intent)
}

internal fun StandardBrowserSessionTools.launchBrowserExternalIntent(intent: Intent): Boolean {
    val currentActivity = ActivityLifecycleManager.getCurrentActivity()
    return try {
        if (currentActivity != null && !currentActivity.isFinishing && !currentActivity.isDestroyed) {
            currentActivity.startActivity(Intent(intent))
        } else {
            context.applicationContext.startActivity(
                Intent(intent).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
        true
    } catch (e: ActivityNotFoundException) {
        AppLogger.w(DOWNLOAD_SUPPORT_TAG, "No activity found for browser intent: ${e.message}")
        false
    } catch (e: Exception) {
        AppLogger.w(DOWNLOAD_SUPPORT_TAG, "Failed to launch browser intent: ${e.message}")
        false
    }
}

private fun BrowserDownloadManager.resolveUniqueDestinationFile(suggestedFileName: String): File {
    val downloadsDir = publicDownloadsDirectory()
    val sanitized = suggestedFileName.trim().ifBlank { "download" }
    val dotIndex = sanitized.lastIndexOf('.')
    val base = if (dotIndex > 0) sanitized.substring(0, dotIndex) else sanitized
    val ext = if (dotIndex > 0) sanitized.substring(dotIndex) else ""
    var candidate = File(downloadsDir, sanitized)
    var index = 1
    while (
        candidate.exists() ||
            File(candidate.absolutePath + ".part").exists() ||
            File(candidate.absolutePath + ".part.0").exists()
    ) {
        candidate = File(downloadsDir, "$base ($index)$ext")
        index += 1
    }
    return candidate
}

private fun publicDownloadsDirectory(): File =
    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).apply {
        if (!exists()) {
            mkdirs()
        }
    }

private fun buildSinglePartPath(destination: File): File =
    File(destination.parentFile, "${destination.name}.part")

private fun buildSegmentPartPath(destination: File, index: Int): File =
    File(destination.parentFile, "${destination.name}.part.$index")

private fun decodeInlineDownloadBytes(rawData: String): ByteArray {
    if (rawData.startsWith("data:", ignoreCase = true)) {
        val metadata = rawData.substringBefore(',', "")
        val payload = rawData.substringAfter(',', "")
        return if (metadata.contains(";base64", ignoreCase = true)) {
            android.util.Base64.decode(payload, android.util.Base64.DEFAULT)
        } else {
            android.net.Uri.decode(payload).toByteArray(Charsets.UTF_8)
        }
    }
    return android.util.Base64.decode(rawData.substringAfter(',', rawData), android.util.Base64.DEFAULT)
}

private fun BrowserDownloadTaskRecord.progressOrNull(): Float? =
    if (totalBytes > 0L) {
        (downloadedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
    } else {
        null
    }

private fun formatTaskProgress(task: BrowserDownloadTaskRecord): String {
    val progressText =
        task.progressOrNull()?.let {
            "${(it * 100f).toInt()}%"
        } ?: "Unknown"
    val sizeText =
        if (task.totalBytes > 0L) {
            "${formatBytes(task.downloadedBytes)} / ${formatBytes(task.totalBytes)}"
        } else {
            formatBytes(task.downloadedBytes)
        }
    return "$progressText ($sizeText)"
}

internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) {
        return "$bytes B"
    }
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= 1024.0 && unitIndex + 1 < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return String.format(Locale.US, "%.1f %s", value, units[max(0, unitIndex)])
}
