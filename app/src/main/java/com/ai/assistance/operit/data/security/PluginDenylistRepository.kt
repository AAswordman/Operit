package com.ai.assistance.operit.data.security

import android.content.Context
import android.util.AtomicFile
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class PluginDenylistPointer(
    val schemaVersion: Int = 1,
    val latestVersion: Int = 0,
    val latestFile: String = "",
    val updatedAt: String? = null,
    val notes: String? = null
)

@Serializable
data class PluginDenylistPayload(
    val schemaVersion: Int = 1,
    val version: Int = 0,
    val updatedAt: String? = null,
    val hashAlgorithm: String = "",
    val match: String = "",
    val action: String = "",
    val entries: List<PluginDenylistEntry> = emptyList()
)

@Serializable
data class PluginDenylistEntry(
    val sha256: String = "",
    val note: String? = null
)

class PluginDenylistRepository(
    context: Context,
    private val pointerUrl: String = DEFAULT_POINTER_URL
) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .build()

    suspend fun refreshFromRemote(): Boolean {
        return withContext(Dispatchers.IO) {
            runCatching {
                val pointer = json.decodeFromString<PluginDenylistPointer>(fetchText(addCacheBust(pointerUrl)))
                validatePointer(pointer)

                val payloadText = fetchText(addCacheBust(resolveUrl(pointer.latestFile)))
                val payload = json.decodeFromString<PluginDenylistPayload>(payloadText)
                validatePayload(pointer, payload)
                writeCachedPayload(payloadText.toByteArray(Charsets.UTF_8))
                true
            }.onFailure { error ->
                AppLogger.w(TAG, "Plugin denylist refresh failed", error)
            }.getOrDefault(false)
        }
    }

    fun findDeniedImport(file: File): PluginDenylistEntry? {
        if (!file.isFile || !file.canRead()) return null

        return runCatching {
            val payload = readCachedPayload() ?: return@runCatching null
            val sha256 = sha256Hex(file)
            payload.entries.firstOrNull { entry -> entry.sha256 == sha256 }
        }.onFailure { error ->
            AppLogger.w(TAG, "Plugin denylist check failed for ${file.absolutePath}", error)
        }.getOrNull()
    }

    fun cacheSignature(): String {
        val file = cacheFile()
        val readableFile =
            when {
                file.exists() && file.isFile -> file
                atomicBackupFile(file).exists() -> atomicBackupFile(file)
                else -> null
            }
        return if (readableFile != null) {
            "${readableFile.length()}:${readableFile.lastModified()}"
        } else {
            "none"
        }
    }

    private fun readCachedPayload(): PluginDenylistPayload? {
        val file = cacheFile()
        if (!file.exists() && !atomicBackupFile(file).exists()) return null

        return runCatching {
            val content = AtomicFile(file).openRead().use { input -> input.readBytes().toString(Charsets.UTF_8) }
            val payload = json.decodeFromString<PluginDenylistPayload>(content)
            validatePayload(pointer = null, payload = payload)
            payload
        }.onFailure { error ->
            AppLogger.w(TAG, "Ignoring invalid cached plugin denylist", error)
        }.getOrNull()
    }

    private fun writeCachedPayload(bytes: ByteArray) {
        val file = cacheFile()
        val parent = file.parentFile ?: throw IOException("Plugin denylist cache has no parent directory")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Failed to create plugin denylist cache directory: ${parent.absolutePath}")
        }

        val atomicFile = AtomicFile(file)
        val output = atomicFile.startWrite()
        try {
            output.write(bytes)
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    private fun validatePointer(pointer: PluginDenylistPointer) {
        require(pointer.schemaVersion == SCHEMA_VERSION) { "Unsupported denylist pointer schema" }
        require(pointer.latestVersion > 0) { "Denylist pointer has no version" }
        require(pointer.latestFile.isNotBlank()) { "Denylist pointer has no payload path" }
    }

    private fun validatePayload(pointer: PluginDenylistPointer?, payload: PluginDenylistPayload) {
        require(payload.schemaVersion == SCHEMA_VERSION) { "Unsupported denylist payload schema" }
        require(payload.version > 0) { "Denylist payload has no version" }
        if (pointer != null) {
            require(payload.version == pointer.latestVersion) { "Denylist pointer and payload versions differ" }
        }
        require(payload.hashAlgorithm == HASH_ALGORITHM) { "Unsupported denylist hash algorithm" }
        require(payload.match == MATCH_MODE) { "Unsupported denylist match mode" }
        require(payload.action == ACTION) { "Unsupported denylist action" }

        val hashes = HashSet<String>()
        payload.entries.forEachIndexed { index, entry ->
            require(SHA256_PATTERN.matches(entry.sha256)) { "Invalid SHA-256 at denylist entry $index" }
            require(hashes.add(entry.sha256)) { "Duplicate SHA-256 at denylist entry $index" }
        }
    }

    private fun fetchText(url: String): String {
        val request =
            Request.Builder()
                .url(url)
                .get()
                .header("Cache-Control", "no-cache")
                .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Request failed: ${response.code} $url")
            }
            return response.body?.string() ?: throw IOException("Empty response body: $url")
        }
    }

    private fun resolveUrl(latestFile: String): String {
        val trimmed = latestFile.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed

        return URI(pointerUrl).resolve(trimmed).toString()
    }

    private fun addCacheBust(url: String): String {
        val separator = if (url.contains('?')) "&" else "?"
        return "$url${separator}ts=${System.currentTimeMillis()}"
    }

    private fun cacheFile(): File = File(File(appContext.filesDir, CACHE_DIRECTORY), CACHE_FILE_NAME)

    private fun atomicBackupFile(file: File): File = File(file.parentFile, "${file.name}.bak")

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte ->
            String.format(Locale.US, "%02x", byte.toInt() and 0xff)
        }
    }

    companion object {
        private const val TAG = "PluginDenylistRepo"
        private const val CACHE_DIRECTORY = "plugin_denylist"
        private const val CACHE_FILE_NAME = "denylist.json"
        private const val SCHEMA_VERSION = 1
        private const val HASH_ALGORITHM = "sha256"
        private const val MATCH_MODE = "raw_file_bytes"
        private const val ACTION = "reject_import"
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")

        const val DEFAULT_POINTER_URL = "https://operit.app/plugin-denylist/latest.json"
    }
}
