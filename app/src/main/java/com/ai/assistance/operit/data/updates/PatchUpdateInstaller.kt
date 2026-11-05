package com.ai.assistance.operit.data.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.GitHubApiService
import com.ai.assistance.operit.util.GithubReleaseUtil
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.coroutines.coroutineContext
import kotlin.math.min

object PatchUpdateInstaller {
    private const val TAG = "PatchUpdateInstaller"
    private const val MAX_CHAIN_STEPS = 64

    enum class Stage {
        DOWNLOADING_META,
        DOWNLOADING_PATCH,
        APPLYING_PATCH,
        VERIFYING_APK,
        READY_TO_INSTALL
    }

    /**
     * Probe result for one source in the download-source dialog. Kept here because the
     * dialog and the patch progress reducer both render it; the patch flow itself no
     * longer probes, it receives an already-ranked source list.
     */
    data class MirrorProbeSummary(
        val ok: Boolean,
        val latencyMs: Long?,
        val speedBytesPerSec: Long?,
        val error: String?
    )

    sealed class ProgressEvent {
        data class StageChanged(val stage: Stage, val message: String) : ProgressEvent()

        /** The source the patch flow is currently downloading from. */
        data class MirrorSelected(val name: String, val attempt: Int, val total: Int) : ProgressEvent()

        /**
         * A source died while transferring, so the flow moved to the next one. Carrying
         * the reason is what lets the user see 403/404/timeout per source instead of a
         * single opaque failure at the end.
         */
        data class SourceFailed(
            val name: String,
            val reason: String,
            val attempt: Int,
            val total: Int
        ) : ProgressEvent()

        data class ChainScanProgress(
            val scanned: Int,
            val total: Int
        ) : ProgressEvent()
        data class DownloadProgress(
            val label: String,
            val readBytes: Long,
            val totalBytes: Long?,
            val speedBytesPerSec: Long
        ) : ProgressEvent()
    }

    private val releaseAssetUrlRegex =
        Regex("^https?://github\\.com/([^/]+)/([^/]+)/releases/download/[^/]+/[^?]+(?:\\?.*)?$", RegexOption.IGNORE_CASE)

    private const val WORK_DIR_NAME = "patch_update"

    /**
     * One edge of the patch chain.
     *
     * [metaAssetSha256] and [patchAssetSha256] are the digests GitHub publishes for the
     * release assets. Verifying the downloaded meta against its published digest is what
     * makes the `patchSha256` / `targetSha256` values *inside* that meta trustworthy —
     * without it a mirror could hand over a rewritten meta and the chain would happily
     * verify against the attacker's own hashes.
     */
    private data class PatchStep(
        val patchUrl: String,
        val metaUrl: String,
        val baseSha256: String,
        val targetSha256: String,
        val toVersion: String,
        val toPatchIndex: Int,
        val targetVersionLabel: String,
        val metaAssetSha256: String? = null,
        val patchAssetSha256: String? = null
    )

    /** Every candidate source failed; the per-source reasons are surfaced to the user. */
    class AllSourcesFailedException(
        val failures: List<UpdateSourceDownloader.SourceFailure>
    ) : IllegalStateException(failures.joinToString("; ") { "${it.sourceKey}: ${it.reason}" })

    fun installApk(context: Context, apkFile: File) {
        val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
        } else {
            Uri.fromFile(apkFile)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        context.startActivity(intent)
    }

    /**
     * Download and apply a patch chain.
     *
     * [sourceKeys] is the ordered candidate list produced by the download-source dialog,
     * with the user's pick first. Every artifact (meta and each patch zip) is fetched
     * through that list independently, so a mirror that serves the small meta but dies on
     * the large patch still lets the update finish from another source.
     *
     * [metaSha256] and [patchSha256] are the digests GitHub publishes for the *target*
     * meta and patch assets. Verifying the downloaded target meta against its published
     * digest is what makes the `patchSha256` / `targetSha256` values inside it trustworthy.
     */
    suspend fun downloadAndPreparePatchUpdate(
        context: Context,
        patchUrl: String,
        metaUrl: String,
        sourceKeys: List<String>,
        metaSha256: String? = null,
        patchSha256: String? = null,
        onEvent: (ProgressEvent) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val workDir = prepareCleanWorkDir(context)
        val targetMetaFile = File(workDir, "target_patch_meta.json")
        val currentApk = File(workDir, "current.apk")
        val nextApk = File(workDir, "next.apk")
        val outApk = File(workDir, "rebuilt.apk")
        val baseApk = File(context.applicationInfo.sourceDir)
        baseApk.copyTo(currentApk, overwrite = true)

        val metaSources = resolveSources(metaUrl, sourceKeys)

        onEvent(
            ProgressEvent.StageChanged(
                Stage.DOWNLOADING_META,
                context.getString(R.string.patch_update_stage_downloading_meta)
            )
        )
        downloadWithFailover(
            sources = metaSources,
            out = targetMetaFile,
            label = context.getString(R.string.patch_update_label_meta),
            expectedSha256 = metaSha256,
            multiThread = false,
            onEvent = onEvent
        )
        val targetMeta = JSONObject(targetMetaFile.readText())

        val format = targetMeta.optString("format", "")
        if (format != "apkraw-1") {
            throw IllegalStateException("Unsupported patch format: $format")
        }

        val baseShaActual = sha256Hex(currentApk)
        onEvent(
            ProgressEvent.StageChanged(
                Stage.VERIFYING_APK,
                context.getString(R.string.patch_update_stage_resolving_chain)
            )
        )
        val chain = resolvePatchChain(
            context = context,
            patchUrl = patchUrl,
            metaUrl = metaUrl,
            currentBaseSha = baseShaActual,
            targetMeta = targetMeta,
            targetMetaSha256 = metaSha256,
            targetPatchSha256 = patchSha256,
            onEvent = onEvent
        )

        if (chain.isEmpty()) {
            throw IllegalStateException("No applicable patch steps")
        }

        for ((index, step) in chain.withIndex()) {
            coroutineContext.ensureActive()

            val stepNo = index + 1
            val total = chain.size
            val stepMetaFile = File(workDir, "patch_meta_$stepNo.json")
            val stepPatchFile = File(workDir, "patch_$stepNo.zip")

            onEvent(
                ProgressEvent.StageChanged(
                    Stage.DOWNLOADING_META,
                    context.getString(
                        R.string.patch_update_stage_downloading_meta_step,
                        stepNo,
                        total
                    )
                )
            )
            downloadWithFailover(
                sources = resolveSources(step.metaUrl, sourceKeys),
                out = stepMetaFile,
                label = context.getString(R.string.patch_update_label_meta_step, stepNo, total),
                expectedSha256 = step.metaAssetSha256,
                multiThread = false,
                onEvent = onEvent
            )
            val stepMeta = JSONObject(stepMetaFile.readText())

            val stepFormat = stepMeta.optString("format", "")
            if (stepFormat != "apkraw-1") {
                throw IllegalStateException("Unsupported patch format: $stepFormat")
            }

            val stepBaseExpected = stepMeta.optString("baseSha256", "")
            if (stepBaseExpected.isNotBlank()) {
                val stepBaseActual = sha256Hex(currentApk)
                if (!stepBaseActual.equals(stepBaseExpected, ignoreCase = true)) {
                    throw IllegalStateException("Base sha256 mismatch")
                }
            }

            onEvent(
                ProgressEvent.StageChanged(
                    Stage.DOWNLOADING_PATCH,
                    context.getString(
                        R.string.patch_update_stage_downloading_patch_step,
                        stepNo,
                        total
                    )
                )
            )
            // The published asset digest is checked during transfer; the `patchSha256`
            // inside the meta is checked again below because the chain resolution reads
            // meta bodies straight from the release API rather than from a download.
            downloadWithFailover(
                sources = resolveSources(step.patchUrl, sourceKeys),
                out = stepPatchFile,
                label = context.getString(R.string.patch_update_label_patch_step, stepNo, total),
                expectedSha256 = step.patchAssetSha256,
                multiThread = true,
                onEvent = onEvent
            )

            val patchShaExpected = stepMeta.optString("patchSha256", "")
            if (patchShaExpected.isNotBlank()) {
                onEvent(
                    ProgressEvent.StageChanged(
                        Stage.VERIFYING_APK,
                        context.getString(
                            R.string.patch_update_stage_verifying_patch_step,
                            stepNo,
                            total
                        )
                    )
                )
                val patchShaActual = sha256Hex(stepPatchFile)
                if (!patchShaActual.equals(patchShaExpected, ignoreCase = true)) {
                    throw IllegalStateException("Patch sha256 mismatch")
                }
            }

            onEvent(
                ProgressEvent.StageChanged(
                    Stage.APPLYING_PATCH,
                    context.getString(
                        R.string.patch_update_stage_applying_patch_step,
                        stepNo,
                        total
                    )
                )
            )
            applyApkrawPatch(currentApk, stepPatchFile, stepMeta, nextApk)

            val targetShaExpected = stepMeta.optString("targetSha256", "")
            if (targetShaExpected.isNotBlank()) {
                onEvent(
                    ProgressEvent.StageChanged(
                        Stage.VERIFYING_APK,
                        context.getString(
                            R.string.patch_update_stage_verifying_new_apk_step,
                            stepNo,
                            total
                        )
                    )
                )
                val targetShaActual = sha256Hex(nextApk)
                if (!targetShaActual.equals(targetShaExpected, ignoreCase = true)) {
                    throw IllegalStateException("Target sha256 mismatch")
                }
            }

            if (currentApk.exists()) currentApk.delete()
            if (!nextApk.renameTo(currentApk)) {
                throw IllegalStateException("Failed to advance patch chain at step $stepNo")
            }
        }

        currentApk.copyTo(outApk, overwrite = true)

        cleanupPatchWorkDir(workDir, outApk)
        onEvent(
            ProgressEvent.StageChanged(
                Stage.READY_TO_INSTALL,
                context.getString(R.string.full_update_ready_to_install)
            )
        )
        outApk
    }

    private suspend fun resolvePatchChain(
        context: Context,
        patchUrl: String,
        metaUrl: String,
        currentBaseSha: String,
        targetMeta: JSONObject,
        targetMetaSha256: String?,
        targetPatchSha256: String?,
        onEvent: (ProgressEvent) -> Unit
    ): List<PatchStep> {
        val targetFormat = targetMeta.optString("format", "")
        if (targetFormat != "apkraw-1") {
            throw IllegalStateException("Unsupported patch format: $targetFormat")
        }

        val targetSha = targetMeta.optString("targetSha256", "").trim().lowercase()
        if (targetSha.isBlank()) {
            throw IllegalStateException("Patch meta missing targetSha256")
        }

        val targetToVersion = targetMeta.optString("toVersion", "").trim()
        val targetToPatchIndex = if (targetMeta.has("toPatchIndex")) targetMeta.optInt("toPatchIndex", -1) else -1

        val repo = parseRepoFromReleaseAssetUrl(metaUrl) ?: parseRepoFromReleaseAssetUrl(patchUrl)
            ?: throw IllegalStateException("Unsupported patch source URL")

        val api = GitHubApiService(context)
        val releases = api.getRepositoryReleases(
            owner = repo.first,
            repo = repo.second,
            page = 1,
            perPage = 100
        ).getOrElse { e ->
            throw IllegalStateException("Failed to query patch releases: ${e.message ?: e.javaClass.simpleName}")
        }

        val stepsByEdge = LinkedHashMap<String, PatchStep>()
        val scannableReleases = releases.filterNot { it.draft }
        if (scannableReleases.isNotEmpty()) {
            onEvent(ProgressEvent.ChainScanProgress(scanned = 0, total = scannableReleases.size))
        }

        val targetCandidate = parsePatchStep(
            meta = targetMeta,
            patchUrl = patchUrl,
            metaUrl = metaUrl,
            tag = targetMeta.optString("tag", ""),
            metaAssetSha256 = targetMetaSha256,
            patchAssetSha256 = targetPatchSha256
        )
        if (targetCandidate != null) {
            val edge = "${targetCandidate.baseSha256}->${targetCandidate.targetSha256}"
            stepsByEdge[edge] = targetCandidate
        }

        var scannedReleases = 0
        for (release in releases) {
            coroutineContext.ensureActive()
            if (release.draft) continue

            scannedReleases += 1
            onEvent(ProgressEvent.ChainScanProgress(scanned = scannedReleases, total = scannableReleases.size))

            val metaAsset =
                release.assets.firstOrNull { it.name.startsWith("patch_") && it.name.endsWith(".json") }
                    ?: release.assets.firstOrNull { it.name.endsWith(".json") }
            val patchAsset =
                release.assets.firstOrNull { it.name.startsWith("apkrawpatch_") && it.name.endsWith(".zip") }
                    ?: release.assets.firstOrNull { it.name.endsWith(".zip") }
            if (metaAsset == null || patchAsset == null) continue

            val meta = parseReleaseBodyAsPatchMeta(release.body) ?: continue
            val candidate = parsePatchStep(
                meta = meta,
                patchUrl = patchAsset.browser_download_url,
                metaUrl = metaAsset.browser_download_url,
                tag = release.tag_name,
                metaAssetSha256 = GithubReleaseUtil.parseAssetDigest(metaAsset.digest),
                patchAssetSha256 = GithubReleaseUtil.parseAssetDigest(patchAsset.digest)
            ) ?: continue

            val edge = "${candidate.baseSha256}->${candidate.targetSha256}"
            val existing = stepsByEdge[edge]
            if (existing == null) {
                stepsByEdge[edge] = candidate
            } else {
                val replace = candidate.toPatchIndex > existing.toPatchIndex ||
                    (candidate.toPatchIndex == existing.toPatchIndex &&
                        UpdateManager.compareVersions(candidate.targetVersionLabel, existing.targetVersionLabel) > 0)
                if (replace) {
                    stepsByEdge[edge] = candidate
                }
            }
        }

        var currentSha = currentBaseSha.trim().lowercase()
        val chain = mutableListOf<PatchStep>()
        val seenEdges = HashSet<String>()

        while (currentSha != targetSha) {
            coroutineContext.ensureActive()
            val candidates = stepsByEdge.values.filter { step ->
                step.baseSha256 == currentSha &&
                    (targetToVersion.isBlank() || step.toVersion.isBlank() || step.toVersion == targetToVersion) &&
                    (targetToPatchIndex < 0 || step.toPatchIndex < 0 || step.toPatchIndex <= targetToPatchIndex)
            }
            if (candidates.isEmpty()) {
                throw IllegalStateException("No applicable next patch for base sha ${currentSha.take(12)}")
            }

            var selected = candidates.first()
            for (candidate in candidates.drop(1)) {
                val betterByIndex = candidate.toPatchIndex > selected.toPatchIndex
                val betterByVersion =
                    candidate.toPatchIndex == selected.toPatchIndex &&
                        UpdateManager.compareVersions(candidate.targetVersionLabel, selected.targetVersionLabel) > 0
                if (betterByIndex || betterByVersion) {
                    selected = candidate
                }
            }

            val edge = "${selected.baseSha256}->${selected.targetSha256}"
            if (!seenEdges.add(edge)) {
                throw IllegalStateException("Patch chain loop detected")
            }

            chain.add(selected)
            currentSha = selected.targetSha256
            if (chain.size > MAX_CHAIN_STEPS) {
                throw IllegalStateException("Patch chain exceeds $MAX_CHAIN_STEPS steps")
            }
        }

        return chain
    }

    private fun parsePatchStep(
        meta: JSONObject,
        patchUrl: String,
        metaUrl: String,
        tag: String,
        metaAssetSha256: String?,
        patchAssetSha256: String?
    ): PatchStep? {
        val format = meta.optString("format", "")
        if (format != "apkraw-1") return null

        val baseSha = meta.optString("baseSha256", "").trim().lowercase()
        val targetSha = meta.optString("targetSha256", "").trim().lowercase()
        if (baseSha.isBlank() || targetSha.isBlank()) return null

        val toVersion = meta.optString("toVersion", "").trim()
        val toPatchIndex = if (meta.has("toPatchIndex")) meta.optInt("toPatchIndex", -1) else -1
        val targetVersionLabel = when {
            tag.isNotBlank() -> tag.removePrefix("v")
            toVersion.isNotBlank() && toPatchIndex >= 0 -> "$toVersion+$toPatchIndex"
            else -> toVersion
        }

        return PatchStep(
            patchUrl = patchUrl,
            metaUrl = metaUrl,
            baseSha256 = baseSha,
            targetSha256 = targetSha,
            toVersion = toVersion,
            toPatchIndex = toPatchIndex,
            targetVersionLabel = targetVersionLabel,
            metaAssetSha256 = metaAssetSha256,
            patchAssetSha256 = patchAssetSha256
        )
    }

    private fun parseRepoFromReleaseAssetUrl(url: String): Pair<String, String>? {
        val match = releaseAssetUrlRegex.find(url.trim()) ?: return null
        return match.groupValues[1] to match.groupValues[2]
    }

    private fun parseReleaseBodyAsPatchMeta(body: String?): JSONObject? {
        val text = body?.trim().orEmpty()
        if (text.isBlank()) {
            return null
        }
        return try {
            JSONObject(text)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Skip patch release with invalid JSON body", e)
            null
        }
    }

    private fun cleanupPatchWorkDir(workDir: File, keepFile: File) {
        val keep = runCatching { keepFile.canonicalFile }.getOrElse { keepFile.absoluteFile }
        val files = workDir.listFiles() ?: return
        for (f in files) {
            val fc = runCatching { f.canonicalFile }.getOrElse { f.absoluteFile }
            if (fc.path == keep.path) continue
            runCatching {
                if (f.isDirectory) {
                    f.deleteRecursively()
                } else {
                    f.delete()
                }
            }.onFailure { e ->
                AppLogger.w(TAG, "Failed to cleanup patch work file: ${f.absolutePath}", e)
            }
        }
    }


    private fun prepareCleanWorkDir(context: Context): File {
        val workDir = File(context.cacheDir, WORK_DIR_NAME)
        if (workDir.exists()) {
            runCatching { workDir.deleteRecursively() }
        }
        workDir.mkdirs()
        return workDir
    }

    /**
     * Map ordered source keys onto concrete URLs for [originalUrl].
     *
     * Keys come from [GithubReleaseUtil.getDownloadSources] for a release asset URL, so
     * every key resolves; `getValue` keeps an unknown key a loud failure instead of
     * quietly shortening the candidate list.
     */
    private fun resolveSources(originalUrl: String, sourceKeys: List<String>): List<SourceCandidate> {
        val byKey = GithubReleaseUtil.getDownloadSources(originalUrl)
        return sourceKeys.map { key -> SourceCandidate(key = key, url = byKey.getValue(key)) }
    }

    /**
     * Fetch one artifact through the ordered source list, verifying it.
     *
     * A source is dropped for this artifact only, so a mirror that serves the small meta
     * but times out on the large patch zip still lets the rest of the chain finish.
     */
    private suspend fun downloadWithFailover(
        sources: List<SourceCandidate>,
        out: File,
        label: String,
        expectedSha256: String? = null,
        multiThread: Boolean,
        onEvent: (ProgressEvent) -> Unit
    ) {
        val outcome = UpdateSourceDownloader.download(
            sources = sources,
            out = out,
            expectedSha256 = expectedSha256,
            multiThread = multiThread,
            onAttempt = { attempt, total, source ->
                onEvent(
                    ProgressEvent.MirrorSelected(name = source.key, attempt = attempt, total = total)
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
                        label = label,
                        readBytes = progress.readBytes,
                        totalBytes = progress.totalBytes,
                        speedBytesPerSec = progress.speedBytesPerSec
                    )
                )
            }
        )

        when (outcome) {
            is UpdateSourceDownloader.Outcome.Success -> Unit
            is UpdateSourceDownloader.Outcome.Failed ->
                throw AllSourcesFailedException(outcome.failures)
        }
    }

    private fun applyApkrawPatch(baseApk: File, patchZip: File, meta: JSONObject, outApk: File) {
        val entriesJson = meta.optJSONArray("apkRawEntries") ?: JSONArray()
        val tailName = meta.optString("apkRawTailFile", "tail.bin")

        val baseMap = RandomAccessFile(baseApk, "r").use { raf ->
            readCentralDirectory(raf)
        }

        if (outApk.exists()) outApk.delete()
        outApk.parentFile?.mkdirs()

        val md = MessageDigest.getInstance("SHA-256")
        DigestOutputStream(BufferedOutputStream(FileOutputStream(outApk)), md).use { dout ->
            RandomAccessFile(baseApk, "r").use { raf ->
                ZipFile(patchZip).use { pz ->
                    for (i in 0 until entriesJson.length()) {
                        val ent = entriesJson.getJSONObject(i)
                        val name = ent.optString("name", "")
                        val mode = ent.optString("mode", "")
                        if (name.isBlank()) throw IllegalStateException("Bad apkRawEntries")

                        if (mode == "copy") {
                            val cd = baseMap[name] ?: throw IllegalStateException("Base apk missing entry: $name")
                            copyLocalRecord(raf, cd, dout)
                        } else if (mode == "add") {
                            val recordPath = ent.optString("recordPath", "")
                            if (recordPath.isBlank()) throw IllegalStateException("apkraw add missing recordPath")
                            val ze = pz.getEntry(recordPath) ?: throw IllegalStateException("patch zip missing $recordPath")
                            pz.getInputStream(ze).use { ins ->
                                ins.copyTo(dout)
                            }
                        } else {
                            throw IllegalStateException("Bad apkraw mode: $mode")
                        }
                    }

                    val tailEntry = pz.getEntry(tailName) ?: throw IllegalStateException("patch zip missing $tailName")
                    pz.getInputStream(tailEntry).use { ins ->
                        ins.copyTo(dout)
                    }
                }
            }
            dout.flush()
        }
    }

    private data class CdEntry(
        val localHeaderOffset: Long,
        val compressedSize: Long
    )

    private fun readCentralDirectory(raf: RandomAccessFile): Map<String, CdEntry> {
        val fileLen = raf.length()
        val readLen = min(65557L, fileLen).toInt()
        val buf = ByteArray(readLen)
        raf.seek(fileLen - readLen)
        raf.readFully(buf)

        var eocdIndex = -1
        for (i in readLen - 22 downTo 0) {
            if (buf[i] == 0x50.toByte() &&
                buf[i + 1] == 0x4b.toByte() &&
                buf[i + 2] == 0x05.toByte() &&
                buf[i + 3] == 0x06.toByte()
            ) {
                eocdIndex = i
                break
            }
        }
        if (eocdIndex < 0) throw IllegalStateException("EOCD not found")

        val eocdOffset = (fileLen - readLen + eocdIndex).toLong()
        raf.seek(eocdOffset)
        val eocd = ByteArray(22)
        raf.readFully(eocd)

        val totalEntries = readUShortLE(eocd, 10)
        val cdOffset = readUIntLE(eocd, 16)

        raf.seek(cdOffset)
        val out = LinkedHashMap<String, CdEntry>(totalEntries)
        repeat(totalEntries) {
            val hdr = ByteArray(46)
            raf.readFully(hdr)
            val sig = readUIntLE(hdr, 0)
            if (sig != 0x02014b50L) throw IllegalStateException("Bad central directory signature")

            val flags = readUShortLE(hdr, 8)
            val compressedSize = readUIntLE(hdr, 20)
            val fileNameLen = readUShortLE(hdr, 28)
            val extraLen = readUShortLE(hdr, 30)
            val commentLen = readUShortLE(hdr, 32)
            val localHeaderOffset = readUIntLE(hdr, 42)

            val nameBytes = ByteArray(fileNameLen)
            raf.readFully(nameBytes)
            val charset = if ((flags and 0x800) != 0) Charsets.UTF_8 else Charsets.ISO_8859_1
            val name = String(nameBytes, charset)

            if (extraLen > 0) raf.skipBytes(extraLen)
            if (commentLen > 0) raf.skipBytes(commentLen)

            if (!name.endsWith("/")) {
                out[name] = CdEntry(localHeaderOffset = localHeaderOffset, compressedSize = compressedSize)
            }
        }

        return out
    }

    private fun copyLocalRecord(raf: RandomAccessFile, cd: CdEntry, out: java.io.OutputStream) {
        raf.seek(cd.localHeaderOffset)

        val lfh = ByteArray(30)
        raf.readFully(lfh)
        val sig = readUIntLE(lfh, 0)
        if (sig != 0x04034b50L) throw IllegalStateException("Bad local header signature")

        val flags = readUShortLE(lfh, 6)
        val fileNameLen = readUShortLE(lfh, 26)
        val extraLen = readUShortLE(lfh, 28)

        out.write(lfh)

        if (fileNameLen + extraLen > 0) {
            val nameExtra = ByteArray(fileNameLen + extraLen)
            raf.readFully(nameExtra)
            out.write(nameExtra)
        }

        copyBytes(raf, out, cd.compressedSize)

        if ((flags and 0x08) != 0) {
            val first4 = ByteArray(4)
            raf.readFully(first4)
            out.write(first4)
            val ddSig = readUIntLE(first4, 0)
            if (ddSig == 0x08074b50L) {
                val rest = ByteArray(12)
                raf.readFully(rest)
                out.write(rest)
            } else {
                val rest = ByteArray(8)
                raf.readFully(rest)
                out.write(rest)
            }
        }
    }

    private fun copyBytes(raf: RandomAccessFile, out: java.io.OutputStream, count: Long) {
        var remaining = count
        val buffer = ByteArray(1024 * 1024)
        while (remaining > 0) {
            val toRead = min(remaining, buffer.size.toLong()).toInt()
            val read = raf.read(buffer, 0, toRead)
            if (read <= 0) throw IllegalStateException("Unexpected EOF")
            out.write(buffer, 0, read)
            remaining -= read.toLong()
        }
    }

    private fun sha256Hex(file: File): String {
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

    private fun readUShortLE(buf: ByteArray, off: Int): Int {
        return (buf[off].toInt() and 0xff) or ((buf[off + 1].toInt() and 0xff) shl 8)
    }

    private fun readUIntLE(buf: ByteArray, off: Int): Long {
        return (buf[off].toLong() and 0xff) or
            ((buf[off + 1].toLong() and 0xff) shl 8) or
            ((buf[off + 2].toLong() and 0xff) shl 16) or
            ((buf[off + 3].toLong() and 0xff) shl 24)
    }
}
