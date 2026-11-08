package com.ai.assistance.operit.data.resources.models

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl

/** 已核对的目录使用固定版本；自定义仓库必须提供可校验的 Operit 模型清单。 */
class SpeechModelRepository(context: Context) {
    private val store = SpeechModels(context)
    suspend fun resolve(input: String, source: ModelDownloadSource, revision: String, purpose: SpeechModelPurpose, displayName: String): SpeechModelManifest = withContext(Dispatchers.IO) {
        val repo = ModelRepository.parse(input, source, revision)
        val known = store.catalog().filter { model ->
            model.purpose == purpose && when (repo.source) {
                ModelDownloadSource.MODELSCOPE -> model.modelScopeRepository == repo.repository && (repo.revision == null || repo.revision == model.modelScopeRevision)
                else -> model.hfRepository == repo.repository && (repo.revision == null || repo.revision == model.version)
            }
        }
        require(known.size <= 1) { "仓库中匹配到多个目录模型，请从目录选择" }
        if (known.size == 1) return@withContext known.single().let { if (displayName.isBlank()) it else it.copy(name = displayName.trim()) }
        fun url(path: String): String {
            validateRelativePath(path)
            val base = when (repo.source) {
                ModelDownloadSource.HUGGING_FACE -> "https://huggingface.co/${repo.repository}/resolve/"
                ModelDownloadSource.HF_MIRROR -> "https://hf-mirror.com/${repo.repository}/resolve/"
                ModelDownloadSource.MODELSCOPE -> "https://modelscope.cn/models/${repo.repository}/resolve/"
            }.toHttpUrl().newBuilder().addPathSegment(repo.revision ?: if (repo.source == ModelDownloadSource.MODELSCOPE) "master" else "main")
            path.split('/').forEach { base.addPathSegment(it) }
            return base.build().toString()
        }
        val manifest = Json { ignoreUnknownKeys = true }.decodeFromString<SpeechModelManifest>(ResourceTransfer.readText(url(SpeechModels.MANIFEST)))
        manifest.validate()
        require(manifest.purpose == purpose) { "仓库模型与所选用途不一致" }
        manifest.copy(
            name = displayName.trim().ifEmpty { manifest.name }, imported = true,
            files = manifest.files.map { file -> if (file.url.isNotBlank()) file else file.copy(url = url(file.path)) },
            archive = manifest.archive?.let { archive -> if (archive.url.isNotBlank()) archive else archive.copy(url = url(archive.path)) },
        ).also(SpeechModelManifest::validate)
    }
}
