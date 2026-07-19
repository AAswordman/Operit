package com.ai.assistance.operit.ui.features.packages.market

import android.content.Context
import com.ai.assistance.operit.data.api.GitHubApiService
import com.ai.assistance.operit.data.api.GitHubRelease
import com.ai.assistance.operit.data.api.GitHubReleaseAsset
import com.ai.assistance.operit.data.api.MarketStatsApiService
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.api.MarketV2PublishAsset
import com.ai.assistance.operit.data.api.MarketV2PublishRequest
import com.ai.assistance.operit.data.api.MarketV2PublishVersion
import com.ai.assistance.operit.data.api.MarketV2Version
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.util.ToolPkgProtection
import java.io.File
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
    val encryptArtifact: Boolean = false
)

sealed class PublishAttemptResult {
    data class NeedsForgeInitialization(
        val publisherLogin: String
    ) : PublishAttemptResult()

    data class Success(
        val entry: MarketV2Entry,
        val forgeRepo: ForgeRepoInfo,
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

            onProgress(PublishProgressStage.ENSURING_REPO)
            val forgeRepoResult =
                ensureForgeRepository(
                    publisherLogin = currentUser.login,
                    allowCreateForgeRepo = allowCreateForgeRepo
                ).getOrElse { error ->
                    return@withContext Result.failure(error)
                }

            if (forgeRepoResult == null) {
                return@withContext Result.success(
                    PublishAttemptResult.NeedsForgeInitialization(currentUser.login)
                )
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
                    publishContext = request.publishContext,
                    protection = if (request.encryptArtifact) ToolPkgProtection.PROTECTION_ID else null
                )
            val releaseDescriptor = buildPublishReleaseDescriptor(descriptor)

            onProgress(PublishProgressStage.CREATING_RELEASE)
            val ensuredRelease =
                ensureRelease(
                    owner = currentUser.login,
                    repo = forgeRepoResult.repoName,
                    releaseDescriptor = releaseDescriptor
                ).getOrElse { error ->
                    return@withContext Result.failure(error)
                }
            val release = ensuredRelease.release

            onProgress(PublishProgressStage.UPLOADING_ASSET)
            val fileBytes =
                if (request.encryptArtifact) {
                    ToolPkgProtection.protectArtifactFile(
                        context = context,
                        sourceFile = sourceFile,
                        isToolPkg = descriptor.type == PublishArtifactType.PACKAGE
                    )
                } else {
                    sourceFile.readBytes()
                }
            val uploadedAsset =
                uploadAssetReplacingExisting(
                    owner = currentUser.login,
                    repo = forgeRepoResult.repoName,
                    release = release,
                    descriptor = descriptor,
                    content = fileBytes
                ).getOrElse { error ->
                    return@withContext Result.failure(error)
                }

            val computedSha256 = sha256Hex(fileBytes)

            onProgress(PublishProgressStage.REGISTERING_MARKET)
            // Obtain proof token from market API (attests ownership of the release)
            val proofToken =
                marketStatsApiService.publishProof(
                    owner = currentUser.login,
                    repo = forgeRepoResult.repoName,
                    releaseTag = releaseDescriptor.tagName,
                    assetName = uploadedAsset.name,
                    sha256 = computedSha256
                ).getOrElse { error ->
                    return@withContext Result.failure(error)
                }

            // Patch the release body to include the proof marker
            val proofBody =
                releaseDescriptor.releaseBody + "\n\n<!-- operit-market-proof $proofToken -->"
            githubApiService.updateRelease(
                owner = currentUser.login,
                repo = forgeRepoResult.repoName,
                releaseId = release.id,
                name = releaseDescriptor.releaseName,
                body = proofBody,
                draft = false,
                prerelease = false
            ).getOrElse { error ->
                return@withContext Result.failure(error)
            }

            val payload =
                MarketRegistrationPayload(
                    type = descriptor.type,
                    projectId = descriptor.projectId,
                    projectDisplayName = descriptor.projectDisplayName,
                    projectDescription = descriptor.projectDescription,
                    runtimePackageId = descriptor.runtimePackageId,
                    publisherLogin = currentUser.login,
                    forgeRepo = forgeRepoResult.repoName,
                    releaseTag = releaseDescriptor.tagName,
                    assetName = uploadedAsset.name,
                    downloadUrl = uploadedAsset.browser_download_url,
                    sha256 = computedSha256,
                    version = descriptor.version,
                    displayName = descriptor.displayName,
                    description = descriptor.description,
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
                    existingEntryId = request.publishContext?.entryId
                ).getOrElse { error ->
                    rollbackFailedMarketRegistration(
                        owner = currentUser.login,
                        repo = forgeRepoResult.repoName,
                        release = release,
                        releaseWasCreated = ensuredRelease.created,
                        uploadedAsset = uploadedAsset
                    )
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
                    forgeRepo = forgeRepoResult,
                    release = release,
                    asset = uploadedAsset,
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

    private suspend fun rollbackFailedMarketRegistration(
        owner: String,
        repo: String,
        release: GitHubRelease,
        releaseWasCreated: Boolean,
        uploadedAsset: GitHubReleaseAsset
    ) {
        if (releaseWasCreated) {
            githubApiService.deleteRelease(owner, repo, release.id)
        } else {
            githubApiService.deleteReleaseAsset(owner, repo, uploadedAsset.id)
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
        existingEntryId: String?
    ): Result<MarketV2Entry> {
        val request =
            MarketV2PublishRequest(
                type = payload.type.wireValue,
                title = payload.displayName,
                description = payload.description,
                categoryId = payload.categoryId,
                allowPublicUpdates = payload.allowPublicUpdates,
                detail = payload.projectDescription.ifBlank { payload.description },
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
                    ghOwner = payload.publisherLogin,
                    ghRepo = payload.forgeRepo,
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
            includeEntryPatch = true
        ).map { response ->
            MarketV2Entry(
                type = payload.type.wireValue,
                id = response.entryId,
                title = payload.displayName,
                description = payload.description,
                detail = payload.projectDescription.ifBlank { payload.description },
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
