package com.ai.assistance.operit.data.resources.models

import android.content.Context
import com.ai.assistance.operit.data.resources.packs.PackResources
import com.ai.assistance.operit.data.storage.LocalModelRuntimeRegistry
import com.ai.assistance.operit.data.storage.LocalModelDeleteOutcome
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class InstalledSpeechModels(val models: List<SpeechModelManifest>, val errors: List<String>)
data class ModelImportPreview(val model: SpeechModelManifest, val stage: File, val root: File)

/** 安装记录与模型在同一目录提交；旧 NCNN 目录继续由 OfflineResources 管理。 */
class SpeechModels(context: Context) {
    private val context = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    companion object {
        const val MANIFEST = "operit-model.json"
        private val locks = ConcurrentHashMap<String, Mutex>()
        private val verifiedStamps = ConcurrentHashMap<String, List<String>>()
    }

    fun catalog(): List<SpeechModelManifest> = context.assets.open("local-speech-models.json").bufferedReader().use {
        json.decodeFromString<SpeechModelCatalog>(it.readText()).also { data -> require(data.schemaVersion == 1) }.models
    }.onEach(SpeechModelManifest::validate)

    fun directory(model: SpeechModelManifest): File = File(context.filesDir, model.relativeInstallPath())
    private fun downloadDirectory(model: SpeechModelManifest): File = File(context.filesDir, "runtime/downloads/${storageSegment(model.id)}/${storageSegment(model.version)}")

    fun installed(): InstalledSpeechModels {
        val models = mutableListOf<SpeechModelManifest>()
        val errors = mutableListOf<String>()
        File(context.filesDir, "runtime/models/local").walkTopDown().maxDepth(5).filter { it.isFile && it.name == MANIFEST }.forEach { record ->
            try {
                val model = json.decodeFromString<SpeechModelManifest>(record.readText())
                model.validate()
                require(record.parentFile.canonicalFile == directory(model).canonicalFile) { "安装目录与模型记录不一致" }
                models += model
            } catch (failure: Exception) {
                AppLogger.e("SpeechModels", "读取模型安装记录失败：${record.path}", failure)
                errors += "${record.path}：${failure.localizedMessage}"
            }
        }
        return InstalledSpeechModels(models, errors)
    }

    fun find(key: String): SpeechModelManifest = installed().models.firstOrNull { it.key == key } ?: error("已选择的本地模型未安装，请到离线资源准备模型")
    fun isInstalled(model: SpeechModelManifest, checkpoint: () -> Unit = {}): Boolean {
        val root = directory(model)
        val record = File(root, MANIFEST)
        if (!record.isFile) { verifiedStamps.remove(root.path); return false }
        val signature = model.files.map { spec ->
            val file = root.containedFile(spec.path)
            "${spec.path}:${spec.sha256}:${file.length()}:${file.lastModified()}"
        } + "${record.length()}:${record.lastModified()}"
        if (verifiedStamps[root.path] == signature) return true
        val metadata = try {
            json.decodeFromString<SpeechModelManifest>(record.readText()).also(SpeechModelManifest::validate)
        } catch (failure: Exception) {
            AppLogger.e("SpeechModels", "安装记录损坏：${record.path}", failure)
            verifiedStamps.remove(root.path)
            return false
        }
        val ready = metadata.key == model.key && model.files.all { ModelArchive.verified(root, it, checkpoint) }
        if (ready) verifiedStamps[root.path] = signature else verifiedStamps.remove(root.path)
        return ready
    }
    fun hasData(model: SpeechModelManifest): Boolean = directory(model).exists() || downloadDirectory(model).exists()

    suspend fun download(model: SpeechModelManifest, source: ModelDownloadSource, phase: (ResourceDownloadPhase) -> Unit = {}, progress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        model.validate()
        locks.getOrPut(model.key) { Mutex() }.withLock {
            val engineBytes = if (PackResources.isReady(context, PackResources.SPEECH_ENGINE)) 0L else PackResources.spec(context, PackResources.SPEECH_ENGINE).archive.bytes
            val total = engineBytes + model.bytes
            if (engineBytes > 0) PackResources.download(context, PackResources.SPEECH_ENGINE, phase) { done, _ -> progress(done, total) }
            if (isInstalled(model)) { progress(total, total); return@withLock }
            phase(ResourceDownloadPhase.CONNECTING)
            val cache = downloadDirectory(model)
            cache.mkdirs()
            val job = currentCoroutineContext()
            val checkpoint = { job.ensureActive() }
            val archive = model.archive
            if (archive != null) {
                val compressed = cache.containedFile(archive.path)
                if (!ModelArchive.verified(cache, archive, checkpoint)) {
                    progress(engineBytes, total)
                    phase(ResourceDownloadPhase.DOWNLOADING)
                    ResourceTransfer.download(archive.url, archive, compressed) { progress(engineBytes + it, total) }
                }
                phase(ResourceDownloadPhase.INSTALLING)
                val extracted = File(context.filesDir, "runtime/staging/model-${UUID.randomUUID()}")
                try {
                    compressed.inputStream().use { ModelArchive.unpack(it, archive.path, extracted, checkpoint) }
                    // 归档整体已校验；把运行所需的完整词典树纳入以后每次检查与备份。
                    val root = (listOf(extracted) + extracted.listFiles().orEmpty().filter(File::isDirectory)).firstOrNull { candidate -> model.files.all { ModelArchive.verified(candidate, it, checkpoint) } } ?: error("归档缺少模型文件")
                    val installed = model.copy(files = describeFiles(root, checkpoint), archive = null)
                    commit(installed, root, checkpoint)
                    cache.deleteRecursively()
                } finally { extracted.deleteRecursively() }
            } else {
                val needed = model.files.filterNot { ModelArchive.verified(cache, it, checkpoint) }
                require(cache.usableSpace >= needed.sumOf(ModelFile::bytes) + 1024 * 1024) { "设备空间不足" }
                var completed = model.files.sumOf(ModelFile::bytes) - needed.sumOf(ModelFile::bytes)
                progress(engineBytes + completed, total)
                phase(ResourceDownloadPhase.DOWNLOADING)
                needed.forEach { file ->
                    ResourceTransfer.download(ResourceTransfer.modelUrl(model, file, source), file, cache.containedFile(file.path)) { progress(engineBytes + completed + it, total) }
                    completed += file.bytes
                }
                phase(ResourceDownloadPhase.INSTALLING)
                commit(model, cache, checkpoint)
            }
            progress(total, total)
        }
    }

    suspend fun preview(input: InputStream, name: String, purpose: SpeechModelPurpose, displayName: String, progress: (Long, Long) -> Unit): ModelImportPreview = withContext(Dispatchers.IO) {
        val stage = File(context.filesDir, "runtime/staging/import-${UUID.randomUUID()}")
        val job = currentCoroutineContext()
        val checkpoint = { job.ensureActive() }
        try {
            input.use { ModelArchive.unpack(it, name, stage, checkpoint) { bytes -> progress(bytes, 0) } }
            val manifests = stage.walkTopDown().maxDepth(5).filter { it.isFile && it.name == MANIFEST }.toList()
            require(manifests.size <= 1) { "资源包包含多份模型记录，请分别导入" }
            val known = catalog().filter { it.purpose == purpose }
            val roots = listOf(stage) + stage.walkTopDown().maxDepth(3).filter { it.isDirectory && it != stage && it.name != "operit-engine" }.toList()
            var root: File? = null
            var model: SpeechModelManifest? = null
            if (manifests.isNotEmpty()) {
                root = manifests.single().parentFile
                model = json.decodeFromString<SpeechModelManifest>(manifests.single().readText())
                require(model.purpose == purpose) { "模型用途与所选用途不一致" }
            } else {
                outer@ for (candidate in roots) {
                    for (entry in known) {
                        if (entry.files.all { ModelArchive.verified(candidate, it, checkpoint) }) { root = candidate; model = entry.copy(files = describeFiles(candidate, checkpoint), archive = null, imported = true); break@outer }
                    }
                }
                if (model == null) {
                    val candidate = roots.firstOrNull { ModelStructure.probe(it, purpose) != null } ?: error("无法识别模型结构，请提供支持的 Sherpa 模型包或 operit-model.json")
                    val driver = checkNotNull(ModelStructure.probe(candidate, purpose))
                    val files = describeFiles(candidate, checkpoint)
                    val identity = MessageDigest.getInstance("SHA-256").digest(files.joinToString { it.path + it.sha256 }.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
                    root = candidate
                    model = SpeechModelManifest("imported-${identity.take(20)}", identity.take(32), name.substringBeforeLast('.'), purpose = purpose, driver = driver, files = files, imported = true)
                }
            }
            val selected = checkNotNull(model).let { if (displayName.isNotBlank()) it.copy(name = displayName.trim()) else it }
            selected.validate()
            val selectedRoot = checkNotNull(root)
            require(selected.files.all { ModelArchive.verified(selectedRoot, it, checkpoint) }) { "模型文件与安装记录校验不一致" }
            val engine = File(selectedRoot, "operit-engine")
            if (engine.isDirectory) PackResources.validateExtracted(context, PackResources.SPEECH_ENGINE, engine, checkpoint)
            ModelImportPreview(selected, stage, selectedRoot)
        } catch (failure: Throwable) { stage.deleteRecursively(); throw failure }
    }

    suspend fun install(preview: ModelImportPreview, progress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        locks.getOrPut(preview.model.key) { Mutex() }.withLock {
            val job = currentCoroutineContext()
            val engine = File(preview.root, "operit-engine")
            if (engine.isDirectory) {
                if (!PackResources.isReady(context, PackResources.SPEECH_ENGINE)) PackResources.restoreExtracted(context, PackResources.SPEECH_ENGINE, engine) { job.ensureActive() }
                check(engine.deleteRecursively() || !engine.exists()) { "无法清理已经恢复的引擎副本" }
            }
            commit(preview.model, preview.root) { job.ensureActive() }
            preview.stage.deleteRecursively()
            progress(preview.model.files.sumOf(ModelFile::bytes), preview.model.files.sumOf(ModelFile::bytes))
        }
    }

    private fun commit(model: SpeechModelManifest, source: File, checkpoint: () -> Unit) {
        model.validate()
        require(model.files.all { ModelArchive.verified(source, it, checkpoint) }) { "模型校验失败，未修改已有安装" }
        val destination = directory(model)
        checkpoint()
        File(source, MANIFEST).writeText(json.encodeToString(model))
        ModelArchive.install(source, destination, checkpoint)
        verifiedStamps.remove(destination.path)
    }

    suspend fun export(model: SpeechModelManifest, output: OutputStream, progress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        locks.getOrPut(model.key) { Mutex() }.withLock {
            require(isInstalled(model)) { "模型未完整安装" }
            val root = directory(model)
            val usage = LocalModelRuntimeRegistry.acquire(root) ?: error("模型正在移除")
            val engine = if (PackResources.isReady(context, PackResources.SPEECH_ENGINE)) PackResources.directory(context, PackResources.SPEECH_ENGINE) else null
            var engineUsage: com.ai.assistance.operit.data.storage.LocalModelUsageHandle? = null
            val job = currentCoroutineContext()
            try {
                engineUsage = engine?.let { LocalModelRuntimeRegistry.acquire(it) ?: error("引擎正在移除") }
                output.use { ModelArchive.export(root, it, { job.ensureActive() }, progress, if (engine != null) mapOf("operit-engine" to engine) else emptyMap()) }
            } finally { usage.close(); engineUsage?.close() }
        }
    }

    suspend fun delete(model: SpeechModelManifest) = withContext(Dispatchers.IO) {
        locks.getOrPut(model.key) { Mutex() }.withLock {
            check(LocalModelRuntimeRegistry.deleteIfUnused(directory(model)) == LocalModelDeleteOutcome.DELETED) { "模型正在使用或无法移除" }
            downloadDirectory(model).deleteRecursively()
        }
    }

    private fun describeFiles(root: File, checkpoint: () -> Unit): List<ModelFile> = root.walkTopDown().filter { it.isFile && it.name != MANIFEST && !it.relativeTo(root).invariantSeparatorsPath.startsWith("operit-engine/") }.map { file ->
        checkpoint()
        ModelFile(file.relativeTo(root).invariantSeparatorsPath, file.length(), ModelArchive.digest(file, checkpoint))
    }.sortedBy(ModelFile::path).toList()
}

/** 文件结构识别只声明支持的协议，不把下载完成当作推理成功。 */
internal object ModelStructure {
    fun probe(root: File, purpose: SpeechModelPurpose): ModelDriver? {
        val names = root.listFiles()?.filter(File::isFile)?.map(File::getName).orEmpty()
        if ("tokens.txt" !in names) return null
        if (purpose == SpeechModelPurpose.STT) {
            val encoder = names.filter { it.startsWith("encoder") && it.endsWith(".onnx") }
            val decoder = names.filter { it.startsWith("decoder") && it.endsWith(".onnx") }
            val joiner = names.filter { it.startsWith("joiner") && it.endsWith(".onnx") }
            if (encoder.size != 1 || decoder.size != 1 || joiner.size != 1) return null
            return ModelDriver("transducer", encoder = encoder.single(), decoder = decoder.single(), joiner = joiner.single(), tokens = "tokens.txt")
        }
        val models = names.filter { it.endsWith(".onnx") && !it.contains("voco", true) }
        if (models.size != 1) return null
        if ("voices.bin" in names && File(root, "espeak-ng-data").isDirectory) return ModelDriver("kitten", model = models.single(), tokens = "tokens.txt", voices = "voices.bin", dataDir = "espeak-ng-data")
        if ("lexicon.txt" !in names) return null
        val vocoders = names.filter { it.endsWith(".onnx") && it.contains("voco", true) }
        val rules = names.filter { it.endsWith(".fst") }.sorted()
        if (vocoders.size == 1) return ModelDriver("matcha", model = models.single(), tokens = "tokens.txt", lexicon = "lexicon.txt", vocoder = vocoders.single(), ruleFsts = rules)
        if (vocoders.isNotEmpty()) return null
        return ModelDriver("vits", model = models.single(), tokens = "tokens.txt", lexicon = "lexicon.txt", ruleFsts = rules)
    }
}
