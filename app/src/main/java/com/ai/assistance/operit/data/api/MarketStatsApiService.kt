package com.ai.assistance.operit.data.api

import android.os.SystemClock
import com.ai.assistance.operit.core.application.OperitApplication
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.data.preferences.GitHubUser
import com.ai.assistance.operit.ui.features.packages.market.normalizeMarketArtifactId
import com.ai.assistance.operit.util.AppLogger
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

@Serializable
data class MarketStatsEntryResponse(
    val downloads: Int = 0,
    val lastDownloadAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class MarketTypeStatsResponse(
    val updatedAt: String? = null,
    val items: Map<String, MarketStatsEntryResponse> = emptyMap()
)

@Serializable
data class MarketRankEntryResponse(
    val id: String,
    val downloads: Int = 0,
    val lastDownloadAt: String? = null,
    val updatedAt: String? = null,
    val statsUpdatedAt: String? = null,
    val displayTitle: String = "",
    val summaryDescription: String = "",
    val authorLogin: String = "",
    val authorAvatarUrl: String = "",
    val metadata: JsonElement? = null,
    val entry: MarketV2Entry
)

@Serializable
data class MarketRankPageResponse(
    val updatedAt: String? = null,
    val type: String = "",
    val metric: String = "",
    val page: Int = 1,
    val pageSize: Int = 0,
    val totalPages: Int = 1,
    val totalItems: Int = 0,
    val items: List<MarketRankEntryResponse> = emptyList()
)

@Serializable
data class ArtifactProjectRankDefaultVersionResponse(
    val versionId: String = "",
    val runtimePackageId: String = "",
    val sha256: String = "",
    val version: String = "",
    val downloadUrl: String = "",
    val state: String = "open",
    val publishedAt: String? = null
)

@Serializable
data class ArtifactProjectRankEntryResponse(
    val entryId: String = "",
    val projectId: String = "",
    val type: String = "",
    val projectDisplayName: String = "",
    val projectDescription: String = "",
    val rootPublisherLogin: String = "",
    val rootPublisherAvatarUrl: String = "",
    val contributorCount: Int = 0,
    val downloads: Int = 0,
    val likes: Int = 0,
    val latestVersionId: String = "",
    val defaultVersionId: String = "",
    val latestPublishedAt: String? = null,
    val defaultVersion: ArtifactProjectRankDefaultVersionResponse? = null,
    val runtimePackageVersionSha256s: List<String> = emptyList()
)

@Serializable
data class ArtifactProjectRankPageResponse(
    val updatedAt: String? = null,
    val type: String = "",
    val metric: String = "",
    val page: Int = 1,
    val pageSize: Int = 0,
    val totalPages: Int = 1,
    val totalItems: Int = 0,
    val items: List<ArtifactProjectRankEntryResponse> = emptyList()
)

@Serializable
data class ArtifactProjectVersionResponse(
    val projectId: String = "",
    val type: String = "",
    val projectDisplayName: String = "",
    val projectDescription: String = "",
    val runtimePackageId: String = "",
    val versionId: String = "",
    val publisherLogin: String = "",
    val releaseTag: String = "",
    val assetName: String = "",
    val assetId: String = "",
    val downloadUrl: String = "",
    val sha256: String = "",
    val version: String = "",
    val displayName: String = "",
    val description: String = "",
    val sourceFileName: String = "",
    val minSupportedAppVersion: String? = null,
    val maxSupportedAppVersion: String? = null,
    val publishedAt: String? = null,
    val state: String = "open",
    val entry: MarketV2Entry
)

@Serializable
data class ArtifactProjectDetailResponse(
    val projectId: String = "",
    val type: String = "",
    val projectDisplayName: String = "",
    val projectDescription: String = "",
    val rootPublisherLogin: String = "",
    val rootPublisherAvatarUrl: String = "",
    val contributorCount: Int = 0,
    val downloads: Int = 0,
    val likes: Int = 0,
    val latestVersionId: String = "",
    val defaultVersionId: String = "",
    val latestPublishedAt: String? = null,
    val versions: List<ArtifactProjectVersionResponse> = emptyList()
)

@Serializable
data class MarketV2AuthResponse(
    val ok: Boolean = false,
    val session: String = "",
    val githubId: Long = 0,
    val login: String = "",
    val avatarUrl: String = ""
)

@Serializable
data class MarketV2ListResponse(
    val ok: Boolean = false,
    val marketVersion: Int = 2,
    val generatedAt: String? = null,
    val sort: String = "",
    val page: Int = 1,
    val pageSize: Int = 50,
    val total: Int = 0,
    val items: List<MarketV2Entry> = emptyList()
)

@Serializable
data class MarketV2ManifestResponse(
    val ok: Boolean = false,
    val marketVersion: Int = 2,
    val generatedAt: String? = null,
    val types: List<MarketV2ManifestType> = emptyList(),
    val categories: List<MarketV2ManifestCategory> = emptyList(),
    val states: List<MarketV2ManifestState> = emptyList()
)

@Serializable
data class MarketV2ManifestType(
    val id: String = "",
    val name: String = "",
    val description: String = ""
)

@Serializable
data class MarketV2ManifestCategory(
    val id: String = "",
    val name: String = "",
    val description: String = ""
)

@Serializable
data class MarketV2ManifestState(
    val code: String = "",
    val publicListed: Boolean = false
)

@Serializable
data class MarketV2EntriesShardResponse(
    val ok: Boolean = false,
    val marketVersion: Int = 2,
    val generatedAt: String? = null,
    val shard: String = "",
    val entriesById: Map<String, MarketV2Entry> = emptyMap()
)

@Serializable
data class MarketV2MyEntriesResponse(
    val ok: Boolean = false,
    val entries: MarketV2PublisherEntriesResponse = MarketV2PublisherEntriesResponse()
)

@Serializable
data class MarketV2PublisherEntriesResponse(
    val ok: Boolean = false,
    val generatedAt: String? = null,
    val shard: String = "",
    val entries: List<MarketV2PublisherEntrySummary> = emptyList()
)

@Serializable
data class MarketV2PublisherShardResponse(
    val ok: Boolean = false,
    val marketVersion: Int = 2,
    val generatedAt: String? = null,
    val shard: String = "",
    val authors: Map<String, MarketV2PublisherEntriesResponse> = emptyMap()
)

@Serializable
data class MarketV2PublisherEntrySummary(
    val id: String = "",
    val title: String = "",
    val type: String = "",
    val relation: String,
    val stateCode: String = "pending",
    val listingState: String = "",
    val categoryId: String = "",
    val updatedAt: String = "",
    val reasonCodes: List<String> = emptyList(),
    val reviewDetail: String? = null,
    val reviewDetailUpdatedAt: String? = null,
    /**
     * Server-computed time at which a changes-requested revision may be
     * submitted.  It is intentionally optional for older private shards and
     * for entries that are not currently waiting for changes.
     */
    val revisionAvailableAt: String? = null
)

@Serializable
data class MarketV2EntryResponse(
    val ok: Boolean = false,
    val item: MarketV2Entry? = null,
    val entry: MarketV2Entry? = null
)

@Serializable
data class MarketV2CommentsPageResponse(
    val ok: Boolean = false,
    val marketVersion: Int = 2,
    val entryId: String = "",
    val page: Int = 1,
    val pageSize: Int = 50,
    val total: Int = 0,
    val items: List<MarketV2Comment> = emptyList(),
    val generatedAt: String? = null
)

@Serializable
data class MarketV2CommentResponse(
    val ok: Boolean = false,
    val item: MarketV2Comment? = null,
    val comment: MarketV2Comment? = null
)

@Serializable
data class MarketV2NotificationsResponse(
    val ok: Boolean = false,
    val items: List<MarketV2Notification> = emptyList()
)

@Serializable
data class MarketV2PublishResponse(
    val ok: Boolean = false,
    val item: MarketV2Entry? = null,
    val entry: MarketV2Entry? = null,
    val entryId: String? = null,
    val versionId: String? = null
)

@Serializable
data class MarketV2NewVersionResponse(
    val ok: Boolean = false,
    val entryId: String = "",
    val versionId: String = ""
)

@Serializable
data class MarketV2Entry(
    val type: String = "",
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val detail: String = "",
    val authorId: String = "",
    val publisherId: String = "",
    val allowPublicUpdates: Boolean = true,
    val categoryId: String = "",
    val stateCode: String = "approved",
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val publishedAt: String? = null,
    val source: MarketV2Source? = null,
    val artifact: MarketV2Artifact? = null,
    val assets: List<MarketV2Asset> = emptyList(),
    val versions: List<MarketV2Version> = emptyList(),
    val latestVersion: MarketV2Version? = null,
    val reactions: List<MarketV2Reaction> = emptyList(),
    val downloads: Int = 0,
    val downloadCount: Int = 0,
    val stats: MarketV2EntryStats? = null,
    val author: MarketV2Author? = null,
    val publisher: MarketV2Author? = null,
    val contributors: List<MarketV2Author> = emptyList(),
    val featured: Boolean = false,
)

@Serializable
data class MarketV2EntryStats(
    val downloads: Int = 0,
    val likes: Int = 0,
    val lastDownloadAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class MarketV2Author(
    val id: String = "",
    val githubId: Long? = null,
    val login: String = "",
    val avatar: String? = null,
    val avatarUrl: String? = null,
    val status: String = ""
)

@Serializable
data class MarketV2Source(
    val kind: String = "",
    val url: String = ""
)

@Serializable
data class MarketV2Artifact(
    val projectId: String = "",
    val runtimePkg: String? = null
)

@Serializable
data class MarketV2Asset(
    val id: String = "",
    val versionId: String = "",
    val kind: String = "",
    val url: String = "",
    val sha256: String = "",
    val ghOwner: String = "",
    val ghRepo: String = "",
    val ghReleaseTag: String = "",
    val name: String = "",
    val assetName: String = ""
)

@Serializable
data class MarketV2Version(
    val id: String = "",
    val version: String = "",
    val formatVer: String = "",
    val publisherId: String = "",
    val publisher: MarketV2Author? = null,
    val minAppVer: String? = null,
    val maxAppVer: String? = null,
    val changelog: String = "",
    val installConfig: String = "",
    val stateCode: String = "approved",
    val publishedAt: String? = null,
    val projectId: String = "",
    val runtimePackageId: String = ""
)

@Serializable
data class MarketV2Reaction(
    val reaction: String = "",
    val content: String = "",
    val total: Int = 0
)

@Serializable
data class MarketV2Comment(
    val id: String = "",
    val entryId: String = "",
    val parentId: String? = null,
    val author: MarketV2Author = MarketV2Author(),
    val body: String = "",
    val createdAt: String = "",
    val updatedAt: String = ""
)

@Serializable
data class MarketV2Notification(
    val id: String = "",
    val kind: String = "",
    val entryId: String = "",
    val commentId: String? = null,
    val actorId: String? = null,
    val title: String = "",
    val body: String = "",
    val createdAt: String = ""
)

@Serializable
data class MarketV2PublishRequest(
    val type: String,
    val title: String,
    val description: String,
    val detail: String = "",
    val categoryId: String = "",
    val allowPublicUpdates: Boolean = true,
    val version: MarketV2PublishVersion,
    val source: MarketV2PublishSource? = null,
    val repoVersion: MarketV2PublishRepoVersion? = null,
    val asset: MarketV2PublishAsset? = null
)

@Serializable
data class MarketV2NewVersionRequest(
    val entry: MarketV2NewVersionEntryPatch? = null,
    val version: MarketV2PublishVersion,
    val repoVersion: MarketV2PublishRepoVersion? = null,
    val asset: MarketV2PublishAsset? = null
)

@Serializable
data class MarketV2NewVersionEntryPatch(
    val title: String? = null,
    val description: String? = null,
    val detail: String? = null,
    val categoryId: String? = null,
    val allowPublicUpdates: Boolean? = null
)

@Serializable
data class MarketV2EntryUpdateRequest(
    val title: String,
    val description: String,
    val detail: String = "",
    val categoryId: String = "",
    val allowPublicUpdates: Boolean? = null
)

@Serializable
data class MarketV2PublishVersion(
    val version: String,
    val formatVer: String,
    val minAppVer: String,
    val maxAppVer: String? = null,
    val changelog: String? = null,
    val projectId: String? = null,
    val runtimePackageId: String? = null
)

@Serializable
data class MarketV2PublishSource(
    val kind: String,
    val url: String
)

@Serializable
data class MarketV2PublishRepoVersion(
    val refType: String,
    val refName: String,
    val installConfig: String = "{}"
)

@Serializable
data class MarketV2PublishAsset(
    val kind: String,
    val url: String,
    val ghOwner: String = "",
    val ghRepo: String = "",
    val ghReleaseTag: String = "",
    val assetName: String = "",
    val sha256: String = ""
)

@Serializable
private data class MarketV2CommentCreateRequest(
    val body: String,
    val parentId: String? = null
)

class MarketStatsApiService {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val staticClient = STATIC_CLIENT
    private val dynamicClient = DYNAMIC_CLIENT
    private val authPreferences = GitHubAuthPreferences.getInstance(OperitApplication.instance)

    suspend fun getManifest(): Result<MarketV2ManifestResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                requestStaticJson(
                    pathSegments = listOf("market", "v2", "manifest.json"),
                    label = "getManifest"
                ) { body ->
                    json.decodeFromString(MarketV2ManifestResponse.serializer(), body)
                }
            }
        }

    suspend fun getStats(type: String): Result<MarketTypeStatsResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val firstPage =
                    requestStaticJson(
                        pathSegments = typeListPathSegments(type, "updated", 1),
                        label = "getStats type=$type"
                    ) { body ->
                        json.decodeFromString(MarketV2ListResponse.serializer(), body)
                    }
                MarketTypeStatsResponse(
                    updatedAt = firstPage.generatedAt,
                    items =
                        firstPage.items.associate { entry ->
                            entry.id to
                                MarketStatsEntryResponse(
                                    downloads = entry.downloadCountValue(),
                                    lastDownloadAt = entry.stats?.lastDownloadAt,
                                    updatedAt = entry.stats?.updatedAt ?: entry.updatedAt
                                )
                        }
                )
            }
        }

    suspend fun getRankPage(
        type: String,
        metric: String,
        page: Int
    ): Result<MarketRankPageResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sort = metric.toV2Sort()
                val response =
                    requestStaticJson(
                        pathSegments = typeListPathSegments(type, sort, page),
                        label = "getRankPage type=$type metric=$metric page=$page"
                    ) { body ->
                        json.decodeFromString(MarketV2ListResponse.serializer(), body)
                    }
                MarketRankPageResponse(
                    updatedAt = response.generatedAt,
                    type = type,
                    metric = metric,
                    page = response.page,
                    pageSize = response.pageSize,
                    totalPages = ((response.total + response.pageSize - 1) / response.pageSize).coerceAtLeast(1),
                    totalItems = response.total,
                    items = response.items.map { it.toRankEntry() }
                )
            }
        }

    suspend fun getAllRankPage(
        metric: String,
        page: Int
    ): Result<MarketRankPageResponse> =
        getListRankPage(
            pathSegments = allListPathSegments(metric.toV2Sort(), page),
            label = "getAllRankPage metric=$metric page=$page",
            type = "",
            metric = metric
        )

    suspend fun getTypeRankPage(
        type: String,
        metric: String,
        page: Int
    ): Result<MarketRankPageResponse> =
        getListRankPage(
            pathSegments = typeListPathSegments(type, metric.toV2Sort(), page),
            label = "getTypeRankPage type=$type metric=$metric page=$page",
            type = type,
            metric = metric
        )

    suspend fun getCategoryRankPage(
        categoryId: String,
        metric: String,
        page: Int
    ): Result<MarketRankPageResponse> =
        getListRankPage(
            pathSegments = categoryListPathSegments(categoryId, metric.toV2Sort(), page),
            label = "getCategoryRankPage categoryId=$categoryId metric=$metric page=$page",
            type = "",
            metric = metric
        )

    suspend fun getTypedCategoryRankPage(
        type: String,
        categoryId: String,
        metric: String,
        page: Int
    ): Result<MarketRankPageResponse> =
        getListRankPage(
            pathSegments = typedCategoryListPathSegments(type, categoryId, metric.toV2Sort(), page),
            label = "getTypedCategoryRankPage type=$type categoryId=$categoryId metric=$metric page=$page",
            type = type,
            metric = metric
        )

    suspend fun getArtifactRankPage(
        type: String,
        metric: String,
        page: Int
    ): Result<ArtifactProjectRankPageResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sort = metric.toV2Sort()
                val response =
                    requestStaticJson(
                        pathSegments = typeListPathSegments(type, sort, page),
                        label = "getArtifactRankPage type=$type metric=$metric page=$page"
                    ) { body ->
                        json.decodeFromString(MarketV2ListResponse.serializer(), body)
                    }
                val projects =
                    response.items
                        .map { it.toArtifactProjectRankEntry() }
                ArtifactProjectRankPageResponse(
                    updatedAt = response.generatedAt,
                    type = type,
                    metric = metric,
                    page = response.page,
                    pageSize = response.pageSize,
                    totalPages = ((response.total + response.pageSize - 1) / response.pageSize).coerceAtLeast(1),
                    totalItems = response.total,
                    items = projects
                )
            }
        }

    private suspend fun getListRankPage(
        pathSegments: List<String>,
        label: String,
        type: String,
        metric: String
    ): Result<MarketRankPageResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response =
                    requestStaticJson(
                        pathSegments = pathSegments,
                        label = label
                    ) { body ->
                        json.decodeFromString(MarketV2ListResponse.serializer(), body)
                    }
                MarketRankPageResponse(
                    updatedAt = response.generatedAt,
                    type = type,
                    metric = metric,
                    page = response.page,
                    pageSize = response.pageSize,
                    totalPages = ((response.total + response.pageSize - 1) / response.pageSize).coerceAtLeast(1),
                    totalItems = response.total,
                    items = response.items.map { it.toRankEntry() }
                )
            }
        }

    suspend fun getArtifactProject(
        projectId: String
    ): Result<ArtifactProjectDetailResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val entry = getEntry(projectId).getOrElse { error -> throw error }
                    ?: error("Entry not found: $projectId")
                artifactProjectFromEntry(entry)
            }
        }

    fun artifactProjectFromEntry(entry: MarketV2Entry): ArtifactProjectDetailResponse {
        val artifact = entry.artifact ?: error("Entry is not artifact: ${entry.id}")
        val versions = entry.versions
        val latestVersion = versions.firstOrNull() ?: error("Entry versions is empty: ${entry.id}")
        val assetByVersionId = entry.assets.associateBy { it.versionId }
        val artifactVersions = versions.map { version ->
            val runtimePackageId = version.runtimePackageId.ifBlank { error("Artifact runtime package id not found for version: ${version.id}") }
            val asset = assetByVersionId[version.id] ?: error("Artifact asset not found for version: ${version.id}")
            ArtifactProjectVersionResponse(
                projectId = artifact.projectId,
                type = entry.type,
                projectDisplayName = entry.title,
                projectDescription = entry.description,
                runtimePackageId = runtimePackageId,
                versionId = version.id,
                publisherLogin = entry.publisherLogin(),
                releaseTag = "",
                assetName = asset.assetName.ifBlank { asset.name },
                assetId = asset.id,
                downloadUrl = downloadUrlForAsset(asset.id),
                sha256 = asset.sha256,
                version = version.version,
                displayName = entry.title,
                description = entry.detail,
                sourceFileName = asset.assetName.ifBlank { asset.name },
                minSupportedAppVersion = version.minAppVer,
                maxSupportedAppVersion = version.maxAppVer,
                publishedAt = version.publishedAt,
                state = version.stateCode.toPublicationState(),
                entry = entry.copy(
                    latestVersion = version,
                    assets = listOf(asset)
                )
            )
        }
        return ArtifactProjectDetailResponse(
            projectId = artifact.projectId,
            type = entry.type,
            projectDisplayName = entry.title,
            projectDescription = entry.description,
            rootPublisherLogin = entry.publisherLogin(),
            rootPublisherAvatarUrl = entry.publisherAvatarUrl(),
            contributorCount = 1,
            downloads = entry.downloadCountValue(),
            likes = entry.likesCount(),
            latestVersionId = latestVersion.id,
            defaultVersionId = latestVersion.id,
            latestPublishedAt = latestVersion.publishedAt,
            versions = artifactVersions
        )
    }

    suspend fun trackDownload(assetId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolvedAssetId = assetId.trim().ifBlank { error("Missing v2 asset id") }
                requestDynamic(
                    method = "GET",
                    pathSegments = listOf("market", "v2", "assets", resolvedAssetId, "download"),
                    label = "trackDownload assetId=$resolvedAssetId",
                    includeMarketSession = false,
                    followRedirects = false
                ) { _, response ->
                    if (response.code in 300..399 || response.isSuccessful) Unit else error("Download tracking failed")
                }
            }
        }

    fun downloadUrlForAsset(assetId: String): String {
        val resolvedAssetId = assetId.trim()
        if (resolvedAssetId.isBlank()) return ""
        val urlBuilder = BASE_URL.newBuilder()
        listOf("market", "v2", "assets", resolvedAssetId, "download").forEach(urlBuilder::addPathSegment)
        return urlBuilder.build().toString()
    }

    suspend fun getEntry(entryId: String): Result<MarketV2Entry?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val shard = entryId.marketShard()
                requestStaticJson(
                    pathSegments = listOf("market", "v2", "entries", "$shard.json"),
                    label = "getEntry entryId=$entryId shard=$shard"
                ) { body ->
                    json.decodeFromString(MarketV2EntriesShardResponse.serializer(), body)
                }.entriesById[entryId]
            }
        }

    suspend fun getComments(entryId: String, page: Int = 1): Result<List<MarketV2Comment>> =
        withContext(Dispatchers.IO) {
            runCatching {
                requestStaticJson(
                    pathSegments = listOf("market", "v2", "comments", entryId, "page-$page.json"),
                    label = "getComments entryId=$entryId page=$page",
                    notFoundValue = MarketV2CommentsPageResponse(entryId = entryId, page = page)
                ) { body ->
                    json.decodeFromString(MarketV2CommentsPageResponse.serializer(), body)
                }.items
            }
        }

    suspend fun postComment(entryId: String, body: String, parentId: String? = null): Result<MarketV2Comment> =
        withContext(Dispatchers.IO) {
            runCatching {
                val requestJson =
                    json.encodeToString(MarketV2CommentCreateRequest(body = body, parentId = parentId))
                requestDynamic(
                    method = "POST",
                    pathSegments = listOf("market", "v2", "entries", entryId, "comments"),
                    body = requestJson,
                    label = "postComment entryId=$entryId"
                ) { responseBody, _ ->
                    val response = json.decodeFromString(MarketV2CommentResponse.serializer(), responseBody)
                    response.item ?: response.comment ?: MarketV2Comment(entryId = entryId, body = body)
                }
            }
        }

    suspend fun addReaction(entryId: String): Result<MarketV2Reaction> =
        withContext(Dispatchers.IO) {
            runCatching {
                requestDynamic(
                    method = "POST",
                    pathSegments = listOf("market", "v2", "entries", entryId, "reactions"),
                    label = "addReaction entryId=$entryId"
                ) { _, _ ->
                    MarketV2Reaction(reaction = "+1", content = "+1", total = 1)
                }
            }
        }

    suspend fun getUserPublishedEntries(type: String? = null): Result<List<MarketV2PublisherEntrySummary>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response =
                    requestDynamic(
                        method = "GET",
                        pathSegments = listOf("market", "v2", "my", "entries"),
                        queryParameters =
                            if (type.isNullOrBlank()) emptyMap() else mapOf("type" to type),
                        label = "getUserPublishedEntries type=$type"
                    ) { body, _ ->
                        json.decodeFromString(MarketV2MyEntriesResponse.serializer(), body)
                    }
                response.entries.entries
                    .sortedByDescending { it.updatedAt }
            }
        }

    /**
     * Loads the authenticated publisher's full entry data, including an unpublished
     * latest version. This is required to prepare a revision after review changes.
     */
    suspend fun getMyEntryDetail(entryId: String): Result<MarketV2Entry> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolvedEntryId = entryId.trim().ifBlank { error("Missing entry id") }
                requestDynamic(
                    method = "GET",
                    pathSegments = listOf("market", "v2", "my", "entries", resolvedEntryId, "detail"),
                    label = "getMyEntryDetail entryId=$resolvedEntryId"
                ) { body, _ ->
                    val response = json.decodeFromString(MarketV2EntryResponse.serializer(), body)
                    response.item ?: response.entry ?: error("Entry detail not found")
                }
            }
        }

    suspend fun getPublisherEntries(authorId: String): Result<List<MarketV2PublisherEntrySummary>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolvedAuthorId = authorId.trim().ifBlank { error("Missing author id") }
                val shard = resolvedAuthorId.marketShard()
                requestStaticJson(
                    pathSegments = listOf("market", "v2", "private", "publishers", "$shard.json"),
                    label = "getPublisherEntries authorId=$resolvedAuthorId shard=$shard"
                ) { body ->
                    json.decodeFromString(MarketV2PublisherShardResponse.serializer(), body)
                }.authors.getValue(resolvedAuthorId).entries.sortedByDescending { it.updatedAt }
            }
        }

    suspend fun getNotifications(limit: Int = 50, offset: Int = 0): Result<List<MarketV2Notification>> =
        withContext(Dispatchers.IO) {
            runCatching {
                requestDynamic(
                    method = "GET",
                    pathSegments = listOf("market", "v2", "notifications"),
                    queryParameters = mapOf("limit" to limit.toString(), "offset" to offset.toString()),
                    label = "getNotifications limit=$limit offset=$offset"
                ) { body, _ ->
                    json.decodeFromString(MarketV2NotificationsResponse.serializer(), body).items
                }
            }
        }

    suspend fun editComment(commentId: String, body: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val requestJson =
                    json.encodeToString(MarketV2CommentCreateRequest(body = body))
                requestDynamic(
                    method = "PATCH",
                    pathSegments = listOf("market", "v2", "comments", commentId),
                    body = requestJson,
                    label = "editComment commentId=$commentId"
                ) { _, _ -> Unit }
            }
        }

    suspend fun deleteComment(commentId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                requestDynamic(
                    method = "DELETE",
                    pathSegments = listOf("market", "v2", "comments", commentId),
                    label = "deleteComment commentId=$commentId"
                ) { _, _ -> Unit }
            }
        }

    suspend fun publish(request: MarketV2PublishRequest): Result<MarketV2Entry> =
        withContext(Dispatchers.IO) {
            runCatching {
                val requestJson = json.encodeToString(request)
                requestDynamic(
                    method = "POST",
                    pathSegments = listOf("market", "v2", "publish"),
                    body = requestJson,
                    label = "publish type=${request.type} title=${request.title}"
                ) { body, _ ->
                    val response = json.decodeFromString(MarketV2PublishResponse.serializer(), body)
                    val item =
                        response.item ?: response.entry ?: MarketV2Entry(
                            type = request.type,
                            id = response.entryId ?: normalizeMarketArtifactId(request.title),
                            title = request.title,
                            description = request.description,
                            detail = request.detail,
                            stateCode = "pending",
                            source = request.source?.let { MarketV2Source(kind = it.kind, url = it.url) }
                        )
                    item
                }
            }
        }

    suspend fun updateEntry(entryId: String, request: MarketV2EntryUpdateRequest): Result<MarketV2Entry> =
        withContext(Dispatchers.IO) {
            runCatching {
                requestDynamic(
                    method = "PATCH",
                    pathSegments = listOf("market", "v2", "entries", entryId),
                    body = json.encodeToString(request),
                    label = "updateEntry entryId=$entryId"
                ) { body, _ ->
                    runCatching {
                        json.decodeFromString(MarketV2PublishResponse.serializer(), body)
                    }.getOrNull()?.let { response ->
                        response.item ?: response.entry
                    } ?: request.toPendingEntry(entryId)
                }
            }
        }

    suspend fun publishNewVersion(
        entryId: String,
        request: MarketV2PublishRequest,
        entryPatch: MarketV2NewVersionEntryPatch? = null
    ): Result<MarketV2NewVersionResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolvedEntryId = entryId.trim().ifBlank { error("Missing entry id") }
                requestDynamic(
                    method = "POST",
                    pathSegments = listOf("market", "v2", "entries", resolvedEntryId, "versions"),
                    body =
                        json.encodeToString(
                            MarketV2NewVersionRequest(
                                entry = entryPatch,
                                version = request.version,
                                repoVersion = request.repoVersion,
                                asset = request.asset
                            )
                        ),
                    label = "publishNewVersion entryId=$resolvedEntryId version=${request.version.version}"
                ) { body, _ ->
                    json.decodeFromString(MarketV2NewVersionResponse.serializer(), body)
                }
            }
        }

    suspend fun withdrawEntry(entryId: String): Result<MarketV2Entry> =
        withContext(Dispatchers.IO) {
            runCatching {
                requestDynamic(
                    method = "DELETE",
                    pathSegments = listOf("market", "v2", "entries", entryId),
                    label = "withdrawEntry entryId=$entryId"
                ) { _, _ ->
                    (getEntry(entryId).getOrNull() ?: MarketV2Entry(id = entryId, stateCode = "withdrawn"))
                        .copy(stateCode = "withdrawn")
                }
            }
        }

    private inline fun <T> requestStaticJson(
        pathSegments: List<String>,
        label: String,
        notFoundValue: T? = null,
        decode: (String) -> T
    ): T {
        val urlBuilder = STATIC_BASE_URL.newBuilder()
        pathSegments.forEach(urlBuilder::addPathSegment)
        val url = urlBuilder.build()

        val request =
            Request.Builder()
                .url(url)
                .get()
                .addHeader("User-Agent", USER_AGENT)
                .build()

        val startedAt = SystemClock.elapsedRealtime()
        AppLogger.d(TAG, "HTTP GET $label url=$url")
        staticClient.newCall(request).execute().use { response ->
            AppLogger.d(
                TAG,
                "HTTP RESP $label code=${response.code} source=${resolveResponseSource(response)} elapsed=${SystemClock.elapsedRealtime() - startedAt}ms url=$url"
            )
            val body = response.body?.string().orEmpty()
            if (response.code == 404 && notFoundValue != null) {
                return notFoundValue
            }
            if (!response.isSuccessful) {
                error(buildHttpErrorMessage(response, body))
            }
            return decode(body)
        }
    }

    private inline fun <T> requestDynamic(
        method: String,
        pathSegments: List<String>,
        label: String,
        body: String? = null,
        queryParameters: Map<String, String> = emptyMap(),
        includeMarketSession: Boolean = true,
        followRedirects: Boolean = true,
        decode: (String, Response) -> T
    ): T {
        val urlBuilder = BASE_URL.newBuilder()
        pathSegments.forEach(urlBuilder::addPathSegment)
        queryParameters.forEach { (key, value) -> urlBuilder.addQueryParameter(key, value) }
        val url = urlBuilder.build()

        val requestBuilder =
            Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)

        if (includeMarketSession) {
            requestBuilder.addHeader("Authorization", "Bearer ${ensureMarketSession()}")
        }

        val requestBody = body?.toRequestBody(JSON_MEDIA_TYPE)
        when (method.uppercase()) {
            "GET" -> requestBuilder.get()
            "POST" -> requestBuilder.post(requestBody ?: ByteArray(0).toRequestBody(JSON_MEDIA_TYPE))
            "PATCH" -> requestBuilder.patch(requestBody ?: ByteArray(0).toRequestBody(JSON_MEDIA_TYPE))
            "DELETE" -> requestBuilder.delete(requestBody)
            else -> error("Unsupported HTTP method: $method")
        }

        val client = if (followRedirects) dynamicClient else NO_REDIRECT_DYNAMIC_CLIENT
        val startedAt = SystemClock.elapsedRealtime()
        AppLogger.d(TAG, "HTTP $method $label url=$url")
        client.newCall(requestBuilder.build()).execute().use { response ->
            AppLogger.d(
                TAG,
                "HTTP RESP $label code=${response.code} elapsed=${SystemClock.elapsedRealtime() - startedAt}ms url=$url"
            )
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful && response.code !in 300..399) {
                error(buildHttpErrorMessage(response, responseBody))
            }
            return decode(responseBody, response)
        }
    }

    private fun ensureMarketSession(): String {
        val cached = marketSession
        if (!cached.isNullOrBlank()) return cached

        return synchronized(MARKET_SESSION_LOCK) {
            val lockedCached = marketSession
            if (!lockedCached.isNullOrBlank()) {
                lockedCached
            } else {
                val githubToken =
                    kotlinx.coroutines.runBlocking {
                        authPreferences.getCurrentAccessToken()
                    } ?: error("GitHub login required")
                val session = requestMarketSession(githubToken)
                marketSession = session
                session
            }
        }
    }

    private fun requestMarketSession(githubToken: String): String {
        val urlBuilder = BASE_URL.newBuilder()
        listOf("market", "v2", "auth", "github").forEach(urlBuilder::addPathSegment)
        val request =
            Request.Builder()
                .url(urlBuilder.build())
                .post(ByteArray(0).toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Authorization", "Bearer $githubToken")
                .build()
        val startedAt = SystemClock.elapsedRealtime()
        AppLogger.d(TAG, "HTTP POST authGithub url=${request.url}")
        dynamicClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            AppLogger.d(TAG, "HTTP RESP authGithub code=${response.code} elapsed=${SystemClock.elapsedRealtime() - startedAt}ms url=${request.url}")
            if (!response.isSuccessful) {
                error(buildHttpErrorMessage(response, responseBody))
            }
            val auth = json.decodeFromString(MarketV2AuthResponse.serializer(), responseBody)
            if (!auth.ok || auth.session.isBlank()) {
                error("Market authentication failed")
            }
            return auth.session
        }
    }

    private fun buildHttpErrorMessage(
        response: Response,
        body: String
    ): String {
        val requestPath = response.request.url.encodedPath
        val summary =
            when {
                body.isBlank() -> ""
                body.contains("<html", ignoreCase = true) || body.contains("<!DOCTYPE html", ignoreCase = true) ->
                    " [html body omitted]"
                else -> " ${body.lineSequence().firstOrNull().orEmpty().trim().take(180)}"
            }

        return "HTTP ${response.code}: ${response.message} ($requestPath)$summary"
    }

    private fun resolveResponseSource(response: Response): String {
        return when {
            response.cacheResponse != null && response.networkResponse == null -> "cache"
            response.cacheResponse != null && response.networkResponse != null -> "conditional-cache"
            else -> "network"
        }
    }

    private fun String.toV2Sort(): String {
        return when (lowercase()) {
            "likes" -> "likes"
            "downloads" -> "downloads"
            else -> "updated"
        }
    }

    private fun allListPathSegments(sort: String, page: Int): List<String> {
        return listOf("market", "v2", "lists", "all", sort, "page-$page.json")
    }

    private fun typeListPathSegments(type: String, sort: String, page: Int): List<String> {
        return listOf("market", "v2", "lists", "type", type.lowercase(), sort, "page-$page.json")
    }

    private fun categoryListPathSegments(categoryId: String, sort: String, page: Int): List<String> {
        return listOf("market", "v2", "lists", "category", categoryId.lowercase(), sort, "page-$page.json")
    }

    private fun typedCategoryListPathSegments(
        type: String,
        categoryId: String,
        sort: String,
        page: Int
    ): List<String> {
        return listOf("market", "v2", "lists", "type", type.lowercase(), "category", categoryId.lowercase(), sort, "page-$page.json")
    }

    private fun MarketV2Entry.toRankEntry(): MarketRankEntryResponse {
        return MarketRankEntryResponse(
            id = id,
            downloads = downloadCountValue(),
            lastDownloadAt = stats?.lastDownloadAt,
            updatedAt = updatedAt,
            statsUpdatedAt = stats?.updatedAt ?: updatedAt,
            displayTitle = title,
            summaryDescription = description,
            authorLogin = publisherLogin(),
            authorAvatarUrl = publisherAvatarUrl(),
            metadata = buildJsonObject {
                put("entryId", id)
                put("type", type)
                put("repositoryUrl", source?.url.orEmpty())
                put("categoryId", categoryId)
                put("version", latestVersion?.version.orEmpty())
                put("detail", detail)
            },
            entry = this
        )
    }

    private fun MarketV2Entry.toArtifactProjectRankEntry(): ArtifactProjectRankEntryResponse {
        val artifactValue = artifact
        val version = latestVersion
        val versionId = version?.id.orEmpty().ifBlank { id }
        val asset = assets.firstOrNull { it.versionId == version?.id }
        return ArtifactProjectRankEntryResponse(
            entryId = id,
            projectId = artifactValue?.projectId.orEmpty().ifBlank { normalizeMarketArtifactId(id) },
            type = type,
            projectDisplayName = title,
            projectDescription = description,
            rootPublisherLogin = publisherLogin(),
            rootPublisherAvatarUrl = publisherAvatarUrl(),
            contributorCount = 1,
            downloads = downloadCountValue(),
            likes = likesCount(),
            latestVersionId = versionId,
            defaultVersionId = versionId,
            latestPublishedAt = publishedAt ?: updatedAt,
            defaultVersion =
                ArtifactProjectRankDefaultVersionResponse(
                    versionId = versionId,
                    runtimePackageId = version?.runtimePackageId.orEmpty(),
                    sha256 = asset?.sha256.orEmpty(),
                    version = latestVersion?.version.orEmpty(),
                    downloadUrl = asset?.id?.let(::downloadUrlForAsset).orEmpty(),
                    state = stateCode.toPublicationState(),
                    publishedAt = publishedAt ?: updatedAt
                ),
            runtimePackageVersionSha256s = listOf(asset?.sha256.orEmpty()).filter(String::isNotBlank)
        )
    }

    private fun MarketV2PublishRequest.toPendingEntry(entryId: String): MarketV2Entry {
        return MarketV2Entry(
            type = type,
            id = entryId,
            title = title,
            description = description,
            detail = detail,
            categoryId = categoryId,
            allowPublicUpdates = allowPublicUpdates,
            stateCode = "pending",
            source = null
        )
    }

    private fun MarketV2EntryUpdateRequest.toPendingEntry(entryId: String): MarketV2Entry {
        return MarketV2Entry(
            id = entryId,
            title = this.title,
            description = this.description,
            detail = this.detail,
            categoryId = this.categoryId,
            allowPublicUpdates = this.allowPublicUpdates ?: true,
            stateCode = "pending"
        )
    }

    private fun MarketV2Entry.publisherUser(): GitHubUser {
        return (publisher ?: author)?.toPublisherUser()
            ?: GitHubUser(
                id = stableLongId(publisherId.ifBlank { authorId }),
                login = publisherLogin(),
                avatarUrl = publisherAvatarUrl()
            )
    }


    private fun MarketV2Author.toPublisherUser(): GitHubUser {
        return GitHubUser(
            id = githubId ?: stableLongId(id.ifBlank { login }),
            login = login.ifBlank { id },
            avatarUrl = avatarUrl ?: avatar ?: ""
        )
    }
    private fun MarketV2Entry.publisherLogin(): String {
        return publisher?.login?.ifBlank { author?.login.orEmpty() }
            ?.ifBlank { publisherId.removePrefix("gh_").ifBlank { authorId.removePrefix("gh_") } }
            .orEmpty()
    }

    private fun MarketV2Entry.publisherAvatarUrl(): String {
        return publisher?.avatarUrl ?: publisher?.avatar ?: author?.avatarUrl ?: author?.avatar ?: ""
    }

    private fun MarketV2Entry.downloadCountValue(): Int {
        return stats?.downloads ?: downloadCount.takeIf { it > 0 } ?: downloads
    }

    private fun MarketV2Entry.likesCount(): Int {
        return stats?.likes ?: reactions.sumOf { reaction ->
            val key = reaction.reaction.ifBlank { reaction.content }
            if (key == "+1" || key.equals("like", ignoreCase = true)) reaction.total.coerceAtLeast(1) else 0
        }
    }

    private fun String.toPublicationState(): String {
        return if (equals("withdrawn", ignoreCase = true)) "closed" else "open"
    }

    private fun String.marketShard(): String {
        return fnv1a32Hex(this).take(2)
    }

    private fun stableLongId(value: String): Long {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        var result = 0L
        for (index in 0 until 8) {
            result = (result shl 8) or (bytes[index].toLong() and 0xff)
        }
        return result and Long.MAX_VALUE
    }

    private fun sha256Hex(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun fnv1a32Hex(value: String): String {
        var hash = 0x811c9dc5u
        value.forEach { char ->
            hash = hash xor char.code.toUInt()
            hash *= 0x01000193u
        }
        return hash.toString(16)
    }

    companion object {
        private const val TAG = "MarketStatsApiService"
        private const val USER_AGENT = "Operit-Market-V2"
        private const val TIMEOUT_SECONDS = 15L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val BASE_URL = "https://api.operit.app".toHttpUrl()
        private val STATIC_BASE_URL = "https://static.operit.app".toHttpUrl()

        @Volatile
        private var marketSession: String? = null
        private val MARKET_SESSION_LOCK = Any()

        private val STATIC_CLIENT by lazy {
            OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }

        private val DYNAMIC_CLIENT by lazy {
            OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }

        private val NO_REDIRECT_DYNAMIC_CLIENT by lazy {
            DYNAMIC_CLIENT.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
        }
    }
}
