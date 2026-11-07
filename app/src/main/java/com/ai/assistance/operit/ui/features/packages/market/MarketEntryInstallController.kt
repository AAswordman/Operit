package com.ai.assistance.operit.ui.features.packages.market

import android.content.Context
import android.widget.Toast
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.api.MarketStatsApiService
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.mcp.InstallResult
import com.ai.assistance.operit.data.mcp.MCPLocalServer
import com.ai.assistance.operit.data.mcp.McpConfigImportParser
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.skill.SkillRepository
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MarketEntryInstallController(
    private val context: Context,
    private val marketStatsApiService: MarketStatsApiService
) {
    private val appContext = context.applicationContext
    private val skillRepository = SkillRepository.getInstance(appContext)
    private val mcpRepository = MCPRepository(appContext)
    private val packageManager =
        PackageManager.getInstance(appContext, AIToolHandler.getInstance(appContext))

    suspend fun install(
        entry: MarketV2Entry,
        onProgress: MarketInstallProgressReporter = { _, _ -> }
    ) {
        entry.resolveToolPkgApiCompatibility()?.let { incompatibility ->
            throw IllegalArgumentException(
                context.getString(
                    R.string.market_toolpkg_api_incompatible_message,
                    incompatibility.requiredApiVersion,
                    incompatibility.supportedApiVersions
                )
            )
        }
        onProgress(MarketInstallStage.CONNECTING, null)
        when (entry.type.lowercase()) {
            "skill" -> installSkillEntry(entry, onProgress)
            "mcp" -> installMcpEntry(entry, onProgress)
            "script",
            "package" -> installArtifactEntry(entry, onProgress)
            else -> throw IllegalArgumentException("Unsupported market entry type: ${entry.type}")
        }
    }

    private suspend fun installSkillEntry(
        entry: MarketV2Entry,
        onProgress: MarketInstallProgressReporter
    ) {
        val repoUrl = entry.source?.url.orEmpty().trim()
        if (repoUrl.isBlank()) {
            throw IllegalStateException(context.getString(R.string.skillmarket_invalid_repo_url))
        }
        onProgress(MarketInstallStage.IMPORTING_REPOSITORY, null)
        deleteInstalledMarkerRoot(entry)
        val result = skillRepository.importSkillFromGitHubRepoDetailed(repoUrl)
        val installedDir = result.installedDir ?: throw IllegalStateException(result.message)
        withContext(Dispatchers.IO) {
            writeMarketInstallMarker(installedDir, entry)
        }
        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
        trackEntryAssetDownload(entry, onProgress)
    }

    private suspend fun installMcpEntry(
        entry: MarketV2Entry,
        onProgress: MarketInstallProgressReporter
    ) {
        val repoUrl = entry.source?.url.orEmpty().trim()
        val installConfig = entry.latestVersion?.installConfig.orEmpty()
        if (repoUrl.isBlank() && installConfig.isBlank()) {
            throw IllegalStateException(context.getString(R.string.mcp_market_parse_install_info_failed))
        }

        if (installConfig.isNotBlank() && !mcpRepository.checkConfigNeedsPhysicalInstallation(installConfig)) {
            val serverIds = parseMcpServerIds(installConfig)
            if (serverIds.isEmpty()) {
                throw IllegalStateException(context.getString(R.string.mcp_local_no_mcp_servers_field))
            }
            onProgress(MarketInstallStage.IMPORTING_CONFIG, null)
            deleteInstalledMarkerRoot(entry)
            val count =
                MCPLocalServer.getInstance(appContext)
                    .mergeConfigFromJson(installConfig)
                    .getOrElse { error ->
                        AppLogger.e(TAG, "Failed to merge MCP config for entry ${entry.id}", error)
                        throw IllegalStateException(
                            context.getString(
                                R.string.mcp_market_config_import_failed_with_error,
                                error.message ?: ""
                            )
                        )
                    }
            saveMcpConfigEntryMetadata(entry)
            withContext(Dispatchers.IO) {
                serverIds.forEach { serverId ->
                    writeMarketInstallMarker(mcpConfigMarketMarkerRoot(appContext, serverId), entry)
                }
            }
            mcpRepository.refreshPluginList()
            Toast.makeText(
                context,
                context.getString(R.string.mcp_market_config_import_success_with_count, entry.title, count),
                Toast.LENGTH_SHORT
            ).show()
            trackEntryAssetDownload(entry, onProgress)
            return
        }

        onProgress(MarketInstallStage.IMPORTING_REPOSITORY, null)
        deleteInstalledMarkerRoot(entry)
        val server = MCPLocalServer.PluginMetadata(
            id = entry.id,
            name = entry.title,
            description = entry.description,
            logoUrl = entry.publisher?.avatarUrl ?: entry.publisher?.avatar ?: entry.author?.avatarUrl ?: entry.author?.avatar ?: "",
            author = entry.publisher?.login.orEmpty().ifBlank { entry.author?.login.orEmpty() }.ifBlank { entry.publisherId.removePrefix("gh_") },
            isInstalled = false,
            version = entry.latestVersion?.version ?: "1.0.0",
            updatedAt = entry.updatedAt.orEmpty(),
            longDescription = entry.detail.ifBlank { entry.description },
            repoUrl = repoUrl,
            type = "local",
            marketConfig = installConfig.ifBlank { null }
        )
        when (val result = mcpRepository.installMCPServerWithObject(server)) {
            is InstallResult.Success -> {
                withContext(Dispatchers.IO) {
                    writeMarketInstallMarker(File(result.pluginPath), entry)
                }
                Toast.makeText(context, context.getString(R.string.mcp_market_install_success, entry.title), Toast.LENGTH_SHORT).show()
                trackEntryAssetDownload(entry, onProgress)
            }
            is InstallResult.Error -> {
                AppLogger.e(TAG, "Failed to install MCP ${entry.title}: ${result.message}")
                throw IllegalStateException(context.getString(R.string.mcp_market_install_failed_with_error, result.message))
            }
        }
    }

    private suspend fun saveMcpConfigEntryMetadata(entry: MarketV2Entry) {
        val serverIds = parseMcpServerIds(entry.latestVersion?.installConfig.orEmpty())
        if (serverIds.isEmpty()) return
        val localServer = MCPLocalServer.getInstance(appContext)
        for (serverId in serverIds) {
            val current = localServer.getPluginMetadata(serverId)
            localServer.addOrUpdatePluginMetadata(
                MCPLocalServer.PluginMetadata(
                    id = serverId,
                    name = current?.name?.ifBlank { entry.title } ?: entry.title,
                    description = current?.description?.ifBlank { entry.description } ?: entry.description,
                    logoUrl = entry.publisher?.avatarUrl ?: entry.publisher?.avatar ?: entry.author?.avatarUrl ?: entry.author?.avatar ?: current?.logoUrl,
                    author = entry.publisher?.login.orEmpty().ifBlank { entry.author?.login.orEmpty() }.ifBlank { current?.author ?: entry.publisherId.removePrefix("gh_") },
                    isInstalled = true,
                    version = entry.latestVersion?.version ?: current?.version.orEmpty(),
                    updatedAt = entry.updatedAt.orEmpty().ifBlank { current?.updatedAt.orEmpty() },
                    longDescription = entry.detail.ifBlank { entry.description },
                    repoUrl = entry.source?.url.orEmpty(),
                    type = current?.type ?: "local",
                    endpoint = current?.endpoint,
                    connectionType = current?.connectionType,
                    disabled = current?.disabled ?: false,
                    bearerToken = current?.bearerToken,
                    headers = current?.headers,
                    installedPath = current?.installedPath,
                    installedTime = current?.installedTime ?: System.currentTimeMillis(),
                    marketConfig = entry.latestVersion?.installConfig
                )
            )
        }
    }

    private suspend fun installArtifactEntry(
        entry: MarketV2Entry,
        onProgress: MarketInstallProgressReporter
    ) {
        onProgress(MarketInstallStage.FETCHING_METADATA, null)
        val project = marketStatsApiService.artifactProjectFromEntry(entry)
        val defaultVersion =
            project.versions.firstOrNull { it.versionId == project.defaultVersionId }
                ?: throw IllegalStateException("Default artifact version not found")
        installArtifactProjectVersion(
            context = appContext,
            packageManager = packageManager,
            projectVersions = project.versions,
            version = defaultVersion,
            onProgress = onProgress
        )
        withContext(Dispatchers.IO) {
            writeMarketInstallMarker(artifactMarketMarkerRoot(packageManager, defaultVersion.runtimePackageId), entry)
        }
        Toast.makeText(context, context.getString(R.string.external_package_imported), Toast.LENGTH_SHORT).show()
    }

    private suspend fun deleteInstalledMarkerRoot(entry: MarketV2Entry) {
        withContext(Dispatchers.IO) {
            findInstalledMarketMarkerRoots(appContext, packageManager, entry.id).forEach { root ->
                if (root.exists()) {
                    root.deleteRecursively()
                }
            }
        }
    }

    private suspend fun trackEntryAssetDownload(
        entry: MarketV2Entry,
        onProgress: MarketInstallProgressReporter
    ) {
        val assetId = entry.assets.firstOrNull()?.id.orEmpty()
        if (assetId.isBlank()) return
        onProgress(MarketInstallStage.RECORDING, null)
        marketStatsApiService.trackDownload(assetId).onFailure { error ->
            AppLogger.w(TAG, "Failed to track download for entry=${entry.id} asset=$assetId: ${error.message}")
        }
    }

    companion object {
        private const val TAG = "MarketEntryInstallController"
    }
}

fun MarketV2Entry.canInstallFromUnifiedMarket(): Boolean {
    return when (type.lowercase()) {
        "skill" -> source?.url.orEmpty().isNotBlank()
        "mcp" -> source?.url.orEmpty().isNotBlank() || latestVersion?.installConfig.orEmpty().isNotBlank()
        "script",
        "package" -> artifact != null && assets.firstOrNull()?.id.orEmpty().isNotBlank()
        else -> false
    }
}

fun parseMcpServerIds(installConfig: String): Set<String> {
    return McpConfigImportParser.parse(installConfig).servers
        .map { server -> server.id }
        .toSet()
}
