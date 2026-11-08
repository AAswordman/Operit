package com.ai.assistance.operit.data.resources.models

import kotlinx.serialization.Serializable
import java.io.File
import java.net.URI

@Serializable
enum class SpeechModelPurpose { STT, TTS }

@Serializable
enum class ModelDownloadSource { HUGGING_FACE, MODELSCOPE, HF_MIRROR }

@Serializable
data class ModelFile(
    val path: String,
    val bytes: Long,
    val sha256: String,
    val url: String = "",
    val executable: Boolean = false,
)

@Serializable
data class ModelDriver(
    val type: String,
    val model: String = "",
    val encoder: String = "",
    val decoder: String = "",
    val joiner: String = "",
    val tokens: String = "",
    val lexicon: String = "",
    val vocoder: String = "",
    val voices: String = "",
    val dataDir: String = "",
    val speakers: Int = 1,
    val ruleFsts: List<String> = emptyList(),
    val ruleFars: List<String> = emptyList(),
) {
    fun requiredPaths(): List<String> = when (type) {
        "transducer" -> listOf(encoder, decoder, joiner, tokens)
        "vits" -> listOf(model, tokens, lexicon) + ruleFsts + ruleFars
        "matcha" -> listOf(model, vocoder, tokens, lexicon) + ruleFsts + ruleFars
        "kitten" -> listOf(model, voices, tokens, dataDir)
        else -> error("不支持的模型驱动：$type")
    }
}

@Serializable
data class SpeechModelManifest(
    val id: String,
    val version: String,
    val name: String,
    val description: String = "",
    val purpose: SpeechModelPurpose,
    val engine: String = "sherpa_onnx",
    val license: String = "",
    val languages: List<String> = emptyList(),
    val hfRepository: String = "",
    val modelScopeRepository: String = "",
    val modelScopeRevision: String = "master",
    val modelScopePrefix: String = "",
    val driver: ModelDriver,
    val files: List<ModelFile>,
    val archive: ModelFile? = null,
    val imported: Boolean = false,
) {
    val key: String get() = "$id@$version"
    val bytes: Long get() = archive?.bytes ?: files.sumOf { it.bytes }

    fun validate() {
        storageSegment(id)
        storageSegment(version)
        require(engine == "sherpa_onnx") { "模型推理引擎不受支持" }
        require(name.isNotBlank() && name.length <= 256 && files.isNotEmpty() && files.size <= 100_000) { "模型清单不完整" }
        require(files.map { it.path }.distinct().size == files.size) { "模型清单包含重复文件" }
        require((purpose == SpeechModelPurpose.STT) == (driver.type == "transducer")) { "模型用途与驱动不一致" }
        files.forEach {
            validateRelativePath(it.path)
            require(it.bytes in 0..ModelArchive.MAX_EXPANDED_BYTES && it.sha256.matches(Regex("[0-9a-f]{64}"))) { "模型校验信息无效" }
            require(it.url.isEmpty() || (URI(it.url).scheme == "https" && URI(it.url).host != null && URI(it.url).userInfo == null)) { "模型下载地址必须是 HTTPS" }
        }
        require(files.none { it.path == "operit-model.json" || it.path.startsWith("operit-engine/") })
        driver.requiredPaths().forEach { path ->
            validateRelativePath(path)
            require(files.any { it.bytes > 0 && (it.path == path || it.path.startsWith("$path/")) }) { "模型缺少必要文件：$path" }
        }
        require(files.sumOf(ModelFile::bytes) <= ModelArchive.MAX_EXPANDED_BYTES)
        require(driver.speakers in 1..10_000)
        archive?.let {
            validateRelativePath(it.path)
            require(it.bytes in 1..ModelArchive.MAX_EXPANDED_BYTES && it.sha256.matches(Regex("[0-9a-f]{64}")))
            require(it.url.isEmpty() || (URI(it.url).scheme == "https" && URI(it.url).host != null && URI(it.url).userInfo == null))
        }
    }

    fun relativeInstallPath(): String {
        validate()
        return "runtime/models/local/${purpose.name.lowercase()}/$engine/$id/$version"
    }
}

@Serializable
data class SpeechModelCatalog(val schemaVersion: Int, val models: List<SpeechModelManifest>)

internal fun storageSegment(value: String): String = value.also {
    require(it.isNotBlank() && it != "." && it != ".." && it.matches(Regex("[A-Za-z0-9_.-]+"))) { "模型目录名称无效：$it" }
}

internal fun validateRelativePath(path: String) {
    require(path.isNotBlank() && !path.startsWith('/') && !path.contains('\\') && !path.contains(':') && !path.contains('\u0000')) { "文件路径无效" }
    require(path.split('/').none { it.isEmpty() || it == "." || it == ".." }) { "文件路径不能越过模型目录" }
}

internal fun File.containedFile(path: String): File {
    validateRelativePath(path)
    val root = canonicalFile
    return File(root, path).canonicalFile.also { require(it.path.startsWith(root.path + File.separator)) { "文件路径越界" } }
}

/** 只解析明确支持的模型社区地址，不把不同社区的仓库名当作同一地址替换。 */
data class ModelRepository(val source: ModelDownloadSource, val repository: String, val revision: String?) {
    companion object {
        fun parse(input: String, source: ModelDownloadSource, version: String = ""): ModelRepository {
            val trimmed = input.trim()
            val uri = if (trimmed.contains("://")) URI(trimmed) else null
            val selected = if (uri != null) when (uri.host?.lowercase()) {
                "huggingface.co", "www.huggingface.co" -> ModelDownloadSource.HUGGING_FACE
                "hf-mirror.com" -> ModelDownloadSource.HF_MIRROR
                "modelscope.cn", "www.modelscope.cn" -> ModelDownloadSource.MODELSCOPE
                else -> error("仅支持 Hugging Face、ModelScope 与 HF-Mirror 仓库")
            } else source
            if (uri != null) require(uri.scheme == "https" && uri.userInfo == null) { "请使用 HTTPS 模型仓库地址" }
            var parts = (uri?.path ?: trimmed).trim('/').split('/')
            if (selected == ModelDownloadSource.MODELSCOPE && parts.firstOrNull() == "models") parts = parts.drop(1)
            require(parts.size >= 2 && parts.take(2).all { it.matches(Regex("[A-Za-z0-9_.-]+")) && it != "." && it != ".." }) { "请输入 owner/repo 或完整仓库网址" }
            val embeddedVersion = if (parts.size > 3 && parts[2] in setOf("tree", "resolve")) parts[3] else null
            val revision = version.trim().takeIf(String::isNotEmpty) ?: embeddedVersion
            require(revision == null || (revision.isNotBlank() && !revision.contains('\u0000'))) { "模型版本无效" }
            return ModelRepository(selected, parts.take(2).joinToString("/"), revision)
        }
    }
}
