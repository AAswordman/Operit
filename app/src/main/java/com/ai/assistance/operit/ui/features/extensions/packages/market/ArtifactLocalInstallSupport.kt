package com.ai.assistance.operit.ui.features.extensions.packages.market

import android.content.Context
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageManager
import com.ai.assistance.operit.data.api.ArtifactProjectVersionResponse
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.transfer.copyWithTransferProgress
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocalInstalledArtifactSnapshot(
    val packageName: String,
    val sha256: String,
    val isBuiltIn: Boolean
)

enum class LocalArtifactInstallStateKind {
    NOT_INSTALLED,
    EXACT_INSTALLED,
    SAME_PROJECT_VARIANT_INSTALLED,
    NAME_CONFLICT,
    BUILT_IN_CONFLICT
}

data class LocalArtifactInstallState(
    val kind: LocalArtifactInstallStateKind,
    val snapshot: LocalInstalledArtifactSnapshot? = null
)

fun artifactRuntimePackageIds(entries: List<MarketV2Entry>): Set<String> {
    return entries
        .filter { entry -> entry.type.lowercase() == "script" || entry.type.lowercase() == "package" }
        .map { entry ->
            val currentVersion = entry.latestVersion ?: error("Entry latestVersion is missing: ${entry.id}")
            currentVersion.runtimePackageId.ifBlank { error("Artifact runtime package id not found for version: ${currentVersion.id}") }
        }
        .toSet()
}

fun PackageManager.getInstalledArtifactSnapshots(
    packageNames: Collection<String>? = null
): Map<String, LocalInstalledArtifactSnapshot> {
    val targetPackageNames = packageNames?.filter { it.isNotBlank() }?.toSet()
    val publishableSourcesByName =
        getPublishablePackageSources().associateBy { source -> source.packageName }

    return getTopLevelAvailablePackages()
        .filterKeys { packageName ->
            targetPackageNames == null || targetPackageNames.any { sameArtifactRuntimePackageId(packageName, it) }
        }
        .mapValues { (packageName, toolPackage) ->
            val sourceFile =
                publishableSourcesByName[packageName]
                    ?.sourcePath
                    ?.let(::File)
            LocalInstalledArtifactSnapshot(
                packageName = packageName,
                // Built-in packages do not always have a stable external source file path.
                sha256 =
                    if (sourceFile != null && sourceFile.exists() && sourceFile.isFile) {
                        sha256Hex(sourceFile)
                    } else {
                        ""
                    },
                isBuiltIn = toolPackage.isBuiltIn
            )
        }
}

private fun findInstalledArtifactSnapshot(
    installedSnapshots: Map<String, LocalInstalledArtifactSnapshot>,
    packageName: String
): LocalInstalledArtifactSnapshot? {
    val trimmedPackageName = packageName.trim()
    if (trimmedPackageName.isBlank()) {
        return null
    }

    installedSnapshots[trimmedPackageName]?.let { return it }

    return installedSnapshots.values.firstOrNull { snapshot ->
        sameArtifactRuntimePackageId(snapshot.packageName, trimmedPackageName)
    }
}

fun resolveLocalArtifactInstallState(
    installedSnapshots: Map<String, LocalInstalledArtifactSnapshot>,
    packageName: String,
    targetSha256: String,
    projectNodeSha256s: Collection<String>
): LocalArtifactInstallState {
    val installed =
        findInstalledArtifactSnapshot(
            installedSnapshots = installedSnapshots,
            packageName = packageName
        ) ?: return LocalArtifactInstallState(LocalArtifactInstallStateKind.NOT_INSTALLED)
    val normalizedInstalledSha = installed.sha256.trim().lowercase()
    val normalizedTargetSha = targetSha256.trim().lowercase()
    if (normalizedInstalledSha.isNotBlank() && normalizedInstalledSha == normalizedTargetSha) {
        return LocalArtifactInstallState(
            kind = LocalArtifactInstallStateKind.EXACT_INSTALLED,
            snapshot = installed
        )
    }
    if (installed.isBuiltIn) {
        return LocalArtifactInstallState(
            kind = LocalArtifactInstallStateKind.BUILT_IN_CONFLICT,
            snapshot = installed
        )
    }

    val projectHashes =
        projectNodeSha256s
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()

    return if (normalizedInstalledSha.isNotBlank() && normalizedInstalledSha in projectHashes) {
        LocalArtifactInstallState(
            kind = LocalArtifactInstallStateKind.SAME_PROJECT_VARIANT_INSTALLED,
            snapshot = installed
        )
    } else {
        LocalArtifactInstallState(
            kind = LocalArtifactInstallStateKind.NAME_CONFLICT,
            snapshot = installed
        )
    }
}

suspend fun installArtifactProjectVersion(
    context: Context,
    packageManager: PackageManager,
    projectVersions: List<ArtifactProjectVersionResponse>,
    version: ArtifactProjectVersionResponse,
    onProgress: MarketInstallProgressReporter = { _, _ -> }
) {
    onProgress(MarketInstallStage.CHECKING_LOCAL, null)
    val installedSnapshots =
        withContext(Dispatchers.IO) {
            packageManager.getInstalledArtifactSnapshots()
        }
    val installState =
        resolveLocalArtifactInstallState(
            installedSnapshots = installedSnapshots,
            packageName = version.runtimePackageId,
            targetSha256 = version.sha256,
            projectNodeSha256s =
                projectVersions
                    .filter {
                        sameArtifactRuntimePackageId(it.runtimePackageId, version.runtimePackageId)
                    }
                    .map { it.sha256 }
        )

    when (installState.kind) {
        LocalArtifactInstallStateKind.EXACT_INSTALLED -> return
        LocalArtifactInstallStateKind.BUILT_IN_CONFLICT ->
            throw IllegalStateException("本地已安装同名内置插件 `${version.runtimePackageId}`，不能直接覆盖。")
        LocalArtifactInstallStateKind.NAME_CONFLICT,
        LocalArtifactInstallStateKind.NOT_INSTALLED,
        LocalArtifactInstallStateKind.SAME_PROJECT_VARIANT_INSTALLED -> Unit
    }

    val tempFile = withContext(Dispatchers.IO) { downloadArtifactProjectVersionToTempFile(context, version, onProgress) }
    try {
        onProgress(MarketInstallStage.INSTALLING, null)
        if (
            installState.kind == LocalArtifactInstallStateKind.SAME_PROJECT_VARIANT_INSTALLED ||
            installState.kind == LocalArtifactInstallStateKind.NAME_CONFLICT
        ) {
            val installedPackageName = installState.snapshot?.packageName ?: version.runtimePackageId
            val deleted =
                withContext(Dispatchers.IO) {
                    // 更新只重置安装时间，保留插件之前的实际使用记录。
                    packageManager.deletePackage(installedPackageName, preservePluginActivity = true)
                }
            if (!deleted) {
                throw IllegalStateException("替换已安装插件 `${installedPackageName}` 失败。")
            }
        }
        val importResult =
            withContext(Dispatchers.IO) {
                packageManager.addPackageFileFromExternalStorage(tempFile.absolutePath)
            }
        if (!importResult.message.startsWith("Successfully imported", ignoreCase = true)) {
            throw IllegalStateException(importResult.message)
        }
    } finally {
        if (tempFile.exists()) {
            tempFile.delete()
        }
        tempFile.parentFile?.delete()
    }
}

private fun downloadArtifactProjectVersionToTempFile(
    context: Context,
    version: ArtifactProjectVersionResponse,
    onProgress: MarketInstallProgressReporter
): File {
    val downloadUrl = version.downloadUrl.trim().ifBlank { throw IllegalStateException("Missing v2 asset download URL") }
    val downloadDir = File(context.cacheDir, "market_downloads")
    if (!downloadDir.exists() && !downloadDir.mkdirs()) {
        throw IllegalStateException("Failed to create market download cache")
    }
    val connection = URL(downloadUrl).openConnection() as HttpURLConnection
    connection.instanceFollowRedirects = true
    connection.connectTimeout = 30_000
    connection.readTimeout = 60_000
    connection.requestMethod = "GET"
    // 隔离并行下载，同时保留导入器使用的附件原文件名和扩展名。
    val transferDirectory = File(downloadDir, UUID.randomUUID().toString())
    if (!transferDirectory.mkdir()) {
        throw IllegalStateException("Failed to create market download directory")
    }
    val assetName = File(version.assetName.ifBlank { "${version.runtimePackageId}.bin" }).name
    val targetFile = File(transferDirectory, assetName)
    try {
        onProgress(MarketInstallStage.CONNECTING, null)
        connection.connect()
        val code = connection.responseCode
        if (code !in 200..299) {
            throw IllegalStateException("Download failed: HTTP $code")
        }
        val totalBytes = connection.contentLengthLong
        val inputStream = connection.inputStream ?: throw IllegalStateException("Empty download stream")
        inputStream.use { input ->
            targetFile.outputStream().use { output ->
                input.copyWithTransferProgress(output, totalBytes, { transfer ->
                    onProgress(MarketInstallStage.DOWNLOADING, transfer)
                })
            }
        }
        onProgress(MarketInstallStage.VERIFYING, null)
        val actualSha256 = sha256Hex(targetFile)
        if (!actualSha256.equals(version.sha256, ignoreCase = true)) {
            throw IllegalStateException("Downloaded file sha256 mismatch")
        }
        return targetFile
    } catch (error: Exception) {
        targetFile.delete()
        transferDirectory.delete()
        throw error
    } finally {
        connection.disconnect()
    }
}

private fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
