package com.ai.assistance.operit.data.resources

import android.content.Context
import com.ai.assistance.operit.data.resources.models.ResourceDownloadPhase
import android.net.Uri
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.storage.LocalModelRuntimeRegistry
import com.ai.assistance.operit.data.storage.LocalModelDeleteOutcome
import com.ai.assistance.operit.data.storage.DataStorageRepository
import com.ai.assistance.operit.data.storage.DirectoryCleanupRequest
import com.ai.assistance.operit.data.storage.SafeDirectoryCleaner
import com.ai.assistance.operit.terminal.TerminalManager
import com.ai.assistance.operit.util.files.OperitPaths
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object OfflineResources {
    const val SPEECH = "speech"
    const val UBUNTU = "ubuntu"
    const val ANDROID_EXPORT = "android-export"
    const val WINDOWS_EXPORT = "windows-export"
    const val RELEASE_URL = "https://github.com/3316891527/Operit-Kong-resources/releases/tag/resources-v1"
    private const val DOWNLOAD_URL = "https://github.com/3316891527/Operit-Kong-resources/releases/download/resources-v1/"
    private const val SPEECH_DIRECTORY = "sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13"
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).followSslRedirects(false).build()

    data class Group(val id: String, val label: Int, val bytes: Long)

    fun groups(context: Context): List<Group> = listOf(
        SPEECH to R.string.offline_resources_speech,
        UBUNTU to R.string.offline_resources_ubuntu,
        ANDROID_EXPORT to R.string.offline_resources_android,
        WINDOWS_EXPORT to R.string.offline_resources_windows,
    ).map { (id, label) -> Group(id, label, specs(context, id).sumOf { it.size }) }

    private fun specs(context: Context, id: String): List<ResourceFileSpec> =
        context.assets.open("offline-resources.properties").bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
                val fields = line.split('|')
                require(fields.size == 6)
                fields
            }.filter { it[0] == id }.map { ResourceFileSpec(it[1], it[2].toLong(), it[3]) }.toList()
        }.also { require(it.isNotEmpty()) }

    fun directory(context: Context, id: String): File = when (id) {
        SPEECH -> File(OperitPaths.sherpaNcnnModelsDir(context), SPEECH_DIRECTORY)
        UBUNTU, ANDROID_EXPORT, WINDOWS_EXPORT -> File(context.filesDir, "downloaded_resources/$id")
        else -> error("Unknown resource group: $id")
    }

    private fun ubuntuRoot(context: Context): File =
        File(context.filesDir, "usr/var/lib/proot-distro/installed-rootfs/ubuntu")

    private fun ubuntuInstallationPaths(context: Context): List<File> {
        val root = ubuntuRoot(context)
        return listOf(
            root,
            File(root.parentFile, "${root.name}.install.tmp"),
            File(root.parentFile, "${root.name}.install.lock"),
            File(context.filesDir, "ubuntu-noble-aarch64-pd-v4.18.0.tar.xz"),
        )
    }

    fun ubuntuDeletionPending(context: Context): Boolean = TerminalManager.isEnvironmentRemovalPending(context)

    fun ubuntuInstalled(context: Context): Boolean {
        if (ubuntuDeletionPending(context)) return false
        val root = ubuntuRoot(context)
        // 与终端安装脚本判断相同，保留已有环境的离线可用性。
        return File(root, ".operit_installed_ok").isFile || File(root, "etc/issue.net").isFile
    }

    suspend fun hasLocalData(context: Context, id: String): Boolean = withContext(Dispatchers.IO) {
        directory(context, id).exists() || (id == UBUNTU && (
            ubuntuDeletionPending(context) || ubuntuInstallationPaths(context).any { it.exists() }
            ))
    }

    /** 仅移除安装归档，不进入终端维护，不触碰已安装的 Ubuntu。 */
    suspend fun deleteUbuntuArchive(context: Context) = access(context, UBUNTU, writing = true) { dir, _ ->
        check(dir.deleteRecursively() || !dir.exists()) { context.getString(R.string.resource_delete_failed) }
        DataStorageRepository(context).invalidateCache()
    }
    suspend fun resumePendingUbuntuDeletion(context: Context) {
        delete(context, UBUNTU, resumeOnly = true)
    }

    suspend fun delete(context: Context, id: String, resumeOnly: Boolean = false) {
        val operation: suspend () -> Unit = {
            withContext(NonCancellable + Dispatchers.IO) {
                locks.getOrPut(id) { Mutex() }.withLock {
                    val dir = directory(context, id)
                    if (id == SPEECH) {
                        // 删除期间由运行时注册表阻止新识别任务占用模型。
                        when (LocalModelRuntimeRegistry.deleteIfUnused(dir)) {
                            LocalModelDeleteOutcome.DELETED -> Unit
                            LocalModelDeleteOutcome.IN_USE -> throw IOException(context.getString(R.string.offline_resources_in_use))
                            LocalModelDeleteOutcome.FAILED -> throw IOException(context.getString(R.string.resource_delete_failed))
                        }
                    } else {
                        val paths = if (id == UBUNTU) ubuntuInstallationPaths(context) + dir else listOf(dir)
                        paths.forEach { path ->
                            if (path.exists()) {
                                if (path.isDirectory) {
                                    // 终端进程退出后目录内容可能仍在完成收尾，失败时短暂重扫，避免一次竞态永久留下 pending。
                                    val request = DirectoryCleanupRequest(
                                        path, repairDirectoryPermissions = id == UBUNTU,
                                    )
                                    var result = SafeDirectoryCleaner().clean(listOf(request))
                                    var retries = 0
                                    while (result.failedEntryCount > 0 && retries < 2) {
                                        kotlinx.coroutines.delay(250)
                                        result = SafeDirectoryCleaner().clean(listOf(request))
                                        retries++
                                    }
                                    if (result.failedEntryCount > 0) {
                                        throw IOException(
                                            context.getString(R.string.resource_delete_failed) +
                                                "\n" + (result.firstFailedPath ?: path.absolutePath),
                                        )
                                    }
                                }
                                if (!path.delete() && path.exists()) {
                                    throw IOException(context.getString(R.string.resource_delete_failed) + "\n" + path.absolutePath)
                                }
                            }
                        }
                    }
                    DataStorageRepository(context).invalidateCache()
                }
            }
        }
        // 锁顺序与初始化一致：先暂停终端，再锁定资源，避免导入和初始化互相等待。
        if (id == UBUNTU) TerminalManager.withEnvironmentMaintenance(context, resumeOnly, operation) else operation()
    }

    private suspend fun <T> access(
        context: Context,
        id: String,
        writing: Boolean = false,
        allowPendingDeletion: Boolean = false,
        action: suspend (File, List<ResourceFileSpec>) -> T,
    ): T =
        withContext(Dispatchers.IO) {
            locks.getOrPut(id) { Mutex() }.withLock {
                if (id == UBUNTU && !allowPendingDeletion && ubuntuDeletionPending(context)) {
                    throw IOException(context.getString(R.string.resource_ubuntu_delete_pending))
                }
                val directory = directory(context, id)
                val handle = if (id == SPEECH) {
                    if (writing && LocalModelRuntimeRegistry.isInUse(directory)) {
                        throw IOException(context.getString(R.string.offline_resources_in_use))
                    }
                    LocalModelRuntimeRegistry.acquire(directory)
                        ?: throw IOException(context.getString(R.string.offline_resources_in_use))
                } else null
                try { action(directory, specs(context, id)) } finally { handle?.close() }
            }
        }

    suspend fun isReady(context: Context, id: String): Boolean = access(context, id, allowPendingDeletion = true) { dir, files ->
        if (id == UBUNTU && ubuntuDeletionPending(context)) return@access false
        val jobContext = currentCoroutineContext()
        files.all { ResourceArchive.verified(File(dir, it.name), it) { jobContext.ensureActive() } }
    }

    private suspend fun <T> withAvailable(context: Context, id: String, action: suspend (File, List<ResourceFileSpec>) -> T): T =
        access(context, id) { dir, files ->
            val jobContext = currentCoroutineContext()
            if (!files.all { ResourceArchive.verified(File(dir, it.name), it) { jobContext.ensureActive() } }) {
                // 资源检查也会被后台初始化调用，导航由用户从设置主动发起。
                throw IOException(context.getString(R.string.offline_resources_missing))
            }
            action(dir, files)
        }

    suspend fun <T> withSpeechModel(context: Context, action: suspend (File) -> T): T =
        withAvailable(context, SPEECH) { dir, _ -> action(dir) }

    suspend fun prepareUbuntuArchive(context: Context, destination: File) =
        withAvailable(context, UBUNTU) { dir, files ->
            File(dir, files.single().name).copyTo(destination, overwrite = true)
            Unit
        }

    suspend fun copyExportTemplate(context: Context, android: Boolean, destination: File): File =
        withAvailable(context, if (android) ANDROID_EXPORT else WINDOWS_EXPORT) { dir, files ->
            File(dir, files.single().name).copyTo(destination, overwrite = true)
        }

    suspend fun download(context: Context, id: String, phase: (ResourceDownloadPhase) -> Unit = {}, progress: (Long, Long) -> Unit) = access(context, id, writing = true) { dir, files ->
        check(dir.isDirectory || dir.mkdirs())
        val jobContext = currentCoroutineContext()
        phase(ResourceDownloadPhase.CONNECTING)
        val needed = files.filterNot { ResourceArchive.verified(File(dir, it.name), it) { jobContext.ensureActive() } }
        val required = needed.sumOf { it.size }
        if (dir.usableSpace < required + 1024 * 1024) throw IOException(context.getString(R.string.offline_resources_no_space))
        val total = files.sumOf { it.size }
        var completed = total - required
        progress(completed, total)
        needed.forEach { spec ->
            phase(ResourceDownloadPhase.DOWNLOADING)
            val part = File(dir, "${spec.name}.part")
            try {
                coroutineScope {
                    val call = client.newCall(Request.Builder().url(DOWNLOAD_URL + spec.name).build())
                    val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
                        try { awaitCancellation() } finally { call.cancel() }
                    }
                    try {
                        call.execute().use { response ->
                            if (response.code != 200) throw IOException("HTTP ${response.code}: ${spec.name}")
                            val body = response.body ?: throw IOException("Empty response: ${spec.name}")
                            body.byteStream().use { input ->
                                ResourceArchive.writeVerified(input, part, spec, { jobContext.ensureActive() }) {
                                    progress(completed + it, total)
                                }
                            }
                        }
                    } finally { cancellation.cancelAndJoin() }
                }
                jobContext.ensureActive()
                phase(ResourceDownloadPhase.INSTALLING)
                ResourceArchive.install(part, File(dir, spec.name))
                completed += spec.size
            } finally { part.delete() }
        }
        progress(total, total)
    }

    suspend fun exportZip(context: Context, id: String, uri: Uri, progress: (Long, Long) -> Unit) = access(context, id) { dir, files ->
        val jobContext = currentCoroutineContext()
        if (!files.all { ResourceArchive.verified(File(dir, it.name), it) { jobContext.ensureActive() } }) {
            throw IOException(context.getString(R.string.offline_resources_missing))
        }
        val total = files.sumOf { it.size }
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open output")
        output.use { ResourceArchive.exportZip(it, files, dir, { jobContext.ensureActive() }) { done -> progress(done, total) } }
    }

    suspend fun importZip(context: Context, id: String, uri: Uri, progress: (Long, Long) -> Unit) = access(context, id, writing = true) { dir, files ->
        val jobContext = currentCoroutineContext()
        val total = files.sumOf { it.size }
        val stagingRoot = File(context.filesDir, "downloaded_resources")
        check(stagingRoot.isDirectory || stagingRoot.mkdirs())
        if (stagingRoot.usableSpace < total + 1024 * 1024) throw IOException(context.getString(R.string.offline_resources_no_space))
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open input")
        input.use { ResourceArchive.importZip(it, files, File(stagingRoot, ".import-${UUID.randomUUID()}"), dir, { jobContext.ensureActive() }) { done -> progress(done, total) } }
    }
}
