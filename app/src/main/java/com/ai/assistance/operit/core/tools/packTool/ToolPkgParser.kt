package com.ai.assistance.operit.core.tools.packTool

import android.content.Context
import android.provider.DocumentsContract
import com.ai.assistance.operit.core.tools.LocalizedText
import com.ai.assistance.operit.core.tools.StringOrStringListSerializer
import com.ai.assistance.operit.core.tools.ToolPackage
import com.ai.assistance.operit.util.ToolPkgProtection
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.hjson.JsonValue

private val TOOLPKG_DIRECTORY_RESOURCE_MIME_TYPES =
    setOf(
        DocumentsContract.Document.MIME_TYPE_DIR.lowercase(),
        "inode/directory",
        "application/x-directory"
    )

internal enum class ToolPkgSourceType {
    ASSET,
    EXTERNAL
}

internal data class ToolPkgResourceRuntime(
    val key: String,
    val path: String,
    val mime: String
)

internal data class ToolPkgWasmModuleRuntime(
    val id: String,
    val path: String,
    val exports: List<String>,
    val sourceLanguage: String,
    val abi: String
)

internal data class ToolPkgUiModuleRuntime(
    val id: String,
    val runtime: String,
    val screen: String,
    val title: LocalizedText,
    val keepAlive: Boolean = false
)

internal data class ToolPkgUiRouteRuntime(
    val id: String,
    val routeId: String,
    val runtime: String,
    val screen: String,
    val title: LocalizedText,
    val keepAlive: Boolean = false
)

internal data class ToolPkgNavigationEntryRuntime(
    val id: String,
    val routeId: String,
    val surface: String,
    val title: LocalizedText,
    val action: ToolPkgNavigationActionHookRuntime? = null,
    val icon: String? = null,
    val order: Int = 0
)

internal data class ToolPkgNavigationActionHookRuntime(
    val function: String,
    val functionSource: String? = null
)

internal data class ToolPkgDesktopWidgetRuntime(
    val id: String,
    val routeId: String,
    val renderRouteId: String,
    val title: LocalizedText,
    val subtitle: LocalizedText,
    val description: LocalizedText,
    val icon: String? = null,
    val order: Int = 0
)

internal data class ToolPkgAppLifecycleHookRuntime(
    val id: String,
    val event: String,
    val function: String,
    val functionSource: String? = null
)

internal data class ToolPkgFunctionHookRuntime(
    val id: String,
    val function: String,
    val functionSource: String? = null
)

internal data class ToolPkgAiProviderHandlerRuntime(
    val function: String,
    val functionSource: String? = null
)

internal data class ToolPkgAiProviderRuntime(
    val id: String,
    val displayName: String,
    val description: String,
    val listModelsHandler: ToolPkgAiProviderHandlerRuntime,
    val sendMessageHandler: ToolPkgAiProviderHandlerRuntime,
    val testConnectionHandler: ToolPkgAiProviderHandlerRuntime,
    val calculateInputTokensHandler: ToolPkgAiProviderHandlerRuntime
)

internal data class ToolPkgTagFunctionHookRuntime(
    val id: String,
    val tag: String,
    val function: String,
    val functionSource: String? = null
)

internal data class ToolPkgSubpackageRuntime(
    val packageName: String,
    val containerPackageName: String,
    val subpackageId: String,
    val entryPath: String,
    val displayName: LocalizedText,
    val description: LocalizedText,
    val enabledByDefault: Boolean,
    val toolCount: Int
)

internal data class ToolPkgContainerRuntime(
    val packageName: String,
    val displayName: LocalizedText,
    val description: LocalizedText,
    val version: String,
    val author: List<String>,
    val mainEntry: String,
    val sourceType: ToolPkgSourceType,
    val sourcePath: String,
    val subpackages: List<ToolPkgSubpackageRuntime>,
    val resources: List<ToolPkgResourceRuntime>,
    val wasmModules: List<ToolPkgWasmModuleRuntime>,
    val workflowTemplates: List<ToolPkgWorkflowTemplateRuntime>,
    val workspaceTemplates: List<ToolPkgWorkspaceTemplateRuntime>,
    val uiModules: List<ToolPkgUiModuleRuntime>,
    val uiRoutes: List<ToolPkgUiRouteRuntime>,
    val navigationEntries: List<ToolPkgNavigationEntryRuntime>,
    val desktopWidgets: List<ToolPkgDesktopWidgetRuntime>,
    val appLifecycleHooks: List<ToolPkgAppLifecycleHookRuntime>,
    val messageProcessingPlugins: List<ToolPkgFunctionHookRuntime>,
    val xmlRenderPlugins: List<ToolPkgTagFunctionHookRuntime>,
    val inputMenuTogglePlugins: List<ToolPkgFunctionHookRuntime>,
    val chatInputHooks: List<ToolPkgFunctionHookRuntime>,
    val chatViewHooks: List<ToolPkgFunctionHookRuntime>,
    val toolLifecycleHooks: List<ToolPkgFunctionHookRuntime>,
    val promptInputHooks: List<ToolPkgFunctionHookRuntime>,
    val promptHistoryHooks: List<ToolPkgFunctionHookRuntime>,
    val promptEstimateHistoryHooks: List<ToolPkgFunctionHookRuntime>,
    val systemPromptComposeHooks: List<ToolPkgFunctionHookRuntime>,
    val toolPromptComposeHooks: List<ToolPkgFunctionHookRuntime>,
    val promptFinalizeHooks: List<ToolPkgFunctionHookRuntime>,
    val promptEstimateFinalizeHooks: List<ToolPkgFunctionHookRuntime>,
    val summaryGenerateHooks: List<ToolPkgFunctionHookRuntime>,
    val aiProviders: List<ToolPkgAiProviderRuntime>
)

internal data class ToolPkgLoadResult(
    val containerPackage: ToolPackage,
    val subpackagePackages: List<ToolPackage>,
    val containerRuntime: ToolPkgContainerRuntime
)

@Serializable
internal data class ToolPkgManifest(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    @SerialName("toolpkg_id") val toolpkgId: String,
    val version: String = "",
    val main: String = "",
    @SerialName("display_name") val displayName: LocalizedText = LocalizedText.of(""),
    val description: LocalizedText = LocalizedText.of(""),
    @Serializable(with = StringOrStringListSerializer::class)
    val author: List<String> = emptyList(),
    @SerialName("enabled_by_default") val enabledByDefault: Boolean = true,
    val subpackages: List<ToolPkgManifestSubpackage> = emptyList(),
    val resources: List<ToolPkgManifestResource> = emptyList(),
    @SerialName("wasm_modules")
    val wasmModules: List<ToolPkgManifestWasmModule> = emptyList(),
    @SerialName("workflow_templates")
    val workflowTemplates: List<ToolPkgManifestWorkflowTemplate> = emptyList(),
    @SerialName("workspace_templates")
    val workspaceTemplates: List<ToolPkgManifestWorkspaceTemplate> = emptyList()
)

@Serializable
internal data class ToolPkgManifestSubpackage(
    val id: String,
    val entry: String
)

@Serializable
internal data class ToolPkgManifestResource(
    val key: String,
    val path: String,
    val mime: String = ""
)

@Serializable
internal data class ToolPkgManifestWasmModule(
    val id: String,
    val path: String,
    val exports: List<String> = emptyList(),
    @SerialName("source_language") val sourceLanguage: String = "assemblyscript",
    val abi: String = "assemblyscript"
)

internal data class ToolPkgRegisteredUiModule(
    val id: String,
    val runtime: String,
    val screen: String,
    val title: LocalizedText,
    val keepAlive: Boolean = false
)

internal data class ToolPkgRegisteredUiRoute(
    val id: String,
    val routeId: String,
    val runtime: String,
    val screen: String,
    val title: LocalizedText,
    val keepAlive: Boolean = false
)

internal data class ToolPkgRegisteredNavigationEntry(
    val id: String,
    val routeId: String,
    val surface: String,
    val title: LocalizedText,
    val action: ToolPkgNavigationActionHookRuntime? = null,
    val icon: String? = null,
    val order: Int = 0
)

internal data class ToolPkgRegisteredDesktopWidget(
    val id: String,
    val routeId: String,
    val renderRouteId: String,
    val title: LocalizedText,
    val subtitle: LocalizedText,
    val description: LocalizedText,
    val icon: String? = null,
    val order: Int = 0
)

internal data class ToolPkgRegisteredAppLifecycleHook(
    val id: String,
    val event: String,
    val function: String,
    val functionSource: String? = null
)

internal data class ToolPkgRegisteredFunctionHook(
    val id: String,
    val function: String,
    val functionSource: String? = null
)

internal data class ToolPkgRegisteredAiProviderHandler(
    val function: String,
    val functionSource: String? = null
)

internal data class ToolPkgRegisteredAiProvider(
    val id: String,
    val displayName: String,
    val description: String,
    val listModelsHandler: ToolPkgRegisteredAiProviderHandler,
    val sendMessageHandler: ToolPkgRegisteredAiProviderHandler,
    val testConnectionHandler: ToolPkgRegisteredAiProviderHandler,
    val calculateInputTokensHandler: ToolPkgRegisteredAiProviderHandler
)

internal data class ToolPkgRegisteredTagFunctionHook(
    val id: String,
    val tag: String,
    val function: String,
    val functionSource: String? = null
)

internal data class ToolPkgMainRegistration(
    val toolboxUiModules: List<ToolPkgRegisteredUiModule> = emptyList(),
    val uiRoutes: List<ToolPkgRegisteredUiRoute> = emptyList(),
    val navigationEntries: List<ToolPkgRegisteredNavigationEntry> = emptyList(),
    val desktopWidgets: List<ToolPkgRegisteredDesktopWidget> = emptyList(),
    val appLifecycleHooks: List<ToolPkgRegisteredAppLifecycleHook> = emptyList(),
    val messageProcessingPlugins: List<ToolPkgRegisteredFunctionHook> = emptyList(),
    val xmlRenderPlugins: List<ToolPkgRegisteredTagFunctionHook> = emptyList(),
    val inputMenuTogglePlugins: List<ToolPkgRegisteredFunctionHook> = emptyList(),
    val chatInputHooks: List<ToolPkgRegisteredFunctionHook> = emptyList(),
    val chatViewHooks: List<ToolPkgRegisteredFunctionHook> = emptyList(),
    val toolLifecycleHooks: List<ToolPkgRegisteredFunctionHook> = emptyList(),
    val promptInputHooks: List<ToolPkgRegisteredFunctionHook> = emptyList(),
    val promptHistoryHooks: List<ToolPkgRegisteredFunctionHook> = emptyList(),
    val promptEstimateHistoryHooks: List<ToolPkgRegisteredFunctionHook> = emptyList(),
    val systemPromptComposeHooks: List<ToolPkgRegisteredFunctionHook> = emptyList(),
    val toolPromptComposeHooks: List<ToolPkgRegisteredFunctionHook> = emptyList(),
    val promptFinalizeHooks: List<ToolPkgRegisteredFunctionHook> = emptyList(),
    val promptEstimateFinalizeHooks: List<ToolPkgRegisteredFunctionHook> = emptyList(),
    val summaryGenerateHooks: List<ToolPkgRegisteredFunctionHook> = emptyList(),
    val aiProviders: List<ToolPkgRegisteredAiProvider> = emptyList()
)

internal sealed interface ToolPkgMainRegistrationParseResult {
    data class Success(
        val registration: ToolPkgMainRegistration
    ) : ToolPkgMainRegistrationParseResult

    data class Failure(
        val message: String
    ) : ToolPkgMainRegistrationParseResult
}

internal data class ToolPkgEntryIndex(
    val entryNames: Set<String>,
    private val entryNamesByNormalizedLowercase: Map<String, String>
) {
    fun containsEntry(rawPath: String): Boolean {
        return resolveEntryName(rawPath) != null
    }

    fun resolveEntryName(rawPath: String): String? {
        val normalizedPath = ToolPkgArchiveParser.normalizeZipEntryPath(rawPath) ?: return null
        return entryNamesByNormalizedLowercase[normalizedPath.lowercase()]
    }

    fun containsEntriesUnderDirectory(rawDirectoryPath: String): Boolean {
        val normalizedDirectoryPath =
            ToolPkgArchiveParser.normalizeResourcePath(rawDirectoryPath) ?: return false
        val prefix = normalizedDirectoryPath.trimEnd('/') + "/"
        return entryNames.any { it.startsWith(prefix, ignoreCase = true) }
    }
}

internal data class ToolPkgManifestPreview(
    val entryName: String,
    val manifest: ToolPkgManifest
)

internal object ToolPkgArchiveParser {
    fun parseToolPkgFromIndexedEntries(
        entryIndex: ToolPkgEntryIndex,
        readEntryText: (String) -> String?,
        sourceType: ToolPkgSourceType,
        sourcePath: String,
        isBuiltIn: Boolean,
        parseJsPackage: (String, (String, String) -> Unit) -> ToolPackage?,
        parseMainRegistration: (String, String, String) -> ToolPkgMainRegistrationParseResult,
        reportPackageLoadError: (String, String) -> Unit
    ): ToolPkgLoadResult {
        val manifestEntryName = findManifestEntry(entryIndex.entryNames)
            ?: throw IllegalArgumentException("manifest.hjson or manifest.json not found")
        val manifestText =
            readEntryText(manifestEntryName)
                ?: throw IllegalArgumentException("Failed to read manifest entry")
        val manifest = parseToolPkgManifest(manifestText, manifestEntryName)
        val manifestBasePath = manifestEntryName.substringBeforeLast('/', missingDelimiterValue = "")

        if (manifest.toolpkgId.isBlank()) {
            throw IllegalArgumentException("manifest.toolpkg_id is required")
        }
        val normalizedMainEntry =
            resolveManifestRelativeZipEntryPath(manifestBasePath, manifest.main)
                ?: throw IllegalArgumentException("manifest.main is required")
        if (!entryIndex.containsEntry(normalizedMainEntry)) {
            throw IllegalArgumentException("Cannot find manifest.main entry '${manifest.main}'")
        }
        val mainScriptText =
            readEntryText(normalizedMainEntry)
                ?: throw IllegalArgumentException("Failed to read manifest.main entry '${manifest.main}'")

        val subpackagePackages = mutableListOf<ToolPackage>()
        val subpackageRuntimes = mutableListOf<ToolPkgSubpackageRuntime>()

        manifest.subpackages.forEach { subpackage ->
            val rawSubpackageId = subpackage.id.trim()
            val subpackageErrorKey =
                if (rawSubpackageId.isNotBlank()) rawSubpackageId else "${manifest.toolpkgId}:unknown_subpackage"

            if (rawSubpackageId.isBlank()) {
                reportPackageLoadError(
                    subpackageErrorKey,
                    "$sourcePath: subpackage.id is required"
                )
                return@forEach
            }
            if (subpackage.entry.isBlank()) {
                reportPackageLoadError(
                    subpackageErrorKey,
                    "$sourcePath: subpackage.entry is required for '$rawSubpackageId'"
                )
                return@forEach
            }

            val normalizedSubpackageId = rawSubpackageId
            val packageName = normalizedSubpackageId

            try {
                val normalizedSubpackageEntry =
                    resolveManifestRelativeZipEntryPath(manifestBasePath, subpackage.entry)
                        ?: throw IllegalArgumentException(
                            "Invalid subpackage entry '${subpackage.entry}'"
                        )
                val jsContent =
                    readEntryText(normalizedSubpackageEntry)
                        ?: throw IllegalArgumentException(
                            "Cannot find subpackage entry '${subpackage.entry}'"
                        )

                val parsedPackage =
                    parseJsPackage(jsContent) { _, error ->
                        reportPackageLoadError(packageName, "$sourcePath:${subpackage.entry}: $error")
                    }
                        ?: throw IllegalArgumentException(
                            "Failed to parse subpackage script '${subpackage.entry}'"
                        )

                val resolvedDescription = parsedPackage.description

                val resolvedDisplayName =
                    if (hasLocalizedTextContent(parsedPackage.displayName)) {
                        parsedPackage.displayName
                    } else {
                        LocalizedText.of(parsedPackage.name)
                    }

                val normalizedPackage =
                    parsedPackage.copy(
                        name = packageName,
                        isBuiltIn = isBuiltIn
                    )

                subpackagePackages.add(normalizedPackage)
                subpackageRuntimes.add(
                    ToolPkgSubpackageRuntime(
                        packageName = packageName,
                        containerPackageName = manifest.toolpkgId,
                        subpackageId = normalizedSubpackageId,
                        entryPath = normalizedSubpackageEntry,
                        displayName = resolvedDisplayName,
                        description = resolvedDescription,
                        enabledByDefault = normalizedPackage.enabledByDefault,
                        toolCount = normalizedPackage.tools.size
                    )
                )
            } catch (e: Exception) {
                reportPackageLoadError(
                    packageName,
                    "$sourcePath:${subpackage.entry}: ${e.message ?: e.stackTraceToString()}"
                )
            }
        }

        if (manifest.subpackages.isNotEmpty() && subpackagePackages.isEmpty()) {
            throw IllegalArgumentException(
                "No valid subpackages were loaded from toolpkg '${manifest.toolpkgId}'"
            )
        }

        val resources =
            manifest.resources.map { resource ->
                if (resource.key.isBlank()) {
                    throw IllegalArgumentException("resource.key is required")
                }
                if (resource.path.isBlank()) {
                    throw IllegalArgumentException(
                        "resource.path is required for key '${resource.key}'"
                    )
                }
                val normalizedPath =
                    resolveManifestRelativeResourcePath(manifestBasePath, resource.path)
                        ?: throw IllegalArgumentException("Invalid resource path: ${resource.path}")
                if (isDirectoryResourceMime(resource.mime)) {
                    if (!entryIndex.containsEntriesUnderDirectory(normalizedPath)) {
                        throw IllegalArgumentException("Cannot find resource directory '${resource.path}'")
                    }
                } else if (!entryIndex.containsEntry(normalizedPath)) {
                    throw IllegalArgumentException("Cannot find resource path '${resource.path}'")
                }
                ToolPkgResourceRuntime(
                    key = resource.key,
                    path = normalizedPath,
                    mime = resource.mime
                )
            }

        val wasmModuleIds = linkedSetOf<String>()
        val wasmModules =
            manifest.wasmModules.mapIndexed { index, module ->
                val moduleId = module.id.trim()
                if (moduleId.isBlank()) {
                    throw IllegalArgumentException("wasm_modules[$index].id is required")
                }
                if (!wasmModuleIds.add(moduleId.lowercase())) {
                    throw IllegalArgumentException("Duplicate wasm module id: $moduleId")
                }
                if (module.path.isBlank()) {
                    throw IllegalArgumentException("wasm_modules[$index].path is required")
                }
                val normalizedPath =
                    resolveManifestRelativeResourcePath(manifestBasePath, module.path)
                        ?: throw IllegalArgumentException(
                            "Invalid wasm module path: ${module.path}"
                        )
                if (!normalizedPath.endsWith(".wasm", ignoreCase = true)) {
                    throw IllegalArgumentException(
                        "wasm_modules[$index].path must point to a .wasm file: ${module.path}"
                    )
                }
                if (!entryIndex.containsEntry(normalizedPath)) {
                    throw IllegalArgumentException(
                        "Cannot find wasm module path '${module.path}'"
                    )
                }

                val exportNames = module.exports.map { exportName ->
                    exportName.trim()
                }
                if (exportNames.any { it.isBlank() }) {
                    throw IllegalArgumentException(
                        "wasm_modules[$index].exports must not contain blank names"
                    )
                }
                if (exportNames.map { it.lowercase() }.toSet().size != exportNames.size) {
                    throw IllegalArgumentException(
                        "wasm_modules[$index].exports contains duplicate names"
                    )
                }
                val sourceLanguage = module.sourceLanguage.trim()
                if (sourceLanguage.isBlank()) {
                    throw IllegalArgumentException(
                        "wasm_modules[$index].source_language is required"
                    )
                }
                val abi = module.abi.trim()
                if (abi.isBlank()) {
                    throw IllegalArgumentException("wasm_modules[$index].abi is required")
                }

                ToolPkgWasmModuleRuntime(
                    id = moduleId,
                    path = normalizedPath,
                    exports = exportNames,
                    sourceLanguage = sourceLanguage,
                    abi = abi
                )
            }

        val resourceByKey =
            resources.associateBy { resource -> resource.key.lowercase() }
        val workflowTemplateIds = linkedSetOf<String>()
        val workflowTemplates =
            manifest.workflowTemplates.mapIndexed { index, template ->
                val templateId = template.id.trim()
                if (templateId.isBlank()) {
                    throw IllegalArgumentException("workflow_templates[$index].id is required")
                }
                if (!workflowTemplateIds.add(templateId.lowercase())) {
                    throw IllegalArgumentException("Duplicate workflow template id: $templateId")
                }

                val resourceKey = template.resourceKey.trim()
                if (resourceKey.isBlank()) {
                    throw IllegalArgumentException(
                        "workflow_templates[$index].resource_key is required"
                    )
                }
                val resource =
                    resourceByKey[resourceKey.lowercase()]
                        ?: throw IllegalArgumentException(
                            "workflow_templates[$index].resource_key not found in manifest.resources: $resourceKey"
                        )
                if (isDirectoryResourceMime(resource.mime)) {
                    throw IllegalArgumentException(
                        "workflow_templates[$index].resource_key must reference a file resource: $resourceKey"
                    )
                }

                ToolPkgWorkflowTemplateRuntime(
                    id = templateId,
                    displayName = template.displayName,
                    description = template.description,
                    resourceKey = resource.key
                )
            }
        val workspaceTemplateIds = linkedSetOf<String>()
        val workspaceTemplates =
            manifest.workspaceTemplates.mapIndexed { index, template ->
                val templateId = template.id.trim()
                if (templateId.isBlank()) {
                    throw IllegalArgumentException("workspace_templates[$index].id is required")
                }
                if (!workspaceTemplateIds.add(templateId.lowercase())) {
                    throw IllegalArgumentException("Duplicate workspace template id: $templateId")
                }

                val resourceKey = template.resourceKey.trim()
                if (resourceKey.isBlank()) {
                    throw IllegalArgumentException(
                        "workspace_templates[$index].resource_key is required"
                    )
                }
                val resource =
                    resourceByKey[resourceKey.lowercase()]
                        ?: throw IllegalArgumentException(
                            "workspace_templates[$index].resource_key not found in manifest.resources: $resourceKey"
                        )
                if (!isDirectoryResourceMime(resource.mime)) {
                    throw IllegalArgumentException(
                        "workspace_templates[$index].resource_key must reference a directory resource: $resourceKey"
                    )
                }

                ToolPkgWorkspaceTemplateRuntime(
                    id = templateId,
                    displayName = template.displayName,
                    description = template.description,
                    resourceKey = resource.key,
                    projectType = template.projectType.trim()
                )
            }

        val containerDisplayName =
            if (hasLocalizedTextContent(manifest.displayName)) {
                manifest.displayName
            } else {
                LocalizedText.of(manifest.toolpkgId)
            }
        val mainRegistrationResult =
            parseMainRegistration(mainScriptText, manifest.toolpkgId, normalizedMainEntry)
        val mainRegistration =
            when (mainRegistrationResult) {
                is ToolPkgMainRegistrationParseResult.Success -> mainRegistrationResult.registration
                is ToolPkgMainRegistrationParseResult.Failure ->
                    throw IllegalArgumentException(
                        "Failed to parse main registration from '${manifest.main}': ${mainRegistrationResult.message}"
                    )
            }

        val registeredUiRoutes =
            buildList {
                mainRegistration.toolboxUiModules.forEach { module ->
                    add(
                        ToolPkgRegisteredUiRoute(
                            id = module.id,
                            routeId = buildToolPkgRouteId(manifest.toolpkgId, module.id),
                            runtime = module.runtime,
                            screen = module.screen,
                            title = module.title,
                            keepAlive = module.keepAlive
                        )
                    )
                }
                addAll(mainRegistration.uiRoutes)
            }

        val registeredNavigationEntries =
            buildList {
                mainRegistration.toolboxUiModules.forEachIndexed { index, module ->
                    add(
                        ToolPkgRegisteredNavigationEntry(
                            id = "toolbox_${module.id}",
                            routeId = buildToolPkgRouteId(manifest.toolpkgId, module.id),
                            surface = TOOLPKG_NAV_SURFACE_TOOLBOX,
                            title = module.title,
                            order = index
                        )
                    )
                }
                addAll(mainRegistration.navigationEntries)
            }

        val uiModules = mutableListOf<ToolPkgUiModuleRuntime>()
        val uiRoutes = mutableListOf<ToolPkgUiRouteRuntime>()
        val uiModuleIds = linkedSetOf<String>()
        val routeIds = linkedSetOf<String>()
        registeredUiRoutes.forEachIndexed { index, module ->
            val id = module.id.trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_UI_ROUTE[$index].id is required")
            }
            if (!uiModuleIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate toolpkg ui route id: $id")
            }

            val runtimeName = module.runtime.trim().ifBlank { TOOLPKG_RUNTIME_COMPOSE_DSL }
            val routeId = module.routeId.trim()
            if (routeId.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_UI_ROUTE[$index].route is required")
            }
            if (!routeIds.add(routeId.lowercase())) {
                throw IllegalArgumentException("Duplicate toolpkg route id: $routeId")
            }
            val normalizedScreenPath =
                normalizeZipEntryPath(module.screen)
                    ?: throw IllegalArgumentException(
                        "$TOOLPKG_REGISTRATION_UI_ROUTE[$index].screen is invalid: ${module.screen}"
                    )
            if (!entryIndex.containsEntry(normalizedScreenPath)) {
                throw IllegalArgumentException(
                    "$TOOLPKG_REGISTRATION_UI_ROUTE[$index].screen not found: ${module.screen}"
                )
            }

            uiModules.add(
                ToolPkgUiModuleRuntime(
                    id = id,
                    runtime = runtimeName,
                    screen = normalizedScreenPath,
                    title = module.title,
                    keepAlive = module.keepAlive
                )
            )
            uiRoutes.add(
                ToolPkgUiRouteRuntime(
                    id = id,
                    routeId = routeId,
                    runtime = runtimeName,
                    screen = normalizedScreenPath,
                    title = module.title,
                    keepAlive = module.keepAlive
                )
            )
        }

        val navigationEntries = mutableListOf<ToolPkgNavigationEntryRuntime>()
        val navigationEntryIds = linkedSetOf<String>()
        registeredNavigationEntries.forEachIndexed { index, entry ->
            val id = entry.id.trim()
            val routeId = entry.routeId.trim()
            val surface = entry.surface.trim().lowercase()
            val action = entry.action
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_NAVIGATION_ENTRY[$index].id is required")
            }
            if (!navigationEntryIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate toolpkg navigation entry id: $id")
            }
            if (routeId.isBlank() && action == null) {
                throw IllegalArgumentException(
                    "$TOOLPKG_REGISTRATION_NAVIGATION_ENTRY[$index].route or action is required"
                )
            }
            if (routeId.isNotBlank() && uiRoutes.none { it.routeId.equals(routeId, ignoreCase = true) }) {
                throw IllegalArgumentException(
                    "$TOOLPKG_REGISTRATION_NAVIGATION_ENTRY[$index].route not found: $routeId"
                )
            }
            if (
                surface != TOOLPKG_NAV_SURFACE_TOOLBOX &&
                    surface != TOOLPKG_NAV_SURFACE_MAIN_SIDEBAR_PLUGINS
            ) {
                throw IllegalArgumentException(
                    "$TOOLPKG_REGISTRATION_NAVIGATION_ENTRY[$index].surface is unsupported: $surface"
                )
            }
            navigationEntries.add(
                ToolPkgNavigationEntryRuntime(
                    id = id,
                    routeId = routeId,
                    surface = surface,
                    title = entry.title,
                    action = action,
                    icon = entry.icon,
                    order = entry.order
                )
            )
        }

        val desktopWidgets = mutableListOf<ToolPkgDesktopWidgetRuntime>()
        val desktopWidgetIds = linkedSetOf<String>()
        mainRegistration.desktopWidgets.forEachIndexed { index, widget ->
            val id = widget.id.trim()
            val routeId = widget.routeId.trim()
            val renderRouteId = widget.renderRouteId.trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_DESKTOP_WIDGET[$index].id is required")
            }
            if (!desktopWidgetIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate toolpkg desktop widget id: $id")
            }
            if (routeId.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_DESKTOP_WIDGET[$index].route is required")
            }
            if (uiRoutes.none { it.routeId.equals(routeId, ignoreCase = true) }) {
                throw IllegalArgumentException(
                    "$TOOLPKG_REGISTRATION_DESKTOP_WIDGET[$index].route not found: $routeId"
                )
            }
            if (renderRouteId.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_DESKTOP_WIDGET[$index].render is required")
            }
            if (uiRoutes.none { it.routeId.equals(renderRouteId, ignoreCase = true) }) {
                throw IllegalArgumentException(
                    "$TOOLPKG_REGISTRATION_DESKTOP_WIDGET[$index].render not found: $renderRouteId"
                )
            }
            desktopWidgets.add(
                ToolPkgDesktopWidgetRuntime(
                    id = id,
                    routeId = routeId,
                    renderRouteId = renderRouteId,
                    title = widget.title,
                    subtitle = widget.subtitle,
                    description = widget.description,
                    icon = widget.icon,
                    order = widget.order
                )
            )
        }

        val appLifecycleHooks = mutableListOf<ToolPkgAppLifecycleHookRuntime>()
        val hookIds = linkedSetOf<String>()
        mainRegistration.appLifecycleHooks.forEachIndexed { index, hook ->
            val id = hook.id.trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_APP_LIFECYCLE_HOOK[$index].id is required")
            }
            if (!hookIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate app lifecycle hook id: $id")
            }

            val event = hook.event.trim().lowercase()
            val function = hook.function.trim()
            if (event.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_APP_LIFECYCLE_HOOK[$index].event is required")
            }
            if (function.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_APP_LIFECYCLE_HOOK[$index].function is required")
            }

            appLifecycleHooks.add(
                ToolPkgAppLifecycleHookRuntime(
                    id = id,
                    event = event,
                    function = function,
                    functionSource = hook.functionSource
                )
            )
        }

        val messageProcessingPlugins = mutableListOf<ToolPkgFunctionHookRuntime>()
        val messageProcessingIds = linkedSetOf<String>()
        mainRegistration.messageProcessingPlugins.forEachIndexed { index, hook ->
            val id = hook.id.trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_MESSAGE_PROCESSING_PLUGIN[$index].id is required")
            }
            if (!messageProcessingIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate message processing plugin id: $id")
            }

            val function = hook.function.trim()
            if (function.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_MESSAGE_PROCESSING_PLUGIN[$index].function is required")
            }
            messageProcessingPlugins.add(
                ToolPkgFunctionHookRuntime(
                    id = id,
                    function = function,
                    functionSource = hook.functionSource
                )
            )
        }

        val xmlRenderPlugins = mutableListOf<ToolPkgTagFunctionHookRuntime>()
        val xmlRenderIds = linkedSetOf<String>()
        mainRegistration.xmlRenderPlugins.forEachIndexed { index, hook ->
            val id = hook.id.trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_XML_RENDER_PLUGIN[$index].id is required")
            }
            if (!xmlRenderIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate xml render plugin id: $id")
            }

            val tag = hook.tag.trim().lowercase()
            val function = hook.function.trim()
            if (tag.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_XML_RENDER_PLUGIN[$index].tag is required")
            }
            if (function.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_XML_RENDER_PLUGIN[$index].function is required")
            }
            xmlRenderPlugins.add(
                ToolPkgTagFunctionHookRuntime(
                    id = id,
                    tag = tag,
                    function = function,
                    functionSource = hook.functionSource
                )
            )
        }

        val inputMenuTogglePlugins = mutableListOf<ToolPkgFunctionHookRuntime>()
        val inputMenuToggleIds = linkedSetOf<String>()
        mainRegistration.inputMenuTogglePlugins.forEachIndexed { index, hook ->
            val id = hook.id.trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_INPUT_MENU_TOGGLE_PLUGIN[$index].id is required")
            }
            if (!inputMenuToggleIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate input menu toggle plugin id: $id")
            }

            val function = hook.function.trim()
            if (function.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_INPUT_MENU_TOGGLE_PLUGIN[$index].function is required")
            }
            inputMenuTogglePlugins.add(
                ToolPkgFunctionHookRuntime(
                    id = id,
                    function = function,
                    functionSource = hook.functionSource
                )
            )
        }

        val chatInputHooks = mutableListOf<ToolPkgFunctionHookRuntime>()
        val chatInputIds = linkedSetOf<String>()
        mainRegistration.chatInputHooks.forEachIndexed { index, hook ->
            val id = hook.id.trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_CHAT_INPUT_HOOK[$index].id is required")
            }
            if (!chatInputIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate chat input hook id: $id")
            }

            val function = hook.function.trim()
            if (function.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_CHAT_INPUT_HOOK[$index].function is required")
            }
            chatInputHooks.add(
                ToolPkgFunctionHookRuntime(
                    id = id,
                    function = function,
                    functionSource = hook.functionSource
                )
            )
        }

        val chatViewHooks = mutableListOf<ToolPkgFunctionHookRuntime>()
        val chatViewIds = linkedSetOf<String>()
        mainRegistration.chatViewHooks.forEachIndexed { index, hook ->
            val id = hook.id.trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_CHAT_VIEW_HOOK[$index].id is required")
            }
            if (!chatViewIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate chat view hook id: $id")
            }

            val function = hook.function.trim()
            if (function.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_CHAT_VIEW_HOOK[$index].function is required")
            }
            chatViewHooks.add(
                ToolPkgFunctionHookRuntime(
                    id = id,
                    function = function,
                    functionSource = hook.functionSource
                )
            )
        }

        val toolLifecycleHooks = mutableListOf<ToolPkgFunctionHookRuntime>()
        val toolLifecycleIds = linkedSetOf<String>()
        mainRegistration.toolLifecycleHooks.forEachIndexed { index, hook ->
            val id = hook.id.trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_TOOL_LIFECYCLE_HOOK[$index].id is required")
            }
            if (!toolLifecycleIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate tool lifecycle hook id: $id")
            }

            val function = hook.function.trim()
            if (function.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_TOOL_LIFECYCLE_HOOK[$index].function is required")
            }
            toolLifecycleHooks.add(
                ToolPkgFunctionHookRuntime(
                    id = id,
                    function = function,
                    functionSource = hook.functionSource
                )
            )
        }

        val promptInputHooks = mutableListOf<ToolPkgFunctionHookRuntime>()
        val promptInputIds = linkedSetOf<String>()
        mainRegistration.promptInputHooks.forEachIndexed { index, hook ->
            val id = hook.id.trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_PROMPT_INPUT_HOOK[$index].id is required")
            }
            if (!promptInputIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate prompt input hook id: $id")
            }

            val function = hook.function.trim()
            if (function.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_PROMPT_INPUT_HOOK[$index].function is required")
            }
            promptInputHooks.add(
                ToolPkgFunctionHookRuntime(
                    id = id,
                    function = function,
                    functionSource = hook.functionSource
                )
            )
        }

        val promptHistoryHooks = mutableListOf<ToolPkgFunctionHookRuntime>()
        val promptHistoryIds = linkedSetOf<String>()
        mainRegistration.promptHistoryHooks.forEachIndexed { index, hook ->
            val id = hook.id.trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_PROMPT_HISTORY_HOOK[$index].id is required")
            }
            if (!promptHistoryIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate prompt history hook id: $id")
            }

            val function = hook.function.trim()
            if (function.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_PROMPT_HISTORY_HOOK[$index].function is required")
            }
            promptHistoryHooks.add(
                ToolPkgFunctionHookRuntime(
                    id = id,
                    function = function,
                    functionSource = hook.functionSource
                )
            )
        }

        val promptEstimateHistoryHooks = mutableListOf<ToolPkgFunctionHookRuntime>()
        val promptEstimateHistoryIds = linkedSetOf<String>()
        mainRegistration.promptEstimateHistoryHooks.forEachIndexed { index, hook ->
            val id = hook.id.trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_PROMPT_ESTIMATE_HISTORY_HOOK[$index].id is required")
            }
            if (!promptEstimateHistoryIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate prompt estimate history hook id: $id")
            }

            val function = hook.function.trim()
            if (function.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_PROMPT_ESTIMATE_HISTORY_HOOK[$index].function is required")
            }
            promptEstimateHistoryHooks.add(
                ToolPkgFunctionHookRuntime(
                    id = id,
                    function = function,
                    functionSource = hook.functionSource
                )
            )
        }

        val systemPromptComposeHooks = mutableListOf<ToolPkgFunctionHookRuntime>()
        val systemPromptComposeIds = linkedSetOf<String>()
        mainRegistration.systemPromptComposeHooks.forEachIndexed { index, hook ->
            val id = hook.id.trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_SYSTEM_PROMPT_COMPOSE_HOOK[$index].id is required")
            }
            if (!systemPromptComposeIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate system prompt compose hook id: $id")
            }

            val function = hook.function.trim()
            if (function.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_SYSTEM_PROMPT_COMPOSE_HOOK[$index].function is required")
            }
            systemPromptComposeHooks.add(
                ToolPkgFunctionHookRuntime(
                    id = id,
                    function = function,
                    functionSource = hook.functionSource
                )
            )
        }

        val toolPromptComposeHooks = mutableListOf<ToolPkgFunctionHookRuntime>()
        val toolPromptComposeIds = linkedSetOf<String>()
        mainRegistration.toolPromptComposeHooks.forEachIndexed { index, hook ->
            val id = hook.id.trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_TOOL_PROMPT_COMPOSE_HOOK[$index].id is required")
            }
            if (!toolPromptComposeIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate tool prompt compose hook id: $id")
            }

            val function = hook.function.trim()
            if (function.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_TOOL_PROMPT_COMPOSE_HOOK[$index].function is required")
            }
            toolPromptComposeHooks.add(
                ToolPkgFunctionHookRuntime(
                    id = id,
                    function = function,
                    functionSource = hook.functionSource
                )
            )
        }

        val promptFinalizeHooks = mutableListOf<ToolPkgFunctionHookRuntime>()
        val promptFinalizeIds = linkedSetOf<String>()
        mainRegistration.promptFinalizeHooks.forEachIndexed { index, hook ->
            val id = hook.id.trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_PROMPT_FINALIZE_HOOK[$index].id is required")
            }
            if (!promptFinalizeIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate prompt finalize hook id: $id")
            }

            val function = hook.function.trim()
            if (function.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_PROMPT_FINALIZE_HOOK[$index].function is required")
            }
            promptFinalizeHooks.add(
                ToolPkgFunctionHookRuntime(
                    id = id,
                    function = function,
                    functionSource = hook.functionSource
                )
            )
        }

        val promptEstimateFinalizeHooks = mutableListOf<ToolPkgFunctionHookRuntime>()
        val promptEstimateFinalizeIds = linkedSetOf<String>()
        mainRegistration.promptEstimateFinalizeHooks.forEachIndexed { index, hook ->
            val id = hook.id.trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_PROMPT_ESTIMATE_FINALIZE_HOOK[$index].id is required")
            }
            if (!promptEstimateFinalizeIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate prompt estimate finalize hook id: $id")
            }

            val function = hook.function.trim()
            if (function.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_PROMPT_ESTIMATE_FINALIZE_HOOK[$index].function is required")
            }
            promptEstimateFinalizeHooks.add(
                ToolPkgFunctionHookRuntime(
                    id = id,
                    function = function,
                    functionSource = hook.functionSource
                )
            )
        }

        val summaryGenerateHooks = mutableListOf<ToolPkgFunctionHookRuntime>()
        val summaryGenerateIds = linkedSetOf<String>()
        mainRegistration.summaryGenerateHooks.forEachIndexed { index, hook ->
            val id = hook.id.trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_SUMMARY_GENERATE_HOOK[$index].id is required")
            }
            if (!summaryGenerateIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate summary generate hook id: $id")
            }

            val function = hook.function.trim()
            if (function.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_SUMMARY_GENERATE_HOOK[$index].function is required")
            }
            summaryGenerateHooks.add(
                ToolPkgFunctionHookRuntime(
                    id = id,
                    function = function,
                    functionSource = hook.functionSource
                )
            )
        }

        val aiProviders = mutableListOf<ToolPkgAiProviderRuntime>()
        val aiProviderIds = linkedSetOf<String>()
        mainRegistration.aiProviders.forEachIndexed { index, provider ->
            val id = provider.id.trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_AI_PROVIDER[$index].id is required")
            }
            if (!aiProviderIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate ai provider id: $id")
            }

            fun buildHandler(
                fieldName: String,
                handler: ToolPkgRegisteredAiProviderHandler
            ): ToolPkgAiProviderHandlerRuntime {
                val function = handler.function.trim()
                if (function.isBlank()) {
                    throw IllegalArgumentException(
                        "$TOOLPKG_REGISTRATION_AI_PROVIDER[$index].$fieldName is required"
                    )
                }
                return ToolPkgAiProviderHandlerRuntime(
                    function = function,
                    functionSource = handler.functionSource
                )
            }

            aiProviders.add(
                ToolPkgAiProviderRuntime(
                    id = id,
                    displayName = provider.displayName.trim().ifBlank { id },
                    description = provider.description.trim(),
                    listModelsHandler = buildHandler("listModels", provider.listModelsHandler),
                    sendMessageHandler = buildHandler("sendMessage", provider.sendMessageHandler),
                    testConnectionHandler =
                        buildHandler("testConnection", provider.testConnectionHandler),
                    calculateInputTokensHandler =
                        buildHandler(
                            "calculateInputTokens",
                            provider.calculateInputTokensHandler
                        )
                )
            )
        }

        val containerDescription =
            when {
                hasLocalizedTextContent(manifest.description) -> manifest.description
                hasLocalizedTextContent(manifest.displayName) -> manifest.displayName
                else -> LocalizedText.of(manifest.toolpkgId)
            }

        val containerPackage =
            ToolPackage(
                name = manifest.toolpkgId,
                description = containerDescription,
                tools = emptyList(),
                isBuiltIn = isBuiltIn,
                enabledByDefault = manifest.enabledByDefault,
                displayName = containerDisplayName,
                category = "ToolPkg",
                author = manifest.author
            )

        val runtime =
            ToolPkgContainerRuntime(
                packageName = manifest.toolpkgId,
                displayName = containerDisplayName,
                description = containerDescription,
                version = manifest.version,
                author = manifest.author,
                mainEntry = normalizedMainEntry,
                sourceType = sourceType,
                sourcePath = sourcePath,
                subpackages = subpackageRuntimes,
                resources = resources,
                wasmModules = wasmModules,
                workflowTemplates = workflowTemplates,
                workspaceTemplates = workspaceTemplates,
                uiModules = uiModules,
                uiRoutes = uiRoutes,
                navigationEntries = navigationEntries,
                desktopWidgets = desktopWidgets,
                appLifecycleHooks = appLifecycleHooks,
                messageProcessingPlugins = messageProcessingPlugins,
                xmlRenderPlugins = xmlRenderPlugins,
                inputMenuTogglePlugins = inputMenuTogglePlugins,
                chatInputHooks = chatInputHooks,
                chatViewHooks = chatViewHooks,
                toolLifecycleHooks = toolLifecycleHooks,
                promptInputHooks = promptInputHooks,
                promptHistoryHooks = promptHistoryHooks,
                promptEstimateHistoryHooks = promptEstimateHistoryHooks,
                systemPromptComposeHooks = systemPromptComposeHooks,
                toolPromptComposeHooks = toolPromptComposeHooks,
                promptFinalizeHooks = promptFinalizeHooks,
                promptEstimateFinalizeHooks = promptEstimateFinalizeHooks,
                summaryGenerateHooks = summaryGenerateHooks,
                aiProviders = aiProviders
            )

        return ToolPkgLoadResult(
            containerPackage = containerPackage,
            subpackagePackages = subpackagePackages,
            containerRuntime = runtime
        )
    }

    fun buildZipEntryIndex(archive: ZipFile): ToolPkgEntryIndex {
        val normalizedEntryNames = linkedSetOf<String>()
        val entryNamesByNormalizedLowercase = linkedMapOf<String, String>()
        val entries = archive.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val normalizedName = normalizeZipEntryPath(entry.name)
            if (!entry.isDirectory && normalizedName != null) {
                normalizedEntryNames.add(normalizedName)
                val key = normalizedName.lowercase()
                if (!entryNamesByNormalizedLowercase.containsKey(key)) {
                    entryNamesByNormalizedLowercase[key] = entry.name
                }
            }
        }
        return ToolPkgEntryIndex(
            entryNames = normalizedEntryNames,
            entryNamesByNormalizedLowercase = entryNamesByNormalizedLowercase
        )
    }

    fun buildDirectoryEntryIndex(rootDir: File): ToolPkgEntryIndex {
        val normalizedEntryNames = linkedSetOf<String>()
        val entryNamesByNormalizedLowercase = linkedMapOf<String, String>()
        if (!rootDir.exists()) {
            return ToolPkgEntryIndex(
                entryNames = emptySet(),
                entryNamesByNormalizedLowercase = emptyMap()
            )
        }

        rootDir
            .walkTopDown()
            .filter(File::isFile)
            .forEach { file ->
                val relativePath = file.relativeTo(rootDir).invariantSeparatorsPath
                val normalizedName = normalizeZipEntryPath(relativePath) ?: return@forEach
                normalizedEntryNames.add(normalizedName)
                val key = normalizedName.lowercase()
                if (!entryNamesByNormalizedLowercase.containsKey(key)) {
                    entryNamesByNormalizedLowercase[key] = relativePath
                }
            }

        return ToolPkgEntryIndex(
            entryNames = normalizedEntryNames,
            entryNamesByNormalizedLowercase = entryNamesByNormalizedLowercase
        )
    }

    fun normalizeZipEntryPath(rawPath: String): String? {
        val normalized = rawPath.replace('\\', '/').trim().trimStart('/')
        if (normalized.isBlank()) {
            return null
        }
        if (normalized.contains("..")) {
            return null
        }
        return normalized
    }

    fun resolveManifestRelativeZipEntryPath(manifestBasePath: String, rawPath: String): String? {
        val normalized = normalizeZipEntryPath(rawPath) ?: return null
        if (manifestBasePath.isBlank()) {
            return normalized
        }
        return normalizeZipEntryPath("$manifestBasePath/$normalized")
    }

    fun normalizeResourcePath(rawPath: String): String? {
        val normalized = normalizeZipEntryPath(rawPath) ?: return null
        return normalized.trimEnd('/').ifBlank { null }
    }

    fun resolveManifestRelativeResourcePath(manifestBasePath: String, rawPath: String): String? {
        val normalized = normalizeResourcePath(rawPath) ?: return null
        if (manifestBasePath.isBlank()) {
            return normalized
        }
        return normalizeResourcePath("$manifestBasePath/$normalized")
    }

    fun isDirectoryResourceMime(mime: String?): Boolean {
        val normalizedMime = mime?.trim()?.lowercase().orEmpty()
        return TOOLPKG_DIRECTORY_RESOURCE_MIME_TYPES.contains(normalizedMime)
    }

    fun readZipEntryText(
        archive: ZipFile,
        entryIndex: ToolPkgEntryIndex,
        rawPath: String
    ): String? {
        val archiveEntryName = entryIndex.resolveEntryName(rawPath) ?: return null
        val entry = archive.getEntry(archiveEntryName) ?: return null
        archive.getInputStream(entry).use { input ->
            return ToolPkgProtection.decodeUtf8(input.readBytes())
        }
    }

    fun readDirectoryEntryText(
        rootDir: File,
        entryIndex: ToolPkgEntryIndex,
        rawPath: String
    ): String? {
        val relativePath = entryIndex.resolveEntryName(rawPath) ?: return null
        val file = File(rootDir, relativePath)
        if (!file.isFile) {
            return null
        }
        return ToolPkgProtection.decodeUtf8(file.readBytes())
    }

    fun readToolPkgManifestPreview(inputStreamFactory: () -> InputStream): ToolPkgManifestPreview? {
        inputStreamFactory().use { input ->
            ZipInputStream(input.buffered()).use { zipInput ->
                while (true) {
                    val entry = zipInput.nextEntry ?: break
                    val normalizedName = normalizeZipEntryPath(entry.name)
                    if (!entry.isDirectory && normalizedName != null && isManifestEntryName(normalizedName)) {
                        val manifestText =
                            zipInput.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                                reader.readText()
                            }
                        return ToolPkgManifestPreview(
                            entryName = normalizedName,
                            manifest = parseToolPkgManifest(manifestText, normalizedName)
                        )
                    }
                    zipInput.closeEntry()
                }
            }
        }
        return null
    }

    fun extractZipEntriesFromExternal(zipFilePath: String, destinationDir: File): Boolean {
        val zipFile = File(zipFilePath)
        if (!zipFile.exists()) {
            return false
        }

        ZipFile(zipFile).use { archive ->
            val entries = archive.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) {
                    continue
                }
                val normalizedEntry = normalizeZipEntryPath(entry.name) ?: continue
                val outputFile = File(destinationDir, normalizedEntry)
                val parent = outputFile.parentFile
                if (parent != null && !parent.exists()) {
                    parent.mkdirs()
                }
                archive.getInputStream(entry).use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
        return true
    }

    fun extractZipEntriesFromAsset(
        context: Context,
        assetPath: String,
        destinationDir: File
    ): Boolean {
        context.assets.open(assetPath).use { input ->
            ZipInputStream(input.buffered()).use { zipInput ->
                while (true) {
                    val entry = zipInput.nextEntry ?: break
                    if (entry.isDirectory) {
                        zipInput.closeEntry()
                        continue
                    }
                    val normalizedEntry = normalizeZipEntryPath(entry.name)
                    if (normalizedEntry != null) {
                        val outputFile = File(destinationDir, normalizedEntry)
                        val parent = outputFile.parentFile
                        if (parent != null && !parent.exists()) {
                            parent.mkdirs()
                        }
                        outputFile.outputStream().use { output ->
                            zipInput.copyTo(output)
                        }
                    }
                    zipInput.closeEntry()
                }
            }
        }
        return true
    }

    private fun findManifestEntry(entryNames: Collection<String>): String? {
        val exactHjson = entryNames.firstOrNull { it.equals("manifest.hjson", ignoreCase = true) }
        if (exactHjson != null) return exactHjson

        val exactJson = entryNames.firstOrNull { it.equals("manifest.json", ignoreCase = true) }
        if (exactJson != null) return exactJson

        val nestedHjson =
            entryNames.firstOrNull {
                it.substringAfterLast('/').equals("manifest.hjson", ignoreCase = true)
            }
        if (nestedHjson != null) return nestedHjson

        return entryNames.firstOrNull {
            it.substringAfterLast('/').equals("manifest.json", ignoreCase = true)
        }
    }

    private fun isManifestEntryName(entryName: String): Boolean {
        if (entryName.equals("manifest.hjson", ignoreCase = true)) return true
        if (entryName.equals("manifest.json", ignoreCase = true)) return true
        val fileName = entryName.substringAfterLast('/')
        return fileName.equals("manifest.hjson", ignoreCase = true) ||
            fileName.equals("manifest.json", ignoreCase = true)
    }

    private fun parseToolPkgManifest(content: String, manifestEntryName: String): ToolPkgManifest {
        val manifestJson =
            if (manifestEntryName.endsWith(".hjson", ignoreCase = true)) {
                JsonValue.readHjson(content).toString()
            } else {
                content
            }

        val jsonConfig = Json { ignoreUnknownKeys = true }
        return jsonConfig.decodeFromString<ToolPkgManifest>(manifestJson)
    }

    private fun hasLocalizedTextContent(text: LocalizedText?): Boolean {
        return text?.values?.values?.any { it.isNotBlank() } == true
    }
}
