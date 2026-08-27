package com.ai.assistance.operit.ui.features.packages.market

import com.ai.assistance.operit.BuildConfig
import com.ai.assistance.operit.data.api.GitHubRelease
import com.ai.assistance.operit.data.api.MarketV2Entry
import java.io.File
import kotlinx.serialization.Serializable

const val OPERIT_MARKET_OWNER = "AAswordman"
const val OPERIT_FORGE_REPO_NAME = "OperitForge"

private const val SCRIPT_MARKET_LABEL = "script-artifact"
private const val PACKAGE_MARKET_LABEL = "package-artifact"
private const val PLACEHOLDER_MARKET_ARTIFACT_ID = "artifact"
private val APP_VERSION_REGEX = Regex("""^(\d+)\.(\d+)\.(\d+)(?:\+(\d+))?$""")
private data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val build: Int?
) {
    override fun toString(): String {
        return build?.let { "$major.$minor.$patch+$it" } ?: "$major.$minor.$patch"
    }
}

enum class PublishArtifactType(
    val wireValue: String,
    val marketRepo: String,
    val releaseTagPrefix: String,
    val titleLabel: String,
    val marketLabel: String,
    val marketLabelColor: String,
    val marketLabelDescription: String
) {
    SCRIPT(
        wireValue = "script",
        marketRepo = "OperitScriptMarket",
        releaseTagPrefix = "script",
        titleLabel = "Script",
        marketLabel = SCRIPT_MARKET_LABEL,
        marketLabelColor = "0e8a16",
        marketLabelDescription = "Published script artifacts managed by Operit."
    ),
    PACKAGE(
        wireValue = "package",
        marketRepo = "OperitPackageMarket",
        releaseTagPrefix = "package",
        titleLabel = "Package",
        marketLabel = PACKAGE_MARKET_LABEL,
        marketLabelColor = "1d76db",
        marketLabelDescription = "Published package artifacts managed by Operit."
    );

    companion object {
        fun fromWireValue(value: String?): PublishArtifactType? {
            val normalized = value?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.wireValue == normalized }
        }
    }
}

enum class MarketAppVersionCompatibilityKind {
    BELOW_MINIMUM,
    ABOVE_MAXIMUM
}

data class MarketAppVersionCompatibility(
    val kind: MarketAppVersionCompatibilityKind,
    val currentVersion: String,
    val requiredVersion: String
)

fun PublishArtifactType.marketFormatVersion(): String {
    return when (this) {
        PublishArtifactType.SCRIPT -> "script_v2"
        PublishArtifactType.PACKAGE -> "toolpkg_v2"
    }
}

enum class ArtifactMarketScope {
    ALL,
    SCRIPT_ONLY,
    PACKAGE_ONLY;

    fun supportedTypes(): List<PublishArtifactType> {
        return when (this) {
            ALL -> PublishArtifactType.entries
            SCRIPT_ONLY -> listOf(PublishArtifactType.SCRIPT)
            PACKAGE_ONLY -> listOf(PublishArtifactType.PACKAGE)
        }
    }
}

enum class PublishProgressStage {
    IDLE,
    VALIDATING,
    ENSURING_REPO,
    CREATING_RELEASE,
    UPLOADING_ASSET,
    RESOLVING_RELEASE_ASSET,
    REGISTERING_MARKET,
    COMPLETED
}

data class ForgeRepoInfo(
    val ownerLogin: String,
    val repoName: String,
    val htmlUrl: String,
    val existedBefore: Boolean
)

data class LocalPublishableArtifact(
    val type: PublishArtifactType,
    val packageName: String,
    val displayName: String,
    val description: String,
    val author: List<String> = emptyList(),
    val sourceFile: File,
    val hasDeclaredAuthorField: Boolean = false,
    val declaredAuthorSlotCount: Int = 0,
    val inferredVersion: String? = null
)

sealed interface PublishArtifactSource {
    data class DirectUpload(
        val minifyArtifact: Boolean
    ) : PublishArtifactSource

    data class GitHubReleaseAsset(
        val owner: String,
        val repository: String,
        val releaseTag: String,
        val assetName: String
    ) : PublishArtifactSource
}

data class GitHubReleaseRepository(
    val owner: String,
    val repository: String
)

data class GitHubReleaseCatalog(
    val repository: GitHubReleaseRepository,
    val releases: List<GitHubRelease>
)

data class ArtifactPublishClusterContext(
    val entryId: String,
    val projectId: String,
    val runtimePackageId: String,
    val lockedDisplayName: String,
    val projectDisplayName: String,
    val projectDescription: String,
    val marketDescription: String,
    val marketDetail: String,
    val categoryId: String = "",
    val canEditEntry: Boolean = false
)

data class PublishArtifactDescriptor(
    val type: PublishArtifactType,
    val projectId: String,
    val projectDisplayName: String,
    val projectDescription: String,
    val runtimePackageId: String,
    val displayName: String,
    val description: String,
    val detail: String,
    val categoryId: String,
    val version: String,
    val allowPublicUpdates: Boolean = true,
    val sourceFile: File,
    val contentType: String,
    val assetName: String,
    val minSupportedAppVersion: String?,
    val maxSupportedAppVersion: String?,
    val protection: String? = null
)

data class PublishReleaseDescriptor(
    val tagName: String,
    val releaseName: String,
    val releaseBody: String
)

data class MarketRegistrationPayload(
    val type: PublishArtifactType,
    val entryId: String = "",
    val projectId: String,
    val projectDisplayName: String,
    val projectDescription: String,
    val runtimePackageId: String,
    val publisherLogin: String,
    val releaseOwner: String,
    val releaseRepository: String,
    val releaseTag: String,
    val assetName: String,
    val downloadUrl: String,
    val sha256: String,
    val version: String,
    val displayName: String,
    val description: String,
    val detail: String,
    val categoryId: String,
    val allowPublicUpdates: Boolean = true,
    val sourceFileName: String,
    val minSupportedAppVersion: String?,
    val maxSupportedAppVersion: String?,
    val protection: String? = null
)

@Serializable
data class ArtifactMarketMetadata(
    val type: String = "",
    val projectId: String = "",
    val projectDisplayName: String = "",
    val projectDescription: String = "",
    val runtimePackageId: String = "",
    val publisherLogin: String = "",
    val releaseTag: String = "",
    val assetName: String = "",
    val downloadUrl: String = "",
    val sha256: String = "",
    val version: String = "",
    val displayName: String = "",
    val description: String = "",
    val categoryId: String = "",
    val sourceFileName: String = "",
    val minSupportedAppVersion: String? = null,
    val maxSupportedAppVersion: String? = null,
    val protection: String? = null,
    val normalizedId: String = "",
    val releaseOwner: String = "",
    val releaseRepository: String = ""
)

fun ArtifactMarketMetadata.effectiveProjectId(): String {
    val candidate =
        projectId.trim().ifBlank {
            normalizedId.trim().ifBlank {
                normalizeMarketArtifactId(runtimePackageId.ifBlank { displayName.ifBlank { assetName } })
            }
        }
    return normalizeMarketArtifactId(candidate)
}

fun ArtifactMarketMetadata.effectiveProjectDisplayName(): String {
    return projectDisplayName.trim().ifBlank { displayName.trim() }
}

fun ArtifactMarketMetadata.effectiveProjectDescription(): String {
    return projectDescription.trim().ifBlank { description.trim() }
}

fun ArtifactMarketMetadata.effectiveRuntimePackageId(): String {
    val candidate =
        runtimePackageId.trim().ifBlank {
            normalizedId.trim().ifBlank { effectiveProjectId() }
        }
    return candidate.ifBlank { effectiveProjectId() }
}

fun ArtifactMarketMetadata.toPublishClusterContext(entryId: String? = null): ArtifactPublishClusterContext {
    return ArtifactPublishClusterContext(
        entryId = entryId ?: effectiveProjectId(),
        projectId = effectiveProjectId(),
        runtimePackageId = effectiveRuntimePackageId(),
        lockedDisplayName = displayName.trim().ifBlank { effectiveProjectDisplayName() },
        projectDisplayName = effectiveProjectDisplayName(),
        projectDescription = effectiveProjectDescription(),
        marketDescription = description,
        marketDetail = effectiveProjectDescription(),
        categoryId = categoryId
    )
}

private val NON_ALPHANUMERIC_RX = Regex("[^a-z0-9]+")
private val MULTI_DASH_RX = Regex("-+")

fun normalizeMarketArtifactId(raw: String): String {
    val normalized =
        raw.trim()
            .lowercase()
            .replace(NON_ALPHANUMERIC_RX, "-")
            .replace(MULTI_DASH_RX, "-")
            .trim('-')
    return normalized.ifBlank { PLACEHOLDER_MARKET_ARTIFACT_ID }
}

fun isPlaceholderMarketArtifactId(raw: String): Boolean {
    return normalizeMarketArtifactId(raw) == PLACEHOLDER_MARKET_ARTIFACT_ID
}

fun requiresStandaloneArtifactIdUpgrade(runtimePackageId: String): Boolean {
    val trimmed = runtimePackageId.trim()
    return trimmed.isNotBlank() &&
        isPlaceholderMarketArtifactId(trimmed) &&
        !trimmed.equals(PLACEHOLDER_MARKET_ARTIFACT_ID, ignoreCase = true)
}

fun validateStandaloneArtifactRuntimePackageId(runtimePackageId: String) {
    require(!requiresStandaloneArtifactIdUpgrade(runtimePackageId)) {
        "当前包 ID「$runtimePackageId」无法生成稳定的市场项目 ID。请改用包含英文字母或数字的包 ID（可含 -、_、.），再重新发布。"
    }
}

fun sameArtifactRuntimePackageId(
    left: String,
    right: String
): Boolean {
    val trimmedLeft = left.trim()
    val trimmedRight = right.trim()
    if (trimmedLeft.isBlank() || trimmedRight.isBlank()) {
        return false
    }
    return trimmedLeft.equals(trimmedRight, ignoreCase = true) ||
        normalizeMarketArtifactId(trimmedLeft) == normalizeMarketArtifactId(trimmedRight)
}

fun buildPublishArtifactDescriptor(
    type: PublishArtifactType,
    localArtifact: LocalPublishableArtifact,
    displayName: String,
    description: String,
    detail: String,
    categoryId: String,
    version: String,
    allowPublicUpdates: Boolean = true,
    minSupportedAppVersion: String?,
    maxSupportedAppVersion: String?,
    publishContext: ArtifactPublishClusterContext? = null,
    protection: String? = null
): PublishArtifactDescriptor {
    val runtimePackageId = localArtifact.packageName.trim().ifBlank { localArtifact.packageName }
    if (publishContext == null) {
        validateStandaloneArtifactRuntimePackageId(runtimePackageId)
    }
    val normalizedRuntimePackageId = normalizeMarketArtifactId(runtimePackageId)
    val contextRuntimePackageId = publishContext?.runtimePackageId?.trim().orEmpty()
    if (contextRuntimePackageId.isNotBlank()) {
        require(
            normalizeMarketArtifactId(contextRuntimePackageId) == normalizedRuntimePackageId
        ) {
            "Continuation publish must keep runtime package id '$contextRuntimePackageId'"
        }
    }

    val cleanVersion =
        version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .ifBlank { "1.0.0" }
    val lockedDisplayName = publishContext?.lockedDisplayName?.trim().orEmpty()
    val isContributorContinuation = publishContext?.canEditEntry == false
    if (publishContext != null) {
        require(lockedDisplayName.isNotBlank()) {
            "Continuation publish must keep source display name"
        }
    }
    val resolvedDisplayName =
        if (isContributorContinuation) {
            lockedDisplayName
        } else {
            displayName.trim().ifBlank { lockedDisplayName.ifBlank { localArtifact.displayName } }
        }
    val extension = localArtifact.sourceFile.extension.lowercase().ifBlank { "bin" }
    val projectId =
        publishContext?.projectId
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(::normalizeMarketArtifactId)
            ?: normalizeMarketArtifactId(runtimePackageId)
    val projectDisplayName =
        if (isContributorContinuation) {
            publishContext?.projectDisplayName?.trim().orEmpty().ifBlank { resolvedDisplayName }
        } else {
            resolvedDisplayName
        }
    val projectDescription = detail.trim().ifBlank { description.trim().ifBlank { localArtifact.description } }
    val resolvedCategoryId =
        if (isContributorContinuation) {
            publishContext?.categoryId?.trim().orEmpty()
        } else {
            categoryId.trim().ifBlank { publishContext?.categoryId?.trim().orEmpty() }
        }
    val assetName = "$normalizedRuntimePackageId-v$cleanVersion.$extension"
    val normalizedProtection = protection?.trim()?.takeIf { it.isNotBlank() }

    return PublishArtifactDescriptor(
        type = type,
        projectId = projectId,
        projectDisplayName = projectDisplayName,
        projectDescription = projectDescription,
        runtimePackageId = runtimePackageId,
        displayName = resolvedDisplayName,
        description = description.trim().ifBlank { localArtifact.description },
        detail = detail.trim(),
        categoryId = resolvedCategoryId,
        version = cleanVersion,
        allowPublicUpdates = allowPublicUpdates,
        sourceFile = localArtifact.sourceFile,
        contentType =
            if (normalizedProtection != null && type == PublishArtifactType.SCRIPT) {
                "application/octet-stream"
            } else {
                inferArtifactContentType(type, extension)
            },
        assetName = assetName,
        minSupportedAppVersion = normalizeAppVersionOrNull(minSupportedAppVersion),
        maxSupportedAppVersion = normalizeAppVersionOrNull(maxSupportedAppVersion),
        protection = normalizedProtection
    )
}

fun buildPublishReleaseDescriptor(
    descriptor: PublishArtifactDescriptor
): PublishReleaseDescriptor {
    val normalizedRuntimePackageId = normalizeMarketArtifactId(descriptor.runtimePackageId)
    val tagName =
        "${descriptor.type.releaseTagPrefix}-${normalizedRuntimePackageId}-v${descriptor.version}"
    return PublishReleaseDescriptor(
        tagName = tagName,
        releaseName = "${descriptor.displayName} v${descriptor.version}",
        releaseBody =
            buildString {
                appendLine("${descriptor.type.titleLabel} artifact published by OperitForge.")
                appendLine()
                appendLine("Project ID: ${descriptor.projectId}")
                appendLine("Runtime package ID: ${descriptor.runtimePackageId}")
                appendLine("Display name: ${descriptor.displayName}")
                appendLine("Version: ${descriptor.version}")
                descriptor.protection?.let { protection ->
                    appendLine("Protection: $protection")
                }
                appendLine(
                    "Supported app versions: ${formatSupportedAppVersions(descriptor.minSupportedAppVersion, descriptor.maxSupportedAppVersion)}"
                )
            }
    )
}

fun buildArtifactMarketMetadata(
    payload: MarketRegistrationPayload
): ArtifactMarketMetadata {
    return ArtifactMarketMetadata(
        type = payload.type.wireValue,
        projectId = payload.projectId,
        projectDisplayName = payload.projectDisplayName,
        projectDescription = payload.projectDescription,
        runtimePackageId = payload.runtimePackageId,
        publisherLogin = payload.publisherLogin,
        releaseOwner = payload.releaseOwner,
        releaseRepository = payload.releaseRepository,
        releaseTag = payload.releaseTag,
        assetName = payload.assetName,
        downloadUrl = payload.downloadUrl,
        sha256 = payload.sha256,
        version = payload.version,
        displayName = payload.displayName,
        description = payload.description,
        categoryId = payload.categoryId,
        sourceFileName = payload.sourceFileName,
        minSupportedAppVersion = payload.minSupportedAppVersion,
        maxSupportedAppVersion = payload.maxSupportedAppVersion,
        protection = payload.protection
    )
}


fun normalizeAppVersionOrNull(value: String?): String? {
    return parseAppVersionOrNull(value)?.toString()
}

fun validateSupportedAppVersions(
    minSupportedAppVersion: String?,
    maxSupportedAppVersion: String?
) {
    val normalizedMin = normalizeAppVersionOrNull(minSupportedAppVersion)
    val normalizedMax = normalizeAppVersionOrNull(maxSupportedAppVersion)
    require(normalizedMin != null) {
        "Minimum supported app version is required"
    }
    if (normalizedMin != null && normalizedMax != null) {
        require(compareAppVersions(normalizedMin, normalizedMax) <= 0) {
            "Minimum supported app version cannot be greater than maximum supported app version"
        }
    }
}

fun compareAppVersions(left: String, right: String): Int {
    val leftVersion = requireNotNull(parseAppVersionOrNull(left))
    val rightVersion = requireNotNull(parseAppVersionOrNull(right))

    if (leftVersion.major != rightVersion.major) {
        return leftVersion.major.compareTo(rightVersion.major)
    }
    if (leftVersion.minor != rightVersion.minor) {
        return leftVersion.minor.compareTo(rightVersion.minor)
    }
    if (leftVersion.patch != rightVersion.patch) {
        return leftVersion.patch.compareTo(rightVersion.patch)
    }

    val leftBuild = leftVersion.build ?: 0
    val rightBuild = rightVersion.build ?: 0
    return leftBuild.compareTo(rightBuild)
}

fun isAppVersionSupported(
    appVersion: String,
    minSupportedAppVersion: String?,
    maxSupportedAppVersion: String?
): Boolean {
    return resolveAppVersionCompatibility(
        appVersion = appVersion,
        minSupportedAppVersion = minSupportedAppVersion,
        maxSupportedAppVersion = maxSupportedAppVersion
    ) == null
}

fun resolveAppVersionCompatibility(
    appVersion: String,
    minSupportedAppVersion: String?,
    maxSupportedAppVersion: String?
): MarketAppVersionCompatibility? {
    val current = requireNotNull(parseAppVersionOrNull(appVersion)) {
        "Current app version must use x.y.z or x.y.z+n format"
    }
    val minimum = parseAppVersionOrNull(minSupportedAppVersion)
    if (minimum != null && compareAppVersions(current.toString(), minimum.toString()) < 0) {
        return MarketAppVersionCompatibility(
            kind = MarketAppVersionCompatibilityKind.BELOW_MINIMUM,
            currentVersion = current.toString(),
            requiredVersion = minimum.toString()
        )
    }
    val maximum = parseAppVersionOrNull(maxSupportedAppVersion)
    if (maximum != null && compareAppVersions(current.toString(), maximum.toString()) > 0) {
        return MarketAppVersionCompatibility(
            kind = MarketAppVersionCompatibilityKind.ABOVE_MAXIMUM,
            currentVersion = current.toString(),
            requiredVersion = maximum.toString()
        )
    }
    return null
}

fun MarketV2Entry.resolveCurrentAppVersionCompatibility(): MarketAppVersionCompatibility? {
    val version = latestVersion ?: return null
    return resolveAppVersionCompatibility(
        appVersion = BuildConfig.VERSION_NAME,
        minSupportedAppVersion = version.minAppVer,
        maxSupportedAppVersion = version.maxAppVer
    )
}

fun MarketV2Entry.isUnsupportedByCurrentAppVersion(): Boolean {
    return resolveCurrentAppVersionCompatibility() != null
}

fun isOperit2VersionAllowed(maxSupportedAppVersion: String?): Boolean {
    val normalizedMax =
        runCatching { normalizeAppVersionOrNull(maxSupportedAppVersion) }.getOrNull()
            ?: return false
    return compareAppVersions(normalizedMax, "2.0.0") >= 0
}

fun formatSupportedAppVersions(
    minSupportedAppVersion: String?,
    maxSupportedAppVersion: String?
): String {
    val normalizedMin = normalizeAppVersionOrNull(minSupportedAppVersion)
    val normalizedMax = normalizeAppVersionOrNull(maxSupportedAppVersion)
    return when {
        normalizedMin != null && normalizedMax != null -> "$normalizedMin - $normalizedMax"
        normalizedMin != null -> ">= $normalizedMin"
        normalizedMax != null -> "<= $normalizedMax"
        else -> "Any"
    }
}

private fun inferArtifactContentType(
    type: PublishArtifactType,
    extension: String
): String {
    return when {
        type == PublishArtifactType.PACKAGE && extension == "toolpkg" -> "application/zip"
        extension == "js" -> "application/javascript"
        extension == "ts" -> "text/plain"
        extension == "json" -> "application/json"
        extension == "hjson" -> "application/json"
        extension == "zip" -> "application/zip"
        else -> "application/octet-stream"
    }
}

private fun parseAppVersionOrNull(value: String?): AppVersion? {
    val normalized = value?.trim().orEmpty()
    if (normalized.isBlank()) return null

    val match =
        APP_VERSION_REGEX.matchEntire(normalized)
            ?: throw IllegalArgumentException("App version must use x.y.z or x.y.z+n format")

    return AppVersion(
        major = match.groupValues[1].toInt(),
        minor = match.groupValues[2].toInt(),
        patch = match.groupValues[3].toInt(),
        build = match.groupValues[4].takeIf { it.isNotBlank() }?.toInt()
    )
}
