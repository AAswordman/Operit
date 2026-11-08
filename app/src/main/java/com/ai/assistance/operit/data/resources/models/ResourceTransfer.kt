package com.ai.assistance.operit.data.resources.models

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl

internal object ResourceTransfer {
    private val client = OkHttpClient.Builder().connectTimeout(25, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).followSslRedirects(false).build()

    suspend fun download(url: String, spec: ModelFile, target: File, progress: (Long) -> Unit) = coroutineScope {
        val jobContext = currentCoroutineContext()
        val call = client.newCall(Request.Builder().url(url).build())
        val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        val partial = File(target.parentFile, target.name + ".part")
        try {
            target.parentFile!!.mkdirs()
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}：$url")
                val body = response.body ?: throw IOException("下载响应为空")
                body.byteStream().use { ModelArchive.write(it, partial, spec, { jobContext.ensureActive() }, progress) }
            }
            jobContext.ensureActive()
            if (target.exists()) check(target.delete())
            ModelArchive.move(partial, target)
        } finally {
            partial.delete()
            cancellation.cancelAndJoin()
        }
    }

    suspend fun readText(url: String, maxBytes: Long = 8L * 1024 * 1024): String = coroutineScope {
        val jobContext = currentCoroutineContext()
        val call = client.newCall(Request.Builder().url(url).build())
        val cancellation = launch(start = CoroutineStart.UNDISPATCHED) { try { awaitCancellation() } finally { call.cancel() } }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}：$url")
                val body = response.body ?: throw IOException("目录响应为空")
                body.byteStream().use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        jobContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(count <= maxBytes - output.size()) { "目录信息过大" }
                        output.write(buffer, 0, count)
                    }
                    output.toString("UTF-8")
                }
            }
        } finally { cancellation.cancelAndJoin() }
    }

    fun modelUrl(model: SpeechModelManifest, file: ModelFile, source: ModelDownloadSource): String {
        if (file.url.isNotBlank()) return file.url
        val base = when (source) {
            ModelDownloadSource.HUGGING_FACE -> "https://huggingface.co/${model.hfRepository}/resolve/".toHttpUrl().newBuilder().addPathSegment(model.version)
            ModelDownloadSource.HF_MIRROR -> "https://hf-mirror.com/${model.hfRepository}/resolve/".toHttpUrl().newBuilder().addPathSegment(model.version)
            ModelDownloadSource.MODELSCOPE -> {
                require(model.modelScopeRepository.isNotBlank()) { "这个模型没有 ModelScope 来源，请选择支持的下载源" }
                "https://modelscope.cn/models/${model.modelScopeRepository}/resolve/".toHttpUrl().newBuilder().addPathSegment(model.modelScopeRevision).apply {
                    model.modelScopePrefix.trim('/').takeIf(String::isNotEmpty)?.split('/')?.forEach { addPathSegment(it) }
                }
            }
        }
        validateRelativePath(file.path)
        file.path.split('/').forEach { base.addPathSegment(it) }
        return base.build().toString()
    }
}
