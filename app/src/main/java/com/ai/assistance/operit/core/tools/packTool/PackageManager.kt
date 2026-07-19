package com.ai.assistance.operit.core.tools.packTool

import android.content.Context
import android.os.Looper
import com.ai.assistance.operit.core.chat.logMessageTiming
import com.ai.assistance.operit.core.chat.messageTimingNow
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.PackageToolExecutor
import com.ai.assistance.operit.core.tools.PackageTool
import com.ai.assistance.operit.core.tools.ToolPackage
import com.ai.assistance.operit.core.tools.ToolPackageState
import com.ai.assistance.operit.core.tools.agent.ShowerController
import com.ai.assistance.operit.core.tools.condition.ConditionEvaluator
import com.ai.assistance.operit.core.tools.javascript.JsEngine
import com.ai.assistance.operit.core.tools.mcp.MCPManager
import com.ai.assistance.operit.core.tools.mcp.MCPPackage
import com.ai.assistance.operit.core.tools.mcp.MCPServerConfig
import com.ai.assistance.operit.core.tools.mcp.MCPToolExecutor
import com.ai.assistance.operit.core.tools.skill.SkillManager
import com.ai.assistance.operit.data.preferences.SkillVisibilityPreferences
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.core.tools.system.ShizukuAuthorizer
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.model.Workflow
import com.ai.assistance.operit.data.preferences.EnvPreferences
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences
import com.ai.assistance.operit.data.model.PackageToolPromptCategory
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.ui.features.chat.webview.workspace.WorkspaceConfig
import com.ai.assistance.operit.widget.ToolPkgDesktopWidgetHost
import com.ai.assistance.operit.util.OperitPaths
import com.ai.assistance.operit.util.ToolPkgProtection
import com.ai.assistance.operit.util.ToolPkgWasmRuntime
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hjson.JsonValue

/**
 * Manages the loading, registration, and handling of tool packages
 *
 * Package Lifecycle:
 * 1. Available Packages: All packages in assets (both JS and HJSON format)
 * 2. Enabled Packages: Packages that user has enabled for availability (but not necessarily using)
 * 3. Used Packages: Packages that are loaded and registered with AI in current session
 */
class PackageManager
private constructor(private val context: Context, private val aiToolHandler: AIToolHandler) {
    companion object {
        private const val TAG = "PackageManager"
        private const val TOOLPKG_TAG = "ToolPkg"
        private const val PACKAGES_DIR = "packages" // Directory for packages
        private const val ASSETS_PACKAGES_DIR = "packages" // Directory in assets for packages
        private const val PACKAGE_PREFS = "com.ai.assistance.operit.core.tools.PackageManager"
        private const val ENABLED_PACKAGES_KEY = "imported_packages"
        private const val DISABLED_PACKAGES_KEY = "disabled_packages"
        private const val ACTIVE_PACKAGES_KEY = "active_packages"
        private const val TOOLPKG_SUBPACKAGE_STATES_KEY = "toolpkg_subpackage_states"
        private const val TOOLPKG_EXTENSION = ".toolpkg"
        private const val TOOLPKG_CACHE_DIR = "toolpkg_cache"
        private const val TOOLPKG_CACHE_SIGNATURE_FILE = ".toolpkg-cache-signature"

        @Volatile
        private var INSTANCE: PackageManager? = null

        fun getInstance(context: Context, aiToolHandler: AIToolHandler): PackageManager {
            return INSTANCE
                ?: synchronized(this) {
                    INSTANCE
                        ?: PackageManager(context.applicationContext, aiToolHandler).also {
                            INSTANCE = it
                        }
                }
        }
    }

    // Map of package name to package description (all available packages in market)
    private val availablePackages = ConcurrentHashMap<String, ToolPackage>()

    private val packageLoadErrors = ConcurrentHashMap<String, String>()

    private val activePackageToolNames = ConcurrentHashMap<String, Set<String>>()

    private val activePackageStateIds = ConcurrentHashMap<String, String?>()

    private val toolPkgManager = ToolPkgManager(context)
    private val toolPkgContainers: MutableMap<String, ToolPkgContainerRuntime>
        get() = toolPkgManager.containersInternal
    private val toolPkgSubpackageByPackageName: MutableMap<String, ToolPkgSubpackageRuntime>
        get() = toolPkgManager.subpackageByPackageNameInternal

    data class ToolPkgSubpackageInfo(
        val packageName: String,
        val subpackageId: String,
        val displayName: String,
        val description: String,
        val enabledByDefault: Boolean,
        val toolCount: Int,
        val enabled: Boolean
    )

    data class ToolPkgContainerDetails(
        val packageName: String,
        val displayName: String,
        val description: String,
        val version: String,
        val author: List<String>,
        val resourceCount: Int,
        val wasmModuleCount: Int,
        val workflowTemplateCount: Int,
        val workspaceTemplateCount: Int,
        val uiModuleCount: Int,
        val wasmModules: List<ToolPkgWasmModule>,
        val toolboxUiModules: List<ToolPkgToolboxUiModule>,
        val subpackages: List<ToolPkgSubpackageInfo>,
        val workflowTemplates: List<ToolPkgWorkflowTemplate>,
        val workspaceTemplates: List<ToolPkgWorkspaceTemplate>
    )

    data class ToolPkgWasmModule(
        val id: String,
        val path: String,
        val exports: List<String>,
        val sourceLanguage: String,
        val abi: String
    )

    data class ToolPkgWasmModuleBytes(
        val containerPackageName: String,
        val moduleId: String,
        val path: String,
        val bytes: ByteArray
    )

    data class ToolPkgWorkflowTemplate(
        val containerPackageName: String,
        val toolPkgId: String,
        val templateId: String,
        val displayName: String,
        val description: String,
        val resourceKey: String
    )

    data class ToolPkgWorkspaceTemplate(
        val containerPackageName: String,
        val toolPkgId: String,
        val templateId: String,
        val displayName: String,
        val description: String,
        val resourceKey: String,
        val projectType: String
    )

    data class ToolPkgWorkspaceTemplateImportResult(
        val containerPackageName: String,
        val toolPkgId: String,
        val templateId: String,
        val workspacePath: String,
        val workspaceConfig: WorkspaceConfig
    )

    data class ToolPkgToolboxUiModule(
        val containerPackageName: String,
        val toolPkgId: String,
        val routeId: String,
        val uiModuleId: String,
        val runtime: String,
        val screen: String,
        val title: String,
        val description: String,
        val moduleSpec: Map<String, Any?>,
        val keepAlive: Boolean = false
    )

    data class ToolPkgUiRoute(
        val containerPackageName: String,
        val toolPkgId: String,
        val routeId: String,
        val uiModuleId: String,
        val runtime: String,
        val screen: String,
        val title: String,
        val description: String,
        val moduleSpec: Map<String, Any?>,
        val keepAlive: Boolean = false
    )

    data class ToolPkgNavigationEntry(
        val containerPackageName: String,
        val toolPkgId: String,
        val entryId: String,
        val routeId: String,
        val surface: String,
        val title: String,
        val description: String,
        val action: ToolPkgNavigationActionHook? = null,
        val icon: String?,
        val order: Int
    )

    data class ToolPkgNavigationActionHook(
        val functionName: String,
        val functionSource: String? = null
    )

    data class ToolPkgDesktopWidget(
        val containerPackageName: String,
        val toolPkgId: String,
        val widgetId: String,
        val routeId: String,
        val renderRouteId: String,
        val title: String,
        val subtitle: String,
        val description: String,
        val icon: String?,
        val order: Int
    )

    data class PackageLoadErrorInfo(
        val packageName: String,
        val message: String,
        val sourcePath: String?,
        val isExternalSource: Boolean
    )

    data class PublishablePackageSource(
        val packageName: String,
        val displayName: String,
        val description: String,
        val author: List<String>,
        val sourcePath: String,
        val sourceFileName: String,
        val fileExtension: String,
        val isToolPkg: Boolean,
        val inferredVersion: String? = null
    )

    private data class PackageScanSnapshot(
        val packageLoadErrors: Map<String, String>,
        val availablePackages: Map<String, ToolPackage>,
        val toolPkgContainers: Map<String, ToolPkgContainerRuntime>,
        val toolPkgSubpackages: Map<String, ToolPkgSubpackageRuntime>
    )

    private data class PackageScanCandidateResult(
        val phase: String,
        val packageLoadErrors: Map<String, String> = emptyMap(),
        val toolPackage: ToolPackage? = null,
        val toolPkgLoadResult: ToolPkgLoadResult? = null,
        val sourcePath: String? = null
    )

    private data class ExternalPackageScanCacheEntry(
        val signature: String,
        val result: PackageScanCandidateResult
    )

    internal fun interface ToolPkgRuntimeChangeListener {
        fun onToolPkgRuntimeChanged(activeContainers: List<ToolPkgContainerRuntime>)
    }

    @Volatile
    private var isInitialized = false
    @Volatile
    private var runtimeCachesReady = false
    private val initLock = Any()
    private val toolPkgCacheLock = Any()
    @Volatile
    private var initializationFuture: CompletableFuture<Unit>? = null
    private val initializationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var enabledPackageNamesCache: List<String> = emptyList()
    @Volatile
    private var enabledPackageNameSetCache: Set<String> = emptySet()
    @Volatile
    private var toolPkgSubpackageStatesCache: Map<String, Boolean> = emptyMap()
    @Volatile
    private var assetPackageScanSnapshot: PackageScanSnapshot? = null
    @Volatile
    private var externalPackageScanCache: Map<String, ExternalPackageScanCacheEntry> = emptyMap()

    private val skillManager by lazy { SkillManager.getInstance(context) }

    private val skillVisibilityPreferences by lazy { SkillVisibilityPreferences.getInstance(context) }

    private val toolPkgFacade by lazy { PackageManagerToolPkgFacade(this) }

    // Environment preferences for package-level env variables
    private val envPreferences by lazy { EnvPreferences.getInstance(context) }

    // MCP Manager instance (lazy loading)
    private val mcpManager by lazy { MCPManager.getInstance(context) }

    private fun logToolPkgInfo(message: String) {
        AppLogger.i(TOOLPKG_TAG, "PKG: $message")
    }

    private fun logToolPkgError(message: String, tr: Throwable? = null) {
        if (tr == null) {
            AppLogger.e(TOOLPKG_TAG, "PKG: $message")
        } else {
            AppLogger.e(TOOLPKG_TAG, "PKG: $message", tr)
        }
    }

    private fun persistEnabledPackageNamesToPrefs(enabledPackageNames: List<String>) {
        val prefs = context.getSharedPreferences(PACKAGE_PREFS, Context.MODE_PRIVATE)
        val updatedJson = Json.encodeToString(enabledPackageNames)
        prefs.edit().putString(ENABLED_PACKAGES_KEY, updatedJson).apply()
    }

    private fun persistToolPkgSubpackageStatesToPrefs(states: Map<String, Boolean>) {
        val prefs = context.getSharedPreferences(PACKAGE_PREFS, Context.MODE_PRIVATE)
        val updatedJson = Json.encodeToString(states)
        prefs.edit().putString(TOOLPKG_SUBPACKAGE_STATES_KEY, updatedJson).apply()
    }

    private fun decodeEnabledPackageNamesFromPrefs(): List<String> {
        val prefs = context.getSharedPreferences(PACKAGE_PREFS, Context.MODE_PRIVATE)
        val packagesJson = prefs.getString(ENABLED_PACKAGES_KEY, "[]")
        return try {
            val jsonConfig = Json { ignoreUnknownKeys = true }
            val rawPackages = jsonConfig.decodeFromString<List<String>>(packagesJson ?: "[]")
            normalizeEnabledPackageNames(rawPackages)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error decoding enabled package names", e)
            emptyList()
        }
    }

    private fun decodeToolPkgSubpackageStatesFromPrefs(): Map<String, Boolean> {
        val prefs = context.getSharedPreferences(PACKAGE_PREFS, Context.MODE_PRIVATE)
        val statesJson = prefs.getString(TOOLPKG_SUBPACKAGE_STATES_KEY, "{}")
        return try {
            val jsonConfig = Json { ignoreUnknownKeys = true }
            jsonConfig.decodeFromString<Map<String, Boolean>>(statesJson ?: "{}")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error decoding toolpkg subpackage states", e)
            emptyMap()
        }
    }

    private fun buildEnabledToolPkgContainerRuntimes(
        enabledPackageNames: List<String>
    ): List<ToolPkgContainerRuntime> {
        return toolPkgManager.getEnabledToolPkgContainerRuntimes(enabledPackageNames)
    }

    private fun notifyToolPkgRuntimeChangeListeners() {
        toolPkgManager.notifyToolPkgRuntimeChangeListeners(getEnabledToolPkgContainerRuntimes())
        runCatching {
            ToolPkgDesktopWidgetHost.refreshAll(context)
        }.onFailure { error ->
            logToolPkgError(
                "toolpkg desktop widget refresh failed: ${error.message ?: error.javaClass.simpleName}",
                error
            )
        }
    }

    private fun refreshToolPkgRuntimeState(
        persistIfChanged: Boolean,
        enabledPackageNamesOverride: List<String>? = null,
        subpackageStatesOverride: Map<String, Boolean>? = null
    ) {
        synchronized(initLock) {
            val normalizedEnabledPackageNames =
                normalizeEnabledPackageNames(enabledPackageNamesOverride ?: decodeEnabledPackageNamesFromPrefs())
            val cleanedEnabledPackageNames =
                normalizedEnabledPackageNames.filter { packageName -> availablePackages.containsKey(packageName) }
            if (persistIfChanged && cleanedEnabledPackageNames != normalizedEnabledPackageNames) {
                persistEnabledPackageNamesToPrefs(cleanedEnabledPackageNames)
            }

            val normalizedStates =
                normalizeToolPkgSubpackageStates(subpackageStatesOverride ?: decodeToolPkgSubpackageStatesFromPrefs())
            val cleanedStates =
                normalizedStates.filterKeys { packageName ->
                    toolPkgSubpackageByPackageName.containsKey(packageName)
                }
            if (persistIfChanged && cleanedStates != normalizedStates) {
                persistToolPkgSubpackageStatesToPrefs(cleanedStates)
            }

            enabledPackageNamesCache = cleanedEnabledPackageNames
            enabledPackageNameSetCache = cleanedEnabledPackageNames.toSet()
            toolPkgSubpackageStatesCache = cleanedStates
            runtimeCachesReady = true
        }
        notifyToolPkgRuntimeChangeListeners()
    }

    internal val contextInternal: Context
        get() = context

    internal fun getToolPkgExecutionEngine(
        contextKey: String,
        containerPackageName: String
    ): JsEngine {
        return toolPkgManager.getToolPkgExecutionEngine(contextKey, containerPackageName)
    }

    internal fun findToolPkgExecutionEngine(contextKey: String): JsEngine? {
        return toolPkgManager.findToolPkgExecutionEngine(contextKey)
    }

    fun releaseToolPkgExecutionEngine(
        contextKey: String,
        executionEngine: JsEngine
    ) {
        toolPkgManager.releaseToolPkgExecutionEngine(contextKey, executionEngine)
    }

    fun releaseToolPkgExecutionEngine(contextKey: String) {
        // Preserve the existing application-facing signature.
        toolPkgManager.releaseToolPkgExecutionEngine(contextKey)
    }

    private fun destroyToolPkgExecutionEngines(packageName: String) {
        val normalizedPackageName = normalizePackageName(packageName)
        toolPkgManager.destroyToolPkgExecutionEngines(normalizedPackageName)
        ToolPkgWasmRuntime.closeAllForPackage(normalizedPackageName)
    }

    internal val toolPkgContainersInternal: MutableMap<String, ToolPkgContainerRuntime>
        get() = toolPkgContainers

    internal val toolPkgSubpackageByPackageNameInternal: MutableMap<String, ToolPkgSubpackageRuntime>
        get() = toolPkgSubpackageByPackageName

    internal fun resolveToolPkgSubpackageRuntimeInternal(nameOrId: String): ToolPkgSubpackageRuntime? {
        return resolveToolPkgSubpackageRuntime(nameOrId)
    }

    internal fun getToolPkgMainScriptInternal(containerPackageName: String): String? {
        return getToolPkgMainScript(containerPackageName)
    }

    internal fun addToolPkgRuntimeChangeListener(listener: ToolPkgRuntimeChangeListener) {
        runCatching {
            toolPkgManager.addToolPkgRuntimeChangeListener(
                listener = listener,
                activeContainers = getEnabledToolPkgContainerRuntimes()
            )
        }.onFailure { error ->
            logToolPkgError(
                "toolpkg runtime change listener initial sync failed: ${error.message ?: error.javaClass.simpleName}",
                error
            )
        }
    }

    internal fun removeToolPkgRuntimeChangeListener(listener: ToolPkgRuntimeChangeListener) {
        toolPkgManager.removeToolPkgRuntimeChangeListener(listener)
    }

    internal fun getEnabledPackageNameSetInternal(): Set<String> {
        ensureInitialized()
        if (runtimeCachesReady) {
            return enabledPackageNameSetCache
        }
        return getEnabledPackageNamesInternal().toSet()
    }

    internal fun getEnabledToolPkgContainerRuntimes(): List<ToolPkgContainerRuntime> {
        ensureInitialized()
        if (runtimeCachesReady) {
            return buildEnabledToolPkgContainerRuntimes(enabledPackageNamesCache)
        }
        return buildEnabledToolPkgContainerRuntimes(getEnabledPackageNamesInternal())
    }

    fun cancelToolPkgExecutionsForChat(
        chatId: String,
        reason: String = "Execution canceled: requested by caller"
    ): Boolean {
        val normalizedChatId = chatId.trim()
        if (normalizedChatId.isEmpty()) {
            return false
        }

        return runCatching {
            toolPkgManager.cancelExecutionsForChat(normalizedChatId, reason)
        }.getOrElse { error ->
            AppLogger.e(
                TAG,
                "Failed to cancel toolpkg execution for chatId=$normalizedChatId: ${error.message ?: error.javaClass.simpleName}",
                error
            )
            false
        }
    }

    // Get the external packages directory
    private val externalPackagesDir: File
        get() {
            val dir = File(context.getExternalFilesDir(null), PACKAGES_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            AppLogger.d(TAG, "External packages directory: ${dir.absolutePath}")
            return dir
        }

    private fun formatPackageLoadError(
        message: String,
        sourcePath: String? = null
    ): String {
        val normalizedSourcePath = sourcePath?.trim().orEmpty()
        return if (normalizedSourcePath.isBlank()) {
            message
        } else {
            "Source: $normalizedSourcePath\n$message"
        }
    }

    private fun extractPackageLoadErrorSourcePath(errorText: String): String? {
        val sourcePrefix = "Source: "
        val firstLine = errorText.lineSequence().firstOrNull()?.trim().orEmpty()
        return firstLine
            .takeIf { it.startsWith(sourcePrefix) }
            ?.removePrefix(sourcePrefix)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun stripPackageLoadErrorSourcePath(errorText: String): String {
        val sourcePrefix = "Source: "
        val lines = errorText.lines()
        return if (lines.firstOrNull()?.trim()?.startsWith(sourcePrefix) == true) {
            lines.drop(1).joinToString("\n").trim()
        } else {
            errorText
        }
    }

    private fun isExternalPackageSourcePath(sourcePath: String?): Boolean {
        if (sourcePath.isNullOrBlank()) {
            return false
        }

        val candidateCanonicalPath =
            runCatching { File(sourcePath).canonicalPath }.getOrElse { return false }
        val externalRootCanonicalPath =
            runCatching { externalPackagesDir.canonicalPath }.getOrElse { externalPackagesDir.absolutePath }
        val externalRootPrefix =
            if (externalRootCanonicalPath.endsWith(File.separator)) {
                externalRootCanonicalPath
            } else {
                externalRootCanonicalPath + File.separator
            }

        return candidateCanonicalPath.equals(externalRootCanonicalPath, ignoreCase = true) ||
            candidateCanonicalPath.startsWith(externalRootPrefix, ignoreCase = true)
    }

    private val toolPkgCacheRootDir: File
        get() {
            val dir = File(context.filesDir, TOOLPKG_CACHE_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    internal fun ensureInitialized() {
        if (isInitialized) return
        val isMainThread = Looper.myLooper() == Looper.getMainLooper()
        val future = ensureInitializationStarted()
        if (isMainThread) {
            // Never block main thread for toolpkg parsing: it requires WebView main-thread callbacks.
            return
        }
        try {
            future.get()
        } catch (e: Exception) {
            val threadName = Thread.currentThread().name
            logToolPkgError("ensureInitialized failed on background thread, thread=$threadName, reason=${e.message ?: e.javaClass.simpleName}", e)
            throw IllegalStateException("PackageManager initialization failed", e)
        }
    }

    private fun ensureInitializationStarted(): CompletableFuture<Unit> {
        synchronized(initLock) {
            if (isInitialized) {
                return CompletableFuture.completedFuture(Unit)
            }
            initializationFuture?.let {
                return it
            }

            val future = CompletableFuture<Unit>()
            initializationFuture = future

            initializationScope.launch {
                val initStart = System.currentTimeMillis()
                try {
                    runtimeCachesReady = false
                    cleanupLegacyToolPkgAssetParseCache()

                    // Create packages directory if it doesn't exist
                    externalPackagesDir

                    // Load available packages info (metadata only) from assets and external storage
                    loadAvailablePackages()

                    // Automatically import built-in packages that are enabled by default
                    initializeDefaultPackages()
                    reconcileToolPkgCaches()

                    synchronized(initLock) {
                        isInitialized = true
                    }
                    refreshToolPkgRuntimeState(persistIfChanged = true)
                    logToolPkgInfo("initialization coroutine success, totalMs=${System.currentTimeMillis() - initStart}")
                    future.complete(Unit)
                } catch (e: Exception) {
                    logToolPkgError(
                        "initialization coroutine failed after ${System.currentTimeMillis() - initStart}ms, reason=${e.message ?: e.javaClass.simpleName}",
                        e
                    )
                    future.completeExceptionally(e)
                } finally {
                    synchronized(initLock) {
                        if (initializationFuture === future) {
                            initializationFuture = null
                        }
                    }
                }
            }
            return future
        }
    }

    private fun resolveToolPkgSubpackageRuntime(nameOrId: String): ToolPkgSubpackageRuntime? {
        val candidate = nameOrId.trim()
        if (candidate.isBlank()) {
            return null
        }

        toolPkgSubpackageByPackageName[candidate]?.let { return it }

        return toolPkgSubpackageByPackageName.values.firstOrNull {
            it.subpackageId.equals(candidate, ignoreCase = true)
        }
    }

    internal fun normalizePackageName(packageName: String): String {
        val trimmed = packageName.trim()
        if (trimmed.isBlank()) {
            return trimmed
        }
        return resolveToolPkgSubpackageRuntime(trimmed)?.packageName ?: trimmed
    }

    private fun normalizeEnabledPackageNames(packageNames: List<String>): List<String> {
        val normalized = LinkedHashSet<String>()
        packageNames.forEach { original ->
            val canonical = normalizePackageName(original)
            if (canonical.isNotBlank()) {
                normalized.add(canonical)
            }
        }
        return normalized.toList()
    }

    private fun normalizeToolPkgSubpackageStates(states: Map<String, Boolean>): Map<String, Boolean> {
        val normalized = linkedMapOf<String, Boolean>()
        states.forEach { (name, enabled) ->
            val canonical = normalizePackageName(name)
            if (!toolPkgSubpackageByPackageName.containsKey(canonical)) {
                return@forEach
            }

            val isCanonicalKey = name.trim().equals(canonical, ignoreCase = true)
            if (isCanonicalKey || !normalized.containsKey(canonical)) {
                normalized[canonical] = enabled
            }
        }
        return normalized
    }

    private fun toolPkgCacheDirName(packageName: String): String {
        val normalized = packageName.trim()
        val safeName =
            normalized
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .ifBlank { "toolpkg" }
        val hash = Integer.toHexString(normalized.hashCode())
        return "$safeName-$hash"
    }

    private fun toolPkgCacheDir(packageName: String): File {
        return File(toolPkgCacheRootDir, toolPkgCacheDirName(packageName))
    }

    private fun deleteToolPkgCacheDir(packageName: String) {
        synchronized(toolPkgCacheLock) {
            val dir = toolPkgCacheDir(packageName)
            if (dir.exists() && !dir.deleteRecursively()) {
                AppLogger.w(TAG, "Failed to delete toolpkg cache dir: ${dir.absolutePath}")
            }
        }
    }

    private fun cleanupLegacyToolPkgAssetParseCache() {
        val legacyDir = File(context.filesDir, "toolpkg_asset_parse_cache")
        if (legacyDir.exists() && !legacyDir.deleteRecursively()) {
            AppLogger.w(TAG, "Failed to delete legacy toolpkg asset parse cache: ${legacyDir.absolutePath}")
        }
    }

    private fun ensureToolPkgCacheDir(
        packageName: String,
        signature: String,
        mainEntry: String,
        extractArchive: (destinationDir: File) -> Boolean
    ): File? {
        synchronized(toolPkgCacheLock) {
            val cacheDir = toolPkgCacheDir(packageName)
            val signatureFile = File(cacheDir, TOOLPKG_CACHE_SIGNATURE_FILE)
            val cacheDirExists = cacheDir.exists()
            val signatureFileExists = signatureFile.exists()
            val signatureMatches =
                if (signatureFileExists) {
                    runCatching { signatureFile.readText() == signature }.getOrDefault(false)
                } else {
                    false
                }
            val mainScriptFile = File(cacheDir, mainEntry)
            val mainScriptExists = mainScriptFile.exists()

            if (cacheDirExists && signatureFileExists && signatureMatches && mainScriptExists) {
                return cacheDir
            }

            deleteToolPkgCacheDir(packageName)
            if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                throw IllegalStateException("Failed to create toolpkg cache dir: ${cacheDir.absolutePath}")
            }

            return try {
                if (!extractArchive(cacheDir)) {
                    deleteToolPkgCacheDir(packageName)
                    null
                } else {
                    signatureFile.writeText(signature)
                    cacheDir
                }
            } catch (e: Exception) {
                deleteToolPkgCacheDir(packageName)
                AppLogger.e(TAG, "Failed to extract toolpkg cache: $packageName", e)
                null
            }
        }
    }

    private fun buildToolPkgCacheSignature(
        sourceType: ToolPkgSourceType,
        sourcePath: String,
        version: String,
        mainEntry: String
    ): String? {
        return when (sourceType) {
            ToolPkgSourceType.EXTERNAL -> {
                val sourceFile = File(sourcePath)
                if (!sourceFile.exists()) {
                    null
                } else {
                    buildString {
                        append("external|")
                        append(sourceFile.absolutePath)
                        append('|')
                        append(sourceFile.length())
                        append('|')
                        append(sourceFile.lastModified())
                        append('|')
                        append(version)
                        append('|')
                        append(mainEntry)
                    }
                }
            }
            ToolPkgSourceType.ASSET -> {
                val apkFile = File(context.packageResourcePath)
                buildString {
                    append("asset|")
                    append(sourcePath)
                    append('|')
                    append(apkFile.length())
                    append('|')
                    append(apkFile.lastModified())
                    append('|')
                    append(version)
                    append('|')
                    append(mainEntry)
                }
            }
        }
    }

    private fun buildToolPkgCacheSignature(runtime: ToolPkgContainerRuntime): String? {
        return buildToolPkgCacheSignature(
            sourceType = runtime.sourceType,
            sourcePath = runtime.sourcePath,
            version = runtime.version,
            mainEntry = runtime.mainEntry
        )
    }

    private fun extractToolPkgArchive(runtime: ToolPkgContainerRuntime, destinationDir: File): Boolean {
        return when (runtime.sourceType) {
            ToolPkgSourceType.EXTERNAL ->
                ToolPkgArchiveParser.extractZipEntriesFromExternal(
                    zipFilePath = runtime.sourcePath,
                    destinationDir = destinationDir
                )
            ToolPkgSourceType.ASSET ->
                ToolPkgArchiveParser.extractZipEntriesFromAsset(
                    context = context,
                    assetPath = runtime.sourcePath,
                    destinationDir = destinationDir
                )
        }
    }

    private fun ensureToolPkgCache(runtime: ToolPkgContainerRuntime): File? {
        val signature = buildToolPkgCacheSignature(runtime) ?: return null
        return ensureToolPkgCacheDir(
            packageName = runtime.packageName,
            signature = signature,
            mainEntry = runtime.mainEntry,
            extractArchive = { destinationDir -> extractToolPkgArchive(runtime, destinationDir) }
        )
    }

    private fun reconcileToolPkgCaches() {
        synchronized(toolPkgCacheLock) {
            val enabledPackageNames = getEnabledPackageNamesInternal().toSet()
            val availableContainerNames = toolPkgContainers.keys.toSet()
            val enabledContainerNames = linkedSetOf<String>().apply {
                toolPkgContainers.keys.forEach { containerName ->
                    if (enabledPackageNames.contains(containerName)) {
                        add(containerName)
                    }
                }
                toolPkgSubpackageByPackageName.values.forEach { subpackage ->
                    if (enabledPackageNames.contains(subpackage.packageName)) {
                        add(subpackage.containerPackageName)
                    }
                }
            }

            val retainedContainerNames = linkedSetOf<String>().apply {
                toolPkgContainers.values.forEach { runtime ->
                    if (runtime.sourceType == ToolPkgSourceType.ASSET) {
                        add(runtime.packageName)
                    }
                }
                addAll(enabledContainerNames)
            }

            val expectedCacheDirNames = retainedContainerNames.map(::toolPkgCacheDirName).toSet()
            toolPkgCacheRootDir.listFiles()?.forEach { child ->
                if (!expectedCacheDirNames.contains(child.name)) {
                    if (!child.deleteRecursively()) {
                        AppLogger.w(TAG, "Failed to remove stale toolpkg cache: ${child.absolutePath}")
                    }
                }
            }

            retainedContainerNames.forEach { containerName ->
                val runtime = toolPkgContainers[containerName] ?: return@forEach
                ensureToolPkgCache(runtime)
            }

            availableContainerNames
                .filterNot { retainedContainerNames.contains(it) }
                .forEach(::deleteToolPkgCacheDir)
        }
    }

    private fun buildPackageScanSnapshot(
        packageLoadErrors: Map<String, String>,
        availablePackages: Map<String, ToolPackage>,
        toolPkgContainers: Map<String, ToolPkgContainerRuntime>,
        toolPkgSubpackages: Map<String, ToolPkgSubpackageRuntime>
    ): PackageScanSnapshot {
        return PackageScanSnapshot(
            packageLoadErrors = LinkedHashMap(packageLoadErrors),
            availablePackages = LinkedHashMap(availablePackages),
            toolPkgContainers = LinkedHashMap(toolPkgContainers),
            toolPkgSubpackages = LinkedHashMap(toolPkgSubpackages)
        )
    }

    private fun applyPackageScanSnapshot(snapshot: PackageScanSnapshot) {
        synchronized(initLock) {
            packageLoadErrors.clear()
            packageLoadErrors.putAll(snapshot.packageLoadErrors)

            availablePackages.clear()
            availablePackages.putAll(snapshot.availablePackages)

            toolPkgContainers.clear()
            toolPkgContainers.putAll(snapshot.toolPkgContainers)

            toolPkgSubpackageByPackageName.clear()
            toolPkgSubpackageByPackageName.putAll(snapshot.toolPkgSubpackages)
        }
    }

    private data class PackageScanCandidate(
        val fileName: String,
        val sourcePath: String,
        val loadJs: (((String, String) -> Unit) -> ToolPackage?)? = null,
        val loadToolPkg: (((String, String) -> Unit) -> ToolPkgLoadResult?)? = null
    )

    private fun removeToolPkgContainerFromTargets(
        containerPackageName: String,
        availablePackagesTarget: MutableMap<String, ToolPackage>,
        toolPkgContainersTarget: MutableMap<String, ToolPkgContainerRuntime>,
        toolPkgSubpackageByPackageNameTarget: MutableMap<String, ToolPkgSubpackageRuntime>
    ) {
        val runtime = toolPkgContainersTarget.remove(containerPackageName) ?: return
        availablePackagesTarget.remove(containerPackageName)
        runtime.subpackages.forEach { subpackage ->
            availablePackagesTarget.remove(subpackage.packageName)
            toolPkgSubpackageByPackageNameTarget.remove(subpackage.packageName)
        }
    }

    private fun prepareExternalStandalonePackageOverride(
        packageName: String,
        availablePackagesTarget: MutableMap<String, ToolPackage>,
        toolPkgContainersTarget: MutableMap<String, ToolPkgContainerRuntime>,
        toolPkgSubpackageByPackageNameTarget: MutableMap<String, ToolPkgSubpackageRuntime>
    ): Boolean {
        val existingContainer = toolPkgContainersTarget[packageName]
        if (existingContainer != null) {
            return if (existingContainer.sourceType == ToolPkgSourceType.ASSET) {
                removeToolPkgContainerFromTargets(
                    containerPackageName = existingContainer.packageName,
                    availablePackagesTarget = availablePackagesTarget,
                    toolPkgContainersTarget = toolPkgContainersTarget,
                    toolPkgSubpackageByPackageNameTarget = toolPkgSubpackageByPackageNameTarget
                )
                true
            } else {
                false
            }
        }

        val existingSubpackage = toolPkgSubpackageByPackageNameTarget[packageName]
        if (existingSubpackage != null) {
            val ownerContainer = toolPkgContainersTarget[existingSubpackage.containerPackageName]
            return if (ownerContainer?.sourceType == ToolPkgSourceType.ASSET) {
                removeToolPkgContainerFromTargets(
                    containerPackageName = ownerContainer.packageName,
                    availablePackagesTarget = availablePackagesTarget,
                    toolPkgContainersTarget = toolPkgContainersTarget,
                    toolPkgSubpackageByPackageNameTarget = toolPkgSubpackageByPackageNameTarget
                )
                true
            } else {
                false
            }
        }

        val existingPackage = availablePackagesTarget[packageName] ?: return true
        if (!existingPackage.isBuiltIn) {
            return false
        }
        availablePackagesTarget.remove(packageName)
        return true
    }

    private fun prepareExternalToolPkgOverride(
        loadResult: ToolPkgLoadResult,
        availablePackagesTarget: MutableMap<String, ToolPackage>,
        toolPkgContainersTarget: MutableMap<String, ToolPkgContainerRuntime>,
        toolPkgSubpackageByPackageNameTarget: MutableMap<String, ToolPkgSubpackageRuntime>
    ): Boolean {
        val builtInContainersToRemove = linkedSetOf<String>()
        val builtInStandalonePackagesToRemove = linkedSetOf<String>()
        val conflictingNames =
            buildList {
                add(loadResult.containerPackage.name)
                addAll(loadResult.subpackagePackages.map(ToolPackage::name))
            }

        conflictingNames.forEach { packageName ->
            val existingContainer = toolPkgContainersTarget[packageName]
            if (existingContainer != null) {
                if (existingContainer.sourceType != ToolPkgSourceType.ASSET) {
                    return false
                }
                builtInContainersToRemove += existingContainer.packageName
                return@forEach
            }

            val existingSubpackage = toolPkgSubpackageByPackageNameTarget[packageName]
            if (existingSubpackage != null) {
                val ownerContainer = toolPkgContainersTarget[existingSubpackage.containerPackageName]
                if (ownerContainer?.sourceType != ToolPkgSourceType.ASSET) {
                    return false
                }
                builtInContainersToRemove += ownerContainer.packageName
                return@forEach
            }

            val existingPackage = availablePackagesTarget[packageName] ?: return@forEach
            if (!existingPackage.isBuiltIn) {
                return false
            }
            builtInStandalonePackagesToRemove += packageName
        }

        builtInContainersToRemove.forEach { containerPackageName ->
            removeToolPkgContainerFromTargets(
                containerPackageName = containerPackageName,
                availablePackagesTarget = availablePackagesTarget,
                toolPkgContainersTarget = toolPkgContainersTarget,
                toolPkgSubpackageByPackageNameTarget = toolPkgSubpackageByPackageNameTarget
            )
        }
        builtInStandalonePackagesToRemove.forEach(availablePackagesTarget::remove)
        return true
    }

    private fun parsePackageCandidate(
        phase: String,
        candidate: PackageScanCandidate
    ): PackageScanCandidateResult {
        val stagedPackageLoadErrors = LinkedHashMap<String, String>()
        return try {
            when {
                candidate.fileName.endsWith(".js", ignoreCase = true) && candidate.loadJs != null -> {
                    val packageMetadata =
                        candidate.loadJs.invoke { key, error ->
                            stagedPackageLoadErrors[key] = error
                        }
                    PackageScanCandidateResult(
                        phase = phase,
                        packageLoadErrors = stagedPackageLoadErrors,
                        toolPackage = packageMetadata,
                        sourcePath = candidate.sourcePath
                    )
                }
                candidate.fileName.endsWith(TOOLPKG_EXTENSION, ignoreCase = true) &&
                    candidate.loadToolPkg != null -> {
                    val loadResult =
                        candidate.loadToolPkg.invoke { key, error ->
                            stagedPackageLoadErrors[key] = error
                        }
                    PackageScanCandidateResult(
                        phase = phase,
                        packageLoadErrors = stagedPackageLoadErrors,
                        toolPkgLoadResult = loadResult,
                        sourcePath = candidate.sourcePath
                    )
                }
                else ->
                    PackageScanCandidateResult(
                        phase = phase,
                        packageLoadErrors = stagedPackageLoadErrors,
                        sourcePath = candidate.sourcePath
                    )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Unexpected error while loading $phase package: ${candidate.sourcePath}", e)
            logToolPkgError("loadAvailablePackages $phase parse failed, source=${candidate.sourcePath}", e)
            stagedPackageLoadErrors[candidate.fileName.substringBeforeLast('.')] =
                formatPackageLoadError(
                    message = e.stackTraceToString(),
                    sourcePath = candidate.sourcePath
                )
            PackageScanCandidateResult(
                phase = phase,
                packageLoadErrors = stagedPackageLoadErrors,
                sourcePath = candidate.sourcePath
            )
        }
    }

    private fun mergePackageScanCandidateResults(
        candidateResults: Iterable<PackageScanCandidateResult>,
        baseSnapshot: PackageScanSnapshot? = null
    ): PackageScanSnapshot {
        val stagedPackageLoadErrors = LinkedHashMap(baseSnapshot?.packageLoadErrors.orEmpty())
        val stagedAvailablePackages = LinkedHashMap(baseSnapshot?.availablePackages.orEmpty())
        val stagedToolPkgContainers = LinkedHashMap(baseSnapshot?.toolPkgContainers.orEmpty())
        val stagedToolPkgSubpackages = LinkedHashMap(baseSnapshot?.toolPkgSubpackages.orEmpty())

        candidateResults.forEach { result ->
            stagedPackageLoadErrors.putAll(result.packageLoadErrors)
            result.toolPackage?.let { packageMetadata ->
                if (result.phase == "external") {
                    val prepared =
                        prepareExternalStandalonePackageOverride(
                            packageName = packageMetadata.name,
                            availablePackagesTarget = stagedAvailablePackages,
                            toolPkgContainersTarget = stagedToolPkgContainers,
                            toolPkgSubpackageByPackageNameTarget = stagedToolPkgSubpackages
                        )
                    if (!prepared) {
                        stagedPackageLoadErrors[packageMetadata.name] =
                            formatPackageLoadError(
                                message = "Duplicate package name: ${packageMetadata.name}",
                                sourcePath = result.sourcePath
                            )
                        return@let
                    }
                }
                if (stagedAvailablePackages.containsKey(packageMetadata.name)) {
                    stagedPackageLoadErrors[packageMetadata.name] =
                        formatPackageLoadError(
                            message = "Duplicate package name: ${packageMetadata.name}",
                            sourcePath = result.sourcePath
                        )
                } else {
                    stagedAvailablePackages[packageMetadata.name] = packageMetadata
                }
            }
            result.toolPkgLoadResult?.let { loadResult ->
                if (result.phase == "external") {
                    prepareExternalToolPkgOverride(
                        loadResult = loadResult,
                        availablePackagesTarget = stagedAvailablePackages,
                        toolPkgContainersTarget = stagedToolPkgContainers,
                        toolPkgSubpackageByPackageNameTarget = stagedToolPkgSubpackages
                    )
                }
                registerToolPkgInto(
                    loadResult = loadResult,
                    availablePackagesTarget = stagedAvailablePackages,
                    toolPkgContainersTarget = stagedToolPkgContainers,
                    toolPkgSubpackageByPackageNameTarget = stagedToolPkgSubpackages,
                    packageLoadErrorsTarget = stagedPackageLoadErrors
                )
            }
        }

        return buildPackageScanSnapshot(
            packageLoadErrors = stagedPackageLoadErrors,
            availablePackages = stagedAvailablePackages,
            toolPkgContainers = stagedToolPkgContainers,
            toolPkgSubpackages = stagedToolPkgSubpackages
        )
    }

    private fun buildExternalPackageScanSignature(file: File): String {
        return buildString {
            append(file.absolutePath)
            append('|')
            append(file.length())
            append('|')
            append(file.lastModified())
        }
    }

    private fun scanPackageCandidates(
        phase: String,
        candidates: List<PackageScanCandidate>,
        baseSnapshot: PackageScanSnapshot? = null
    ): PackageScanSnapshot {
        return mergePackageScanCandidateResults(
            candidateResults = candidates.map { candidate -> parsePackageCandidate(phase, candidate) },
            baseSnapshot = baseSnapshot
        )
    }

    private fun scanAssetPackages(): PackageScanSnapshot {
        val packageFiles = context.assets.list(ASSETS_PACKAGES_DIR) ?: emptyArray()
        val candidates =
            packageFiles.map { fileName ->
                val assetPath = "$ASSETS_PACKAGES_DIR/$fileName"
                PackageScanCandidate(
                    fileName = fileName,
                    sourcePath = assetPath,
                    loadJs = { onError -> loadPackageFromJsAsset(assetPath, onError)?.copy(isBuiltIn = true) },
                    loadToolPkg = { onError -> loadToolPkgFromAsset(assetPath, onError) }
                )
            }
        return scanPackageCandidates(phase = "asset", candidates = candidates)
    }

    private fun scanExternalPackages(baseSnapshot: PackageScanSnapshot): PackageScanSnapshot {
        val externalFiles =
            if (externalPackagesDir.exists()) {
                (externalPackagesDir.listFiles() ?: emptyArray()).filter(File::isFile)
            } else {
                emptyList()
            }
        val previousCache = externalPackageScanCache
        val nextCache = LinkedHashMap<String, ExternalPackageScanCacheEntry>()
        val candidateResults =
            externalFiles.map { file ->
                val candidate =
                    PackageScanCandidate(
                        fileName = file.name,
                        sourcePath = file.absolutePath,
                        loadJs = { onError -> loadPackageFromJsFile(file, onError) },
                        loadToolPkg = { onError -> loadToolPkgFromExternalFile(file, onError) }
                    )
                val signature = buildExternalPackageScanSignature(file)
                val cachedResult =
                    previousCache[file.absolutePath]
                        ?.takeIf { entry -> entry.signature == signature }
                        ?.result
                val result = cachedResult ?: parsePackageCandidate("external", candidate)
                nextCache[file.absolutePath] =
                    ExternalPackageScanCacheEntry(
                        signature = signature,
                        result = result
                    )
                result
            }
        externalPackageScanCache = nextCache
        return mergePackageScanCandidateResults(
            candidateResults = candidateResults,
            baseSnapshot = baseSnapshot
        )
    }

    fun resolvePackageForDisplay(packageName: String): ToolPackage? {
        ensureInitialized()
        val normalizedPackageName = normalizePackageName(packageName)
        val toolPackage = availablePackages[normalizedPackageName] ?: return null
        return selectToolPackageState(toolPackage)
    }

    fun isToolPkgContainer(packageName: String): Boolean {
        return toolPkgFacade.isToolPkgContainer(packageName)
    }

    fun isToolPkgSubpackage(packageName: String): Boolean {
        return toolPkgFacade.isToolPkgSubpackage(packageName)
    }

    fun isTopLevelPackage(packageName: String): Boolean {
        ensureInitialized()
        return resolveToolPkgSubpackageRuntime(packageName) == null
    }

    fun getTopLevelAvailablePackages(forceRefresh: Boolean = false): Map<String, ToolPackage> {
        val packages = getAvailablePackages(forceRefresh)
        return packages.filterKeys { !toolPkgSubpackageByPackageName.containsKey(it) }
    }

    fun getExecutableAvailablePackages(forceRefresh: Boolean = false): Map<String, ToolPackage> {
        val packages = getAvailablePackages(forceRefresh)
        return packages.filterKeys { !toolPkgContainers.containsKey(it) }
    }

    fun getToolPkgPluginContainerDetails(
        resolveContext: Context? = null
    ): List<ToolPkgContainerDetails> {
        ensureInitialized()
        val localizationContext = resolveContext ?: context
        return toolPkgContainers.keys
            .sorted()
            .mapNotNull { packageName ->
                getToolPkgContainerDetails(
                    packageName = packageName,
                    resolveContext = localizationContext
                )
            }
    }

    fun getPublishablePackageSources(): List<PublishablePackageSource> {
        ensureInitialized()

        return getTopLevelAvailablePackages()
            .entries
            .asSequence()
            .filter { (_, toolPackage) -> !toolPackage.isBuiltIn }
            .mapNotNull { (packageName, toolPackage) ->
                val isToolPkg = isToolPkgContainer(packageName)
                val sourcePath =
                    if (isToolPkg) {
                        toolPkgContainers[packageName]
                            ?.takeIf { it.sourceType == ToolPkgSourceType.EXTERNAL }
                            ?.sourcePath
                    } else {
                        findPackageFile(packageName)?.absolutePath
                    }
                        ?: return@mapNotNull null

                val sourceFile = File(sourcePath)
                if (!sourceFile.exists() || !sourceFile.isFile) {
                    return@mapNotNull null
                }

                PublishablePackageSource(
                    packageName = packageName,
                    displayName =
                        toolPackage.displayName.resolve(context).ifBlank { packageName },
                    description = toolPackage.description.resolve(context),
                    author = if (isToolPkg) {
                        toolPkgContainers[packageName]?.author ?: toolPackage.author
                    } else {
                        toolPackage.author
                    },
                    sourcePath = sourceFile.absolutePath,
                    sourceFileName = sourceFile.name,
                    fileExtension = sourceFile.extension.lowercase(),
                    isToolPkg = isToolPkg,
                    inferredVersion =
                        if (isToolPkg) {
                            toolPkgContainers[packageName]?.version?.takeIf { it.isNotBlank() }
                        } else {
                            null
                        }
                )
            }
            .sortedWith(
                compareBy<PublishablePackageSource> { it.isToolPkg }
                    .thenBy { it.displayName.lowercase() }
            )
            .toList()
    }

    fun getToolPkgContainerDetails(
        packageName: String,
        resolveContext: Context? = null
    ): ToolPkgContainerDetails? {
        return toolPkgFacade.getToolPkgContainerDetails(packageName, resolveContext)
    }

    fun getToolPkgUiRoutes(
        runtime: String = TOOLPKG_RUNTIME_COMPOSE_DSL,
        resolveContext: Context? = null
    ): List<ToolPkgUiRoute> {
        return toolPkgFacade.getToolPkgUiRoutes(runtime, resolveContext)
    }

    fun getToolPkgDesktopWidgets(
        resolveContext: Context? = null
    ): List<ToolPkgDesktopWidget> {
        return toolPkgFacade.getToolPkgDesktopWidgets(resolveContext)
    }

    fun getToolPkgNavigationEntries(
        resolveContext: Context? = null
    ): List<ToolPkgNavigationEntry> {
        return toolPkgFacade.getToolPkgNavigationEntries(resolveContext)
    }

    fun getToolPkgWorkflowTemplates(
        resolveContext: Context? = null
    ): List<ToolPkgWorkflowTemplate> {
        return toolPkgFacade.getToolPkgWorkflowTemplates(resolveContext)
    }

    fun importToolPkgWorkflowTemplate(
        containerPackageName: String,
        templateId: String
    ): Result<Workflow> {
        return toolPkgFacade.importToolPkgWorkflowTemplate(containerPackageName, templateId)
    }

    fun getToolPkgWorkspaceTemplates(
        resolveContext: Context? = null
    ): List<ToolPkgWorkspaceTemplate> {
        return toolPkgFacade.getToolPkgWorkspaceTemplates(resolveContext)
    }

    fun importToolPkgWorkspaceTemplate(
        containerPackageName: String,
        templateId: String,
        destinationDir: File
    ): Result<ToolPkgWorkspaceTemplateImportResult> {
        return toolPkgFacade.importToolPkgWorkspaceTemplate(
            containerPackageName = containerPackageName,
            templateId = templateId,
            destinationDir = destinationDir
        )
    }

    fun setToolPkgSubpackageEnabled(subpackagePackageName: String, enabled: Boolean): Boolean {
        return toolPkgFacade.setToolPkgSubpackageEnabled(subpackagePackageName, enabled)
    }

    fun findPreferredPackageNameForSubpackageId(
        subpackageId: String,
        preferEnabled: Boolean = true
    ): String? {
        return toolPkgFacade.findPreferredPackageNameForSubpackageId(subpackageId, preferEnabled)
    }

    fun copyToolPkgResourceToFileBySubpackageId(
        subpackageId: String,
        resourceKey: String,
        destinationFile: File,
        preferEnabledContainer: Boolean = true
    ): Boolean {
        return toolPkgFacade.copyToolPkgResourceToFileBySubpackageId(
            subpackageId = subpackageId,
            resourceKey = resourceKey,
            destinationFile = destinationFile,
            preferEnabledContainer = preferEnabledContainer
        )
    }

    fun copyToolPkgResourceToFile(
        containerPackageName: String,
        resourceKey: String,
        destinationFile: File
    ): Boolean {
        return toolPkgFacade.copyToolPkgResourceToFile(
            containerPackageName = containerPackageName,
            resourceKey = resourceKey,
            destinationFile = destinationFile
        )
    }

    fun getToolPkgResourceOutputFileName(
        packageNameOrSubpackageId: String,
        resourceKey: String,
        preferEnabledContainer: Boolean = true
    ): String? {
        return toolPkgFacade.getToolPkgResourceOutputFileName(
            packageNameOrSubpackageId = packageNameOrSubpackageId,
            resourceKey = resourceKey,
            preferEnabledContainer = preferEnabledContainer
        )
    }

    fun getToolPkgComposeDslScriptBySubpackageId(
        subpackageId: String,
        uiModuleId: String? = null,
        preferEnabledContainer: Boolean = true
    ): String? {
        return toolPkgFacade.getToolPkgComposeDslScriptBySubpackageId(
            subpackageId = subpackageId,
            uiModuleId = uiModuleId,
            preferEnabledContainer = preferEnabledContainer
        )
    }

    fun getToolPkgComposeDslScript(
        containerPackageName: String,
        uiModuleId: String? = null
    ): String? {
        return toolPkgFacade.getToolPkgComposeDslScript(
            containerPackageName = containerPackageName,
            uiModuleId = uiModuleId
        )
    }

    private fun getToolPkgMainScript(containerPackageName: String): String? {
        val totalStartTime = messageTimingNow()
        ensureInitialized()
        val normalizedContainerPackageName = normalizePackageName(containerPackageName)
        val runtime = toolPkgContainers[normalizedContainerPackageName] ?: return null
        val enabledSet = getEnabledPackageNameSetInternal()
        if (!enabledSet.contains(runtime.packageName)) {
            return null
        }
        if (runtime.mainEntry.isBlank()) {
            return null
        }

        return try {
            val readBytesStartTime = messageTimingNow()
            val bytes = readToolPkgResourceBytes(runtime, runtime.mainEntry) ?: return null
            logMessageTiming(
                stage = "toolpkg.getMainScript.readBytes",
                startTimeMs = readBytesStartTime,
                details = "container=${runtime.packageName}, sourceType=${runtime.sourceType}, entry=${runtime.mainEntry}, bytes=${bytes.size}"
            )
            val script = bytes.toString(StandardCharsets.UTF_8)
            logMessageTiming(
                stage = "toolpkg.getMainScript.total",
                startTimeMs = totalStartTime,
                details = "container=${runtime.packageName}, entry=${runtime.mainEntry}, scriptLength=${script.length}"
            )
            script
        } catch (e: Exception) {
            AppLogger.e(
                TAG,
                "Failed to read toolpkg main script: ${runtime.packageName}:${runtime.mainEntry}",
                e
            )
            null
        }
    }

    fun getToolPkgComposeDslScreenPath(
        containerPackageName: String,
        uiModuleId: String? = null
    ): String? {
        return toolPkgFacade.getToolPkgComposeDslScreenPath(
            containerPackageName = containerPackageName,
            uiModuleId = uiModuleId
        )
    }

    fun runToolPkgMainHook(
        containerPackageName: String,
        functionName: String,
        event: String,
        eventName: String? = null,
        pluginId: String? = null,
        inlineFunctionSource: String? = null,
        eventPayload: Map<String, Any?> = emptyMap(),
        onIntermediateResult: ((Any?) -> Unit)? = null,
        executionContextKey: String? = null,
        runtimeKind: String? = null,
        dispatchIntermediateOnMain: Boolean = true
    ): Result<Any?> {
        return toolPkgFacade.runToolPkgMainHook(
            containerPackageName = containerPackageName,
            functionName = functionName,
            event = event,
            eventName = eventName,
            pluginId = pluginId,
            inlineFunctionSource = inlineFunctionSource,
            eventPayload = eventPayload,
            onIntermediateResult = onIntermediateResult,
            executionContextKey = executionContextKey,
            runtimeKind = runtimeKind,
            dispatchIntermediateOnMain = dispatchIntermediateOnMain
        )
    }

    fun runToolPkgNavigationEntryAction(
        containerPackageName: String,
        entryId: String,
        functionName: String,
        inlineFunctionSource: String? = null,
        eventPayload: Map<String, Any?> = emptyMap(),
        onIntermediateResult: ((Any?) -> Unit)? = null
    ): Result<Any?> {
        return toolPkgFacade.runToolPkgNavigationEntryAction(
            containerPackageName = containerPackageName,
            entryId = entryId,
            functionName = functionName,
            inlineFunctionSource = inlineFunctionSource,
            eventPayload = eventPayload,
            onIntermediateResult = onIntermediateResult
        )
    }

    fun readToolPkgTextResource(
        packageNameOrSubpackageId: String,
        resourcePath: String,
        preferEnabledContainer: Boolean = true
    ): String? {
        return toolPkgFacade.readToolPkgTextResource(
            packageNameOrSubpackageId = packageNameOrSubpackageId,
            resourcePath = resourcePath,
            preferEnabledContainer = preferEnabledContainer
        )
    }

    fun readToolPkgWasmModuleBytes(
        packageNameOrSubpackageId: String,
        moduleId: String,
        exportName: String,
        preferEnabledContainer: Boolean = true
    ): ToolPkgWasmModuleBytes? {
        return toolPkgFacade.readToolPkgWasmModuleBytes(
            packageNameOrSubpackageId = packageNameOrSubpackageId,
            moduleId = moduleId,
            exportName = exportName,
            preferEnabledContainer = preferEnabledContainer
        )
    }

    /**
     * Automatically enables built-in packages that are marked as enabled by default.
     * This ensures that essential or commonly used packages are available without
     * manual user intervention. It also respects a user's choice to disable a
     * default package.
     */
    private fun initializeDefaultPackages() {
        val enabledPackageNames = getEnabledPackageNamesInternal().toMutableSet()
        val disabledPackages = getDisabledPackagesInternal().toSet()
        var packagesChanged = false

        synchronized(initLock) {
            availablePackages.values.forEach { toolPackage ->
                if (
                    toolPackage.isBuiltIn &&
                    toolPackage.enabledByDefault &&
                    !toolPkgSubpackageByPackageName.containsKey(toolPackage.name) &&
                    !disabledPackages.contains(toolPackage.name)
                ) {
                    if (enabledPackageNames.add(toolPackage.name)) {
                        packagesChanged = true
                        AppLogger.d(TAG, "Auto-enabling default package: ${toolPackage.name}")
                    }
                }
            }
        }

        if (packagesChanged) {
            val prefs = context.getSharedPreferences(PACKAGE_PREFS, Context.MODE_PRIVATE)
            val updatedJson = Json.encodeToString(enabledPackageNames.toList())
            prefs.edit().putString(ENABLED_PACKAGES_KEY, updatedJson).apply()
            AppLogger.d(TAG, "Updated enabled package names with default packages.")
        }
    }

    /**
     * Loads all available packages metadata (from assets and external storage).
     * Includes legacy JS packages and new .toolpkg containers/subpackages.
     */
    private fun loadAvailablePackages(refreshExternalOnly: Boolean = false) {
        val loadStart = System.currentTimeMillis()
        logToolPkgInfo("loadAvailablePackages start")

        val assetSnapshot =
            if (refreshExternalOnly) {
                val cachedSnapshot = assetPackageScanSnapshot
                if (cachedSnapshot != null) {
                    cachedSnapshot
                } else {
                    scanAssetPackages().also { assetPackageScanSnapshot = it }
                }
            } else {
                scanAssetPackages().also { assetPackageScanSnapshot = it }
            }

        val mergedSnapshot = scanExternalPackages(assetSnapshot)
        applyPackageScanSnapshot(mergedSnapshot)
        reconcileToolPkgCaches()
        if (isInitialized) {
            refreshToolPkgRuntimeState(persistIfChanged = true)
        }
        logToolPkgInfo(
            "loadAvailablePackages finish, elapsedMs=${System.currentTimeMillis() - loadStart}, available=${mergedSnapshot.availablePackages.size}, containers=${mergedSnapshot.toolPkgContainers.size}, subpackages=${mergedSnapshot.toolPkgSubpackages.size}, errors=${mergedSnapshot.packageLoadErrors.size}"
        )
    }

    private fun registerToolPkg(loadResult: ToolPkgLoadResult): Boolean {
        return registerToolPkgInto(
            loadResult = loadResult,
            availablePackagesTarget = availablePackages,
            toolPkgContainersTarget = toolPkgContainers,
            toolPkgSubpackageByPackageNameTarget = toolPkgSubpackageByPackageName,
            packageLoadErrorsTarget = packageLoadErrors
        )
    }

    private fun registerToolPkgInto(
        loadResult: ToolPkgLoadResult,
        availablePackagesTarget: MutableMap<String, ToolPackage>,
        toolPkgContainersTarget: MutableMap<String, ToolPkgContainerRuntime>,
        toolPkgSubpackageByPackageNameTarget: MutableMap<String, ToolPkgSubpackageRuntime>,
        packageLoadErrorsTarget: MutableMap<String, String>
    ): Boolean {
        val containerName = loadResult.containerPackage.name
        if (availablePackagesTarget.containsKey(containerName)) {
            packageLoadErrorsTarget[containerName] =
                formatPackageLoadError(
                    message = "Duplicate package name: $containerName",
                    sourcePath = loadResult.containerRuntime.sourcePath
                )
            AppLogger.w(TAG, "Skipped duplicated toolpkg container: $containerName")
            return false
        }

        val duplicateSubpackages =
            loadResult.subpackagePackages
                .map { it.name }
                .filter { availablePackagesTarget.containsKey(it) }

        if (duplicateSubpackages.isNotEmpty()) {
            packageLoadErrorsTarget[containerName] =
                formatPackageLoadError(
                    message = "Duplicate subpackage names: ${duplicateSubpackages.joinToString(", ")}",
                    sourcePath = loadResult.containerRuntime.sourcePath
                )
            AppLogger.w(TAG, "Skipped toolpkg '$containerName' due to duplicate subpackages: $duplicateSubpackages")
            return false
        }

        availablePackagesTarget[containerName] = loadResult.containerPackage
        toolPkgContainersTarget[containerName] = loadResult.containerRuntime

        loadResult.subpackagePackages.forEach { subpackage ->
            availablePackagesTarget[subpackage.name] = subpackage
        }

        loadResult.containerRuntime.subpackages.forEach { runtime ->
            toolPkgSubpackageByPackageNameTarget[runtime.packageName] = runtime
        }
        return true
    }

    /** Loads a complete ToolPackage from a JavaScript file */
    private fun loadPackageFromJsFile(
        file: File,
        reportPackageLoadError: (key: String, error: String) -> Unit = { key, error ->
            packageLoadErrors[key] = error
        }
    ): ToolPackage? {
        try {
            val jsContent = ToolPkgProtection.decodeUtf8(file.readBytes())
            return parseJsPackage(jsContent) { key, error ->
                reportPackageLoadError(
                    key,
                    formatPackageLoadError(
                        message = error,
                        sourcePath = file.path
                    )
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error loading package from JS file: ${file.path}", e)
            reportPackageLoadError(
                file.nameWithoutExtension,
                formatPackageLoadError(
                    message = e.stackTraceToString(),
                    sourcePath = file.path
                )
            )
            return null
        }
    }

    /** Loads a complete ToolPackage from a JavaScript file in assets */
    private fun loadPackageFromJsAsset(
        assetPath: String,
        reportPackageLoadError: (key: String, error: String) -> Unit = { key, error ->
            packageLoadErrors[key] = error
        }
    ): ToolPackage? {
        try {
            val assetManager = context.assets
            val jsContent = assetManager.open(assetPath).use { input ->
                ToolPkgProtection.decodeUtf8(input.readBytes())
            }
            return parseJsPackage(jsContent) { key, error ->
                reportPackageLoadError(
                    key,
                    formatPackageLoadError(
                        message = error,
                        sourcePath = assetPath
                    )
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error loading package from JS asset: $assetPath", e)
            reportPackageLoadError(
                assetPath.substringAfterLast("/").removeSuffix(".js"),
                formatPackageLoadError(
                    message = e.stackTraceToString(),
                    sourcePath = assetPath
                )
            )
            return null
        }
    }

    private inline fun <T> withToolPkgRegistrationEngine(block: (JsEngine) -> T): T {
        // Registration executes untrusted package code. Per-package ownership keeps a timed-out
        // runtime from retaining state or blocking registration of the next package.
        val registrationEngine = JsEngine(context)
        return try {
            block(registrationEngine)
        } finally {
            registrationEngine.destroy()
        }
    }

    private fun loadToolPkgFromExternalFile(
        file: File,
        reportPackageLoadError: (key: String, error: String) -> Unit = { key, error ->
            packageLoadErrors[key] = error
        }
    ): ToolPkgLoadResult? {
        val startMs = System.currentTimeMillis()
        return try {
            withToolPkgRegistrationEngine { registrationEngine ->
                ToolPkgLoader.loadToolPkgFromExternalFile(
                    file = file,
                    jsEngine = registrationEngine,
                    parseJsPackage = { jsContent, onError -> parseJsPackage(jsContent, onError) },
                    reportPackageLoadError = { key, error ->
                        reportPackageLoadError(
                            key,
                            formatPackageLoadError(
                                message = error,
                                sourcePath = file.absolutePath
                            )
                        )
                    }
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error loading toolpkg from external file: ${file.absolutePath}", e)
            logToolPkgError(
                "loadToolPkgFromExternalFile failed, source=${file.absolutePath}, elapsedMs=${System.currentTimeMillis() - startMs}, reason=${e.message ?: e.javaClass.simpleName}",
                e
            )
            reportPackageLoadError(
                file.nameWithoutExtension,
                formatPackageLoadError(
                    message = e.stackTraceToString(),
                    sourcePath = file.absolutePath
                )
            )
            null
        }
    }

    private fun prepareToolPkgAssetCache(
        assetPath: String,
        manifestPreview: ToolPkgManifestPreview
    ): File {
        val toolPkgId =
            manifestPreview.manifest.toolpkgId.trim()
                .ifBlank { throw IllegalArgumentException("manifest.toolpkg_id is required") }
        val mainEntry =
            ToolPkgArchiveParser.resolveManifestRelativeZipEntryPath(
                manifestBasePath = manifestPreview.entryName.substringBeforeLast('/', missingDelimiterValue = ""),
                rawPath = manifestPreview.manifest.main
            )
                ?: throw IllegalArgumentException("manifest.main is required")
        val signature =
            buildToolPkgCacheSignature(
                sourceType = ToolPkgSourceType.ASSET,
                sourcePath = assetPath,
                version = manifestPreview.manifest.version,
                mainEntry = mainEntry
            )
                ?: throw IllegalStateException("Failed to build toolpkg cache signature")
        return ensureToolPkgCacheDir(
            packageName = toolPkgId,
            signature = signature,
            mainEntry = mainEntry,
            extractArchive = { destinationDir ->
                ToolPkgArchiveParser.extractZipEntriesFromAsset(
                    context = context,
                    assetPath = assetPath,
                    destinationDir = destinationDir
                )
            }
        )
            ?: throw IllegalStateException("Failed to prepare toolpkg cache")
    }

    private fun loadToolPkgFromAsset(
        assetPath: String,
        reportPackageLoadError: (key: String, error: String) -> Unit = { key, error ->
            packageLoadErrors[key] = error
        }
    ): ToolPkgLoadResult? {
        val startMs = System.currentTimeMillis()
        return try {
            withToolPkgRegistrationEngine { registrationEngine ->
                ToolPkgLoader.loadToolPkgFromAsset(
                    context = context,
                    assetPath = assetPath,
                    jsEngine = registrationEngine,
                    parseJsPackage = { jsContent, onError -> parseJsPackage(jsContent, onError) },
                    prepareAssetCache = { manifestPreview ->
                        prepareToolPkgAssetCache(
                            assetPath = assetPath,
                            manifestPreview = manifestPreview
                        )
                    },
                    reportPackageLoadError = { key, error ->
                        reportPackageLoadError(
                            key,
                            formatPackageLoadError(
                                message = error,
                                sourcePath = assetPath
                            )
                        )
                    }
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error loading toolpkg from asset: $assetPath", e)
            logToolPkgError(
                "loadToolPkgFromAsset failed, source=$assetPath, elapsedMs=${System.currentTimeMillis() - startMs}, reason=${e.message ?: e.javaClass.simpleName}",
                e
            )
            reportPackageLoadError(
                assetPath.substringAfterLast('/').removeSuffix(TOOLPKG_EXTENSION),
                formatPackageLoadError(
                    message = e.stackTraceToString(),
                    sourcePath = assetPath
                )
            )
            null
        }
    }

    internal fun readToolPkgResourceBytes(
        runtime: ToolPkgContainerRuntime,
        normalizedResourcePath: String
    ): ByteArray? {
        val resourceFile = resolveToolPkgResourceFile(runtime, normalizedResourcePath)
        if (resourceFile == null || !resourceFile.isFile) {
            return null
        }
        return ToolPkgProtection.decryptIfNeeded(resourceFile.readBytes())
    }

    internal fun exportToolPkgResource(
        runtime: ToolPkgContainerRuntime,
        resource: ToolPkgResourceRuntime,
        destinationFile: File
    ): Boolean {
        val resourceFile = resolveToolPkgResourceFile(runtime, resource.path) ?: return false
        val parent = destinationFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }

        return if (ToolPkgArchiveParser.isDirectoryResourceMime(resource.mime)) {
            if (!resourceFile.isDirectory) {
                false
            } else {
                zipToolPkgResourceDirectory(resourceFile, destinationFile)
            }
        } else {
            if (!resourceFile.isFile) {
                false
            } else {
                destinationFile.outputStream().use { output ->
                    output.write(ToolPkgProtection.decryptIfNeeded(resourceFile.readBytes()))
                }
                true
            }
        }
    }

    internal fun copyToolPkgResourceDirectory(
        runtime: ToolPkgContainerRuntime,
        normalizedResourcePath: String,
        destinationDir: File
    ): Boolean {
        val sourceDirectory = resolveToolPkgResourceFile(runtime, normalizedResourcePath) ?: return false
        if (!sourceDirectory.isDirectory) {
            return false
        }
        sourceDirectory
            .walkTopDown()
            .sortedBy { it.relativeTo(sourceDirectory).invariantSeparatorsPath }
            .forEach { source ->
                val relativePath = source.relativeTo(sourceDirectory).invariantSeparatorsPath
                val destination = if (relativePath.isBlank()) destinationDir else File(destinationDir, relativePath)
                if (source.isDirectory) {
                    if (!destination.exists()) {
                        destination.mkdirs()
                    }
                } else if (source.isFile) {
                    val parent = destination.parentFile
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs()
                    }
                    destination.outputStream().use { output ->
                        output.write(ToolPkgProtection.decryptIfNeeded(source.readBytes()))
                    }
                }
            }
        return true
    }

    internal fun resolveToolPkgResourceFile(
        runtime: ToolPkgContainerRuntime,
        normalizedResourcePath: String
    ): File? {
        val normalizedPath = ToolPkgArchiveParser.normalizeResourcePath(normalizedResourcePath) ?: return null
        val cacheDir = ensureToolPkgCache(runtime) ?: return null
        val resourceFile = File(cacheDir, normalizedPath)
        if (!resourceFile.exists()) {
            return null
        }
        return resourceFile
    }

    private fun zipToolPkgResourceDirectory(sourceDirectory: File, destinationZip: File): Boolean {
        val zipRootParent = sourceDirectory.parentFile ?: return false
        destinationZip.outputStream().buffered().use { fileOutput ->
            ZipOutputStream(fileOutput).use { zipOutput ->
                sourceDirectory
                    .walkTopDown()
                    .filter { it.isFile }
                    .sortedBy { it.relativeTo(zipRootParent).invariantSeparatorsPath }
                    .forEach { file ->
                        val entryName = file.relativeTo(zipRootParent).invariantSeparatorsPath
                        val entry = ZipEntry(entryName)
                        zipOutput.putNextEntry(entry)
                        zipOutput.write(ToolPkgProtection.decryptIfNeeded(file.readBytes()))
                        zipOutput.closeEntry()
                    }
            }
        }
        return true
    }

    /**
     * Parses a JavaScript package file into a ToolPackage object Uses the metadata in the file
     * header and extracts function definitions using JsEngine
     */
    private fun parseJsPackage(
        jsContent: String,
        onError: (key: String, error: String) -> Unit = { _, _ -> }
    ): ToolPackage? {
        try {
            // Extract metadata from comments at the top of the file
            val metadataString = extractMetadataFromJs(jsContent)

            // 先将元数据解析为 JSONObject 以便修改 tools 数组中的每个元素
            val metadataJson = org.json.JSONObject(JsonValue.readHjson(metadataString).toString())

            // 统一历史键名/值格式，避免 enabledByDefault 在 Kotlin 侧被错误解析为默认值
            normalizeJsPackageMetadata(metadataJson)

            // 检查并修复 tools 数组中的元素，确保每个工具都有 script 字段
            if (metadataJson.has("tools") && metadataJson.get("tools") is org.json.JSONArray) {
                val toolsArray = metadataJson.getJSONArray("tools")
                for (i in 0 until toolsArray.length()) {
                    val tool = toolsArray.getJSONObject(i)
                    if (!tool.has("script")) {
                        // 添加一个临时的空 script 字段
                        tool.put("script", "")
                    }
                }
            }

            // 检查并修复 states.tools 数组中的元素，确保每个工具都有 script 字段
            if (metadataJson.has("states") && metadataJson.get("states") is org.json.JSONArray) {
                val statesArray = metadataJson.getJSONArray("states")
                for (i in 0 until statesArray.length()) {
                    val state = statesArray.optJSONObject(i) ?: continue
                    if (state.has("tools") && state.get("tools") is org.json.JSONArray) {
                        val toolsArray = state.getJSONArray("tools")
                        for (j in 0 until toolsArray.length()) {
                            val tool = toolsArray.getJSONObject(j)
                            if (!tool.has("script")) {
                                tool.put("script", "")
                            }
                        }
                    }
                }
            }

            // 使用修改后的 JSON 字符串进行反序列化
            val jsonString = metadataJson.toString()

            val jsonConfig = Json { ignoreUnknownKeys = true }
            val packageMetadata = jsonConfig.decodeFromString<ToolPackage>(jsonString)

            // 更新所有工具，使用相同的完整脚本内容，但记录每个工具的函数名
            val tools =
                packageMetadata.tools.map { tool ->
                    // 检查函数是否存在于脚本中
                    if (!tool.advice) {
                        validateToolFunctionExists(jsContent, tool.name)
                    }

                    // 使用整个脚本，并记录函数名，而不是提取单个函数
                    tool.copy(script = jsContent)
                }

            val states =
                packageMetadata.states.map { state ->
                    val stateTools =
                        state.tools.map { tool ->
                            if (!tool.advice) {
                                validateToolFunctionExists(jsContent, tool.name)
                            }
                            tool.copy(script = jsContent)
                        }
                    state.copy(tools = stateTools)
                }

            return packageMetadata.copy(tools = tools, states = states)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error parsing JS package: ${e.message}", e)
            val errorKey = try {
                val metadataString = extractMetadataFromJs(jsContent)
                val metadataJson = org.json.JSONObject(JsonValue.readHjson(metadataString).toString())
                metadataJson.optString("name").takeIf { it.isNotBlank() } ?: "unknown"
            } catch (_: Exception) {
                "unknown"
            }
            onError(errorKey, e.stackTraceToString())
            return null
        }
    }

    private fun normalizeJsPackageMetadata(metadataJson: org.json.JSONObject) {
        normalizeBooleanFieldAlias(
            metadataJson = metadataJson,
            canonicalKey = "enabledByDefault",
            legacyAlias = "enabled_by_default"
        )
        normalizeBooleanFieldAlias(
            metadataJson = metadataJson,
            canonicalKey = "isBuiltIn",
            legacyAlias = "is_built_in"
        )
        normalizeCategoryField(metadataJson)
    }

    private fun normalizeBooleanFieldAlias(
        metadataJson: org.json.JSONObject,
        canonicalKey: String,
        legacyAlias: String
    ) {
        if (!metadataJson.has(canonicalKey) && metadataJson.has(legacyAlias)) {
            metadataJson.put(canonicalKey, metadataJson.opt(legacyAlias))
        }

        if (!metadataJson.has(canonicalKey)) {
            return
        }

        val normalized = normalizeToBoolean(metadataJson.opt(canonicalKey)) ?: return
        metadataJson.put(canonicalKey, normalized)
    }

    private fun normalizeToBoolean(value: Any?): Boolean? {
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> {
                when (value.trim().lowercase()) {
                    "true", "1", "yes", "on" -> true
                    "false", "0", "no", "off" -> false
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun normalizeCategoryField(metadataJson: org.json.JSONObject) {
        val normalized =
            metadataJson
                .opt("category")
                ?.toString()
                ?.trim()
                .orEmpty()

        metadataJson.put("category", normalized.ifBlank { "Other" })
    }

    fun getPackageLoadErrors(): Map<String, String> {
        ensureInitialized()
        return packageLoadErrors.toMap()
    }

    fun getPackageLoadErrorInfos(): List<PackageLoadErrorInfo> {
        ensureInitialized()
        return packageLoadErrors
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .map { (packageName, errorText) ->
                val sourcePath = extractPackageLoadErrorSourcePath(errorText)
                PackageLoadErrorInfo(
                    packageName = packageName,
                    message = stripPackageLoadErrorSourcePath(errorText),
                    sourcePath = sourcePath,
                    isExternalSource = isExternalPackageSourcePath(sourcePath)
                )
            }
    }

    fun deleteExternalPackageSource(sourcePath: String): Boolean {
        ensureInitialized()
        val normalizedSourcePath = sourcePath.trim()
        if (normalizedSourcePath.isEmpty()) {
            return false
        }

        val targetFile = File(normalizedSourcePath)
        val targetCanonicalPath =
            runCatching { targetFile.canonicalPath }.getOrElse { return false }
        if (!isExternalPackageSourcePath(targetCanonicalPath)) {
            AppLogger.w(TAG, "Refusing to delete non-external package source: $normalizedSourcePath")
            return false
        }

        if (targetFile.exists() && (!targetFile.isFile || !targetFile.delete())) {
            AppLogger.e(TAG, "Failed to delete external package source: $targetCanonicalPath")
            return false
        }

        externalPackageScanCache =
            externalPackageScanCache.filterKeys { cachedPath ->
                val cachedCanonicalPath =
                    runCatching { File(cachedPath).canonicalPath }.getOrElse { cachedPath }
                !cachedCanonicalPath.equals(targetCanonicalPath, ignoreCase = true)
            }
        loadAvailablePackages(refreshExternalOnly = true)
        return true
    }

    /** 验证JavaScript文件中是否存在指定的函数 这确保了我们可以在运行时调用该函数 */
    private fun validateToolFunctionExists(jsContent: String, toolName: String): Boolean {
        // 各种函数声明模式
        val patterns =
            listOf(
                """async\s+function\s+$toolName\s*\(""",
                """function\s+$toolName\s*\(""",
                """exports\.$toolName\s*=\s*(?:async\s+)?function""",
                """(?:const|let|var)\s+$toolName\s*=\s*(?:async\s+)?\(""",
                """exports\.$toolName\s*=\s*(?:async\s+)?\(?"""
            )

        for (pattern in patterns) {
            if (pattern.toRegex().find(jsContent) != null) {
                return true
            }
        }

        AppLogger.w(TAG, "Could not find function '$toolName' in JavaScript file")
        return false
    }

    /** Extracts the metadata from JS comments at the top of the file */
    private fun extractMetadataFromJs(jsContent: String): String {
        val metadataPattern = """/\*\s*METADATA\s*([\s\S]*?)\*/""".toRegex()
        val match = metadataPattern.find(jsContent)

        return if (match != null) {
            match.groupValues[1].trim()
        } else {
            // If no metadata block is found, return empty metadata
            "{}"
        }
    }

    /**
     * Returns the path to the external packages directory This can be used to show the user where
     * the packages are stored for manual editing
     */
    fun getExternalPackagesPath(): String {
        // 为了更易读，改成Android/data/包名/files/packages的形式
        return "Android/data/${context.packageName}/files/packages"
    }

    fun getPluginConfigDirPath(pluginId: String): String {
        val candidate = pluginId.trim()
        if (candidate.isBlank()) {
            return ""
        }

        val resolvedPluginId =
            resolveToolPkgSubpackageRuntime(candidate)?.containerPackageName
                ?: candidate
        return OperitPaths.pluginConfigDir(resolvedPluginId).absolutePath
    }

    /**
     * Imports a package from external storage path.
     * Supports legacy JS/TS/HJSON files and .toolpkg containers.
     */
    fun addPackageFileFromExternalStorage(filePath: String): String {
        try {
            ensureInitialized()

            val file = File(filePath)
            if (!file.exists() || !file.canRead()) {
                return "Cannot access file at path: $filePath"
            }

            val lowerPath = filePath.lowercase()
            val isToolPkg = lowerPath.endsWith(TOOLPKG_EXTENSION)
            val isJsLike = lowerPath.endsWith(".js") || lowerPath.endsWith(".ts")
            val isHjson = lowerPath.endsWith(".hjson")

            if (!isToolPkg && !isJsLike && !isHjson) {
                return "Only .toolpkg, HJSON, JavaScript (.js) and TypeScript (.ts) package files are supported"
            }

            if (isToolPkg) {
                val preview = loadToolPkgFromExternalFile(file)
                    ?: return "Failed to parse toolpkg file"
                val containerName = preview.containerPackage.name
                if (availablePackages.containsKey(containerName)) {
                    return "A package with name '$containerName' already exists in available packages"
                }

                val conflictSubpackages =
                    preview.subpackagePackages
                        .map { it.name }
                        .filter { availablePackages.containsKey(it) }
                if (conflictSubpackages.isNotEmpty()) {
                    return "Subpackage name conflict: ${conflictSubpackages.joinToString(", ")}"
                }

                val destinationFile = File(externalPackagesDir, file.name)
                if (file.absolutePath != destinationFile.absolutePath) {
                    file.inputStream().use { input ->
                        destinationFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }

                val loadedFromDestination = loadToolPkgFromExternalFile(destinationFile)
                    ?: return "Failed to parse copied toolpkg file"
                if (!registerToolPkg(loadedFromDestination)) {
                    return "Failed to register toolpkg '$containerName' due to naming conflict"
                }

                return "Successfully imported toolpkg: $containerName\nStored at: ${destinationFile.absolutePath}"
            }

            val packageMetadata =
                if (isHjson) {
                    val hjsonContent = file.readText()
                    val metadataJson = org.json.JSONObject(JsonValue.readHjson(hjsonContent).toString())
                    normalizeJsPackageMetadata(metadataJson)
                    val jsonString = metadataJson.toString()
                    val jsonConfig = Json { ignoreUnknownKeys = true }
                    jsonConfig.decodeFromString<ToolPackage>(jsonString)
                } else {
                    loadPackageFromJsFile(file)
                        ?: return "Failed to parse ${if (lowerPath.endsWith(".ts")) "TypeScript" else "JavaScript"} package file"
                }

            if (availablePackages.containsKey(packageMetadata.name)) {
                return "A package with name '${packageMetadata.name}' already exists in available packages"
            }

            val destinationFile = File(externalPackagesDir, file.name)
            if (file.absolutePath != destinationFile.absolutePath) {
                file.inputStream().use { input ->
                    destinationFile.outputStream().use { output -> input.copyTo(output) }
                }
            }

            availablePackages[packageMetadata.name] = packageMetadata.copy(isBuiltIn = false)

            AppLogger.d(TAG, "Successfully imported external package to: ${destinationFile.absolutePath}")
            return "Successfully imported package: ${packageMetadata.name}\nStored at: ${destinationFile.absolutePath}"
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error importing package from external storage", e)
            return "Error importing package: ${e.message}"
        }
    }

    private fun findDuplicateExternalToolPkgFiles(
        containerPackageName: String,
        preferredFilePath: String
    ): List<File> {
        val normalizedPackageName = normalizePackageName(containerPackageName)
        val preferredCanonicalPath =
            runCatching { File(preferredFilePath).canonicalPath }
                .getOrElse { File(preferredFilePath).absolutePath }

        return (externalPackagesDir.listFiles() ?: emptyArray())
            .asSequence()
            .filter { candidate ->
                candidate.isFile && candidate.name.endsWith(TOOLPKG_EXTENSION, ignoreCase = true)
            }
            .filterNot { candidate ->
                val candidateCanonicalPath =
                    runCatching { candidate.canonicalPath }.getOrElse { candidate.absolutePath }
                candidateCanonicalPath == preferredCanonicalPath
            }
            .filter { candidate ->
                val loadResult =
                    loadToolPkgFromExternalFile(candidate) { _, _ -> }
                        ?: return@filter false
                loadResult.containerPackage.name == normalizedPackageName
            }
            .toList()
    }

    private fun collectRelatedToolPkgLoadErrors(
        containerPackageName: String,
        externalFilePath: String
    ): Map<String, String> {
        val normalizedPackageName = normalizePackageName(containerPackageName)
        val normalizedExternalFilePath = externalFilePath.trim()
        return getPackageLoadErrors()
            .filter { (key, value) ->
                key.equals(normalizedPackageName, ignoreCase = true) ||
                    value.contains(normalizedPackageName, ignoreCase = true) ||
                    value.contains(normalizedExternalFilePath, ignoreCase = true)
            }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }

    /**
     * Debug-only install path for ToolPkg development.
     *
     * The archive is expected to already be pushed into the app's external packages directory.
     * This method refreshes the package scan, enables the container, resets subpackage states
     * from manifest defaults when requested, and reactivates any subpackages that were active
     * before the refresh so tool registrations do not stay stale.
     */
    fun installDebugToolPkg(
        containerPackageName: String,
        externalFilePath: String,
        resetSubpackageStatesToManifest: Boolean = true
    ): String {
        ensureInitialized()

        val normalizedContainerPackageName = normalizePackageName(containerPackageName)
        if (normalizedContainerPackageName.isBlank()) {
            return "Missing toolpkg package name"
        }

        val normalizedExternalFilePath = externalFilePath.trim()
        if (normalizedExternalFilePath.isBlank()) {
            return "Missing toolpkg file path"
        }

        val targetFile = File(normalizedExternalFilePath)
        if (!targetFile.exists()) {
            return "ToolPkg file not found: ${targetFile.absolutePath}"
        }
        if (!targetFile.isFile) {
            return "ToolPkg path is not a file: ${targetFile.absolutePath}"
        }
        if (!targetFile.name.endsWith(TOOLPKG_EXTENSION, ignoreCase = true)) {
            return "ToolPkg debug install only supports .toolpkg files: ${targetFile.absolutePath}"
        }

        val externalPackagesCanonicalPath =
            runCatching { externalPackagesDir.canonicalPath }.getOrElse { externalPackagesDir.absolutePath }
        val targetParentCanonicalPath =
            runCatching { targetFile.parentFile?.canonicalPath }
                .getOrElse { targetFile.parentFile?.absolutePath }
                .orEmpty()
        if (targetParentCanonicalPath != externalPackagesCanonicalPath) {
            return "ToolPkg debug install expects file inside external packages dir: $externalPackagesCanonicalPath"
        }

        val previousContainerRuntime = toolPkgContainers[normalizedContainerPackageName]
        val previousSubpackageNames =
            previousContainerRuntime
                ?.subpackages
                ?.map(ToolPkgSubpackageRuntime::packageName)
                .orEmpty()
        val previouslyActivePackages =
            previousSubpackageNames
                .filter { packageName -> activePackageToolNames.containsKey(packageName) }
                .toSortedSet(String.CASE_INSENSITIVE_ORDER)

        val removedDuplicateArchives = mutableListOf<String>()
        findDuplicateExternalToolPkgFiles(
            containerPackageName = normalizedContainerPackageName,
            preferredFilePath = normalizedExternalFilePath
        ).forEach { duplicateFile ->
            if (!duplicateFile.delete()) {
                return "Failed to remove duplicate external toolpkg file: ${duplicateFile.absolutePath}"
            }
            removedDuplicateArchives += duplicateFile.absolutePath
        }

        getAvailablePackages(forceRefresh = true)

        val runtime = toolPkgContainers[normalizedContainerPackageName]
        if (runtime == null) {
            val relatedErrors =
                collectRelatedToolPkgLoadErrors(
                    containerPackageName = normalizedContainerPackageName,
                    externalFilePath = normalizedExternalFilePath
                )
            return buildString {
                append("Failed to reload debug toolpkg: ")
                append(normalizedContainerPackageName)
                if (relatedErrors.isNotEmpty()) {
                    append("\nRelated load errors:")
                    relatedErrors.forEach { (key, value) ->
                        append("\n- ")
                        append(key)
                        append(": ")
                        append(value)
                    }
                }
            }
        }

        if (runtime.sourceType != ToolPkgSourceType.EXTERNAL) {
            val relatedErrors =
                collectRelatedToolPkgLoadErrors(
                    containerPackageName = normalizedContainerPackageName,
                    externalFilePath = normalizedExternalFilePath
                )
            return buildString {
                append("Failed to install debug toolpkg '")
                append(normalizedContainerPackageName)
                append("': a built-in package with the same name is taking precedence")
                if (relatedErrors.isNotEmpty()) {
                    append("\nRelated load errors:")
                    relatedErrors.forEach { (key, value) ->
                        append("\n- ")
                        append(key)
                        append(": ")
                        append(value)
                    }
                }
            }
        }

        val runtimeSourceCanonicalPath =
            runCatching { File(runtime.sourcePath).canonicalPath }.getOrElse { File(runtime.sourcePath).absolutePath }
        val targetCanonicalPath =
            runCatching { targetFile.canonicalPath }.getOrElse { targetFile.absolutePath }
        if (runtimeSourceCanonicalPath != targetCanonicalPath) {
            return buildString {
                append("Reloaded toolpkg source does not match pushed archive for '")
                append(normalizedContainerPackageName)
                append("'\nExpected: ")
                append(targetCanonicalPath)
                append("\nActual: ")
                append(runtimeSourceCanonicalPath)
            }
        }

        if (resetSubpackageStatesToManifest) {
            val updatedStates = getToolPkgSubpackageStatesInternal().toMutableMap()
            val currentSubpackageNames =
                runtime.subpackages
                    .map(ToolPkgSubpackageRuntime::packageName)
                    .toSet()

            previousSubpackageNames
                .filterNot { previousName -> currentSubpackageNames.contains(previousName) }
                .forEach(updatedStates::remove)

            runtime.subpackages.forEach { subpackage ->
                updatedStates[subpackage.packageName] = subpackage.enabledByDefault
            }

            saveToolPkgSubpackageStates(updatedStates)
        }

        val enableMessage = enablePackage(normalizedContainerPackageName)

        destroyToolPkgExecutionEngines(normalizedContainerPackageName)

        (
            previousSubpackageNames +
                runtime.subpackages.map(ToolPkgSubpackageRuntime::packageName)
            )
            .distinct()
            .forEach(::unregisterPackageTools)

        val reactivatedPackages = mutableListOf<String>()
        val reactivationFailures = mutableListOf<String>()
        previouslyActivePackages.forEach { packageName ->
            if (!toolPkgSubpackageByPackageName.containsKey(packageName)) {
                return@forEach
            }
            if (!isPackageEnabled(packageName)) {
                return@forEach
            }

            val useMessage = usePackage(packageName)
            if (useMessage.startsWith("Using package:")) {
                reactivatedPackages += packageName
            } else {
                reactivationFailures += "$packageName -> $useMessage"
            }
        }

        val enabledSubpackages =
            runtime.subpackages
                .map(ToolPkgSubpackageRuntime::packageName)
                .filter(::isPackageEnabled)

        return buildString {
            append("Successfully installed debug toolpkg: ")
            append(normalizedContainerPackageName)
            append("\nSource file: ")
            append(targetCanonicalPath)
            append("\n")
            append(enableMessage)
            append("\nEnabled subpackages: ")
            append(if (enabledSubpackages.isEmpty()) "(none)" else enabledSubpackages.joinToString(", "))
            if (removedDuplicateArchives.isNotEmpty()) {
                append("\nRemoved duplicate archives: ")
                append(removedDuplicateArchives.joinToString(", "))
            }
            if (reactivatedPackages.isNotEmpty()) {
                append("\nReactivated packages: ")
                append(reactivatedPackages.joinToString(", "))
            }
            if (reactivationFailures.isNotEmpty()) {
                append("\nReactivation failures: ")
                append(reactivationFailures.joinToString(" | "))
            }
        }
    }

    fun refreshExternalPackagesForDebug(
        reactivateActivePackages: Boolean = true
    ): String {
        ensureInitialized()

        val previouslyActivePackages =
            activePackageToolNames.keys
                .toSortedSet(String.CASE_INSENSITIVE_ORDER)
                .toList()
        val enabledToolPkgContainersBeforeRefresh =
            getEnabledToolPkgContainerRuntimes()
                .map(ToolPkgContainerRuntime::packageName)
                .toSortedSet(String.CASE_INSENSITIVE_ORDER)
                .toList()

        getAvailablePackages(forceRefresh = true)

        enabledToolPkgContainersBeforeRefresh.forEach(::destroyToolPkgExecutionEngines)
        previouslyActivePackages.forEach(::unregisterPackageTools)

        val reactivatedPackages = mutableListOf<String>()
        val skippedPackages = mutableListOf<String>()
        val reactivationFailures = mutableListOf<String>()

        if (reactivateActivePackages) {
            previouslyActivePackages.forEach { packageName ->
                if (!availablePackages.containsKey(packageName)) {
                    skippedPackages += "$packageName -> package missing after refresh"
                    return@forEach
                }
                if (!isPackageEnabled(packageName)) {
                    skippedPackages += "$packageName -> package disabled after refresh"
                    return@forEach
                }

                val useMessage = usePackage(packageName)
                if (activePackageToolNames.containsKey(packageName)) {
                    reactivatedPackages += packageName
                } else {
                    reactivationFailures += "$packageName -> $useMessage"
                }
            }
        }

        val enabledExternalContainers =
            getEnabledToolPkgContainerRuntimes()
                .filter { runtime -> runtime.sourceType == ToolPkgSourceType.EXTERNAL }
                .map(ToolPkgContainerRuntime::packageName)
                .sorted()

        return buildString {
            append("Successfully refreshed external packages for debug")
            append("\nAvailable packages: ")
            append(availablePackages.size)
            append("\nEnabled external toolpkg containers: ")
            append(
                if (enabledExternalContainers.isEmpty()) "(none)"
                else enabledExternalContainers.joinToString(", ")
            )
            append("\nReactivated packages: ")
            append(
                if (reactivatedPackages.isEmpty()) "(none)"
                else reactivatedPackages.joinToString(", ")
            )
            if (skippedPackages.isNotEmpty()) {
                append("\nSkipped packages: ")
                append(skippedPackages.joinToString(" | "))
            }
            if (reactivationFailures.isNotEmpty()) {
                append("\nReactivation failures: ")
                append(reactivationFailures.joinToString(" | "))
            }
        }
    }

    /**
     * Enable a package by name, adding it to the user's enabled package list.
     * For toolpkg containers this may also activate default-enabled subpackages.
     */
    fun enablePackage(packageName: String): String {
        ensureInitialized()
        val normalizedPackageName = normalizePackageName(packageName)

        if (!availablePackages.containsKey(normalizedPackageName)) {
            return "Package not found in available packages: $normalizedPackageName"
        }

        val enabledPackageNames = LinkedHashSet(getEnabledPackageNames())
        val subpackageStates = getToolPkgSubpackageStatesInternal().toMutableMap()

        val containerRuntime = toolPkgContainers[normalizedPackageName]
        if (containerRuntime != null) {
            val containerAlreadyEnabled = enabledPackageNames.contains(normalizedPackageName)
            enabledPackageNames.add(normalizedPackageName)

            containerRuntime.subpackages.forEach { subpackage ->
                val shouldEnable =
                    subpackageStates[subpackage.packageName] ?: subpackage.enabledByDefault
                subpackageStates.putIfAbsent(subpackage.packageName, shouldEnable)

                if (shouldEnable) {
                    enabledPackageNames.add(subpackage.packageName)
                } else {
                    enabledPackageNames.remove(subpackage.packageName)
                }
            }

            saveEnabledPackageNames(enabledPackageNames.toList())
            saveToolPkgSubpackageStates(subpackageStates)
            removeFromDisabledPackages(normalizedPackageName)
            ensureToolPkgCache(containerRuntime)

            val message =
                if (containerAlreadyEnabled) {
                    "ToolPkg container '$normalizedPackageName' is already enabled"
                } else {
                    "Successfully enabled toolpkg container: $normalizedPackageName"
                }
            AppLogger.d(TAG, message)
            return message
        }

        val subpackageRuntime = toolPkgSubpackageByPackageName[normalizedPackageName]
        if (subpackageRuntime != null) {
            enabledPackageNames.add(subpackageRuntime.containerPackageName)
            enabledPackageNames.add(normalizedPackageName)
            subpackageStates[normalizedPackageName] = true

            saveEnabledPackageNames(enabledPackageNames.toList())
            saveToolPkgSubpackageStates(subpackageStates)
            removeFromDisabledPackages(subpackageRuntime.containerPackageName)
            toolPkgContainers[subpackageRuntime.containerPackageName]?.let { runtime ->
                ensureToolPkgCache(runtime)
            }

            val message = "Successfully enabled toolpkg subpackage: $normalizedPackageName"
            AppLogger.d(TAG, message)
            return message
        }

        if (enabledPackageNames.contains(normalizedPackageName)) {
            return "Package '$normalizedPackageName' is already enabled"
        }

        enabledPackageNames.add(normalizedPackageName)
        saveEnabledPackageNames(enabledPackageNames.toList())
        removeFromDisabledPackages(normalizedPackageName)

        AppLogger.d(TAG, "Successfully enabled package: $normalizedPackageName")
        return "Successfully enabled package: $normalizedPackageName"
    }

    /**
     * Activates and loads a package for use in the current AI session This loads the full package
     * data and registers its tools with AIToolHandler
     * @param packageName The name of the enabled package to use
     * @return Package description and tools for AI prompt enhancement, or error message
     */
    fun usePackage(packageName: String): String {
        ensureInitialized()
        val normalizedPackageName = normalizePackageName(packageName)

        val containerRuntime = toolPkgContainers[normalizedPackageName]
        if (containerRuntime != null) {
            return "ToolPkg container '$normalizedPackageName' is not a package and cannot be activated."
        }

        // First check if packageName is a standard enabled package (priority)
        val enabledPackageNames = getEnabledPackageNames()
        val subpackageRuntime = toolPkgSubpackageByPackageName[normalizedPackageName]
        if (subpackageRuntime != null &&
            !enabledPackageNames.contains(subpackageRuntime.containerPackageName)
        ) {
            return "ToolPkg container '${subpackageRuntime.containerPackageName}' is not enabled. Package '$normalizedPackageName' is inactive."
        }
        if (enabledPackageNames.contains(normalizedPackageName)) {
            // Load the full package data for a standard package
            val toolPackage =
                getPackageTools(normalizedPackageName)
                    ?: return "Failed to load package data for: $normalizedPackageName"

            // Validate required environment variables, if any
            if (toolPackage.env.isNotEmpty()) {
                val missingRequiredEnv = mutableListOf<String>()
                val missingOptionalEnv = mutableListOf<Pair<String, String>>() // env name, default value

                toolPackage.env.forEach { envVar ->
                    val envName = envVar.name.trim()
                    if (envName.isEmpty()) return@forEach

                    val value = try {
                        envPreferences.getEnv(envName)
                    } catch (e: Exception) {
                        AppLogger.e(
                            TAG,
                            "Error reading environment variable '$envName' for package '$normalizedPackageName'",
                            e
                        )
                        null
                    }

                    if (envVar.required) {
                        // Check required environment variables
                        if (value.isNullOrEmpty()) {
                            missingRequiredEnv.add(envName)
                        }
                    } else {
                        // Check optional environment variables
                        if (value.isNullOrEmpty()) {
                            if (envVar.defaultValue != null) {
                                // Use default value for optional env vars
                                missingOptionalEnv.add(envName to envVar.defaultValue)
                                AppLogger.d(
                                    TAG,
                                    "Optional env var '$envName' not set for package '$normalizedPackageName', using default value: ${envVar.defaultValue}"
                                )
                            } else {
                                // Optional env var without default value is acceptable
                                AppLogger.d(
                                    TAG,
                                    "Optional env var '$envName' not set for package '$normalizedPackageName' (no default value)"
                                )
                            }
                        }
                    }
                }

                // Only fail if required environment variables are missing
                if (missingRequiredEnv.isNotEmpty()) {
                    val msg =
                        buildString {
                            append("Package '")
                            append(normalizedPackageName)
                            append("' requires environment variable")
                            if (missingRequiredEnv.size > 1) append("s")
                            append(": ")
                            append(missingRequiredEnv.joinToString(", "))
                            append(". Please set them before using this package.")
                        }
                    AppLogger.w(TAG, msg)
                    return msg
                }

                // Log info about optional env vars using defaults
                if (missingOptionalEnv.isNotEmpty()) {
                    AppLogger.i(
                        TAG,
                        "Package '$normalizedPackageName' will use default values for optional env vars: ${missingOptionalEnv.map { it.first }.joinToString(", ")}"
                    )
                }
            }

            // Register the package tools with AIToolHandler
            val selectedPackage = selectToolPackageState(toolPackage)
            registerPackageTools(selectedPackage)

            AppLogger.d(TAG, "Successfully loaded and activated package: $normalizedPackageName")

            // Generate and return the system prompt enhancement
            return generatePackageSystemPrompt(selectedPackage)
        }

        // Then check if it's a Skill package
        if (skillManager.getAvailableSkills().containsKey(normalizedPackageName) &&
            !skillVisibilityPreferences.isSkillVisibleToAi(normalizedPackageName)
        ) {
            return "Skill '$normalizedPackageName' is set to not show to AI"
        }

        val skillPrompt = skillManager.getSkillSystemPrompt(normalizedPackageName)
        if (skillPrompt != null) {
            return skillPrompt
        }

        // Next check if it's an MCP server by checking with MCPManager
        if (isRegisteredMCPServer(normalizedPackageName)) {
            return useMCPServer(normalizedPackageName)
        }

        return "Package not found: $normalizedPackageName. Please import it first or register it as an MCP server."
    }

    fun getActivePackageStateId(packageName: String): String? {
        ensureInitialized()
        val normalizedPackageName = normalizePackageName(packageName)
        return activePackageStateIds[normalizedPackageName]
    }

    /**
     * Wrapper for tool execution: builds ToolResult for the 'use_package' tool.
     * Keeps registration site minimal by centralizing result construction here.
     */
    fun executeUsePackageTool(toolName: String, packageName: String): ToolResult {
        if (packageName.isBlank()) {
            return ToolResult(
                toolName = toolName,
                success = false,
                result = StringResultData(""),
                error = "Missing required parameter: package_name"
            )
        }

        val normalizedPackageName = normalizePackageName(packageName)
        if (isToolPkgContainer(normalizedPackageName)) {
            return ToolResult(
                toolName = toolName,
                success = false,
                result = StringResultData(""),
                error = "ToolPkg container '$normalizedPackageName' is not a package and cannot be activated."
            )
        }

        if (skillManager.getAvailableSkills().containsKey(normalizedPackageName) &&
            !skillVisibilityPreferences.isSkillVisibleToAi(normalizedPackageName)
        ) {
            return ToolResult(
                toolName = toolName,
                success = false,
                result = StringResultData(""),
                error = "Skill '$normalizedPackageName' is set to not show to AI"
            )
        }

        val text = usePackage(normalizedPackageName)
        return ToolResult(
            toolName = toolName,
            success = true,
            result = StringResultData(text)
        )
    }

    /**
     * 检查是否是已注册的MCP服务器
     *
     * @param serverName 服务器名称
     * @return 如果是已注册的MCP服务器则返回true
     */
    private fun isRegisteredMCPServer(serverName: String): Boolean {
        return mcpManager.isServerRegistered(serverName)
    }

    /**
     * 获取所有可用的MCP服务器包
     *
     * @return MCP服务器列表
     */
    fun getAvailableServerPackages(): Map<String, MCPServerConfig> {
        return mcpManager.getRegisteredServers()
    }

    // Helper function to determine if a package is an MCP server
    private fun isMCPServerPackage(toolPackage: ToolPackage): Boolean {
        // Check if any tool has MCP script placeholder
        return if (toolPackage.tools.isNotEmpty()) {
            val script = toolPackage.tools[0].script
            script.contains("/* MCPJS") // Check for MCP script marker
        } else {
            false
        }
    }

    /** Registers all tools in a package with the AIToolHandler */
    private fun registerPackageTools(toolPackage: ToolPackage) {
        val packageToolExecutor = PackageToolExecutor(toolPackage, context, this)
        val executableTools = toolPackage.tools.filter { !it.advice }
        val newToolNames = executableTools.map { packageTool -> "${toolPackage.name}:${packageTool.name}" }.toSet()
        val oldToolNames = activePackageToolNames[toolPackage.name] ?: emptySet()
        (oldToolNames - newToolNames).forEach { toolName ->
            aiToolHandler.unregisterTool(toolName)
        }
        activePackageToolNames[toolPackage.name] = newToolNames

        // Register each tool with the format packageName:toolName
        executableTools.forEach { packageTool ->
            val toolName = "${toolPackage.name}:${packageTool.name}"
            aiToolHandler.registerTool(toolName) { tool ->
                packageToolExecutor.invoke(tool)
            }
        }
    }

    private fun selectToolPackageState(toolPackage: ToolPackage): ToolPackage {
        if (toolPackage.states.isEmpty()) {
            activePackageStateIds.remove(toolPackage.name)
            return toolPackage
        }

        val capabilities = buildConditionCapabilitiesSnapshot()
        val selectedState = toolPackage.states.firstOrNull { state ->
            ConditionEvaluator.evaluate(state.condition, capabilities)
        }

        if (selectedState == null) {
            activePackageStateIds.remove(toolPackage.name)
            return toolPackage
        }

        activePackageStateIds[toolPackage.name] = selectedState.id

        val mergedTools = mergeToolsForState(toolPackage.tools, selectedState)
        return toolPackage.copy(tools = mergedTools)
    }

    private fun mergeToolsForState(baseTools: List<PackageTool>, state: ToolPackageState): List<PackageTool> {
        val toolMap = linkedMapOf<String, PackageTool>()
        if (state.inheritTools) {
            baseTools.forEach { toolMap[it.name] = it }
        }
        state.excludeTools.forEach { toolMap.remove(it) }
        state.tools.forEach { toolMap[it.name] = it }
        return toolMap.values.toList()
    }

    private fun buildConditionCapabilitiesSnapshot(): Map<String, Any?> {
        val level = try {
            androidPermissionPreferences.getPreferredPermissionLevel() ?: AndroidPermissionLevel.STANDARD
        } catch (_: Exception) {
            AndroidPermissionLevel.STANDARD
        }

        val shizukuAvailable = try {
            ShizukuAuthorizer.isShizukuServiceRunning() && ShizukuAuthorizer.hasShizukuPermission()
        } catch (_: Exception) {
            false
        }

        val experimentalEnabled = try {
            DisplayPreferencesManager.getInstance(context).isExperimentalVirtualDisplayEnabled()
        } catch (_: Exception) {
            true
        }

        val adbOrHigher = when (level) {
            AndroidPermissionLevel.DEBUGGER,
            AndroidPermissionLevel.ADMIN,
            AndroidPermissionLevel.ROOT -> true
            else -> false
        }

        val virtualDisplayCapable = adbOrHigher && experimentalEnabled && (level != AndroidPermissionLevel.DEBUGGER || shizukuAvailable)

        return mapOf(
            "platform.name" to "android",
            "platform.windows" to false,
            "platform.linux" to false,
            "platform.android" to true,
            "platform.macos" to false,
            "ui.virtual_display" to virtualDisplayCapable,
            "android.permission_level" to level,
            "android.shizuku_available" to shizukuAvailable,
            "ui.shower_display" to (try { ShowerController.getDisplayId("default") != null } catch (_: Exception) { false })
        )
    }

    /** Generates a system prompt enhancement for the imported package */
    private fun generatePackageSystemPrompt(toolPackage: ToolPackage): String {
        val sb = StringBuilder()

        sb.appendLine("Using package: ${toolPackage.name}")
        sb.appendLine("Use Time: ${java.time.LocalDateTime.now()}")
        sb.appendLine("Description: ${toolPackage.description.resolve(context)}")
        sb.appendLine()
        sb.appendLine("Available tools in this package:")

        toolPackage.tools.forEach { tool ->
            val toolLabel =
                if (tool.advice) {
                    "- (advice): ${tool.description.resolve(context)}"
                } else {
                    "- ${toolPackage.name}:${tool.name}: ${tool.description.resolve(context)}"
                }
            sb.appendLine(toolLabel)
            if (tool.parameters.isNotEmpty()) {
                sb.appendLine("  Parameters:")
                tool.parameters.forEach { param ->
                    val requiredText = if (param.required) "(required)" else "(optional)"
                    sb.appendLine("  - ${param.name} ${requiredText}: ${param.description.resolve(context)}")
                }
            }
            sb.appendLine()
        }

        return sb.toString()
    }

    /**
     * Gets a list of all available packages for discovery (the "market").
     *
     * By default this returns the in-memory cache to avoid re-scanning assets/external storage
     * on every call (which is expensive and can spam logs).
     *
     * @param forceRefresh Set to true to explicitly rescan package sources.
     * @return A map of package name to description
     */
    fun getAvailablePackages(forceRefresh: Boolean = false): Map<String, ToolPackage> {
        val wasInitializedBeforeCall = isInitialized
        ensureInitialized()
        if (forceRefresh && !wasInitializedBeforeCall) {
            return availablePackages
        }
        if (forceRefresh) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                initializationScope.launch {
                    runCatching { loadAvailablePackages(refreshExternalOnly = true) }
                        .onFailure { error ->
                            AppLogger.e(TAG, "Failed to refresh packages on background", error)
                            logToolPkgError("forceRefresh background reload failed", error)
                        }
                }
            } else {
                loadAvailablePackages(refreshExternalOnly = true)
            }
        }
        return availablePackages
    }

    /**
     * Get a list of all enabled packages
     * @return A list of enabled package names
     */
    fun getEnabledPackageNames(): List<String> {
        ensureInitialized()
        if (runtimeCachesReady) {
            return enabledPackageNamesCache
        }
        return getEnabledPackageNamesInternal()
    }

    private fun getEnabledPackageNamesInternal(): List<String> {
        if (runtimeCachesReady) {
            return enabledPackageNamesCache
        }
        val normalizedPackages = decodeEnabledPackageNamesFromPrefs()
        return if (!isInitialized) normalizedPackages else cleanupMissingEnabledPackages(normalizedPackages)
    }

    /**
     * 清理启用列表中不存在的包。
     * 自动移除那些已经被删除但仍然在启用列表中的包。
     */
    private fun cleanupMissingEnabledPackages(currentPackages: List<String>): List<String> {
        // Serialize cleanup with package reload to avoid transient map states
        // (e.g. during forceRefresh) causing accidental removal of valid enabled packages.
        synchronized(initLock) {
            val normalizedPackages = normalizeEnabledPackageNames(currentPackages)
            val cleanedPackages = normalizedPackages.filter { packageName ->
                availablePackages.containsKey(packageName)
            }

            if (cleanedPackages.size != currentPackages.size || cleanedPackages != currentPackages) {
                val removed = currentPackages.filter { !cleanedPackages.contains(it) }
                AppLogger.d(
                    TAG,
                    "Found ${removed.size} non-existent packages in enabled list: $removed"
                )
                saveEnabledPackageNames(cleanedPackages)
                AppLogger.d(TAG, "Cleaned up enabled package list. Removed: $removed")
            }

            val states = getToolPkgSubpackageStatesInternal()
            val cleanedStates =
                states.filterKeys { packageName ->
                    toolPkgSubpackageByPackageName.containsKey(packageName)
                }

            if (cleanedStates.size != states.size) {
                saveToolPkgSubpackageStates(cleanedStates)
            }

            return cleanedPackages
        }
    }

    /**
     * Get a list of all disabled packages
     * @return A list of disabled package names
     */
    fun getDisabledPackages(): List<String> {
        ensureInitialized()
        return getDisabledPackagesInternal()
    }

    private fun getDisabledPackagesInternal(): List<String> {
        val prefs = context.getSharedPreferences(PACKAGE_PREFS, Context.MODE_PRIVATE)
        val packagesJson = prefs.getString(DISABLED_PACKAGES_KEY, "[]")
        return try {
            val jsonConfig = Json { ignoreUnknownKeys = true }
            jsonConfig.decodeFromString<List<String>>(packagesJson ?: "[]")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error decoding disabled packages", e)
            emptyList()
        }
    }

    /** Helper to save disabled packages */
    private fun saveDisabledPackages(disabledPackages: List<String>) {
        val prefs = context.getSharedPreferences(PACKAGE_PREFS, Context.MODE_PRIVATE)
        val updatedJson = Json.encodeToString(disabledPackages)
        prefs.edit().putString(DISABLED_PACKAGES_KEY, updatedJson).apply()
    }

    internal fun saveEnabledPackageNames(enabledPackageNames: List<String>) {
        val normalizedPackages = normalizeEnabledPackageNames(enabledPackageNames)
        persistEnabledPackageNamesToPrefs(normalizedPackages)
        if (isInitialized || runtimeCachesReady) {
            refreshToolPkgRuntimeState(
                persistIfChanged = false,
                enabledPackageNamesOverride = normalizedPackages
            )
        }
    }

    internal fun getToolPkgSubpackageStatesInternal(): Map<String, Boolean> {
        if (runtimeCachesReady) {
            return toolPkgSubpackageStatesCache
        }
        val rawStates = decodeToolPkgSubpackageStatesFromPrefs()
        if (!isInitialized) {
            return rawStates
        }
        val normalizedStates = normalizeToolPkgSubpackageStates(rawStates)
        if (normalizedStates != rawStates) {
            saveToolPkgSubpackageStates(normalizedStates)
        }
        return normalizedStates
    }

    internal fun saveToolPkgSubpackageStates(states: Map<String, Boolean>) {
        val normalizedStates = normalizeToolPkgSubpackageStates(states)
        persistToolPkgSubpackageStatesToPrefs(normalizedStates)
        if (isInitialized || runtimeCachesReady) {
            refreshToolPkgRuntimeState(
                persistIfChanged = false,
                subpackageStatesOverride = normalizedStates
            )
        }
    }

    private fun removeFromDisabledPackages(packageName: String) {
        val disabledPackages = getDisabledPackages().toMutableList()
        if (disabledPackages.remove(packageName)) {
            saveDisabledPackages(disabledPackages)
            AppLogger.d(TAG, "Removed package from disabled list: $packageName")
        }
    }

    private fun addToDisabledIfDefaultEnabled(packageName: String) {
        val toolPackage = availablePackages[packageName]
        if (toolPackage != null && toolPackage.isBuiltIn && toolPackage.enabledByDefault) {
            val disabledPackages = getDisabledPackages().toMutableList()
            if (!disabledPackages.contains(packageName)) {
                disabledPackages.add(packageName)
                saveDisabledPackages(disabledPackages)
                AppLogger.d(TAG, "Added default package to disabled list: $packageName")
            }
        }
    }

    internal fun unregisterPackageTools(packageName: String) {
        val activeTools = activePackageToolNames.remove(packageName).orEmpty()
        activeTools.forEach { toolName ->
            aiToolHandler.unregisterTool(toolName)
        }
        activePackageStateIds.remove(packageName)
    }

    /**
     * Get the tools for a loaded package
     * @param packageName The name of the loaded package
     * @return The ToolPackage object or null if the package is not loaded
     */
    fun getPackageTools(packageName: String): ToolPackage? {
        ensureInitialized()
        val normalizedPackageName = normalizePackageName(packageName)
        return availablePackages[normalizedPackageName]
    }

    fun getEffectivePackageTools(packageName: String): ToolPackage? {
        ensureInitialized()
        val normalizedPackageName = normalizePackageName(packageName)
        val toolPackage = availablePackages[normalizedPackageName] ?: return null
        return selectToolPackageState(toolPackage)
    }

    /** Checks if a package is enabled */
    fun isPackageEnabled(packageName: String): Boolean {
        ensureInitialized()
        val normalizedPackageName = normalizePackageName(packageName)
        val enabledPackageSet = getEnabledPackageNameSetInternal()
        if (!enabledPackageSet.contains(normalizedPackageName)) {
            return false
        }
        val subpackageRuntime = toolPkgSubpackageByPackageName[normalizedPackageName]
        if (subpackageRuntime != null) {
            return enabledPackageSet.contains(subpackageRuntime.containerPackageName)
        }
        return true
    }

    /**
     * Disable an enabled package.
     * For toolpkg containers this also disables all internal subpackages.
     */
    fun disablePackage(packageName: String): String {
        ensureInitialized()
        val normalizedPackageName = normalizePackageName(packageName)

        val currentPackages = LinkedHashSet(getEnabledPackageNames())
        val subpackageStates = getToolPkgSubpackageStatesInternal().toMutableMap()
        var packageWasRemoved = false

        val containerRuntime = toolPkgContainers[normalizedPackageName]
        if (containerRuntime != null) {
            packageWasRemoved = currentPackages.remove(normalizedPackageName) || packageWasRemoved
            unregisterPackageTools(normalizedPackageName)

            containerRuntime.subpackages.forEach { subpackage ->
                packageWasRemoved = currentPackages.remove(subpackage.packageName) || packageWasRemoved
                unregisterPackageTools(subpackage.packageName)
            }

            saveEnabledPackageNames(currentPackages.toList())
            saveToolPkgSubpackageStates(subpackageStates)
            addToDisabledIfDefaultEnabled(normalizedPackageName)
            deleteToolPkgCacheDir(normalizedPackageName)
            destroyToolPkgExecutionEngines(normalizedPackageName)

            return if (packageWasRemoved) {
                "Successfully disabled toolpkg container: $normalizedPackageName"
            } else {
                "ToolPkg container is already disabled: $normalizedPackageName"
            }
        }

        if (toolPkgSubpackageByPackageName.containsKey(normalizedPackageName)) {
            packageWasRemoved = currentPackages.remove(normalizedPackageName)
            subpackageStates[normalizedPackageName] = false
            unregisterPackageTools(normalizedPackageName)

            saveEnabledPackageNames(currentPackages.toList())
            saveToolPkgSubpackageStates(subpackageStates)

            return if (packageWasRemoved) {
                "Successfully disabled toolpkg subpackage: $normalizedPackageName"
            } else {
                "ToolPkg subpackage is already disabled: $normalizedPackageName"
            }
        }

        packageWasRemoved = currentPackages.remove(normalizedPackageName)
        unregisterPackageTools(normalizedPackageName)
        addToDisabledIfDefaultEnabled(normalizedPackageName)

        return if (packageWasRemoved) {
            saveEnabledPackageNames(currentPackages.toList())
            AppLogger.d(TAG, "Disabled package: $normalizedPackageName")
            "Successfully disabled package: $normalizedPackageName"
        } else {
            AppLogger.d(TAG, "Package is already disabled: $normalizedPackageName")
            "Package is already disabled: $normalizedPackageName"
        }
    }

    /**
     * Get the script content for a package by name
     * @param packageName The name of the package
     * @return The full JavaScript content of the package or null if not found
     */
    fun getPackageScript(packageName: String): String? {
        ensureInitialized()
        val normalizedPackageName = normalizePackageName(packageName)
        val toolPackage = availablePackages[normalizedPackageName] ?: return null

        // Load script based on whether it's built-in or external
        // All tools in a package share the same script, so we can get it from any tool
        return if (toolPackage.tools.isNotEmpty()) {
            toolPackage.tools[0].script
        } else {
            null
        }
    }

    /**
     * 使用MCP服务器
     *
     * @param serverName 服务器名称
     * @return 成功或失败的消息
     */
    fun useMCPServer(serverName: String): String {
        // 检查服务器是否已注册
        if (!mcpManager.isServerRegistered(serverName)) {
            return "MCP server '$serverName' does not exist or is not registered."
        }

        // 获取服务器配置
        val serverConfig =
            mcpManager.getRegisteredServers()[serverName]
                ?: return "Cannot get MCP server configuration: $serverName"

        // 创建MCP包
        val mcpLoadResult = MCPPackage.loadFromServer(context, serverConfig)
        val mcpPackage =
            mcpLoadResult.mcpPackage
                ?: return mcpLoadResult.errorMessage?.let {
                    "Cannot connect to MCP server '$serverName': $it"
                } ?: "Cannot connect to MCP server: $serverName"

        // 转换为标准工具包
        val toolPackage = mcpPackage.toToolPackage()

        // 获取或创建MCP工具执行器
        val mcpToolExecutor = MCPToolExecutor(context, mcpManager)

        // 注册包中的每个工具 - 使用 serverName:toolName 格式
        toolPackage.tools.forEach { packageTool ->
            val toolName = "$serverName:${packageTool.name}"

            // 使用MCP特定的执行器注册工具
            aiToolHandler.registerTool(
                name = toolName,
                executor = mcpToolExecutor
            )

            AppLogger.d(TAG, "Registered MCP tool: $toolName")
        }

        return generateMCPSystemPrompt(toolPackage, serverName)
    }

    /** 为MCP服务器生成系统提示 */
    private fun generateMCPSystemPrompt(toolPackage: ToolPackage, serverName: String): String {
        val sb = StringBuilder()

        sb.appendLine("Using MCP server: $serverName")
        sb.appendLine("Time: ${java.time.LocalDateTime.now()}")
        sb.appendLine("Description: ${toolPackage.description.resolve(context)}")
        sb.appendLine()
        sb.appendLine("Available tools:")

        toolPackage.tools.forEach { tool ->
            // 使用 serverName:toolName 格式
            sb.appendLine("- $serverName:${tool.name}: ${tool.description.resolve(context)}")
            if (tool.parameters.isNotEmpty()) {
                sb.appendLine("  Parameters:")
                tool.parameters.forEach { param ->
                    val requiredText = if (param.required) "(required)" else "(optional)"
                    sb.appendLine("  - ${param.name} ${requiredText}: ${param.description.resolve(context)}")
                }
            }
            sb.appendLine()
        }

        return sb.toString()
    }

    /**
     * Deletes a package file from external storage and removes it from the in-memory cache.
     * This action is permanent and cannot be undone.
     */
    fun deletePackage(packageName: String): Boolean {
        val normalizedPackageName = normalizePackageName(packageName)
        AppLogger.d(TAG, "Attempting to delete package: $normalizedPackageName")
        ensureInitialized()

        if (toolPkgSubpackageByPackageName.containsKey(normalizedPackageName)) {
            // Subpackage is part of a toolpkg archive; only remove enable state.
            disablePackage(normalizedPackageName)
            return true
        }

        val packageFile = findPackageFile(normalizedPackageName)

        if (packageFile == null || !packageFile.exists()) {
            AppLogger.w(
                TAG,
                "Package file not found for deletion: $normalizedPackageName. It might be already deleted or never existed."
            )
            disablePackage(normalizedPackageName)
            removeFromCachesAfterDelete(normalizedPackageName)
            return true
        }

        AppLogger.d(TAG, "Found package file to delete: ${packageFile.absolutePath}")

        val fileDeleted = packageFile.delete()

        if (fileDeleted) {
            AppLogger.d(TAG, "Successfully deleted package file: ${packageFile.absolutePath}")
            disablePackage(normalizedPackageName)
            removeFromCachesAfterDelete(normalizedPackageName)
            AppLogger.d(TAG, "Package '$normalizedPackageName' fully deleted.")
            return true
        }

        AppLogger.e(TAG, "Failed to delete package file: ${packageFile.absolutePath}")
        return false
    }

    private fun removeFromCachesAfterDelete(packageName: String) {
        if (toolPkgContainers.containsKey(packageName)) {
            val container = toolPkgContainers.remove(packageName)
            availablePackages.remove(packageName)
            deleteToolPkgCacheDir(packageName)
            destroyToolPkgExecutionEngines(packageName)

            val states = getToolPkgSubpackageStatesInternal().toMutableMap()
            container?.subpackages?.forEach { subpackage ->
                availablePackages.remove(subpackage.packageName)
                toolPkgSubpackageByPackageName.remove(subpackage.packageName)
                states.remove(subpackage.packageName)
            }
            saveToolPkgSubpackageStates(states)
            return
        }

        deleteToolPkgCacheDir(packageName)
        availablePackages.remove(packageName)
        toolPkgSubpackageByPackageName.remove(packageName)
    }

    /**
     * Finds the File object for a given package name in external storage.
     */
    private fun findPackageFile(packageName: String): File? {
        val normalizedPackageName = normalizePackageName(packageName)
        val externalPackagesDir = File(context.getExternalFilesDir(null), PACKAGES_DIR)
        if (!externalPackagesDir.exists()) return null

        val containerRuntime = toolPkgContainers[normalizedPackageName]
        if (containerRuntime != null && containerRuntime.sourceType == ToolPkgSourceType.EXTERNAL) {
            val candidate = File(containerRuntime.sourcePath)
            if (candidate.exists()) {
                return candidate
            }
        }

        val jsFile = File(externalPackagesDir, "$normalizedPackageName.js")
        if (jsFile.exists()) return jsFile

        externalPackagesDir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach

            if (file.name.endsWith(".js", ignoreCase = true)) {
                val loadedPackage = loadPackageFromJsFile(file)
                if (loadedPackage?.name == normalizedPackageName) {
                    return file
                }
            }

        }

        return null
    }

    /**
     * 将 ToolPackage 转换为 PackageToolPromptCategory
     * 用于生成结构化的包工具提示词
     *
     * @param toolPackage 要转换的工具包
     * @return PackageToolPromptCategory 对象
     */
    fun toPromptCategory(toolPackage: ToolPackage): PackageToolPromptCategory {
        val toolPrompts = toolPackage.tools.map { packageTool ->
            // 将 PackageTool 转换为 ToolPrompt
            val parametersString = if (packageTool.parameters.isNotEmpty()) {
                packageTool.parameters.joinToString(", ") { param ->
                    val required = if (param.required) "required" else "optional"
                    "${param.name} (${param.type}, $required)"
                }
            } else {
                ""
            }

            ToolPrompt(
                name = packageTool.name,
                description = packageTool.description.resolve(context),
                parameters = parametersString
            )
        }

        return PackageToolPromptCategory(
            packageName = toolPackage.name,
            packageDescription = toolPackage.description.resolve(context),
            tools = toolPrompts
        )
    }

    /**
     * 获取所有已启用包的提示词分类列表
     *
     * @return 已启用包的 PackageToolPromptCategory 列表
     */
    fun getEnabledPackagesPromptCategories(): List<PackageToolPromptCategory> {
        ensureInitialized()
        val enabledPackageNames = getEnabledPackageNames()
        return enabledPackageNames.mapNotNull { packageName ->
            getPackageTools(packageName)
                ?.takeIf { it.tools.isNotEmpty() }
                ?.let { toolPackage ->
                    toPromptCategory(toolPackage)
                }
        }
    }

    /** Clean up resources when the manager is no longer needed */
    fun destroy() {
        ToolPkgWasmRuntime.closeAll()
        toolPkgManager.destroy()
        mcpManager.shutdown()
    }
}
