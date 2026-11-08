package com.ai.assistance.operit.ui.features.extensions.packages.screens.market.viewmodel

import com.ai.assistance.operit.ui.common.oktoast.OKToast

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.GitHubApiService
import com.ai.assistance.operit.data.api.MarketStatsApiService
import com.ai.assistance.operit.data.api.MarketV2EntryUpdateRequest
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.api.MarketV2NewVersionEntryPatch
import com.ai.assistance.operit.data.api.MarketV2PublishRepoVersion
import com.ai.assistance.operit.data.api.MarketV2PublishRequest
import com.ai.assistance.operit.data.api.MarketV2PublishSource
import com.ai.assistance.operit.data.api.MarketV2PublishVersion
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.ui.features.extensions.packages.market.MarketStatsType
import com.ai.assistance.operit.util.logging.AppLogger
import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RepoPublishDraft(
    val title: String = "",
    val description: String = "",
    val detail: String = "",
    val repositoryUrl: String = "",
    val installConfig: String = "",
    val category: String = "",
    val allowPublicUpdates: Boolean = true
)

class RepoMarketPublishViewModel(
    private val context: Context,
    private val type: MarketStatsType
) : ViewModel() {
    private val marketStatsApiService = MarketStatsApiService()
    private val githubApiService = GitHubApiService(context)
    val githubAuth: GitHubAuthPreferences = GitHubAuthPreferences.getInstance(context)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences("${type.wireValue}_publish_draft", Context.MODE_PRIVATE)

    val publishDraft: RepoPublishDraft
        get() = RepoPublishDraft(
            title = sharedPrefs.getString("title", "") ?: "",
            description = sharedPrefs.getString("description", "") ?: "",
            detail = sharedPrefs.getString("detail", "") ?: "",
            repositoryUrl = sharedPrefs.getString("repositoryUrl", "") ?: "",
            installConfig = sharedPrefs.getString("installConfig", "") ?: "",
            category = sharedPrefs.getString("category", "") ?: "",
            allowPublicUpdates = sharedPrefs.getBoolean("allowPublicUpdates", true)
        )

    fun parseEntry(entry: MarketV2Entry): RepoPublishDraft {
        return RepoPublishDraft(
            title = entry.title,
            description = entry.description,
            detail = entry.detail,
            repositoryUrl = entry.source?.url.orEmpty(),
            installConfig = entry.latestVersion?.installConfig.orEmpty(),
            category = entry.categoryId,
            allowPublicUpdates = entry.allowPublicUpdates
        )
    }

    fun saveDraft(
        title: String,
        description: String,
        detail: String,
        repositoryUrl: String,
        installConfig: String = "",
        category: String = "",
        allowPublicUpdates: Boolean = true
    ) {
        sharedPrefs.edit().apply {
            putString("title", title)
            putString("description", description)
            putString("detail", detail)
            putString("repositoryUrl", repositoryUrl)
            putString("installConfig", installConfig)
            putString("category", category)
            putBoolean("allowPublicUpdates", allowPublicUpdates)
            apply()
        }
    }

    fun clearDraft() {
        sharedPrefs.edit().clear().apply()
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun logoutFromGitHub() {
        viewModelScope.launch {
            try {
                githubAuth.logout()
                OKToast.info(context, context.getString(R.string.skillmarket_logged_out), OKToast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                _errorMessage.value = context.getString(R.string.skillmarket_logout_failed, e.message ?: "")
                AppLogger.e(TAG, "Failed to logout from GitHub", e)
            }
        }
    }

    suspend fun publish(
        title: String,
        description: String,
        detail: String,
        repositoryUrl: String,
        version: String,
        installConfig: String = "",
        category: String = "",
        allowPublicUpdates: Boolean = true
    ): Result<Unit> {
        return submit(
            entryId = null,
            title = title,
            description = description,
            detail = detail,
            repositoryUrl = repositoryUrl,
            version = version,
            installConfig = installConfig,
            category = category,
            allowPublicUpdates = allowPublicUpdates
        )
    }

    suspend fun publishNewVersion(
        entry: MarketV2Entry,
        title: String,
        description: String,
        detail: String,
        category: String,
        allowPublicUpdates: Boolean,
        version: String,
        installConfig: String = "",
        canEditEntry: Boolean = false
    ): Result<Unit> {
        validateNewVersion(entry, version)
        val entryPatch =
            (
                if (canEditEntry) {
                    MarketV2NewVersionEntryPatch(
                        title = title.takeIf { it != entry.title },
                        description = description.takeIf { it != entry.description },
                        detail = detail.takeIf { it != entry.detail },
                        categoryId = category.takeIf { it != entry.categoryId },
                        allowPublicUpdates = allowPublicUpdates.takeIf { it != entry.allowPublicUpdates }
                    )
                } else {
                    MarketV2NewVersionEntryPatch(
                        description = description.takeIf { it != entry.description },
                        detail = detail.takeIf { it != entry.detail }
                    )
                }
            ).takeIf { it.hasChanges() }
        return submit(
            entryId = entry.id,
            title = title,
            description = description,
            detail = detail,
            repositoryUrl = entry.source?.url.orEmpty(),
            version = version,
            installConfig = installConfig,
            category = category,
            allowPublicUpdates = allowPublicUpdates,
            entryPatch = entryPatch
        )
    }

    suspend fun updateEntryMetadata(
        entry: MarketV2Entry,
        title: String,
        description: String,
        detail: String,
        category: String,
        allowPublicUpdates: Boolean
    ): Result<Unit> {
        if (!githubAuth.isLoggedIn()) {
            return Result.failure(IllegalStateException(loginRequiredMessage()))
        }

        _isLoading.value = true
        _errorMessage.value = null

        return try {
            val request =
                MarketV2EntryUpdateRequest(
                    title = title,
                    description = description,
                    detail = detail,
                    categoryId = category,
                    allowPublicUpdates = allowPublicUpdates
                )
            marketStatsApiService.updateEntry(entry.id, request).map { Unit }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to update ${type.wireValue} market entry metadata", e)
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun submit(
        entryId: String?,
        title: String,
        description: String,
        detail: String,
        repositoryUrl: String,
        version: String,
        installConfig: String,
        category: String,
        allowPublicUpdates: Boolean,
        entryPatch: MarketV2NewVersionEntryPatch? = null
    ): Result<Unit> {
        if (!githubAuth.isLoggedIn()) {
            return Result.failure(IllegalStateException(loginRequiredMessage()))
        }

        _isLoading.value = true
        _errorMessage.value = null

        return try {
            val request =
                buildV2PublishRequest(
                    title = title,
                    description = description,
                    detail = detail,
                    repositoryUrl = repositoryUrl,
                    version = version,
                    installConfig = installConfig,
                    category = category,
                    allowPublicUpdates = allowPublicUpdates
                )
            val result =
                if (entryId == null) {
                    marketStatsApiService.publish(request).map { Unit }
                } else {
                    marketStatsApiService.publishNewVersion(entryId = entryId, request = request, entryPatch = entryPatch).map { Unit }
                }
            result
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to submit ${type.wireValue} market entry", e)
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun buildV2PublishRequest(
        title: String,
        description: String,
        detail: String,
        repositoryUrl: String,
        version: String,
        installConfig: String,
        category: String,
        allowPublicUpdates: Boolean
    ): MarketV2PublishRequest {
        val repoTarget = resolveRepoPublishTarget(repositoryUrl)
        return MarketV2PublishRequest(
            type = type.wireValue,
            title = title,
            description = description,
            detail = detail,
            categoryId = category,
            allowPublicUpdates = allowPublicUpdates,
            version = MarketV2PublishVersion(
                version = version.ifBlank { "1.0.0" },
                formatVer = "${type.wireValue}_v2",
                minAppVer = CURRENT_APP_VERSION
            ),
            source = MarketV2PublishSource(
                kind = "github_repo",
                url = repoTarget.sourceUrl
            ),
            repoVersion = MarketV2PublishRepoVersion(
                refType = repoTarget.refType,
                refName = repoTarget.refName,
                installConfig = if (type == MarketStatsType.MCP) installConfig.ifBlank { "{}" } else "{}"
            )
        )
    }

    private suspend fun resolveRepoPublishTarget(repositoryUrl: String): RepoPublishTarget {
        val parsed = parseRepoPublishTarget(repositoryUrl)
            ?: throw IllegalStateException(context.getString(R.string.skill_invalid_github_url))
        val refName =
            parsed.refName
                ?: githubApiService.getRepository(parsed.owner, parsed.repo).getOrThrow().defaultBranch.ifBlank {
                    throw IllegalStateException(context.getString(R.string.skill_cannot_determine_default_branch, "${parsed.owner}/${parsed.repo}"))
                }
        return RepoPublishTarget(
            sourceUrl = parsed.sourceUrl,
            refType = parsed.refType,
            refName = refName
        )
    }

    private fun parseRepoPublishTarget(inputUrlRaw: String): ParsedRepoPublishTarget? {
        val inputUrl = inputUrlRaw.trim()
        if (inputUrl.isBlank()) return null
        val urlWithScheme =
            if (inputUrl.startsWith("http://", ignoreCase = true) || inputUrl.startsWith("https://", ignoreCase = true)) {
                inputUrl
            } else {
                "https://$inputUrl"
            }
        val uri =
            try {
                URI(urlWithScheme.substringBefore('#'))
            } catch (_: Exception) {
                return null
            }
        val host = uri.host?.lowercase() ?: return null
        val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        fun cleanRepoName(repoRaw: String): String = repoRaw.removeSuffix(".git")
        fun normalizeGithubSourceUrl(owner: String, repo: String, rest: List<String>): String =
            "https://github.com/$owner/$repo${if (rest.isNotEmpty()) "/${rest.joinToString("/")}" else ""}"

        return when {
            host == "github.com" || host.endsWith(".github.com") -> {
                if (segments.size < 2) return null
                val owner = segments[0]
                val repo = cleanRepoName(segments[1])
                if (owner.isBlank() || repo.isBlank()) return null
                val refType: String
                val refName: String?
                if (segments.size >= 4 && (segments[2] == "tree" || segments[2] == "blob")) {
                    refType = "branch"
                    refName = segments[3]
                } else {
                    refType = "branch"
                    refName = null
                }
                ParsedRepoPublishTarget(
                    owner = owner,
                    repo = repo,
                    sourceUrl = normalizeGithubSourceUrl(owner, repo, segments.drop(2)),
                    refType = refType,
                    refName = refName
                )
            }

            host == "raw.githubusercontent.com" -> {
                if (segments.size < 4) return null
                val owner = segments[0]
                val repo = cleanRepoName(segments[1])
                val refName = segments[2]
                ParsedRepoPublishTarget(
                    owner = owner,
                    repo = repo,
                    sourceUrl = "https://raw.githubusercontent.com/$owner/$repo/${segments.drop(2).joinToString("/")}",
                    refType = "branch",
                    refName = refName
                )
            }

            else -> null
        }
    }

    private data class ParsedRepoPublishTarget(
        val owner: String,
        val repo: String,
        val sourceUrl: String,
        val refType: String,
        val refName: String?
    )

    private data class RepoPublishTarget(
        val sourceUrl: String,
        val refType: String,
        val refName: String
    )

    private fun MarketV2NewVersionEntryPatch.hasChanges(): Boolean =
        title != null ||
            description != null ||
            detail != null ||
            categoryId != null ||
            allowPublicUpdates != null

    private fun validateNewVersion(entry: MarketV2Entry, version: String) {
        val requestedVersion = version.trim().removePrefix("v").removePrefix("V").ifBlank { "1.0.0" }
        val currentHighestVersion =
            entry.versions
                .map { it.version }
                .filter(String::isNotBlank)
                .maxWithOrNull(::comparePublishVersions)
                ?: entry.latestVersion?.version
                ?: return
        if (comparePublishVersions(requestedVersion, currentHighestVersion) <= 0) {
            throw IllegalStateException(
                "Version $requestedVersion must be greater than existing version $currentHighestVersion"
            )
        }
    }

    private fun comparePublishVersions(left: String, right: String): Int {
        val leftVersion = parsePublishVersion(left)
        val rightVersion = parsePublishVersion(right)
        val maxSize = maxOf(leftVersion.parts.size, rightVersion.parts.size)
        for (index in 0 until maxSize) {
            val diff = (leftVersion.parts.getOrNull(index) ?: 0) -
                (rightVersion.parts.getOrNull(index) ?: 0)
            if (diff != 0) return diff
        }
        if (leftVersion.suffix == rightVersion.suffix) return 0
        if (leftVersion.suffix.isBlank()) return 1
        if (rightVersion.suffix.isBlank()) return -1
        return leftVersion.suffix.compareTo(rightVersion.suffix)
    }

    private fun parsePublishVersion(value: String): PublishVersionParts {
        val normalized = value.trim().removePrefix("v").removePrefix("V")
        val core = normalized.substringBefore("-").substringBefore("+")
        val suffix =
            normalized
                .substringAfter("-", "")
                .substringBefore("+")
        val parts =
            core.split(".")
                .filter(String::isNotBlank)
                .map { it.toIntOrNull() ?: 0 }
        return PublishVersionParts(parts = parts, suffix = suffix)
    }

    private data class PublishVersionParts(
        val parts: List<Int>,
        val suffix: String
    )

    private fun loginRequiredMessage(): String =
        when (type) {
            MarketStatsType.SKILL -> context.getString(R.string.skill_publish_login_required)
            MarketStatsType.MCP -> "GitHub 登录后才能发布 MCP。"
            else -> context.getString(R.string.skillmarket_github_login_required)
        }

    class Factory(
        private val context: Context,
        private val type: MarketStatsType
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RepoMarketPublishViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return RepoMarketPublishViewModel(context, type) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    companion object {
        private const val TAG = "RepoMarketPublishViewModel"
        private const val CURRENT_APP_VERSION = "1.11.0+5"
    }
}
