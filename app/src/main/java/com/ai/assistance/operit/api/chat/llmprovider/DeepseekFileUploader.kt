package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.util.AppLogger
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

/** DeepSeek Files API 返回的文件对象（4 个端点共用） */
internal data class DeepseekFileInfo(
    val id: String,
    val filename: String,
    val bytes: Long,
    val createdAt: Long,
    val purpose: String
)

/**
 * DeepSeek Files API 客户端，供 Chat Completions 与 Responses 两种协议共用。
 *
 * 覆盖官方 4 个端点：
 * - POST /files            上传图片（换取 file_id，用于 chat 请求引用）
 * - GET  /files            列出文件（用于跨会话同图复用 file_id，避免重复上传堆积）
 * - GET  /files/{id}       查询单个文件信息
 * - DELETE /files/{id}     删除文件（保留给后续清理策略挂载）
 *
 * 上传文件名采用图片内容 SHA-256 前缀命名，跨会话恒定：
 * 图片 id 是每次添加随机生成的 UUID（见 ImagePoolManager），不能作为稳定键，
 * 内容寻址命名使"列出->按文件名匹配->复用 file_id"在任意会话下都成立。
 * 上传失败的图片不缓存，由调用方回退为 base64 内嵌。
 */
internal class DeepseekFileUploader(
    private val apiEndpoint: String,
    private val apiKeyProvider: ApiKeyProvider,
    private val httpClient: OkHttpClient,
    private val customHeaders: Map<String, String>
) {
    /** 图片 id -> DeepSeek Files API file_id 的会话级缓存（避免同一图片重复上传） */
    private val uploadedImageFileIds = ConcurrentHashMap<String, String>()
    /** DeepSeek Files API 支持上传的图片 MIME 类型 */
    private val supportedUploadMimeTypes = setOf("image/jpeg", "image/png", "image/gif", "image/webp")
    /** DeepSeek Files API 单文件上传上限（64 MiB） */
    private val maxUploadBytes = 64L * 1024L * 1024L

    /**
     * 扫描消息历史中的图片，优先复用远端已存在的同名文件（跨会话免重传），
     * 否则上传至 DeepSeek Files API 换取 file_id。
     * 上传失败的图片保持原样，构建请求时回退为 base64 内嵌（见各 Provider 的 buildImageContentPart）。
     * @param supportsVision 当前配置是否开启"直接图片处理"；关闭时不触发上传
     * @param logTag 日志标签（区分 Chat Completions / Responses 两条调用线）
     */
    suspend fun prepareMedia(supportsVision: Boolean, chatHistory: List<PromptTurn>, logTag: String) {
        if (!supportsVision) return
        val seenIds = mutableSetOf<String>()
        val imageLinks = mutableListOf<ImageLink>()
        for (turn in chatHistory) {
            MediaLinkParser.extractImageLinks(turn.content).forEach { link ->
                if (seenIds.add(link.id)) imageLinks.add(link)
            }
        }
        // 候选上传项：本地缓存未命中 + 通过格式/大小校验
        val candidates = mutableListOf<Triple<ImageLink, ByteArray, String>>()
        for (link in imageLinks) {
            if (uploadedImageFileIds.containsKey(link.id)) continue
            if (link.mimeType.lowercase() !in supportedUploadMimeTypes) continue
            val bytes =
                runCatching { Base64.getDecoder().decode(link.base64Data) }.getOrNull() ?: continue
            if (bytes.isEmpty() || bytes.size > maxUploadBytes) continue
            val contentName = "image_${sha256Prefix(bytes)}.${extensionForMime(link.mimeType)}"
            candidates.add(Triple(link, bytes, contentName))
        }
        if (candidates.isEmpty()) return
        // 列出远端文件，按文件名匹配复用已有 file_id（列出失败则全部走直传）
        val remoteByName = listFiles()?.associate { it.filename to it.id } ?: emptyMap()
        for ((link, bytes, contentName) in candidates) {
            val existingFileId = remoteByName[contentName]
            if (existingFileId != null) {
                uploadedImageFileIds[link.id] = existingFileId
                AppLogger.d(logTag, "图片 ${link.id} 已在 DeepSeek Files API 存在（$existingFileId），复用免重传")
                continue
            }
            val fileId = uploadImageToFilesApi(bytes, link.mimeType, contentName)
            if (fileId != null) {
                uploadedImageFileIds[link.id] = fileId
                AppLogger.d(logTag, "图片 ${link.id} 已上传至 DeepSeek Files API: $fileId")
            }
        }
    }

    /** 已上传图片对应的 file_id；未上传返回 null（调用方回退 base64） */
    fun fileIdFor(linkId: String): String? = uploadedImageFileIds[linkId]

    /** 列出当前 API key 下的所有文件（自动分页）；失败返回 null */
    suspend fun listFiles(): List<DeepseekFileInfo>? {
        val filesEndpoint = buildFilesEndpoint() ?: return null
        val result = mutableListOf<DeepseekFileInfo>()
        var after: String? = null
        while (true) {
            val base = "${filesEndpoint}?purpose=user_data&limit=1000&order=desc"
            val pageUrl = if (after == null) base else "$base&after=${URLEncoder.encode(after, "UTF-8")}"
            val page = runCatching {
                val builder = Request.Builder().url(pageUrl).get()
                applyAuthAndCustomHeaders(builder)
                withContext(Dispatchers.IO) { httpClient.newCall(builder.build()).execute() }.use { response ->
                    if (!response.isSuccessful) {
                        AppLogger.w(
                            "DeepseekFileUploader",
                            "Files API 列表失败 HTTP ${response.code}: ${response.body?.string()?.take(200)}"
                        )
                        return@use null
                    }
                    response.body?.string()
                }?.let { parseFileListPage(it) }
            }.getOrElse { e ->
                AppLogger.w("DeepseekFileUploader", "Files API 列表异常: ${e.message}")
                null
            } ?: return null
            result.addAll(page.files)
            after = if (page.hasMore) page.lastId else null
            if (after == null) break
        }
        return result
    }

    /** 查询单个文件信息；失败或不存在返回 null */
    suspend fun retrieveFile(fileId: String): DeepseekFileInfo? {
        val filesEndpoint = buildFilesEndpoint() ?: return null
        val url = "$filesEndpoint/${URLEncoder.encode(fileId, "UTF-8")}"
        return runCatching {
            val builder = Request.Builder().url(url).get()
            applyAuthAndCustomHeaders(builder)
            withContext(Dispatchers.IO) { httpClient.newCall(builder.build()).execute() }.use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w(
                        "DeepseekFileUploader",
                        "Files API 查询失败 HTTP ${response.code}: ${response.body?.string()?.take(200)}"
                    )
                    return@use null
                }
                response.body?.string()?.let { parseFileObject(JSONObject(it)) }
            }
        }.getOrElse { e ->
            AppLogger.w("DeepseekFileUploader", "Files API 查询异常: ${e.message}")
            null
        }
    }

    /** 删除单个文件；成功返回 true，失败返回 false */
    suspend fun deleteFile(fileId: String): Boolean {
        val filesEndpoint = buildFilesEndpoint() ?: return false
        val url = "$filesEndpoint/${URLEncoder.encode(fileId, "UTF-8")}"
        return runCatching {
            val builder = Request.Builder().url(url).delete()
            applyAuthAndCustomHeaders(builder)
            withContext(Dispatchers.IO) { httpClient.newCall(builder.build()).execute() }.use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w(
                        "DeepseekFileUploader",
                        "Files API 删除失败 HTTP ${response.code}: ${response.body?.string()?.take(200)}"
                    )
                    return@use false
                }
                val json = response.body?.string()?.let { runCatching { JSONObject(it) }.getOrNull() }
                json?.optBoolean("deleted", false) == true
            }
        }.getOrElse { e ->
            AppLogger.w("DeepseekFileUploader", "Files API 删除异常: ${e.message}")
            false
        }
    }

    /** 上传单张图片到 DeepSeek Files API，返回 file_id；失败返回 null */
    private suspend fun uploadImageToFilesApi(
        bytes: ByteArray,
        mimeType: String,
        contentName: String
    ): String? {
        val filesEndpoint = buildFilesEndpoint() ?: return null
        return runCatching {
            val body =
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("purpose", "user_data")
                    .addFormDataPart(
                        "file",
                        contentName,
                        RequestBody.create(mimeType.toMediaType(), bytes)
                    )
                    .build()
            val builder = Request.Builder().url(filesEndpoint).post(body)
            applyAuthAndCustomHeaders(builder)
            withContext(Dispatchers.IO) { httpClient.newCall(builder.build()).execute() }.use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w(
                        "DeepseekFileUploader",
                        "Files API 上传失败 HTTP ${response.code}: ${response.body?.string()?.take(200)}"
                    )
                    return@use null
                }
                val json = JSONObject(response.body?.string().orEmpty())
                json.optString("id").takeIf { it.startsWith("file-") }
            }
        }.getOrElse { e ->
            AppLogger.w("DeepseekFileUploader", "Files API 上传异常: ${e.message}")
            null
        }
    }

    /** 给请求附加认证头与自定义头（排除会破坏 multipart 边界的 Content-Type 覆写） */
    private suspend fun applyAuthAndCustomHeaders(builder: Request.Builder) {
        val apiKey = apiKeyProvider.getApiKey().trim()
        if (apiKey.isNotEmpty()) {
            builder.addHeader("Authorization", "Bearer $apiKey")
        }
        customHeaders.forEach { (key, value) ->
            if (!key.equals("Content-Type", ignoreCase = true)) builder.addHeader(key, value)
        }
    }

    /** 解析列表端点的一页响应；失败返回 null */
    private fun parseFileListPage(body: String): FileListPage? {
        return runCatching {
            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: return null
            val files = mutableListOf<DeepseekFileInfo>()
            for (i in 0 until data.length()) {
                data.optJSONObject(i)?.let { obj ->
                    parseFileObject(obj)?.let { files.add(it) }
                }
            }
            FileListPage(
                files = files,
                hasMore = json.optBoolean("has_more", false),
                lastId = json.optString("last_id").takeIf { it.isNotEmpty() }
            )
        }.getOrNull()
    }

    /** 解析单个 file object；失败返回 null */
    private fun parseFileObject(json: JSONObject): DeepseekFileInfo? {
        val id = json.optString("id")
        if (!id.startsWith("file-")) return null
        return DeepseekFileInfo(
            id = id,
            filename = json.optString("filename"),
            bytes = json.optLong("bytes"),
            createdAt = json.optLong("created_at"),
            purpose = json.optString("purpose")
        )
    }

    /** 由聊天 API 端点推导 Files API 地址（官方为 https://api.deepseek.com/files） */
    private fun buildFilesEndpoint(): String? {
        val raw = apiEndpoint.trim().removeSuffix("#").trim()
        return runCatching {
            val url = URL(raw)
            val portPart = if (url.port in 1..65535) ":" + url.port else ""
            "${url.protocol}://${url.host}${portPart}/files"
        }.getOrNull()
    }

    /** 图片内容 SHA-256 的十六进制前 24 字符（作为跨会话稳定的文件名键） */
    private fun sha256Prefix(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.take(12).joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private fun extensionForMime(mimeType: String): String {
        return when (mimeType.lowercase()) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            else -> "bin"
        }
    }

    private data class FileListPage(
        val files: List<DeepseekFileInfo>,
        val hasMore: Boolean,
        val lastId: String?
    )
}