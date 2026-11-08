package com.ai.assistance.operit.data.resources.packs

import android.content.Context
import com.ai.assistance.operit.data.resources.models.*
import com.ai.assistance.operit.data.storage.LocalModelRuntimeRegistry
import com.ai.assistance.operit.data.storage.LocalModelUsageHandle
import com.ai.assistance.operit.data.storage.LocalModelDeleteOutcome
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ResourcePackSpec(
    val id: String, val name: String, val version: String, val files: List<ModelFile>,
    val archive: ModelFile, val loadOrder: List<String> = emptyList(),
    val extension: com.ai.assistance.operit.data.resources.extensions.BuiltInExtensionSpec? = null,
)

@Serializable
data class ResourcePackCatalog(val schemaVersion: Int, val releaseTag: String, val packs: List<ResourcePackSpec>)

/** 模板、逆向工具和语音运行库共用固定清单，下载源不影响这类独立发行资源。 */
object PackResources {
    const val OCR_MODELS = "ocr-google-mlkit"
    const val APK_REVERSE = "apk-reverse"
    const val SPEECH_ENGINE = "engine-sherpa-onnx"
    private val json = Json { ignoreUnknownKeys = true }
    private val locks = ConcurrentHashMap<String, Mutex>()
    @Volatile private var cachedCatalog: ResourcePackCatalog? = null
    @Volatile private var cachedHistory: ResourcePackInstallHistory? = null
    private val bundledCache = ConcurrentHashMap<String, Boolean>()
    private val engineLock = Any()
    private var engineLoaded = false
    private var engineFailure: Throwable? = null
    private var engineUsage: LocalModelUsageHandle? = null

    fun catalog(context: Context): ResourcePackCatalog {
        cachedCatalog?.let { return it }
        val resources = context.assets.open("offline-packs.json").bufferedReader().use { json.decodeFromString<ResourcePackCatalog>(it.readText()) }
        return resources.copy(packs = resources.packs + com.ai.assistance.operit.data.resources.extensions.BuiltInExtensionResources.specs(context)).also {
            require(it.schemaVersion == 1 && it.packs.map(ResourcePackSpec::id).distinct().size == it.packs.size)
            it.packs.forEach(ResourcePackInstallations::validateSpec)
            cachedCatalog = it
        }
    }

    fun invalidateCatalog() { cachedCatalog = null; cachedHistory = null; bundledCache.clear() }

    fun spec(context: Context, id: String): ResourcePackSpec = catalog(context).packs.first { it.id == id }
    private fun directoryFor(context: Context, pack: ResourcePackSpec): File {
        val relative = if (pack.id == SPEECH_ENGINE) "runtime/models/local/engines/sherpa-onnx/1.13.2/android-arm64-v8a/${pack.version}"
            else "runtime/resources/${pack.id}/${pack.version}"
        return File(context.filesDir, relative)
    }

    private fun history(context: Context): List<ResourcePackSpec> {
        cachedHistory?.let { return it.packs + com.ai.assistance.operit.data.resources.extensions.BuiltInExtensionResources.historicalSpecs(context) }
        val loaded = context.assets.open("offline-pack-installations.json").bufferedReader().use {
            json.decodeFromString<ResourcePackInstallHistory>(it.readText())
        }
        require(loaded.schemaVersion == 1)
        require(loaded.packs.map { it.id to it.version }.distinct().size == loaded.packs.size)
        loaded.packs.forEach(ResourcePackInstallations::validateSpec)
        cachedHistory = loaded
        return loaded.packs + com.ai.assistance.operit.data.resources.extensions.BuiltInExtensionResources.historicalSpecs(context)
    }

    private fun installed(context: Context, id: String, force: Boolean = false): InstalledResourcePack? =
        ResourcePackInstallations.find(spec(context, id), history(context), { directoryFor(context, it) }, force)

    fun installedSpec(context: Context, id: String): ResourcePackSpec? = installed(context, id)?.spec

    fun directory(context: Context, id: String): File =
        installed(context, id)?.directory ?: directoryFor(context, spec(context, id))

    /** Include incomplete and older installs so import cannot silently replace existing extension files. */
    fun hasLocalData(context: Context, id: String): Boolean =
        directoryFor(context, spec(context, id)).parentFile!!.exists()

    /** 使用检查覆盖整个版本组，旧版本的运行引用也会阻止替换和删除。 */
    fun isInUse(context: Context, id: String): Boolean =
        LocalModelRuntimeRegistry.isInUse(directoryFor(context, spec(context, id)).parentFile!!)

    suspend fun cleanupObsoleteExtensions(context: Context) = withContext(Dispatchers.IO) {
        val job = currentCoroutineContext()
        for (pack in catalog(context).packs.filter { it.extension != null }) {
            job.ensureActive()
            locks.getOrPut(pack.id) { Mutex() }.withLock {
                ResourcePackInstallations.cleanupObsolete(pack, history(context), { directoryFor(context, it) }) {
                    job.ensureActive()
                }
            }
        }
    }

    /** 已安装状态与是否具有清单最新内容分开，只有内容变化才需要下载更新。 */
    fun isCurrentReady(context: Context, id: String, force: Boolean = false): Boolean =
        isBundled(context, id) || installed(context, id, force)?.hasCurrentContent(spec(context, id)) == true

    fun isBundled(context: Context, id: String): Boolean = bundledCache.getOrPut(id) {
        when {
            spec(context, id).extension != null -> {
                val path = spec(context, id).extension!!.path
                context.assets.list("packages").orEmpty().contains(File(path).name)
            }
            id == OCR_MODELS -> context.assets.list("mlkit-google-ocr-models").orEmpty().isNotEmpty()
            id.startsWith("template-") -> context.assets.list("templates/${id.removePrefix("template-")}").orEmpty().isNotEmpty()
            else -> false
        }
    }
    fun isReady(context: Context, id: String, force: Boolean = false): Boolean =
        isBundled(context, id) || installed(context, id, force) != null

    fun requireDirectory(context: Context, id: String): File {
        check(isReady(context, id)) { "请先在设置 → 离线资源准备${spec(context, id).name}" }
        return directory(context, id)
    }

    fun resourceFile(context: Context, id: String, relative: String): File {
        val pack = installed(context, id)?.spec ?: spec(context, id)
        require(pack.files.any { it.path == relative }) { "资源路径不在清单内" }
        return requireDirectory(context, id).containedFile(relative)
    }

    suspend fun download(context: Context, id: String, phase: (ResourceDownloadPhase) -> Unit = {}, progress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        locks.getOrPut(id) { Mutex() }.withLock {
            check(!isBundled(context, id)) { "包内资源无需重复准备" }
            val pack = spec(context, id)
            if (isCurrentReady(context, id, force = true)) { progress(pack.archive.bytes, pack.archive.bytes); return@withLock }
            val cache = File(context.cacheDir, "offline-packs/${pack.archive.path}")
            if (cache.usableSpace < pack.archive.bytes + pack.files.sumOf(ModelFile::bytes) + 1024 * 1024 && context.cacheDir.usableSpace < pack.archive.bytes + pack.files.sumOf(ModelFile::bytes) + 1024 * 1024) error("设备空间不足")
            progress(0, pack.archive.bytes)
            phase(ResourceDownloadPhase.DOWNLOADING)
            ResourceTransfer.download(pack.archive.url, pack.archive, cache) { progress(it, pack.archive.bytes) }
            phase(ResourceDownloadPhase.INSTALLING)
            try {
                cache.inputStream().use { importLocked(context, id, it, pack.archive.path) { _, _ -> } }
                progress(pack.archive.bytes, pack.archive.bytes)
            } finally { cache.delete() }
        }
    }

    suspend fun importArchive(context: Context, id: String, input: InputStream, name: String, progress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        check(!isBundled(context, id)) { "包内资源无需导入" }
        locks.getOrPut(id) { Mutex() }.withLock {
            input.use {
                // Recheck under the install lock: another download may finish while the picker is open.
                check(spec(context, id).extension == null || !hasLocalData(context, id)) {
                    context.getString(com.ai.assistance.operit.R.string.hub_extension_remove_before_import)
                }
                importLocked(context, id, it, name, progress)
            }
        }
    }

    private suspend fun importLocked(context: Context, id: String, input: InputStream, name: String, progress: (Long, Long) -> Unit) {
        val stage = File(context.filesDir, "runtime/staging/pack-${UUID.randomUUID()}")
        val job = currentCoroutineContext()
        try {
            ModelArchive.unpack(input, name, stage, { job.ensureActive() }) { progress(it, spec(context, id).files.sumOf(ModelFile::bytes)) }
            installExtracted(context, id, stage) { job.ensureActive() }
        } finally { stage.deleteRecursively() }
    }

    internal suspend fun restoreExtracted(context: Context, id: String, stage: File, checkpoint: () -> Unit) {
        locks.getOrPut(id) { Mutex() }.withLock {
            if (!isReady(context, id)) installExtracted(context, id, stage, checkpoint)
        }
    }
    internal fun validateExtracted(context: Context, id: String, stage: File, checkpoint: () -> Unit): ResourcePackSpec {
        val current = spec(context, id)
        val info = json.parseToJsonElement(File(stage, "operit-resource.json").readText()).let { it as kotlinx.serialization.json.JsonObject }
        require(info["id"]?.toString()?.trim('"') == id) { "资源包身份与当前条目不匹配" }
        val pack = ResourcePackInstallations.supported(current, history(context), directoryFor(context, current).parentFile!!)
            .firstOrNull { it.version == info["version"]?.toString()?.trim('"') }
            ?: error("资源包版本不在已验证的安装清单内")
        val expected = pack.files.map(ModelFile::path).toSet() + "operit-resource.json"
        require(stage.walkTopDown().filter(File::isFile).map { it.relativeTo(stage).invariantSeparatorsPath }.toSet() == expected) { "资源包文件清单不匹配" }
        require(pack.files.all { ModelArchive.verified(stage, it, checkpoint) }) { "资源包校验失败" }
        return pack
    }
    internal fun installExtracted(context: Context, id: String, stage: File, checkpoint: () -> Unit) {
        val pack = validateExtracted(context, id, stage, checkpoint)
        checkpoint()
        val destination = directoryFor(context, pack)
        pack.files.forEach { entry ->
            val file = stage.containedFile(entry.path)
            if (entry.executable) check(file.setExecutable(true, true))
            if (id == SPEECH_ENGINE) check(file.setReadOnly())
        }
        val exclusiveDirectory = if (pack.extension != null) destination.parentFile!! else destination
        ModelArchive.install(stage, destination, exclusiveDirectory, checkpoint)
        ResourcePackInstallations.remember(pack, destination.parentFile!!)
        ResourcePackInstallations.cleanupObsolete(spec(context, id), history(context), { directoryFor(context, it) }, checkpoint)
    }
    suspend fun exportArchive(context: Context, id: String, output: OutputStream, progress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        locks.getOrPut(id) { Mutex() }.withLock {
            check(!isBundled(context, id)) { "包内资源不提供备份入口" }
            val root = requireDirectory(context, id)
            val usage = LocalModelRuntimeRegistry.acquire(root) ?: error("资源正在移除")
            val job = currentCoroutineContext()
            try { output.use { ModelArchive.export(root, it, { job.ensureActive() }, progress) } } finally { usage.close() }
        }
    }

    suspend fun delete(context: Context, id: String, onProgress: ((Long, Long) -> Unit)? = null) = withContext(Dispatchers.IO) {
        locks.getOrPut(id) { Mutex() }.withLock {
            check(!isBundled(context, id)) { "不能移除包内资源" }
            // 用户明确删除时清理整个资源组，避免较早安装再次被识别出来。
            val groupDirectory = directoryFor(context, spec(context, id)).parentFile!!
            check(LocalModelRuntimeRegistry.deleteIfUnused(groupDirectory, onProgress) == LocalModelDeleteOutcome.DELETED) { "资源正在使用或移除失败" }
        }
    }

    fun copyTemplate(context: Context, template: String, destination: File) {
        val id = "template-${storageSegment(template)}"
        if (isBundled(context, id)) {
            spec(context, id).files.forEach { entry ->
                val path = entry.path.split('/').joinToString("/") { if (it == "gitignore") ".gitignore" else it }
                val target = destination.containedFile(path)
                target.parentFile!!.mkdirs()
                context.assets.open("templates/$template/${entry.path.split('/').joinToString("/") { if (it == ".gitignore") "gitignore" else it }}").use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                if (entry.executable) check(target.setExecutable(true, true))
            }
            return
        }
        val root = requireDirectory(context, id)
        val usage = LocalModelRuntimeRegistry.acquire(root) ?: error("工程模板正在移除")
        try {
            spec(context, id).files.forEach { entry ->
                val path = entry.path.split('/').joinToString("/") { if (it == "gitignore") ".gitignore" else it }
                val target = destination.containedFile(path)
                target.parentFile!!.mkdirs()
                root.containedFile(entry.path).copyTo(target, overwrite = true)
                if (entry.executable) target.setExecutable(true, true)
            }
        } finally { usage.close() }
    }

    /** 只加载当前 APK 固定校验清单中的库，独立 SONAME 隔离宿主的 ONNX Runtime。 */
    fun loadSpeechEngine(context: Context) = synchronized(engineLock) {
        if (engineLoaded) return@synchronized
        engineFailure?.let { throw IllegalStateException("语音引擎加载失败，请重启应用后重试", it) }
        val pack = spec(context, SPEECH_ENGINE)
        val root = requireDirectory(context, SPEECH_ENGINE)
        require(pack.loadOrder.isNotEmpty())
        engineUsage = LocalModelRuntimeRegistry.acquire(root) ?: error("语音引擎正在移除")
        try {
            pack.loadOrder.forEach { System.load(root.containedFile(it).absolutePath) }
            engineLoaded = true
        } catch (failure: LinkageError) {
            engineFailure = failure
            throw IllegalStateException("无法加载本地语音引擎", failure)
        }
    }
}
