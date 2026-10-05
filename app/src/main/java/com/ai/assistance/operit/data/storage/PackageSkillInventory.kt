package com.ai.assistance.operit.data.storage

import android.content.Context
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.core.tools.skill.SkillManager
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.util.OperitPaths
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ExtensionCategory {
    PLUGIN,
    SCRIPT,
    MCP,
    SKILL,
    PLUGIN_SOURCE,
    DEV_PACKAGE,
    MCP_BRIDGE,
    THEME,
}

data class ExtensionItem(
    val id: String,
    val category: ExtensionCategory,
    val name: String,
    val path: File,
    val bytes: Long,
    val fileCount: Long,
    val subtitle: String,
    val packageName: String? = null,
    val hasLogo: Boolean = false,
    val lastModifiedMillis: Long = 0L,
)

data class ExtensionCategoryGroup(
    val category: ExtensionCategory,
    val items: List<ExtensionItem>,
    val bytes: Long,
    val itemCount: Int,
)

data class PackageSkillSnapshot(
    val groups: List<ExtensionCategoryGroup>,
    val items: List<ExtensionItem>,
    val totalBytes: Long,
    val scannedAtMillis: Long,
) {
    val plugins: List<ExtensionItem>
        get() = items.filter { it.category == ExtensionCategory.PLUGIN }
    val skills: List<ExtensionItem>
        get() = items.filter { it.category == ExtensionCategory.SKILL }
}

class PackageSkillInventory(context: Context) {
    private val appContext = context.applicationContext
    private val mcpRepository = MCPRepository(appContext)
    private val skillManager = SkillManager.getInstance(appContext)
    private val storageRepository = DataStorageRepository(appContext)
    private val cleaner = SafeDirectoryCleaner()
    private val packageManager: PackageManager by lazy {
        PackageManager.getInstance(appContext, AIToolHandler.getInstance(appContext))
    }

    suspend fun load(): PackageSkillSnapshot = withContext(Dispatchers.IO) {
        val items = mutableListOf<ExtensionItem>()
        items += scanRuntimePackages()
        items += scanMcpPlugins()
        items += scanSkills()
        items += scanDirectoryChildren(
            root = OperitPaths.pluginsDir(),
            category = ExtensionCategory.PLUGIN_SOURCE,
            idPrefix = "plugin-source",
        )
        items += scanDirectoryChildren(
            root = File(OperitPaths.operitRootDir(), "dev_package"),
            category = ExtensionCategory.DEV_PACKAGE,
            idPrefix = "dev-package",
        )
        items += scanBridge()
        items += scanDirectoryChildren(
            root = File(OperitPaths.operitRootDir(), "themes"),
            category = ExtensionCategory.THEME,
            idPrefix = "theme",
        )

        val groups = ExtensionCategory.entries.map { category ->
            val grouped = items.filter { it.category == category }.sortedByDescending { it.bytes }
            ExtensionCategoryGroup(
                category = category,
                items = grouped,
                bytes = grouped.sumOf { it.bytes },
                itemCount = grouped.size,
            )
        }
        PackageSkillSnapshot(
            groups = groups,
            items = items.sortedByDescending { it.bytes },
            totalBytes = items.filter { it.category != ExtensionCategory.THEME }.sumOf { it.bytes },
            scannedAtMillis = System.currentTimeMillis(),
        )
    }

    suspend fun delete(
        entries: List<ExtensionItem>,
        onProgress: (String, Int, Int, Long) -> Unit,
    ): StorageDeleteBatchResult {
        var released = 0L
        var deleted = 0
        var failed = 0
        entries.forEachIndexed { index, entry ->
            onProgress(entry.name, index, entries.size, released)
            val ok = runCatching { deleteEntry(entry) }.getOrDefault(false)
            if (ok) {
                deleted++
                released += entry.bytes
            } else {
                failed++
            }
        }
        storageRepository.invalidateCache()
        return StorageDeleteBatchResult(deleted, failed, released)
    }

    fun readPluginLogo(packageName: String): PackageManager.ToolPkgLogoBytes? {
        return runCatching { packageManager.readToolPkgLogoBytes(packageName) }.getOrNull()
    }

    private fun scanRuntimePackages(): List<ExtensionItem> {
        val sources = runCatching {
            packageManager.getAvailablePackages(forceRefresh = true)
            packageManager.getPublishablePackageSources()
        }.getOrDefault(emptyList())
        return sources.map { source ->
            val file = File(source.sourcePath)
            val stats = file.computeStorageStats()
            val details = runCatching {
                packageManager.getToolPkgContainerDetails(source.packageName, appContext)
            }.getOrNull()
            ExtensionItem(
                id = if (source.isToolPkg) "plugin:${source.packageName}" else "script:${source.packageName}",
                category = if (source.isToolPkg) ExtensionCategory.PLUGIN else ExtensionCategory.SCRIPT,
                name = source.displayName.ifBlank { source.packageName },
                path = file,
                bytes = stats.bytes,
                fileCount = stats.fileCount,
                subtitle = source.description.ifBlank { source.sourceFileName },
                packageName = source.packageName,
                hasLogo = !details?.logoResourceKey.isNullOrBlank(),
                lastModifiedMillis = stats.lastModifiedMillis,
            )
        }
    }

    private fun scanMcpPlugins(): List<ExtensionItem> {
        mcpRepository.refreshInstalledPlugins()
        return mcpRepository.mcpServers.value.mapNotNull { metadata ->
            val path = mcpRepository.getInstalledPluginPath(metadata.id) ?: return@mapNotNull null
            if (path.startsWith("virtual://")) return@mapNotNull null
            val file = File(path)
            val stats = file.computeStorageStats()
            if (!stats.exists && stats.bytes <= 0L) return@mapNotNull null
            ExtensionItem(
                id = "mcp:${metadata.id}",
                category = ExtensionCategory.MCP,
                name = metadata.name.ifBlank { metadata.id },
                path = file,
                bytes = stats.bytes,
                fileCount = stats.fileCount,
                subtitle = metadata.author.ifBlank { metadata.type },
                lastModifiedMillis = stats.lastModifiedMillis,
            )
        }
    }

    private fun scanSkills(): List<ExtensionItem> {
        return skillManager.getAvailableSkills().values.map { skill ->
            val stats = skill.directory.computeStorageStats()
            ExtensionItem(
                id = "skill:${skill.name}",
                category = ExtensionCategory.SKILL,
                name = skill.name,
                path = skill.directory,
                bytes = stats.bytes,
                fileCount = stats.fileCount,
                subtitle = skill.description,
                lastModifiedMillis = stats.lastModifiedMillis,
            )
        }
    }

    private fun scanDirectoryChildren(
        root: File,
        category: ExtensionCategory,
        idPrefix: String,
    ): List<ExtensionItem> {
        if (!root.isDirectory) return emptyList()
        return root.listFiles().orEmpty()
            .filter { it.isDirectory || it.isFile }
            .map { child ->
                val stats = child.computeStorageStats()
                ExtensionItem(
                    id = "$idPrefix:${child.canonicalOrAbsolute()}",
                    category = category,
                    name = child.name,
                    path = child,
                    bytes = stats.bytes,
                    fileCount = stats.fileCount,
                    subtitle = "",
                    lastModifiedMillis = stats.lastModifiedMillis,
                )
            }
    }

    private fun scanBridge(): List<ExtensionItem> {
        val directory = OperitPaths.bridgeDir()
        val stats = directory.computeStorageStats()
        if (!stats.exists || stats.fileCount <= 0L) return emptyList()
        return listOf(
            ExtensionItem(
                id = "bridge:${directory.canonicalOrAbsolute()}",
                category = ExtensionCategory.MCP_BRIDGE,
                name = directory.name,
                path = directory,
                bytes = stats.bytes,
                fileCount = stats.fileCount,
                subtitle = "",
                lastModifiedMillis = stats.lastModifiedMillis,
            )
        )
    }

    private suspend fun deleteEntry(entry: ExtensionItem): Boolean {
        return when (entry.category) {
            ExtensionCategory.PLUGIN,
            ExtensionCategory.SCRIPT -> {
                val packageName = entry.packageName
                if (!packageName.isNullOrBlank()) {
                    packageManager.deletePackage(packageName)
                } else {
                    deletePath(entry.path)
                }
            }
            ExtensionCategory.MCP -> {
                val pluginId = entry.id.removePrefix("mcp:")
                mcpRepository.uninstallMCPServer(pluginId) || deletePath(entry.path)
            }
            ExtensionCategory.SKILL -> skillManager.deleteSkill(entry.name) || deletePath(entry.path)
            ExtensionCategory.PLUGIN_SOURCE,
            ExtensionCategory.DEV_PACKAGE,
            ExtensionCategory.THEME,
            ExtensionCategory.MCP_BRIDGE -> deletePath(entry.path)
        }
    }

    private suspend fun deletePath(path: File): Boolean {
        return if (path.isFile) {
            path.delete() || !path.exists()
        } else {
            cleaner.cleanDirectory(path).failedEntryCount == 0 || !path.exists()
        }
    }
}
