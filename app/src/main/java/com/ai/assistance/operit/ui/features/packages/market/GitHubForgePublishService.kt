package com.ai.assistance.operit.ui.features.packages.market

import android.content.Context
import com.ai.assistance.operit.data.api.GitHubApiService
import com.ai.assistance.operit.data.api.GitHubRelease
import com.ai.assistance.operit.data.api.GitHubReleaseAsset
import com.ai.assistance.operit.data.api.MarketStatsApiService
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.api.MarketV2NewVersionEntryPatch
import com.ai.assistance.operit.data.api.MarketV2PublishAsset
import com.ai.assistance.operit.data.api.MarketV2PublishRequest
import com.ai.assistance.operit.data.api.MarketV2PublishVersion
import com.ai.assistance.operit.data.api.MarketV2Version
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.core.tools.packTool.ToolPkgMarketOrigin
import com.ai.assistance.operit.util.ToolPkgArtifactMinifier
import java.io.File
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PublishArtifactRequest(
    val localArtifact: LocalPublishableArtifact,
    val displayName: String,
    val description: String,
    val detail: String,
    val categoryId: String,
    val allowPublicUpdates: Boolean = true,
    val version: String,
    val minSupportedAppVersion: String?,
    val maxSupportedAppVersion: String?,
    val publishContext: ArtifactPublishClusterContext? = null,
    val source: PublishArtifactSource
)

sealed class PublishAttemptResult {
    data class NeedsForgeInitialization(
        val publisherLogin: String
    ) : PublishAttemptResult()

    data class Success(
        val entry: MarketV2Entry,
        val release: GitHubRelease,
        val asset: GitHubReleaseAsset,
        val payload: MarketRegistrationPayload
    ) : PublishAttemptResult()

    data class RegistrationFailed(
        val errorMessage: String
    ) : PublishAttemptResult()
}

class GitHubForgePublishService(
    private val context: Context,
    private val githubApiService: GitHubApiService,
    private val marketStatsApiService: MarketStatsApiService = MarketStatsApiService()
) {
    private val githubAuth = GitHubAuthPreferences.getInstance(context)

    private data class EnsuredRelease(
        val release: GitHubRelease,
        val created: Boolean
    )

    private data class ResolvedReleaseAsset(
        val owner: String,
        val repository: String,
        val release: GitHubRelease,
        val asset: GitHubReleaseAsset,
        val sha256: String,
        val releaseWasCreated: Boolean?
    )

    suspend fun loadGitHubReleaseCatalog(repositoryUrl: String): Result<GitHubReleaseCatalog> =
        withContext(Dispatchers.IO) {
            try {
                val repository = parseGitHubRepositoryUrl(repositoryUrl)
                githubApiService.getRepositoryReleases(
                    owner = repository.owner,
                    repo = repository.repository
                ).map { releases ->
                    GitHubReleaseCatalog(
                        repository = repository,
                        releases = releases.filter { !it.draft }
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun publishArtifact(
        request: PublishArtifactRequest,
        allowCreateForgeRepo: Boolean,
        onProgress: (PublishProgressStage) -> Unit = {}
    ): Result<PublishAttemptResult> = withContext(Dispatchers.IO) {
        try {
            if (!githubAuth.isLoggedIn()) {
                return@withContext Result.failure(Exception("GitHub login required"))
            }

            onProgress(PublishProgressStage.VALIDATING)
            val sourceFile = request.localArtifact.sourceFile
            validateSourceFile(sourceFile)
            validateSupportedAppVersions(
                minSupportedAppVersion = request.minSupportedAppVersion,
                maxSupportedAppVersion = request.maxSupportedAppVersion
            )

            val currentUser =
                githubApiService.getCurrentUser().getOrElse { error ->
                    return@withContext Result.failure(error)
                }

            val descriptor =
                buildPublishArtifactDescriptor(
                    type = request.localArtifact.type,
                    localArtifact = request.localArtifact,
                    displayName = request.displayName,
                    description = request.description,
                    detail = request.detail,
                    categoryId = request.categoryId,
                    version = request.version,
                    allowPublicUpdates = request.allowPublicUpdates,
                    minSupportedAppVersion = request.minSupportedAppVersion,
                    maxSupportedAppVersion = request.maxSupportedAppVersion,
                    publishContext = request.publishContext
                )

            val resolvedAsset =
                when (val source = request.source) {
                    is PublishArtifactSource.DirectUpload -> {
                        onProgress(PublishProgressStage.ENSURING_REPO)
                        val forgeRepo =
                            ensureForgeRepository(
                                publisherLogin = currentUser.login,
                                allowCreateForgeRepo = allowCreateForgeRepo
                            ).getOrElse { error ->
                                return@withContext Result.failure(error)
                            }

                        if (forgeRepo == null) {
                            return@withContext Result.success(
                                PublishAttemptResult.NeedsForgeInitialization(currentUser.login)
                            )
                        }

                        val releaseDescriptor = buildPublishReleaseDescriptor(descriptor)
                        onProgress(PublishProgressStage.CREATING_RELEASE)
                        val ensuredRelease =
                            ensureRelease(
                                owner = currentUser.login,
                                repo = forgeRepo.repoName,
                                releaseDescriptor = releaseDescriptor
                            ).getOrElse { error ->
                                return@withContext Result.failure(error)
                            }

                        onProgress(PublishProgressStage.UPLOADING_ASSET)
                        val marketOrigin =
                            ToolPkgMarketOrigin(
                                market = "Operit",
                                toolpkgId = descriptor.runtimePackageId,
                                version = descriptor.version,
                                author = listOf(currentUser.login)
                            )
                        val fileBytes = ToolPkgArtifactMinifier.processArtifactFile(
                            context = context,
                            sourceFile = sourceFile,
                            isToolPkg = descriptor.type == PublishArtifactType.PACKAGE,
                            marketOrigin = marketOrigin,
                            minify = source.minifyArtifact
                        )
                        val uploadedAsset =
                            uploadAssetReplacingExisting(
                                owner = currentUser.login,
                                repo = forgeRepo.repoName,
                                release = ensuredRelease.release,
                                descriptor = descriptor,
                                content = fileBytes
                            ).getOrElse { error ->
                                return@withContext Result.failure(error)
                            }

                        ResolvedReleaseAsset(
                            owner = currentUser.login,
                            repository = forgeRepo.repoName,
                            release = ensuredRelease.release,
                            asset = uploadedAsset,
                            sha256 = sha256Hex(fileBytes),
                            releaseWasCreated = ensuredRelease.created
                        )
                    }

                    is PublishArtifactSource.GitHubReleaseAsset -> {
                        onProgress(PublishProgressStage.RESOLVING_RELEASE_ASSET)
                        val release =
                            githubApiService.getReleaseByTag(
                                owner = source.owner,
                                repo = source.repository,
                                tag = source.releaseTag
                            ).getOrElse { error ->
                                return@withContext Result.failure(error)
                            }
                        val asset =
                            release.assets.firstOrNull { it.name == source.assetName }
                                ?: return@withContext Result.failure(
                                    IllegalStateException("Selected GitHub Release asset was not found")
                                )
                        val assetBytes =
                            githubApiService.downloadReleaseAsset(asset.browser_download_url).getOrElse { error ->
                                return@withContext Result.failure(error)
                            }
                        val remoteSha256 = sha256Hex(assetBytes)
                        require(remoteSha256 == sha256Hex(sourceFile.readBytes())) {
                            "The selected GitHub Release asset does not match the local artifact"
                        }

                        ResolvedReleaseAsset(
                            owner = source.owner,
                            repository = source.repository,
                            release = release,
                            asset = asset,
                            sha256 = remoteSha256,
                            releaseWasCreated = null
                        )
                    }
                }

            onProgress(PublishProgressStage.REGISTERING_MARKET)
            val payload =
                MarketRegistrationPayload(
                    type = descriptor.type,
                    projectId = descriptor.projectId,
                    projectDisplayName = descriptor.projectDisplayName,
                    projectDescription = descriptor.projectDescription,
                    runtimePackageId = descriptor.runtimePackageId,
                    publisherLogin = currentUser.login,
                    releaseOwner = resolvedAsset.owner,
                    releaseRepository = resolvedAsset.repository,
                    releaseTag = resolvedAsset.release.tag_name,
                    assetName = resolvedAsset.asset.name,
                    downloadUrl = resolvedAsset.asset.browser_download_url,
                    sha256 = resolvedAsset.sha256,
                    version = descriptor.version,
                    displayName = descriptor.displayName,
                    description = descriptor.description,
                    detail = descriptor.detail,
                    categoryId = descriptor.categoryId,
                    allowPublicUpdates = descriptor.allowPublicUpdates,
                    sourceFileName = sourceFile.name,
                    minSupportedAppVersion = descriptor.minSupportedAppVersion,
                    maxSupportedAppVersion = descriptor.maxSupportedAppVersion,
                    protection = descriptor.protection
                )

            val entry =
                registerMarketEntry(
                    payload = payload,
                    existingEntryId = request.publishContext?.entryId,
                    publishContext = request.publishContext
                ).getOrElse { error ->
                    return@withContext Result.success(
                        PublishAttemptResult.RegistrationFailed(
                            errorMessage = error.message ?: "Failed to register market entry"
                        )
                    )
                }

            onProgress(PublishProgressStage.COMPLETED)
            Result.success(
                PublishAttemptResult.Success(
                    entry = entry,
                    release = resolvedAsset.release,
                    asset = resolvedAsset.asset,
                    payload = payload
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun ensureForgeRepository(
        publisherLogin: String,
        allowCreateForgeRepo: Boolean
    ): Result<ForgeRepoInfo?> {
        val existingRepo = githubApiService.getRepository(publisherLogin, OPERIT_FORGE_REPO_NAME)
        val existingRepoValue = existingRepo.getOrNull()
        if (existingRepoValue != null) {
            if (existingRepoValue.size == 0) {
                initializeForgeRepository(
                    owner = publisherLogin,
                    repo = existingRepoValue.name
                ).getOrElse { error ->
                    return Result.failure(error)
                }
            }
            return Result.success(
                ForgeRepoInfo(
                    ownerLogin = publisherLogin,
                    repoName = existingRepoValue.name,
                    htmlUrl = existingRepoValue.html_url,
                    existedBefore = true
                )
            )
        }

        val failureMessage = existingRepo.exceptionOrNull()?.message.orEmpty()
        if (!failureMessage.contains("HTTP 404")) {
            return Result.failure(existingRepo.exceptionOrNull() ?: Exception("Failed to load OperitForge"))
        }

        if (!allowCreateForgeRepo) {
            return Result.success(null)
        }

        return githubApiService.createRepository(
            name = OPERIT_FORGE_REPO_NAME,
            description = "Operit publish-only artifact repository for release assets.",
            isPrivate = false,
            autoInit = true
        ).map { repo ->
            ForgeRepoInfo(
                ownerLogin = publisherLogin,
                repoName = repo.name,
                htmlUrl = repo.html_url,
                existedBefore = false
            )
        }
    }

    private fun parseGitHubRepositoryUrl(repositoryUrl: String): GitHubReleaseRepository {
        val parsed = URI(repositoryUrl.trim())
        require(parsed.scheme.equals("https", ignoreCase = true)) {
            "GitHub repository URL must use HTTPS"
        }
        require(parsed.host.equals("github.com", ignoreCase = true) || parsed.host.equals("www.github.com", ignoreCase = true)) {
            "GitHub repository URL must point to github.com"
        }
        val pathParts = parsed.path.trim('/').split('/').filter(String::isNotBlank)
        require(pathParts.size >= 2) {
            "GitHub repository URL must include owner and repository"
        }
        val owner = pathParts[0]
        val repository = pathParts[1].removeSuffix(".git")
        val validSegment = Regex("[A-Za-z0-9_.-]+")
        require(validSegment.matches(owner) && validSegment.matches(repository)) {
            "GitHub repository URL contains an invalid owner or repository"
        }
        return GitHubReleaseRepository(owner = owner, repository = repository)
    }

    private suspend fun initializeForgeRepository(
        owner: String,
        repo: String
    ): Result<Unit> {
        return githubApiService.createTextFile(
            owner = owner,
            repo = repo,
            path = "README.md",
            message = "Initialize OperitForge repository",
            textContent =
                buildString {
                    appendLine("# OperitForge")
                    appendLine()
                    appendLine("This repository stores release assets published from Operit.")
                }
        )
    }

    private suspend fun ensureRelease(
        owner: String,
        repo: String,
        releaseDescriptor: PublishReleaseDescriptor
    ): Result<EnsuredRelease> {
        val existing =
            githubApiService.findReleaseByTag(owner, repo, releaseDescriptor.tagName).getOrElse { error ->
                return Result.failure(error)
            }

        return if (existing == null) {
            githubApiService.createRelease(
                owner = owner,
                repo = repo,
                tagName = releaseDescriptor.tagName,
                name = releaseDescriptor.releaseName,
                body = releaseDescriptor.releaseBody,
                draft = false,
                prerelease = false
            ).map { release -> EnsuredRelease(release = release, created = true) }
        } else {
            githubApiService.updateRelease(
                owner = owner,
                repo = repo,
                releaseId = existing.id,
                name = releaseDescriptor.releaseName,
                body = releaseDescriptor.releaseBody,
                draft = false,
                prerelease = false
            ).map { release -> EnsuredRelease(release = release, created = false) }
        }
    }

    private suspend fun uploadAssetReplacingExisting(
        owner: String,
        repo: String,
        release: GitHubRelease,
        descriptor: PublishArtifactDescriptor,
        content: ByteArray
    ): Result<GitHubReleaseAsset> {
        release.assets
            .firstOrNull { it.name.equals(descriptor.assetName, ignoreCase = true) }
            ?.let { existingAsset ->
                githubApiService.deleteReleaseAsset(owner, repo, existingAsset.id).getOrElse { error ->
                    return Result.failure(error)
                }
            }

        return githubApiService.uploadReleaseAsset(
            owner = owner,
            repo = repo,
            releaseId = release.id,
            assetName = descriptor.assetName,
            contentType = descriptor.contentType,
            content = content
        )
    }

    private suspend fun registerMarketEntry(
        payload: MarketRegistrationPayload,
        existingEntryId: String?,
        publishContext: ArtifactPublishClusterContext?
    ): Result<MarketV2Entry> {
        val request =
            MarketV2PublishRequest(
                type = payload.type.wireValue,
                title = payload.displayName,
                description = payload.description,
                categoryId = payload.categoryId,
                allowPublicUpdates = payload.allowPublicUpdates,
                detail = payload.detail.ifBlank { payload.description },
                version = MarketV2PublishVersion(
                    version = payload.version,
                    formatVer = payload.type.marketFormatVersion(),
                    minAppVer = requireNotNull(payload.minSupportedAppVersion) { "Minimum supported app version is required" },
                    maxAppVer = payload.maxSupportedAppVersion ?: DEFAULT_MAX_SUPPORTED_APP_VERSION,
                    projectId = payload.projectId,
                    runtimePackageId = payload.runtimePackageId
                ),
                asset = MarketV2PublishAsset(
                    kind = "github_release_asset",
                    url = payload.downloadUrl,
                    ghOwner = payload.releaseOwner,
                    ghRepo = payload.releaseRepository,
                    ghReleaseTag = payload.releaseTag,
                    assetName = payload.assetName,
                    sha256 = payload.sha256
                )
            )
        val resolvedEntryId = existingEntryId?.trim().orEmpty()
        if (resolvedEntryId.isBlank()) return marketStatsApiService.publish(request)

        return marketStatsApiService.publishNewVersion(
            entryId = resolvedEntryId,
            request = request,
            entryPatch = buildNewVersionEntryPatch(payload, publishContext)
        ).map { response ->
            MarketV2Entry(
                type = payload.type.wireValue,
                id = response.entryId,
                title = payload.displayName,
                description = payload.description,
                detail = payload.detail.ifBlank { payload.description },
                stateCode = "pending",
                latestVersion = MarketV2Version(
                    id = response.versionId,
                    version = payload.version,
                    formatVer = payload.type.marketFormatVersion(),
                    minAppVer = requireNotNull(payload.minSupportedAppVersion) { "Minimum supported app version is required" },
                    maxAppVer = payload.maxSupportedAppVersion ?: DEFAULT_MAX_SUPPORTED_APP_VERSION,
                    stateCode = "pending",
                    projectId = payload.projectId,
                    runtimePackageId = payload.runtimePackageId
                )
            )
        }
    }

    private fun buildNewVersionEntryPatch(
        payload: MarketRegistrationPayload,
        publishContext: ArtifactPublishClusterContext?
    ): MarketV2NewVersionEntryPatch? {
        val context = publishContext ?: return null
        val patch =
            if (context.canEditEntry) {
                MarketV2NewVersionEntryPatch(
                    title = payload.displayName.takeIf { it != context.lockedDisplayName },
                    description = payload.description.takeIf { it != context.marketDescription },
                    detail = payload.detail.takeIf { it != context.marketDetail },
                    categoryId = payload.categoryId.takeIf { it != context.categoryId }
                )
            } else {
                MarketV2NewVersionEntryPatch(
                    description = payload.description.takeIf { it != context.marketDescription },
                    detail = payload.detail.takeIf { it != context.marketDetail }
                )
            }
        return patch.takeIf {
            it.title != null ||
                it.description != null ||
                it.detail != null ||
                it.categoryId != null ||
                it.allowPublicUpdates != null
        }
    }

    private fun validateSourceFile(file: File) {
        require(file.exists()) { "Source file not found: ${file.absolutePath}" }
        require(file.isFile) { "Source path is not a file: ${file.absolutePath}" }
        require(file.canRead()) { "Cannot read source file: ${file.absolutePath}" }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
    companion object {
        const val DEFAULT_MAX_SUPPORTED_APP_VERSION = "1.99.99"
    }
}
