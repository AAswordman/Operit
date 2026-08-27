package com.ai.assistance.operit.ui.features.packages.market

import android.content.Context
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.mcp.MCPLocalServer
import com.ai.assistance.operit.data.skill.SkillRepository
import com.google.gson.Gson
import java.io.File

data class MarketInstallMarker(
    val entryId: String,
    val versionId: String
)

enum class MarketLocalInstallStateKind {
    NOT_INSTALLED,
    INSTALLED,
    UPDATE_AVAILABLE
}

data class MarketLocalInstallState(
    val entryId: String,
    val kind: MarketLocalInstallStateKind
)

fun writeMarketInstallMarker(root: File, entry: MarketV2Entry) {
    val versionId = entry.latestVersion?.id?.trim().orEmpty()
    if (entry.id.isBlank() || versionId.isBlank()) {
        throw IllegalStateException("Market entry/version id is missing")
    }
    val markerDir = File(root, MARKET_MARKER_DIR_NAME)
    if (!markerDir.exists() && !markerDir.mkdirs()) {
        throw IllegalStateException("Failed to create market marker dir: ${markerDir.absolutePath}")
    }
    File(markerDir, MARKET_MARKER_FILE_NAME).writeText(
        Gson().toJson(MarketInstallMarker(entryId = entry.id, versionId = versionId))
    )
}

fun resolveMarketLocalInstallStates(
    context: Context,
    packageManager: PackageManager,
    entries: List<MarketV2Entry>
): Map<String, MarketLocalInstallState> {
    val markers = readInstalledMarketMarkerRoots(context, packageManager).associateBy { markerRoot -> markerRoot.marker.entryId }
    return entries.associate { entry ->
        val marker = markers[entry.id]?.marker
        val currentVersionId = entry.latestVersion?.id.orEmpty()
        val kind =
            when {
                marker == null -> MarketLocalInstallStateKind.NOT_INSTALLED
                marker.versionId == currentVersionId -> MarketLocalInstallStateKind.INSTALLED
                else -> MarketLocalInstallStateKind.UPDATE_AVAILABLE
            }
        entry.id to MarketLocalInstallState(entryId = entry.id, kind = kind)
    }
}

fun findInstalledMarketMarkerRoot(
    context: Context,
    packageManager: PackageManager,
    entryId: String
): File? {
    return findInstalledMarketMarkerRoots(context, packageManager, entryId).firstOrNull()
}

fun findInstalledMarketMarkerRoots(
    context: Context,
    packageManager: PackageManager,
    entryId: String
): List<File> {
    return readInstalledMarketMarkerRoots(context, packageManager)
        .filter { markerRoot -> markerRoot.marker.entryId == entryId }
        .map { markerRoot -> markerRoot.root }
}

fun artifactMarketMarkerRoot(packageManager: PackageManager, packageName: String): File {
    val path = packageManager.getPluginConfigDirPath(packageName)
    if (path.isBlank()) {
        throw IllegalStateException("Artifact package name is missing")
    }
    return File(path)
}

fun mcpConfigMarketMarkerRoot(context: Context, serverId: String): File {
    val safeServerId =
        serverId
            .trim()
            .replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_")
            .trim('.', ' ')
            .ifBlank { throw IllegalStateException("MCP server id is missing") }
    return File(context.filesDir, "market_install_markers/mcp_config/$safeServerId")
}

private data class MarketInstallMarkerRoot(
    val marker: MarketInstallMarker,
    val root: File
)

private fun readInstalledMarketMarkerRoots(
    context: Context,
    packageManager: PackageManager
): List<MarketInstallMarkerRoot> {
    val roots = buildList {
        val skillRoot = File(SkillRepository.getInstance(context).getSkillsDirectoryPath())
        addAll(skillRoot.listFiles()?.filter { it.isDirectory }.orEmpty())

        val localServer = MCPLocalServer.getInstance(context)
        localServer.getAllPluginMetadata().values.forEach { metadata ->
            metadata.installedPath?.trim()?.takeIf { it.isNotBlank() }?.let { add(File(it)) }
        }
        localServer.getAllMCPServers().keys.forEach { serverId ->
            add(mcpConfigMarketMarkerRoot(context, serverId))
        }

        packageManager.getPublishablePackageSources().forEach { source ->
            add(artifactMarketMarkerRoot(packageManager, source.packageName))
        }
    }

    return roots
        .asSequence()
        .mapNotNull { root ->
            readMarketInstallMarker(root)?.let { marker ->
                MarketInstallMarkerRoot(marker = marker, root = root)
            }
        }
        .toList()
}

private fun readMarketInstallMarker(root: File): MarketInstallMarker? {
    val markerFile = File(File(root, MARKET_MARKER_DIR_NAME), MARKET_MARKER_FILE_NAME)
    if (!markerFile.exists() || !markerFile.isFile) return null
    return Gson().fromJson(markerFile.readText(), MarketInstallMarker::class.java)
}

private const val MARKET_MARKER_DIR_NAME = ".operit"
private const val MARKET_MARKER_FILE_NAME = "market.json"
