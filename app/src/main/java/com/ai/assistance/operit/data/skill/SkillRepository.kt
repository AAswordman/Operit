package com.ai.assistance.operit.data.skill

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.skill.SkillManager
import com.ai.assistance.operit.core.tools.skill.SkillPackage
import com.ai.assistance.operit.data.preferences.SkillVisibilityPreferences
import com.ai.assistance.operit.data.transfer.DownloadTransferProgress
import com.ai.assistance.operit.data.transfer.copyWithTransferProgress
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.data.skill.SkillRepoZipPoolManager
import com.google.gson.JsonParser
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SkillRepository private constructor(private val context: Context) {

    companion object {
        @Volatile private var INSTANCE: SkillRepository? = null

        private const val TAG = "SkillRepository"
        private const val CONNECT_TIMEOUT = 15_000
        private const val READ_TIMEOUT = 30_000
        private const val BUFFER_SIZE = 64 * 1024
        private val SKILL_ID_PATTERN = Regex("^[A-Za-z0-9._-]+$")

        fun getInstance(context: Context): SkillRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SkillRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val skillManager by lazy { SkillManager.getInstance(context) }

    private val skillVisibilityPreferences by lazy { SkillVisibilityPreferences.getInstance(context) }

    private val defaultBranchCache = ConcurrentHashMap<String, String>()

    private data class GitHubSkillTarget(
        val owner: String,
        val repo: String,
        val ref: String?,
        val subDir: String?
    )

    fun getSkillsDirectoryPath(): String = skillManager.getSkillsDirectoryPath()

    fun getAvailableSkillPackages(): Map<String, SkillPackage> = skillManager.getAvailableSkills()

    fun getAvailableSkillPackagesSnapshot(): Pair<Map<String, SkillPackage>, Map<String, String>> =
        skillManager.getAvailableSkillsSnapshot()

    fun getSkillLoadErrors(): Map<String, String> = skillManager.getSkillLoadErrors()

    fun getAiVisibleSkillPackages(): Map<String, SkillPackage> {
        return skillManager.getAvailableSkills().filter { (skillName, _) ->
            skillVisibilityPreferences.isSkillVisibleToAi(skillName)
        }
    }

    fun readSkillContent(skillName: String): String? = skillManager.readSkillContent(skillName)

    fun deleteSkill(skillName: String): Boolean = skillManager.deleteSkill(skillName)

    suspend fun importSkillFromZip(zipFile: File): String = importSkillFromZipDetailed(zipFile).message
    suspend fun importSkillFromZipDetailed(zipFile: File): SkillRepoImportResult = withContext(Dispatchers.IO) {
        val result = skillManager.importSkillFromZipDetailed(zipFile, null)
        SkillRepoImportResult(result.message, result.installedDir)
    }

    suspend fun importSkillFromGitHubRepo(repoUrl: String): String {
        return importSkillFromGitHubRepoDetailed(repoUrl).message
    }

    data class SkillRepoImportResult(
        val message: String,
        val installedDir: File?
    )

    suspend fun importSkillFromGitHubRepoDetailed(
        repoUrl: String,
        onDownloadProgress: (DownloadTransferProgress) -> Unit = {},
        onImportStarted: () -> Unit = {},
    ): SkillRepoImportResult {
        return withContext(Dispatchers.IO) {
            val target = parseGitHubSkillTarget(repoUrl)
                ?: return@withContext SkillRepoImportResult(context.getString(R.string.skill_invalid_github_url), null)

            val owner = target.owner
            val repoName = target.repo
            val repoKey = "$owner/$repoName"
            val ref = target.ref
                ?: defaultBranchCache[repoKey]
                ?: getGithubDefaultBranch(owner, repoName)?.also { defaultBranchCache[repoKey] = it }
                ?: return@withContext SkillRepoImportResult(context.getString(R.string.skill_cannot_determine_default_branch, "$owner/$repoName"), null)

            val encodedRef = encodePathSegment(ref)
            val zipUrl = "https://codeload.github.com/$owner/$repoName/zip/$encodedRef"
            val repoRefKey = "$owner/$repoName@$ref"
            val pooledZip = SkillRepoZipPoolManager.getOrDownloadZip(repoRefKey) { outFile ->
                downloadFromUrl(zipUrl, outFile, onDownloadProgress)
            }

            val suffix = (target.subDir ?: "repo")
                .replace('/', '_')
                .take(60)
            val fallbackTempFile = File(context.cacheDir, "skill_${owner}_${repoName}_$suffix.zip")
            if (pooledZip == null) {
                if (fallbackTempFile.exists()) fallbackTempFile.delete()
            }

            try {
                val zipFile = if (pooledZip != null) {
                    pooledZip
                } else {
                    val downloaded = downloadFromUrl(zipUrl, fallbackTempFile, onDownloadProgress)
                    if (!downloaded || !fallbackTempFile.exists() || fallbackTempFile.length() <= 0L) {
                        if (fallbackTempFile.exists()) fallbackTempFile.delete()
                        return@withContext SkillRepoImportResult(context.getString(R.string.skill_download_zip_failed), null)
                    }
                    fallbackTempFile
                }

                onImportStarted()
                val result = skillManager.importSkillFromZipDetailed(zipFile, target.subDir)

                if (pooledZip == null) {
                    runCatching { fallbackTempFile.delete() }
                }

                SkillRepoImportResult(result.message, result.installedDir)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to import skill from GitHub repo", e)
                if (pooledZip == null && fallbackTempFile.exists()) fallbackTempFile.delete()
                SkillRepoImportResult(context.getString(R.string.skill_import_failed, e.message ?: "Unknown error"), null)
            }
        }
    }

    suspend fun importSkillFromDirectInput(
        skillId: String, description: String, content: String, attachmentUris: List<Uri> = emptyList()
    ): String = importSkillFromDirectInputDetailed(skillId, description, content, attachmentUris).message

    suspend fun importSkillFromDirectInputDetailed(
        skillId: String,
        description: String,
        content: String,
        attachmentUris: List<Uri> = emptyList()
    ): SkillRepoImportResult {
        return withContext(Dispatchers.IO) {
            val trimmedId = skillId.trim()
            val trimmedDescription = description.trim()
            val trimmedContent = content.trim()

            if (trimmedId.isBlank()) {
                return@withContext SkillRepoImportResult(context.getString(R.string.skillmgr_direct_skill_id_required), null)
            }
            if (!isValidSkillId(trimmedId)) {
                return@withContext SkillRepoImportResult(context.getString(R.string.skillmgr_direct_skill_id_invalid), null)
            }
            if (trimmedContent.isBlank()) {
                return@withContext SkillRepoImportResult(context.getString(R.string.skillmgr_direct_content_required), null)
            }

            val skillsRootDir = File(getSkillsDirectoryPath())
            if (!skillsRootDir.exists() && !skillsRootDir.mkdirs()) {
                return@withContext SkillRepoImportResult(context.getString(
                    R.string.skillmgr_direct_create_dir_failed, skillsRootDir.absolutePath
                ), null)
            }

            val finalDir = File(skillsRootDir, trimmedId)
            if (finalDir.exists()) {
                return@withContext SkillRepoImportResult(context.getString(R.string.skill_error_import_duplicate_name, finalDir.name), null)
            }
            if (!finalDir.mkdirs()) {
                return@withContext SkillRepoImportResult(context.getString(
                    R.string.skillmgr_direct_create_dir_failed, finalDir.absolutePath
                ), null)
            }

            try {
                File(finalDir, "SKILL.md").writeText(
                    buildDirectSkillMarkdown(
                        skillId = trimmedId,
                        description = trimmedDescription,
                        content = trimmedContent
                    )
                )

                if (attachmentUris.isNotEmpty()) {
                    val assetsDir = File(finalDir, "assets")
                    if (!assetsDir.exists() && !assetsDir.mkdirs()) {
                        throw IllegalStateException(
                            context.getString(R.string.skillmgr_direct_create_dir_failed, assetsDir.absolutePath)
                        )
                    }

                    val usedFileNames = mutableSetOf<String>()
                    attachmentUris.forEachIndexed { index, uri ->
                        val displayName = resolveAttachmentDisplayName(uri)
                            ?.takeIf { it.isNotBlank() }
                            ?: "attachment_${index + 1}"
                        val safeName = ensureUniqueFileName(
                            sanitizeAttachmentName(displayName),
                            usedFileNames
                        )
                        val outFile = File(assetsDir, safeName)

                        context.contentResolver.openInputStream(uri)?.use { input ->
                            outFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        } ?: throw IllegalStateException(
                            context.getString(R.string.skillmgr_direct_attachment_read_failed, displayName)
                        )
                    }
                }

                skillManager.refreshAvailableSkills()

                val message = if (trimmedDescription.isNotBlank()) {
                    context.getString(R.string.skill_imported_with_desc, finalDir.name, trimmedDescription)
                } else {
                    context.getString(R.string.skill_imported, finalDir.name)
                }
                SkillRepoImportResult(message, finalDir)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to import skill from direct input", e)
                runCatching { finalDir.deleteRecursively() }
                SkillRepoImportResult(context.getString(R.string.skill_import_failed, e.message ?: "Unknown error"), null)
            }
        }
    }

    private fun buildDirectSkillMarkdown(skillId: String, description: String, content: String): String {
        val escapedName = escapeFrontMatterValue(skillId)
        val escapedDescription = escapeFrontMatterValue(description)
        return buildString {
            appendLine("---")
            appendLine("name: \"$escapedName\"")
            appendLine("description: \"$escapedDescription\"")
            appendLine("---")
            appendLine()
            append(content.trimEnd())
            appendLine()
        }
    }

    private fun escapeFrontMatterValue(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r\n", "\n")
            .replace("\n", "\\n")
    }

    private fun resolveAttachmentDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    cursor.getString(nameIndex)
                } else {
                    null
                }
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun sanitizeAttachmentName(rawName: String): String {
        val sanitized = rawName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return sanitized.ifBlank { "attachment" }
    }

    private fun isValidSkillId(skillId: String): Boolean {
        return SKILL_ID_PATTERN.matches(skillId) && skillId != "." && skillId != ".."
    }

    private fun ensureUniqueFileName(baseName: String, usedNames: MutableSet<String>): String {
        var candidate = baseName
        val dotIndex = baseName.lastIndexOf('.').takeIf { it > 0 } ?: -1
        val prefix = if (dotIndex > 0) baseName.substring(0, dotIndex) else baseName
        val extension = if (dotIndex > 0) baseName.substring(dotIndex) else ""
        var suffix = 1
        while (usedNames.contains(candidate)) {
            candidate = "${prefix}_$suffix$extension"
            suffix += 1
        }
        usedNames += candidate
        return candidate
    }

    private fun parseGitHubSkillTarget(inputUrlRaw: String): GitHubSkillTarget? {
        val inputUrl = inputUrlRaw.trim()
        if (inputUrl.isBlank()) return null

        val urlWithScheme = if (inputUrl.startsWith("http://", ignoreCase = true) || inputUrl.startsWith("https://", ignoreCase = true)) {
            inputUrl
        } else {
            "https://$inputUrl"
        }

        val urlNoFragment = urlWithScheme.substringBefore('#')
        val uri = try {
            URI(urlNoFragment)
        } catch (_: Exception) {
            return null
        }

        val host = uri.host?.lowercase() ?: return null
        val path = uri.path.orEmpty()
        val segments = path.split('/').filter { it.isNotBlank() }
        if (segments.size < 2) return null

        fun cleanRepoName(repoRaw: String): String {
            return repoRaw.removeSuffix(".git")
        }

        return when {
            host == "github.com" || host.endsWith(".github.com") -> {
                val owner = segments[0]
                val repo = cleanRepoName(segments[1])
                if (owner.isBlank() || repo.isBlank()) return null

                var ref: String? = null
                var subDir: String? = null

                if (segments.size >= 4 && (segments[2] == "tree" || segments[2] == "blob")) {
                    ref = segments[3]
                    val remainder = if (segments.size > 4) segments.subList(4, segments.size).joinToString("/") else ""
                    if (remainder.isNotBlank()) {
                        subDir = if (segments[2] == "blob") {
                            if (remainder.endsWith("SKILL.md", ignoreCase = true) || remainder.endsWith("skill.md", ignoreCase = true)) {
                                remainder.substringBeforeLast('/')
                            } else {
                                remainder.substringBeforeLast('/').ifBlank { null }
                            }
                        } else {
                            remainder
                        }
                    }
                }

                GitHubSkillTarget(owner = owner, repo = repo, ref = ref, subDir = subDir)
            }

            host == "raw.githubusercontent.com" -> {
                if (segments.size < 4) return null
                val owner = segments[0]
                val repo = cleanRepoName(segments[1])
                val ref = segments[2]
                val remainder = segments.subList(3, segments.size).joinToString("/")
                val subDir = if (remainder.endsWith("SKILL.md", ignoreCase = true) || remainder.endsWith("skill.md", ignoreCase = true)) {
                    remainder.substringBeforeLast('/')
                } else {
                    remainder.substringBeforeLast('/').ifBlank { null }
                }
                GitHubSkillTarget(owner = owner, repo = repo, ref = ref, subDir = subDir)
            }

            else -> null
        }
    }

    private fun encodePathSegment(value: String): String {
        return try {
            URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        } catch (_: Exception) {
            value
        }
    }

    private fun downloadFromUrl(
        zipUrl: String,
        outFile: File,
        onProgress: (DownloadTransferProgress) -> Unit,
    ): Boolean {
        val url = URL(zipUrl)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            doInput = true
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )
        }

        try {
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                AppLogger.e(TAG, "Download failed, HTTP ${connection.responseCode}")
                return false
            }
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyWithTransferProgress(output, connection.contentLengthLong, onProgress, BUFFER_SIZE)
                    output.flush()
                }
            }
            return true
        } finally {
            connection.disconnect()
        }
    }

    private fun getGithubDefaultBranch(owner: String, repoName: String): String? {
        val apiUrl = "https://api.github.com/repos/$owner/$repoName"
        return try {
            val url = URL(apiUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JsonParser.parseString(response).asJsonObject
                jsonObject.get("default_branch")?.asString
            } else {
                AppLogger.e(TAG, "GitHub API failed, HTTP ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to fetch GitHub default branch", e)
            null
        }
    }
}
