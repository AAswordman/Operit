package com.ai.assistance.operit.core.tools.builtin.web.session.userscript.storage

import android.content.Context
import android.util.Base64
import android.webkit.MimeTypeMap
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.ParsedUserscriptMetadata
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptCapabilityRegistry
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptBootstrapPayload
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptExecutionPayload
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptInstallPreview
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptInstallSourceType
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptListItem
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptLogItem
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptMatcher
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptMetadataParser
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptRequireEntry
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptResourceEntry
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptResourcePayload
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.UserscriptRunAt
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.util.files.OperitPaths
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

internal class UserscriptRepository private constructor(
    context: Context
) {
    companion object {
        private const val TAG = "UserscriptRepository"
        private const val LOG_LIMIT = 200
        private const val ENTRY_TYPE_REQUIRE = "require"
        private const val ENTRY_TYPE_RESOURCE = "resource"

        @Volatile
        private var instance: UserscriptRepository? = null

        fun getInstance(context: Context): UserscriptRepository {
            return instance ?: synchronized(this) {
                instance ?: UserscriptRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val store = UserscriptJsonStore.getInstance(context)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val rootDir = OperitPaths.webSessionUserscriptsDir()
    private val scriptsDir = File(rootDir, "scripts").apply { mkdirs() }
    private val cacheDir = File(rootDir, "cache").apply { mkdirs() }
    private val httpClient =
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

    val installedScriptsFlow: Flow<List<UserscriptListItem>> =
        store.observeUserscripts().map { entities ->
            entities.map(::entityToListItem)
        }

    fun observeRecentLogs(limit: Int = 50): Flow<List<UserscriptLogItem>> =
        store.observeRecentLogs(limit).map { logs ->
            logs.map { log ->
                UserscriptLogItem(
                    id = log.id,
                    userscriptId = log.userscriptId,
                    level = log.level,
                    message = log.message,
                    pageUrl = log.pageUrl,
                    createdAt = log.createdAt
                )
            }
        }

    suspend fun prepareInstallPreview(
        rawSource: String,
        sourceType: UserscriptInstallSourceType,
        sourceUrl: String? = null,
        sourceDisplay: String? = null,
        isUpdate: Boolean = false,
        existingScriptId: Long? = null
    ): UserscriptInstallPreview {
        val metadata = UserscriptMetadataParser.parse(rawSource)
        val knownGrants = UserscriptCapabilityRegistry.knownGrants(metadata.grants)
        val unknownGrants = UserscriptCapabilityRegistry.unknownGrants(metadata.grants)
        val blockedReasons = UserscriptCapabilityRegistry.blockedReasons(metadata.grants)
        return UserscriptInstallPreview(
            metadata = metadata,
            rawSource = rawSource,
            sourceType = sourceType,
            sourceUrl = sourceUrl,
            sourceDisplay = sourceDisplay,
            knownGrants = knownGrants,
            unknownGrants = unknownGrants,
            blockedReasons = blockedReasons,
            isUpdate = isUpdate,
            existingScriptId = existingScriptId
        )
    }

    suspend fun fetchRemotePreview(
        rawUrl: String,
        sourceType: UserscriptInstallSourceType
    ): UserscriptInstallPreview {
        val normalizedUrl = rawUrl.trim()
        require(normalizedUrl.isNotBlank()) { "Userscript URL is empty" }
        val response = httpClient.newCall(Request.Builder().url(normalizedUrl).get().build()).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IllegalStateException("Failed to load userscript: HTTP ${response.code}")
        }
        val body = response.body?.string().orEmpty()
        response.close()
        return prepareInstallPreview(
            rawSource = body,
            sourceType = sourceType,
            sourceUrl = normalizedUrl,
            sourceDisplay = normalizedUrl
        )
    }

    suspend fun install(preview: UserscriptInstallPreview): UserscriptListItem = withContext(Dispatchers.IO) {
        val existing = resolveExistingScript(preview)
        if (existing != null && compareVersions(preview.metadata.version, existing.version) < 0) {
            throw IllegalStateException(
                "Refusing to install older userscript version ${preview.metadata.version} over ${existing.version}"
            )
        }
        val now = System.currentTimeMillis()
        val sourceHash = sha256(preview.rawSource)
        val fileStem = buildFileStem(preview.metadata)
        val scriptFile = File(scriptsDir, "$fileStem.user.js")
        scriptFile.parentFile?.mkdirs()
        scriptFile.writeText(preview.rawSource)

        val entity =
            UserscriptEntity(
                id = existing?.id ?: 0L,
                name = preview.metadata.name,
                namespace = preview.metadata.namespace,
                version = preview.metadata.version,
                description = preview.metadata.description,
                author = preview.metadata.author,
                homepageUrl = preview.metadata.homepage,
                sourceUrl = preview.sourceUrl,
                sourceDisplay = preview.sourceDisplay,
                downloadUrl = preview.metadata.downloadUrl,
                updateUrl = preview.metadata.updateUrl,
                runAt = preview.metadata.runAt.rawValue,
                noFrames = preview.metadata.noFrames,
                enabled = true,
                sourceHash = sourceHash,
                scriptFilePath = scriptFile.absolutePath,
                installSourceType = preview.sourceType.name,
                metadataJson = json.encodeToString(preview.metadata),
                grantsJson = json.encodeToString(preview.metadata.grants),
                matchesJson = json.encodeToString(preview.metadata.matches),
                includesJson = json.encodeToString(preview.metadata.includes),
                excludesJson = json.encodeToString(preview.metadata.excludes),
                excludeMatchesJson = json.encodeToString(preview.metadata.excludeMatches),
                connectsJson = json.encodeToString(preview.metadata.connects),
                requiresJson = json.encodeToString(preview.metadata.requires),
                resourcesJson = json.encodeToString(preview.metadata.resources),
                installedAt = existing?.installedAt ?: now,
                updatedAt = now
            )

        val scriptId =
            if (existing == null) {
                store.insertUserscript(entity)
            } else {
                store.updateUserscript(entity)
                entity.id
            }

        val resourceEntities =
            fetchAndCacheResources(
                metadata = preview.metadata,
                userscriptId = scriptId,
                sourceUrl = preview.sourceUrl
            )
        store.replaceResources(scriptId = scriptId, resources = resourceEntities)

        log(
            userscriptId = scriptId,
            level = "info",
            pageUrl = null,
            message =
                if (existing == null) {
                    "Installed userscript ${preview.metadata.name} ${preview.metadata.version}"
                } else {
                    "Updated userscript ${preview.metadata.name} ${preview.metadata.version}"
                }
        )
        entityToListItem(entity.copy(id = scriptId))
    }

    suspend fun checkForUpdate(scriptId: Long): UserscriptInstallPreview? {
        val entity = store.getUserscriptById(scriptId) ?: return null
        val updateTarget = entity.updateUrl ?: entity.downloadUrl ?: entity.sourceUrl ?: return null
        val preview =
            fetchRemotePreview(
                rawUrl = updateTarget,
                sourceType = UserscriptInstallSourceType.UPDATE
            ).copy(
                isUpdate = true,
                existingScriptId = scriptId
            )
        return if (compareVersions(preview.metadata.version, entity.version) > 0) {
            preview
        } else {
            null
        }
    }

    suspend fun setEnabled(scriptId: Long, enabled: Boolean) {
        val entity = store.getUserscriptById(scriptId) ?: return
        store.updateUserscript(entity.copy(enabled = enabled, updatedAt = System.currentTimeMillis()))
        log(
            userscriptId = scriptId,
            level = "info",
            pageUrl = null,
            message = if (enabled) "Enabled userscript ${entity.name}" else "Disabled userscript ${entity.name}"
        )
    }

    suspend fun deleteUserscript(scriptId: Long) {
        val entity = store.getUserscriptById(scriptId) ?: return
        store.getResourcesForScript(scriptId).forEach { resource ->
            runCatching { File(resource.localPath).delete() }
        }
        runCatching { File(entity.scriptFilePath).delete() }
        log(
            userscriptId = scriptId,
            level = "info",
            pageUrl = null,
            message = "Deleted userscript ${entity.name}"
        )
        store.deleteUserscriptById(scriptId)
    }

    suspend fun readSource(scriptId: Long): String? =
        withContext(Dispatchers.IO) {
            store.getUserscriptById(scriptId)?.let { entity ->
                runCatching { File(entity.scriptFilePath).readText() }.getOrNull()
            }
        }

    suspend fun buildBootstrapPayload(
        sessionId: String,
        pageUrl: String,
        isTopFrame: Boolean
    ): UserscriptBootstrapPayload = withContext(Dispatchers.IO) {
        val entities = store.getAllUserscripts()
        if (entities.isEmpty()) {
            return@withContext UserscriptBootstrapPayload()
        }

        val matched =
            entities.filter { entity ->
                if (!entity.enabled) {
                    return@filter false
                }
                val metadata = entityToMetadata(entity)
                if (UserscriptCapabilityRegistry.blockedReasons(metadata.grants).isNotEmpty()) {
                    return@filter false
                }
                UserscriptMatcher.matches(
                    metadata = metadata,
                    pageUrl = pageUrl,
                    isTopFrame = isTopFrame
                )
            }
        if (matched.isEmpty()) {
            return@withContext UserscriptBootstrapPayload()
        }

        val resourcesByScript =
            store.getResourcesForScripts(matched.map { it.id })
                .groupBy { it.userscriptId }

        val payloads =
            matched.mapNotNull { entity ->
                val source = runCatching { File(entity.scriptFilePath).readText() }.getOrNull() ?: return@mapNotNull null
                val metadata = entityToMetadata(entity)
                val values =
                    store.getValuesForScript(entity.id).associate { value ->
                        value.storageKey to value.valueJson
                    }
                val resourceEntities = resourcesByScript[entity.id].orEmpty().sortedBy { it.resourceKey }
                val requireBodies =
                    resourceEntities
                        .filter { it.entryType == ENTRY_TYPE_REQUIRE }
                        .mapNotNull { resource -> runCatching { File(resource.localPath).readText() }.getOrNull() }
                val resourcePayloads =
                    resourceEntities
                        .filter { it.entryType == ENTRY_TYPE_RESOURCE }
                        .associateNotNull { resource ->
                            val file = File(resource.localPath)
                            if (!file.exists()) {
                                null
                            } else {
                                val bytes = file.readBytes()
                                val mimeType = resource.mimeType ?: "application/octet-stream"
                                val dataUrl =
                                    "data:$mimeType;base64," +
                                        Base64.encodeToString(bytes, Base64.NO_WRAP)
                                resource.resourceKey to
                                    UserscriptResourcePayload(
                                        text = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull(),
                                        dataUrl = dataUrl
                                    )
                            }
                        }
                UserscriptExecutionPayload(
                    scriptId = entity.id,
                    sessionId = sessionId,
                    pageUrl = pageUrl,
                    name = entity.name,
                    namespace = entity.namespace,
                    version = entity.version,
                    runAt = entity.runAt,
                    grants = metadata.grants,
                    capabilities = UserscriptCapabilityRegistry.knownGrants(metadata.grants),
                    metadataJson = json.encodeToString(metadata),
                    code = source,
                    requires = requireBodies,
                    values = values,
                    resources = resourcePayloads
                )
            }
        UserscriptBootstrapPayload(scripts = payloads)
    }

    suspend fun log(
        userscriptId: Long?,
        level: String,
        pageUrl: String?,
        message: String
    ) {
        if (message.isBlank()) {
            return
        }
        store.insertLog(
            UserscriptLogEntity(
                userscriptId = userscriptId,
                level = level.trim().ifBlank { "info" },
                message = message.trim(),
                pageUrl = pageUrl?.trim()?.ifBlank { null },
                createdAt = System.currentTimeMillis()
            )
        )
        store.trimLogs(LOG_LIMIT)
        AppLogger.d(TAG, "userscript[$userscriptId][$level] $message")
    }

    suspend fun getInstalledScript(scriptId: Long): UserscriptListItem? =
        store.getUserscriptById(scriptId)?.let(::entityToListItem)

    suspend fun listInstalledScripts(): List<UserscriptListItem> =
        store.getAllUserscripts()
            .map(::entityToListItem)
            .sortedWith(compareBy<UserscriptListItem> { it.name.lowercase(Locale.ROOT) }.thenBy { it.id })

    suspend fun persistValue(
        scriptId: Long,
        key: String,
        valueJson: String
    ) {
        store.insertValue(
            UserscriptValueEntity(
                userscriptId = scriptId,
                storageKey = key,
                valueJson = valueJson,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun persistValues(
        scriptId: Long,
        values: Map<String, String>
    ) {
        values.forEach { (key, valueJson) ->
            persistValue(scriptId, key, valueJson)
        }
    }

    suspend fun readValueJson(
        scriptId: Long,
        key: String
    ): String? = store.getValue(scriptId, key)?.valueJson

    suspend fun deleteValue(
        scriptId: Long,
        key: String
    ) {
        store.deleteValue(scriptId, key)
    }

    suspend fun deleteValues(
        scriptId: Long,
        keys: Collection<String>
    ) {
        keys.forEach { key ->
            store.deleteValue(scriptId, key)
        }
    }

    private suspend fun fetchAndCacheResources(
        metadata: ParsedUserscriptMetadata,
        userscriptId: Long,
        sourceUrl: String?
    ): List<UserscriptResourceEntity> {
        val requireResources =
            metadata.requires.mapIndexed { index, entry ->
                val absoluteUrl = resolveRemoteUrl(sourceUrl, entry.url)
                val target =
                    File(
                        cacheDir,
                        "${buildSafeCachePrefix(metadata)}_require_${index.toString().padStart(4, '0')}.js"
                    )
                val fetched = fetchRemoteAsset(absoluteUrl, target)
                UserscriptResourceEntity(
                    userscriptId = userscriptId,
                    entryType = ENTRY_TYPE_REQUIRE,
                    resourceKey = "require:${index.toString().padStart(4, '0')}",
                    remoteUrl = absoluteUrl,
                    localPath = fetched.file.absolutePath,
                    mimeType = fetched.mimeType,
                    etag = fetched.etag,
                    lastModifiedHeader = fetched.lastModified,
                    updatedAt = System.currentTimeMillis()
                )
            }

        val namedResources =
            metadata.resources.mapIndexed { index, entry ->
                val absoluteUrl = resolveRemoteUrl(sourceUrl, entry.url)
                val extension = MimeTypeMap.getFileExtensionFromUrl(absoluteUrl).takeIf { !it.isNullOrBlank() }
                val target =
                    File(
                        cacheDir,
                        "${buildSafeCachePrefix(metadata)}_resource_${index.toString().padStart(4, '0')}.${extension ?: "bin"}"
                    )
                val fetched = fetchRemoteAsset(absoluteUrl, target)
                UserscriptResourceEntity(
                    userscriptId = userscriptId,
                    entryType = ENTRY_TYPE_RESOURCE,
                    resourceKey = entry.name,
                    remoteUrl = absoluteUrl,
                    localPath = fetched.file.absolutePath,
                    mimeType = fetched.mimeType,
                    etag = fetched.etag,
                    lastModifiedHeader = fetched.lastModified,
                    updatedAt = System.currentTimeMillis()
                )
            }
        return requireResources + namedResources
    }

    private fun entityToListItem(entity: UserscriptEntity): UserscriptListItem {
        val metadata = entityToMetadata(entity)
        val unknownGrants = UserscriptCapabilityRegistry.unknownGrants(metadata.grants)
        val blockedReasons = UserscriptCapabilityRegistry.blockedReasons(metadata.grants)
        return UserscriptListItem(
            id = entity.id,
            name = entity.name,
            namespace = entity.namespace,
            version = entity.version,
            description = entity.description,
            sourceDisplay = entity.sourceDisplay,
            enabled = entity.enabled,
            unknownGrants = unknownGrants,
            blockedReasons = blockedReasons,
            grants = metadata.grants,
            matches = metadata.matches,
            includes = metadata.includes,
            excludes = metadata.excludes,
            excludeMatches = metadata.excludeMatches,
            connects = metadata.connects,
            requires = metadata.requires,
            resources = metadata.resources,
            homepage = entity.homepageUrl,
            website = metadata.website,
            supportUrl = metadata.supportUrl,
            icons = metadata.icons,
            tags = metadata.tags,
            sandbox = metadata.sandbox,
            runIn = metadata.runIn,
            unwrap = metadata.unwrap,
            webRequestRules = metadata.webRequestRules,
            sourceUrl = entity.sourceUrl,
            updateUrl = entity.updateUrl,
            downloadUrl = entity.downloadUrl,
            installedAt = entity.installedAt,
            updatedAt = entity.updatedAt
        )
    }

    private suspend fun resolveExistingScript(preview: UserscriptInstallPreview): UserscriptEntity? {
        preview.existingScriptId?.let { scriptId ->
            return store.getUserscriptById(scriptId)
        }
        val namespace = preview.metadata.namespace
        if (!namespace.isNullOrBlank()) {
            return store.getUserscriptByScope(preview.metadata.name, namespace)
        }
        return store.getAllUserscripts().firstOrNull { entity ->
            entity.name == preview.metadata.name &&
                entity.namespace == null &&
                entity.sourceUrl == preview.sourceUrl
        }
    }

    private fun entityToMetadata(entity: UserscriptEntity): ParsedUserscriptMetadata {
        if (entity.metadataJson.isNotBlank()) {
            runCatching { json.decodeFromString<ParsedUserscriptMetadata>(entity.metadataJson) }
                .getOrNull()
                ?.let { return it }
        }
        return ParsedUserscriptMetadata(
            name = entity.name,
            namespace = entity.namespace,
            version = entity.version,
            description = entity.description,
            author = entity.author,
            homepage = entity.homepageUrl,
            downloadUrl = entity.downloadUrl,
            updateUrl = entity.updateUrl,
            runAt = UserscriptRunAt.fromRaw(entity.runAt),
            grants = decodeStringList(entity.grantsJson),
            matches = decodeStringList(entity.matchesJson),
            includes = decodeStringList(entity.includesJson),
            excludes = decodeStringList(entity.excludesJson),
            excludeMatches = decodeStringList(entity.excludeMatchesJson),
            connects = decodeStringList(entity.connectsJson),
            requires = decodeRequireList(entity.requiresJson),
            resources = decodeResourceList(entity.resourcesJson),
            noFrames = entity.noFrames
        )
    }

    private fun decodeStringList(raw: String): List<String> =
        runCatching { json.decodeFromString<List<String>>(raw) }.getOrElse { emptyList() }

    private fun decodeRequireList(raw: String): List<UserscriptRequireEntry> =
        runCatching { json.decodeFromString<List<UserscriptRequireEntry>>(raw) }.getOrElse { emptyList() }

    private fun decodeResourceList(raw: String): List<UserscriptResourceEntry> =
        runCatching { json.decodeFromString<List<UserscriptResourceEntry>>(raw) }.getOrElse { emptyList() }

    private fun buildFileStem(metadata: ParsedUserscriptMetadata): String =
        buildSafeCachePrefix(metadata) + "_" + sha256("${metadata.namespace.orEmpty()}::${metadata.name}").take(12)

    private fun buildSafeCachePrefix(metadata: ParsedUserscriptMetadata): String {
        val raw = "${metadata.namespace.orEmpty()}_${metadata.name}".lowercase(Locale.ROOT)
        val normalized = raw.replace("[^a-z0-9._-]+".toRegex(), "_").trim('_')
        return normalized.ifBlank { "userscript" }
    }

    private fun sha256(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun resolveRemoteUrl(baseUrl: String?, candidate: String): String {
        val trimmed = candidate.trim()
        if (trimmed.isBlank()) {
            throw IllegalArgumentException("Empty remote dependency URL")
        }
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("data:", ignoreCase = true)
        ) {
            return trimmed
        }
        val base = baseUrl?.trim()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Relative dependency URL without install source: $trimmed")
        return URI(base).resolve(trimmed).toString()
    }

    private fun fetchRemoteAsset(url: String, targetFile: File): FetchedAsset {
        if (url.startsWith("data:", ignoreCase = true)) {
            val header = url.substringAfter("data:", "").substringBefore(',', "")
            val payload = url.substringAfter(',', "")
            val mimeType = header.substringBefore(';', "application/octet-stream")
            val bytes =
                if (header.contains(";base64", ignoreCase = true)) {
                    Base64.decode(payload, Base64.DEFAULT)
                } else {
                    URLDecoder.decode(payload, Charsets.UTF_8.name()).toByteArray(Charsets.UTF_8)
                }
            targetFile.parentFile?.mkdirs()
            targetFile.writeBytes(bytes)
            return FetchedAsset(file = targetFile, mimeType = mimeType, etag = null, lastModified = null)
        }
        val response = httpClient.newCall(Request.Builder().url(url).get().build()).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IllegalStateException("Failed to fetch $url: HTTP ${response.code}")
        }
        val body = response.body ?: run {
            response.close()
            throw IllegalStateException("No response body for $url")
        }
        val bytes = body.bytes()
        val mimeType =
            response.header("Content-Type")
                ?.substringBefore(';')
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: guessMimeTypeFromUrl(url)
        val etag = response.header("ETag")
        val lastModified = response.header("Last-Modified")
        response.close()
        targetFile.parentFile?.mkdirs()
        targetFile.writeBytes(bytes)
        return FetchedAsset(
            file = targetFile,
            mimeType = mimeType,
            etag = etag,
            lastModified = lastModified
        )
    }

    private fun guessMimeTypeFromUrl(url: String): String {
        val extension =
            MimeTypeMap.getFileExtensionFromUrl(url)
                ?.lowercase(Locale.ROOT)
                ?.trim('.')
                .orEmpty()
        return if (extension.isBlank()) {
            "application/octet-stream"
        } else {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
        }
    }

    private fun compareVersions(candidate: String, current: String): Int {
        if (candidate == current) {
            return 0
        }
        val candidateParts = candidate.split(Regex("""[^A-Za-z0-9]+""")).filter { it.isNotBlank() }
        val currentParts = current.split(Regex("""[^A-Za-z0-9]+""")).filter { it.isNotBlank() }
        val max = maxOf(candidateParts.size, currentParts.size)
        for (index in 0 until max) {
            val left = candidateParts.getOrNull(index).orEmpty()
            val right = currentParts.getOrNull(index).orEmpty()
            val leftNumber = left.toLongOrNull()
            val rightNumber = right.toLongOrNull()
            val comparison =
                when {
                    leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                    else -> left.compareTo(right, ignoreCase = true)
                }
            if (comparison != 0) {
                return comparison
            }
        }
        return candidate.compareTo(current, ignoreCase = true)
    }

    private data class FetchedAsset(
        val file: File,
        val mimeType: String?,
        val etag: String?,
        val lastModified: String?
    )

    private inline fun <K, V> Iterable<V>.associateNotNull(
        transform: (V) -> Pair<K, UserscriptResourcePayload>?
    ): Map<K, UserscriptResourcePayload> {
        val result = LinkedHashMap<K, UserscriptResourcePayload>()
        for (item in this) {
            val pair = transform(item) ?: continue
            result[pair.first] = pair.second
        }
        return result
    }
}
