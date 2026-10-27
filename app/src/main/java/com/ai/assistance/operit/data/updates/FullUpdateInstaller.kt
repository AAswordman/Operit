package com.ai.assistance.operit.data.updates

import android.content.Context
import com.ai.assistance.operit.util.GithubReleaseUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Downloads the full release APK and verifies it before install.
 *
 * GitHub publishes a SHA-256 digest for every release asset, and this path checks the
 * downloaded file against it. Without that check a mirror answering with an HTML error
 * page, or a connection dropping mid-transfer, hands a non-APK file to the package
 * installer, which then fails with a message that does not point at the real cause.
 *
 * [sourceKeys] is the ordered candidate list produced by the download-source dialog,
 * with the user's pick first. [UpdateSourceDownloader] walks it until one source
 * delivers a verifiable file, so a single dead mirror no longer aborts the update.
 */
object FullUpdateInstaller {
    enum class Stage {
        DOWNLOADING_APK,
        READY_TO_INSTALL
    }

    sealed class ProgressEvent {
        data class StageChanged(val stage: Stage, val message: String) : ProgressEvent()

        /** The source whose download attempt is currently in flight. */
        data class SourceAttempt(val name: String, val attempt: Int, val total: Int) : ProgressEvent()

        /** A source died mid-transfer; the flow moved on to the next candidate. */
        data class SourceFailed(
            val name: String,
            val reason: String,
            val attempt: Int,
            val total: Int
        ) : ProgressEvent()

        data class DownloadProgress(
            val readBytes: Long,
            val totalBytes: Long?,
            val speedBytesPerSec: Long
        ) : ProgressEvent()
    }

    /** Every candidate source failed; the per-source reasons are surfaced to the user. */
    class AllSourcesFailedException(
        val failures: List<UpdateSourceDownloader.SourceFailure>
    ) : IllegalStateException(failures.joinToString("; ") { "${it.sourceKey}: ${it.reason}" })

    private const val WORK_DIR_NAME = "full_update"

    suspend fun downloadAndPrepareUpdate(
        context: Context,
        apkUrl: String,
        sourceKeys: List<String>,
        downloadMessage: String,
        readyMessage: String,
        expectedSha256: String? = null,
        expectedSize: Long? = null,
        onEvent: (ProgressEvent) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val workDir = prepareCleanWorkDir(context)
        val apkFile = File(workDir, "update.apk")

        val sources = resolveSources(apkUrl = apkUrl, sourceKeys = sourceKeys)

        onEvent(ProgressEvent.StageChanged(Stage.DOWNLOADING_APK, downloadMessage))
        val outcome = UpdateSourceDownloader.download(
            sources = sources,
            out = apkFile,
            expectedSha256 = expectedSha256,
            expectedSize = expectedSize,
            multiThread = true,
            onAttempt = { attempt, total, source ->
                onEvent(
                    ProgressEvent.SourceAttempt(name = source.key, attempt = attempt, total = total)
                )
            },
            onSourceFailed = { attempt, total, source, reason ->
                onEvent(
                    ProgressEvent.SourceFailed(
                        name = source.key,
                        reason = reason,
                        attempt = attempt,
                        total = total
                    )
                )
            },
            onProgress = { progress ->
                onEvent(
                    ProgressEvent.DownloadProgress(
                        readBytes = progress.readBytes,
                        totalBytes = progress.totalBytes,
                        speedBytesPerSec = progress.speedBytesPerSec
                    )
                )
            }
        )

        when (outcome) {
            is UpdateSourceDownloader.Outcome.Success -> {
                onEvent(ProgressEvent.StageChanged(Stage.READY_TO_INSTALL, readyMessage))
                outcome.file
            }

            is UpdateSourceDownloader.Outcome.Failed ->
                throw AllSourcesFailedException(outcome.failures)
        }
    }

    /**
     * Map the ordered source keys onto concrete URLs.
     *
     * Keys come from [GithubReleaseUtil.getDownloadSources] for this same [apkUrl], so
     * every key resolves; `getValue` keeps a key that does not resolve a loud failure
     * instead of quietly shortening the candidate list.
     */
    private fun resolveSources(apkUrl: String, sourceKeys: List<String>): List<SourceCandidate> {
        val byKey = GithubReleaseUtil.getDownloadSources(apkUrl)
        return sourceKeys.map { key -> SourceCandidate(key = key, url = byKey.getValue(key)) }
    }

    private fun prepareCleanWorkDir(context: Context): File {
        val workDir = File(context.cacheDir, WORK_DIR_NAME)
        if (workDir.exists()) {
            runCatching { workDir.deleteRecursively() }
        }
        workDir.mkdirs()
        return workDir
    }
}
