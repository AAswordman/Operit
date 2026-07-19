package com.ai.assistance.operit.core.tools.packTool

import android.content.Context
import com.ai.assistance.operit.core.chat.logMessageTiming
import com.ai.assistance.operit.core.chat.messageTimingNow
import com.ai.assistance.operit.data.model.Workflow
import com.ai.assistance.operit.data.repository.WorkflowRepository
import com.ai.assistance.operit.ui.features.chat.webview.workspace.WorkspaceConfigReader
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal class PackageManagerToolPkgFacade(
    private val packageManager: PackageManager
) {
    private val workflowTemplateJson =
        Json {
            ignoreUnknownKeys = true
            classDiscriminator = "__type"
        }

    private fun buildToolPkgToolboxUiModules(
        container: ToolPkgContainerRuntime,
        localizationContext: Context,
        runtime: String
    ): List<PackageManager.ToolPkgToolboxUiModule> {
        val containerDisplayName =
            container.displayName.resolve(localizationContext).ifBlank { container.packageName }
        val containerDescription = container.description.resolve(localizationContext)
        val toolboxRouteIds =
            container.navigationEntries
                .filter { it.surface.equals(TOOLPKG_NAV_SURFACE_TOOLBOX, ignoreCase = true) }
                .map { it.routeId.lowercase() }
                .toSet()
        return container.uiRoutes
            .filter { route ->
                route.runtime.equals(runtime, ignoreCase = true) &&
                    toolboxRouteIds.contains(route.routeId.lowercase())
            }
            .map { route ->
                val moduleTitle =
                    route.title.resolve(localizationContext).trim().ifBlank { containerDisplayName }
                PackageManager.ToolPkgToolboxUiModule(
                    containerPackageName = container.packageName,
                    toolPkgId = container.packageName,
                    routeId = route.routeId,
                    uiModuleId = route.id,
                    runtime = route.runtime,
                    screen = route.screen,
                    title = moduleTitle,
                    description = containerDescription,
                    keepAlive = route.keepAlive,
                    moduleSpec =
                        mapOf(
                            "id" to route.id,
                            "routeId" to route.routeId,
                            "runtime" to route.runtime,
                            "screen" to route.screen,
                            "title" to moduleTitle,
                            "toolPkgId" to container.packageName,
                            "keepAlive" to route.keepAlive
                        )
                )
            }
            .sortedWith(
                compareBy(
                    PackageManager.ToolPkgToolboxUiModule::title,
                    PackageManager.ToolPkgToolboxUiModule::containerPackageName,
                    PackageManager.ToolPkgToolboxUiModule::uiModuleId
                )
            )
    }

    private fun buildToolPkgUiRoutes(
        container: ToolPkgContainerRuntime,
        localizationContext: Context,
        runtime: String = TOOLPKG_RUNTIME_COMPOSE_DSL
    ): List<PackageManager.ToolPkgUiRoute> {
        val containerDisplayName =
            container.displayName.resolve(localizationContext).ifBlank { container.packageName }
        val containerDescription = container.description.resolve(localizationContext)
        return container.uiRoutes
            .filter { route -> route.runtime.equals(runtime, ignoreCase = true) }
            .map { route ->
                val routeTitle =
                    route.title.resolve(localizationContext).trim().ifBlank { containerDisplayName }
                PackageManager.ToolPkgUiRoute(
                    containerPackageName = container.packageName,
                    toolPkgId = container.packageName,
                    routeId = route.routeId,
                    uiModuleId = route.id,
                    runtime = route.runtime,
                    screen = route.screen,
                    title = routeTitle,
                    description = containerDescription,
                    keepAlive = route.keepAlive,
                    moduleSpec =
                        mapOf(
                            "id" to route.id,
                            "routeId" to route.routeId,
                            "runtime" to route.runtime,
                            "screen" to route.screen,
                            "title" to routeTitle,
                            "toolPkgId" to container.packageName,
                            "keepAlive" to route.keepAlive
                        )
                )
            }
            .sortedWith(
                compareBy(
                    PackageManager.ToolPkgUiRoute::title,
                    PackageManager.ToolPkgUiRoute::containerPackageName,
                    PackageManager.ToolPkgUiRoute::uiModuleId
                )
            )
    }

    private fun buildToolPkgNavigationEntries(
        container: ToolPkgContainerRuntime,
        localizationContext: Context
    ): List<PackageManager.ToolPkgNavigationEntry> {
        val containerDescription = container.description.resolve(localizationContext)
        return container.navigationEntries
            .map { entry ->
                PackageManager.ToolPkgNavigationEntry(
                    containerPackageName = container.packageName,
                    toolPkgId = container.packageName,
                    entryId = entry.id,
                    routeId = entry.routeId,
                    surface = entry.surface,
                    title = entry.title.resolve(localizationContext).trim().ifBlank { entry.id },
                    description = containerDescription,
                    action =
                        entry.action?.let { action ->
                            PackageManager.ToolPkgNavigationActionHook(
                                functionName = action.function,
                                functionSource = action.functionSource
                            )
                        },
                    icon = entry.icon,
                    order = entry.order
                )
            }
            .sortedWith(
                compareBy(
                    PackageManager.ToolPkgNavigationEntry::surface,
                    PackageManager.ToolPkgNavigationEntry::order,
                    PackageManager.ToolPkgNavigationEntry::title
                )
            )
    }

    private fun buildToolPkgDesktopWidgets(
        container: ToolPkgContainerRuntime,
        localizationContext: Context
    ): List<PackageManager.ToolPkgDesktopWidget> {
        return container.desktopWidgets
            .map { widget ->
                PackageManager.ToolPkgDesktopWidget(
                    containerPackageName = container.packageName,
                    toolPkgId = container.packageName,
                    widgetId = widget.id,
                    routeId = widget.routeId,
                    renderRouteId = widget.renderRouteId,
                    title = widget.title.resolve(localizationContext).trim().ifBlank { widget.id },
                    subtitle = widget.subtitle.resolve(localizationContext).trim(),
                    description = widget.description.resolve(localizationContext).trim(),
                    icon = widget.icon,
                    order = widget.order
                )
            }
            .sortedWith(
                compareBy(
                    PackageManager.ToolPkgDesktopWidget::order,
                    PackageManager.ToolPkgDesktopWidget::title,
                    PackageManager.ToolPkgDesktopWidget::widgetId
                )
            )
    }

    private fun buildToolPkgWorkflowTemplates(
        container: ToolPkgContainerRuntime,
        localizationContext: Context
    ): List<PackageManager.ToolPkgWorkflowTemplate> {
        return container.workflowTemplates
            .map { template ->
                PackageManager.ToolPkgWorkflowTemplate(
                    containerPackageName = container.packageName,
                    toolPkgId = container.packageName,
                    templateId = template.id,
                    displayName =
                        template.displayName.resolve(localizationContext).trim().ifBlank {
                            template.id
                        },
                    description = template.description.resolve(localizationContext),
                    resourceKey = template.resourceKey
                )
            }
            .sortedWith(
                compareBy(
                    PackageManager.ToolPkgWorkflowTemplate::displayName,
                    PackageManager.ToolPkgWorkflowTemplate::templateId
                )
            )
    }

    private fun buildToolPkgWorkspaceTemplates(
        container: ToolPkgContainerRuntime,
        localizationContext: Context
    ): List<PackageManager.ToolPkgWorkspaceTemplate> {
        return container.workspaceTemplates
            .map { template ->
                PackageManager.ToolPkgWorkspaceTemplate(
                    containerPackageName = container.packageName,
                    toolPkgId = container.packageName,
                    templateId = template.id,
                    displayName =
                        template.displayName.resolve(localizationContext).trim().ifBlank {
                            template.id
                        },
                    description = template.description.resolve(localizationContext),
                    resourceKey = template.resourceKey,
                    projectType = template.projectType
                )
            }
            .sortedWith(
                compareBy(
                    PackageManager.ToolPkgWorkspaceTemplate::displayName,
                    PackageManager.ToolPkgWorkspaceTemplate::templateId
                )
            )
    }

    private fun buildToolPkgWasmModules(
        container: ToolPkgContainerRuntime
    ): List<PackageManager.ToolPkgWasmModule> {
        return container.wasmModules
            .map { module ->
                PackageManager.ToolPkgWasmModule(
                    id = module.id,
                    path = module.path,
                    exports = module.exports,
                    sourceLanguage = module.sourceLanguage,
                    abi = module.abi
                )
            }
            .sortedWith(
                compareBy(
                    PackageManager.ToolPkgWasmModule::id,
                    PackageManager.ToolPkgWasmModule::path
                )
            )
    }

    fun isToolPkgContainer(packageName: String): Boolean {
        packageManager.ensureInitialized()
        val normalizedPackageName = packageManager.normalizePackageName(packageName)
        return packageManager.toolPkgContainersInternal.containsKey(normalizedPackageName)
    }

    fun isToolPkgSubpackage(packageName: String): Boolean {
        packageManager.ensureInitialized()
        return packageManager.resolveToolPkgSubpackageRuntimeInternal(packageName) != null
    }

    fun getToolPkgContainerDetails(
        packageName: String,
        resolveContext: Context? = null
    ): PackageManager.ToolPkgContainerDetails? {
        packageManager.ensureInitialized()
        val normalizedPackageName = packageManager.normalizePackageName(packageName)
        val container = packageManager.toolPkgContainersInternal[normalizedPackageName] ?: return null
        val enabledSet = packageManager.getEnabledPackageNameSetInternal()
        val localizationContext = resolveContext ?: packageManager.contextInternal
        val containerEnabled = enabledSet.contains(container.packageName)
        val toolboxUiModules =
            if (containerEnabled) {
                buildToolPkgToolboxUiModules(
                    container = container,
                    localizationContext = localizationContext,
                    runtime = TOOLPKG_RUNTIME_COMPOSE_DSL
                )
            } else {
                emptyList()
            }

        val subpackages =
            container.subpackages.map { subpackage ->
                PackageManager.ToolPkgSubpackageInfo(
                    packageName = subpackage.packageName,
                    subpackageId = subpackage.subpackageId,
                    displayName = subpackage.displayName.resolve(localizationContext),
                    description = subpackage.description.resolve(localizationContext),
                    enabledByDefault = subpackage.enabledByDefault,
                    toolCount = subpackage.toolCount,
                    enabled = containerEnabled && enabledSet.contains(subpackage.packageName)
                )
            }
        val workflowTemplates = buildToolPkgWorkflowTemplates(container, localizationContext)
        val workspaceTemplates = buildToolPkgWorkspaceTemplates(container, localizationContext)
        val wasmModules = buildToolPkgWasmModules(container)

        val result = PackageManager.ToolPkgContainerDetails(
            packageName = container.packageName,
            displayName = container.displayName.resolve(localizationContext),
            description = container.description.resolve(localizationContext),
            version = container.version,
            author = container.author,
            resourceCount = container.resources.size,
            wasmModuleCount = wasmModules.size,
            workflowTemplateCount = workflowTemplates.size,
            workspaceTemplateCount = workspaceTemplates.size,
            uiModuleCount = container.uiModules.size,
            wasmModules = wasmModules,
            toolboxUiModules = toolboxUiModules,
            subpackages = subpackages,
            workflowTemplates = workflowTemplates,
            workspaceTemplates = workspaceTemplates
        )
        return result
    }

    fun getToolPkgUiRoutes(
        runtime: String = TOOLPKG_RUNTIME_COMPOSE_DSL,
        resolveContext: Context? = null
    ): List<PackageManager.ToolPkgUiRoute> {
        packageManager.ensureInitialized()
        val enabledSet = packageManager.getEnabledPackageNameSetInternal()
        val localizationContext = resolveContext ?: packageManager.contextInternal
        return packageManager.toolPkgContainersInternal.values
            .filter { container -> enabledSet.contains(container.packageName) }
            .flatMap { container ->
                buildToolPkgUiRoutes(
                    container = container,
                    localizationContext = localizationContext,
                    runtime = runtime
                )
            }
    }

    fun getToolPkgNavigationEntries(
        resolveContext: Context? = null
    ): List<PackageManager.ToolPkgNavigationEntry> {
        packageManager.ensureInitialized()
        val enabledSet = packageManager.getEnabledPackageNameSetInternal()
        val localizationContext = resolveContext ?: packageManager.contextInternal
        return packageManager.toolPkgContainersInternal.values
            .filter { container -> enabledSet.contains(container.packageName) }
            .flatMap { container ->
                buildToolPkgNavigationEntries(
                    container = container,
                    localizationContext = localizationContext
                )
            }
    }

    fun getToolPkgDesktopWidgets(
        resolveContext: Context? = null
    ): List<PackageManager.ToolPkgDesktopWidget> {
        packageManager.ensureInitialized()
        val enabledSet = packageManager.getEnabledPackageNameSetInternal()
        val localizationContext = resolveContext ?: packageManager.contextInternal
        return packageManager.toolPkgContainersInternal.values
            .filter { container -> enabledSet.contains(container.packageName) }
            .flatMap { container ->
                buildToolPkgDesktopWidgets(
                    container = container,
                    localizationContext = localizationContext
                )
            }
            .sortedWith(
                compareBy(
                    PackageManager.ToolPkgDesktopWidget::order,
                    PackageManager.ToolPkgDesktopWidget::title,
                    PackageManager.ToolPkgDesktopWidget::containerPackageName,
                    PackageManager.ToolPkgDesktopWidget::widgetId
                )
            )
    }

    fun getToolPkgWorkflowTemplates(
        resolveContext: Context? = null
    ): List<PackageManager.ToolPkgWorkflowTemplate> {
        packageManager.ensureInitialized()
        val enabledSet = packageManager.getEnabledPackageNameSetInternal()
        val localizationContext = resolveContext ?: packageManager.contextInternal
        return packageManager.toolPkgContainersInternal.values
            .filter { container -> enabledSet.contains(container.packageName) }
            .flatMap { container ->
                buildToolPkgWorkflowTemplates(
                    container = container,
                    localizationContext = localizationContext
                )
            }
    }

    fun importToolPkgWorkflowTemplate(
        containerPackageName: String,
        templateId: String
    ): Result<Workflow> {
        packageManager.ensureInitialized()
        return runCatching {
            val normalizedContainerPackageName = packageManager.normalizePackageName(containerPackageName)
            val runtime =
                packageManager.toolPkgContainersInternal[normalizedContainerPackageName]
                    ?: throw IllegalArgumentException("ToolPkg container not found: $containerPackageName")
            val enabledSet = packageManager.getEnabledPackageNameSetInternal()
            if (!enabledSet.contains(runtime.packageName)) {
                throw IllegalStateException("ToolPkg container is not enabled: ${runtime.packageName}")
            }

            val template =
                runtime.workflowTemplates.firstOrNull {
                    it.id.equals(templateId.trim(), ignoreCase = true)
                } ?: throw IllegalArgumentException("Workflow template not found: $templateId")
            val resource =
                runtime.resources.firstOrNull {
                    it.key.equals(template.resourceKey, ignoreCase = true)
                } ?: throw IllegalStateException(
                    "Workflow template resource not found: ${template.resourceKey}"
                )
            if (ToolPkgArchiveParser.isDirectoryResourceMime(resource.mime)) {
                throw IllegalStateException(
                    "Workflow template resource must be a file: ${template.resourceKey}"
                )
            }

            val bytes =
                packageManager.readToolPkgResourceBytes(runtime, resource.path)
                    ?: throw IllegalStateException(
                        "Workflow template resource is unavailable: ${template.resourceKey}"
                    )
            val templateWorkflowId = UUID.randomUUID().toString()
            val templateElement =
                JsonObject(
                    (workflowTemplateJson.parseToJsonElement(bytes.toString(StandardCharsets.UTF_8)) as JsonObject) +
                        ("id" to JsonPrimitive(templateWorkflowId))
                )
            val decoded =
                workflowTemplateJson.decodeFromJsonElement(Workflow.serializer(), templateElement)
            val now = System.currentTimeMillis()
            val importedWorkflow =
                decoded.copy(
                    id = templateWorkflowId,
                    createdAt = now,
                    updatedAt = now,
                    lastExecutionTime = null,
                    lastExecutionStatus = null,
                    totalExecutions = 0,
                    successfulExecutions = 0,
                    failedExecutions = 0
                )
            kotlinx.coroutines.runBlocking {
                WorkflowRepository(packageManager.contextInternal).createWorkflow(importedWorkflow)
                    .getOrThrow()
            }
        }
    }

    fun getToolPkgWorkspaceTemplates(
        resolveContext: Context? = null
    ): List<PackageManager.ToolPkgWorkspaceTemplate> {
        packageManager.ensureInitialized()
        val enabledSet = packageManager.getEnabledPackageNameSetInternal()
        val localizationContext = resolveContext ?: packageManager.contextInternal
        return packageManager.toolPkgContainersInternal.values
            .filter { container -> enabledSet.contains(container.packageName) }
            .flatMap { container ->
                buildToolPkgWorkspaceTemplates(
                    container = container,
                    localizationContext = localizationContext
                )
            }
    }

    fun importToolPkgWorkspaceTemplate(
        containerPackageName: String,
        templateId: String,
        destinationDir: File
    ): Result<PackageManager.ToolPkgWorkspaceTemplateImportResult> {
        packageManager.ensureInitialized()
        return runCatching {
            val normalizedContainerPackageName = packageManager.normalizePackageName(containerPackageName)
            val runtime =
                packageManager.toolPkgContainersInternal[normalizedContainerPackageName]
                    ?: throw IllegalArgumentException("ToolPkg container not found: $containerPackageName")
            val enabledSet = packageManager.getEnabledPackageNameSetInternal()
            if (!enabledSet.contains(runtime.packageName)) {
                throw IllegalStateException("ToolPkg container is not enabled: ${runtime.packageName}")
            }

            val template =
                runtime.workspaceTemplates.firstOrNull {
                    it.id.equals(templateId.trim(), ignoreCase = true)
                } ?: throw IllegalArgumentException("Workspace template not found: $templateId")
            val resource =
                runtime.resources.firstOrNull {
                    it.key.equals(template.resourceKey, ignoreCase = true)
                } ?: throw IllegalStateException(
                    "Workspace template resource not found: ${template.resourceKey}"
                )
            if (!ToolPkgArchiveParser.isDirectoryResourceMime(resource.mime)) {
                throw IllegalStateException(
                    "Workspace template resource must be a directory: ${template.resourceKey}"
                )
            }

            if (destinationDir.exists()) {
                if (!destinationDir.isDirectory) {
                    throw IllegalArgumentException(
                        "Workspace destination is not a directory: ${destinationDir.absolutePath}"
                    )
                }
                if (!destinationDir.listFiles().isNullOrEmpty()) {
                    throw IllegalArgumentException(
                        "Workspace destination must be empty: ${destinationDir.absolutePath}"
                    )
                }
            } else if (!destinationDir.mkdirs()) {
                throw IllegalStateException(
                    "Failed to create workspace destination: ${destinationDir.absolutePath}"
                )
            }

            val copied =
                packageManager.copyToolPkgResourceDirectory(
                    runtime = runtime,
                    normalizedResourcePath = resource.path,
                    destinationDir = destinationDir
                )
            if (!copied) {
                throw IllegalStateException(
                    "Workspace template directory is invalid: ${template.resourceKey}"
                )
            }

            if (!WorkspaceConfigReader.hasConfig(destinationDir.absolutePath)) {
                throw IllegalStateException(
                    "Workspace template is missing .operit/config.json: ${template.id}"
                )
            }

            val config = WorkspaceConfigReader.readConfig(destinationDir.absolutePath)
            PackageManager.ToolPkgWorkspaceTemplateImportResult(
                containerPackageName = runtime.packageName,
                toolPkgId = runtime.packageName,
                templateId = template.id,
                workspacePath = destinationDir.absolutePath,
                workspaceConfig = config
            )
        }
    }

    fun setToolPkgSubpackageEnabled(subpackagePackageName: String, enabled: Boolean): Boolean {
        packageManager.ensureInitialized()
        val normalizedPackageName = packageManager.normalizePackageName(subpackagePackageName)
        val subpackageRuntime = packageManager.toolPkgSubpackageByPackageNameInternal[normalizedPackageName]
        if (subpackageRuntime == null) {
            return false
        }

        val enabledPackageNames = LinkedHashSet(packageManager.getEnabledPackageNames())
        val subpackageStates = packageManager.getToolPkgSubpackageStatesInternal().toMutableMap()
        val containerEnabled = enabledPackageNames.contains(subpackageRuntime.containerPackageName)

        subpackageStates[normalizedPackageName] = enabled

        if (containerEnabled && enabled) {
            enabledPackageNames.add(normalizedPackageName)
        } else {
            enabledPackageNames.remove(normalizedPackageName)
            packageManager.unregisterPackageTools(normalizedPackageName)
        }

        packageManager.saveEnabledPackageNames(enabledPackageNames.toList())
        packageManager.saveToolPkgSubpackageStates(subpackageStates)

        val stateSaved = packageManager.getToolPkgSubpackageStatesInternal()[normalizedPackageName] == enabled
        val importedMatches =
            if (containerEnabled) {
                packageManager.getEnabledPackageNames().contains(normalizedPackageName) == enabled
            } else {
                !packageManager.getEnabledPackageNames().contains(normalizedPackageName)
            }
        return stateSaved && importedMatches
    }

    fun findPreferredPackageNameForSubpackageId(
        subpackageId: String,
        preferEnabled: Boolean = true
    ): String? {
        packageManager.ensureInitialized()
        if (subpackageId.isBlank()) return null

        val directRuntime = packageManager.resolveToolPkgSubpackageRuntimeInternal(subpackageId)
        if (directRuntime != null) {
            if (preferEnabled) {
                if (packageManager.isPackageEnabled(directRuntime.packageName)) {
                    return directRuntime.packageName
                }
            }
            return directRuntime.packageName
        }

        val candidates =
            packageManager.toolPkgSubpackageByPackageNameInternal.values.filter {
                it.subpackageId.equals(subpackageId, ignoreCase = true)
            }

        if (candidates.isEmpty()) {
            return null
        }

        if (preferEnabled) {
            val enabledCandidate = candidates.firstOrNull { packageManager.isPackageEnabled(it.packageName) }
            if (enabledCandidate != null) {
                return enabledCandidate.packageName
            }
        }

        return candidates.first().packageName
    }

    fun copyToolPkgResourceToFileBySubpackageId(
        subpackageId: String,
        resourceKey: String,
        destinationFile: File,
        preferEnabledContainer: Boolean = true
    ): Boolean {
        packageManager.ensureInitialized()
        if (subpackageId.isBlank() || resourceKey.isBlank()) {
            return false
        }

        val directSubpackage = packageManager.resolveToolPkgSubpackageRuntimeInternal(subpackageId)
        val subpackages =
            if (directSubpackage != null) {
                listOf(directSubpackage)
            } else {
                packageManager.toolPkgSubpackageByPackageNameInternal.values.filter {
                    it.subpackageId.equals(subpackageId, ignoreCase = true)
                }
            }

        if (subpackages.isEmpty()) {
            return false
        }

        val candidateContainers =
            if (preferEnabledContainer) {
                val enabledSet = packageManager.getEnabledPackageNameSetInternal()
                val enabledContainers =
                    subpackages
                        .map { it.containerPackageName }
                        .distinct()
                        .filter { enabledSet.contains(it) }
                if (enabledContainers.isNotEmpty()) {
                    enabledContainers
                } else {
                    subpackages.map { it.containerPackageName }.distinct()
                }
            } else {
                subpackages.map { it.containerPackageName }.distinct()
            }

        candidateContainers.forEach { containerName ->
            if (copyToolPkgResourceToFile(containerName, resourceKey, destinationFile)) {
                return true
            }
        }

        return false
    }

    fun copyToolPkgResourceToFile(
        containerPackageName: String,
        resourceKey: String,
        destinationFile: File
    ): Boolean {
        packageManager.ensureInitialized()
        val normalizedContainerPackageName = packageManager.normalizePackageName(containerPackageName)
        val runtime = packageManager.toolPkgContainersInternal[normalizedContainerPackageName] ?: return false
        val enabledSet = packageManager.getEnabledPackageNameSetInternal()
        if (!enabledSet.contains(runtime.packageName)) {
            return false
        }
        val resource =
            runtime.resources.firstOrNull {
                it.key.equals(resourceKey, ignoreCase = true)
            } ?: return false

        return try {
            packageManager.exportToolPkgResource(runtime, resource, destinationFile)
        } catch (e: Exception) {
            AppLogger.e("PackageManager", "Failed to export toolpkg resource: ${runtime.packageName}:${resource.key}", e)
            false
        }
    }

    fun getToolPkgResourceOutputFileName(
        packageNameOrSubpackageId: String,
        resourceKey: String,
        preferEnabledContainer: Boolean = true
    ): String? {
        packageManager.ensureInitialized()
        val target = packageNameOrSubpackageId.trim()
        val key = resourceKey.trim()
        if (target.isBlank() || key.isBlank()) {
            return null
        }

        fun resolveFromContainer(containerName: String): String? {
            val normalizedContainerName = packageManager.normalizePackageName(containerName)
            val runtime = packageManager.toolPkgContainersInternal[normalizedContainerName] ?: return null
            val resource =
                runtime.resources.firstOrNull {
                    it.key.equals(key, ignoreCase = true)
                } ?: return null
            val baseName =
                resource.path.substringAfterLast('/').substringAfterLast('\\').trim()
            if (baseName.isBlank()) {
                return null
            }
            return if (ToolPkgArchiveParser.isDirectoryResourceMime(resource.mime)) {
                if (baseName.endsWith(".zip", ignoreCase = true)) baseName else "$baseName.zip"
            } else {
                baseName
            }
        }

        resolveFromContainer(target)?.let { return it }

        val directSubpackage = packageManager.resolveToolPkgSubpackageRuntimeInternal(target)
        if (directSubpackage != null) {
            resolveFromContainer(directSubpackage.containerPackageName)?.let { return it }
        }

        val subpackages =
            packageManager.toolPkgSubpackageByPackageNameInternal.values.filter {
                it.subpackageId.equals(target, ignoreCase = true)
            }
        if (subpackages.isEmpty()) {
            return null
        }

        val candidateContainers =
            if (preferEnabledContainer) {
                val enabledSet = packageManager.getEnabledPackageNameSetInternal()
                val enabledContainers =
                    subpackages
                        .map { it.containerPackageName }
                        .distinct()
                        .filter { enabledSet.contains(it) }
                if (enabledContainers.isNotEmpty()) {
                    enabledContainers
                } else {
                    subpackages.map { it.containerPackageName }.distinct()
                }
            } else {
                subpackages.map { it.containerPackageName }.distinct()
            }

        candidateContainers.forEach { containerName ->
            resolveFromContainer(containerName)?.let { return it }
        }

        return null
    }

    fun getToolPkgComposeDslScriptBySubpackageId(
        subpackageId: String,
        uiModuleId: String? = null,
        preferEnabledContainer: Boolean = true
    ): String? {
        packageManager.ensureInitialized()
        if (subpackageId.isBlank()) {
            return null
        }

        val directSubpackage = packageManager.resolveToolPkgSubpackageRuntimeInternal(subpackageId)
        val subpackages =
            if (directSubpackage != null) {
                listOf(directSubpackage)
            } else {
                packageManager.toolPkgSubpackageByPackageNameInternal.values.filter {
                    it.subpackageId.equals(subpackageId, ignoreCase = true)
                }
            }

        if (subpackages.isEmpty()) {
            return null
        }

        val candidateContainers =
            if (preferEnabledContainer) {
                val enabledSet = packageManager.getEnabledPackageNameSetInternal()
                subpackages
                    .map { it.containerPackageName }
                    .distinct()
                    .filter { enabledSet.contains(it) }
            } else {
                subpackages.map { it.containerPackageName }.distinct()
            }

        candidateContainers.forEach { containerName ->
            val script = getToolPkgComposeDslScript(containerName, uiModuleId)
            if (!script.isNullOrBlank()) {
                return script
            }
        }

        return null
    }

    fun getToolPkgComposeDslScript(
        containerPackageName: String,
        uiModuleId: String? = null
    ): String? {
        packageManager.ensureInitialized()
        val normalizedContainerPackageName = packageManager.normalizePackageName(containerPackageName)
        val runtime = packageManager.toolPkgContainersInternal[normalizedContainerPackageName] ?: return null
        val enabledSet = packageManager.getEnabledPackageNameSetInternal()
        if (!enabledSet.contains(runtime.packageName)) {
            return null
        }

        val uiModule =
            if (!uiModuleId.isNullOrBlank()) {
                runtime.uiModules.firstOrNull { module ->
                    module.id.equals(uiModuleId, ignoreCase = true) &&
                        module.runtime.equals(TOOLPKG_RUNTIME_COMPOSE_DSL, ignoreCase = true)
                }
            } else {
                runtime.uiModules.firstOrNull { module ->
                    module.runtime.equals(TOOLPKG_RUNTIME_COMPOSE_DSL, ignoreCase = true)
                }
            } ?: return null

        if (uiModule.screen.isBlank()) {
            return null
        }

        return try {
            val bytes = packageManager.readToolPkgResourceBytes(runtime, uiModule.screen) ?: return null
            bytes.toString(StandardCharsets.UTF_8)
        } catch (e: Exception) {
            AppLogger.e(
                "PackageManager",
                "Failed to read toolpkg compose_dsl script: ${runtime.packageName}:${uiModule.id}",
                e
            )
            null
        }
    }

    fun getToolPkgComposeDslScreenPath(
        containerPackageName: String,
        uiModuleId: String? = null
    ): String? {
        packageManager.ensureInitialized()
        val normalizedContainerPackageName = packageManager.normalizePackageName(containerPackageName)
        val runtime = packageManager.toolPkgContainersInternal[normalizedContainerPackageName] ?: return null
        val enabledSet = packageManager.getEnabledPackageNameSetInternal()
        if (!enabledSet.contains(runtime.packageName)) {
            return null
        }

        val uiModule =
            if (!uiModuleId.isNullOrBlank()) {
                runtime.uiModules.firstOrNull { module ->
                    module.id.equals(uiModuleId, ignoreCase = true) &&
                        module.runtime.equals(TOOLPKG_RUNTIME_COMPOSE_DSL, ignoreCase = true)
                }
            } else {
                runtime.uiModules.firstOrNull { module ->
                    module.runtime.equals(TOOLPKG_RUNTIME_COMPOSE_DSL, ignoreCase = true)
                }
            } ?: return null

        return uiModule.screen.trim().ifBlank { null }
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
        val normalizedPluginId = pluginId?.trim().orEmpty().ifBlank { null }
        val resolvedEventName = eventName?.trim().orEmpty().ifBlank { event }
        val shouldLogTiming = event.equals(TOOLPKG_EVENT_MESSAGE_PROCESSING, ignoreCase = true)
        val totalStartTime = if (shouldLogTiming) messageTimingNow() else 0L

        return runCatching {
            val normalizedContainerPackageName = packageManager.normalizePackageName(containerPackageName)
            val runtime =
                packageManager.toolPkgContainersInternal[normalizedContainerPackageName]
                    ?: throw IllegalArgumentException("ToolPkg container not found: $containerPackageName")

            val getMainScriptStartTime = if (shouldLogTiming) messageTimingNow() else 0L
            val script =
                packageManager.getToolPkgMainScriptInternal(runtime.packageName)
                    ?: throw IllegalStateException("ToolPkg main script is unavailable: ${runtime.packageName}")
            if (shouldLogTiming) {
                logMessageTiming(
                    stage = "toolpkg.runMainHook.getMainScript",
                    startTimeMs = getMainScriptStartTime,
                    details = "container=${runtime.packageName}, plugin=${normalizedPluginId ?: "none"}, scriptLength=${script.length}"
                )
            }

            val resolveFunctionSourceStartTime = if (shouldLogTiming) messageTimingNow() else 0L
            val functionSource = inlineFunctionSource?.trim().orEmpty().ifBlank { null }
            if (shouldLogTiming) {
                logMessageTiming(
                    stage = "toolpkg.runMainHook.resolveFunctionSource",
                    startTimeMs = resolveFunctionSourceStartTime,
                    details = "container=${runtime.packageName}, function=$functionName, hasInline=${!functionSource.isNullOrBlank()}"
                )
            }

            val timestampMs = System.currentTimeMillis()
            val params = mutableMapOf<String, Any?>(
                "event" to resolvedEventName,
                "eventName" to resolvedEventName,
                "eventPayload" to eventPayload,
                "timestampMs" to timestampMs,
                "functionName" to functionName,
                "toolPkgId" to runtime.packageName,
                "containerPackageName" to runtime.packageName,
                "__operit_ui_package_name" to runtime.packageName,
                "__operit_script_screen" to runtime.mainEntry
            )
            if (!normalizedPluginId.isNullOrBlank()) {
                params["pluginId"] = normalizedPluginId
            }
            eventPayload["chatId"]
                ?.toString()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { chatId ->
                    params["__operit_package_chat_id"] = chatId
                }
            if (!functionSource.isNullOrBlank()) {
                params["__operit_inline_function_name"] = functionName
                params["__operit_inline_function_source"] = functionSource
            }
            executionContextKey
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { contextKey ->
                    params["__operit_execution_context_key"] = contextKey
                }
            runtimeKind
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.isNotBlank() }
                ?.let { kind ->
                    params["__operit_toolpkg_runtime_kind"] = kind
                }

            val getExecutionEngineStartTime = if (shouldLogTiming) messageTimingNow() else 0L
            val resolvedExecutionContextKey = resolveToolPkgExecutionContextKey(runtime.packageName, params)
            val executionEngine =
                packageManager.getToolPkgExecutionEngine(
                    contextKey = resolvedExecutionContextKey,
                    containerPackageName = runtime.packageName
                )
            if (shouldLogTiming) {
                logMessageTiming(
                    stage = "toolpkg.runMainHook.getExecutionEngine",
                    startTimeMs = getExecutionEngineStartTime,
                    details = "container=${runtime.packageName}, plugin=${normalizedPluginId ?: "none"}, contextKey=$resolvedExecutionContextKey"
                )
            }

            val executeScriptFunctionStartTime = if (shouldLogTiming) messageTimingNow() else 0L
            val executionResult = executionEngine.executeScriptFunction(
                script = script,
                functionName = functionName,
                params = params,
                onIntermediateResult = onIntermediateResult,
                dispatchIntermediateOnMain = dispatchIntermediateOnMain
            )
            if (shouldLogTiming) {
                logMessageTiming(
                    stage = "toolpkg.runMainHook.executeScriptFunction",
                    startTimeMs = executeScriptFunctionStartTime,
                    details = "container=${runtime.packageName}, plugin=${normalizedPluginId ?: "none"}, function=$functionName, resultType=${executionResult?.javaClass?.simpleName ?: "null"}"
                )
                logMessageTiming(
                    stage = "toolpkg.runMainHook.total",
                    startTimeMs = totalStartTime,
                    details = "container=${runtime.packageName}, plugin=${normalizedPluginId ?: "none"}, function=$functionName, success=true"
                )
            }
            executionResult
        }.onFailure { error ->
            if (shouldLogTiming) {
                logMessageTiming(
                    stage = "toolpkg.runMainHook.total",
                    startTimeMs = totalStartTime,
                    details = "container=$containerPackageName, plugin=${normalizedPluginId ?: "none"}, function=$functionName, success=false, reason=${error.message ?: error.javaClass.simpleName}"
                )
            }
            val pluginPart = if (normalizedPluginId.isNullOrBlank()) "" else ", plugin=$normalizedPluginId"
            AppLogger.e(
                "PackageManagerToolPkgFacade",
                "runToolPkgMainHook failed: container=$containerPackageName, function=$functionName, event=$event$pluginPart",
                error
            )
        }
    }

    fun runToolPkgNavigationEntryAction(
        containerPackageName: String,
        entryId: String,
        functionName: String,
        inlineFunctionSource: String? = null,
        eventPayload: Map<String, Any?> = emptyMap(),
        onIntermediateResult: ((Any?) -> Unit)? = null
    ): Result<Any?> {
        return runToolPkgMainHook(
            containerPackageName = containerPackageName,
            functionName = functionName,
            event = TOOLPKG_EVENT_NAVIGATION_ENTRY_ACTION,
            eventName = "navigation_entry_action",
            pluginId = entryId,
            inlineFunctionSource = inlineFunctionSource,
            eventPayload = eventPayload,
            onIntermediateResult = onIntermediateResult
        )
    }

    private fun resolveToolPkgExecutionContextKey(
        containerPackageName: String,
        params: Map<String, Any?>
    ): String {
        val explicitContextKey =
            sequenceOf(params["__operit_execution_context_key"])
                .mapNotNull { it?.toString()?.trim() }
                .firstOrNull { it.isNotBlank() }
        if (!explicitContextKey.isNullOrBlank()) {
            return explicitContextKey
        }
        return "toolpkg_main:$containerPackageName"
    }

    fun readToolPkgWasmModuleBytes(
        packageNameOrSubpackageId: String,
        moduleId: String,
        exportName: String,
        preferEnabledContainer: Boolean = true
    ): PackageManager.ToolPkgWasmModuleBytes? {
        packageManager.ensureInitialized()
        val target = packageNameOrSubpackageId.trim()
        val normalizedModuleId = moduleId.trim()
        val normalizedExportName = exportName.trim()
        if (target.isBlank() || normalizedModuleId.isBlank() || normalizedExportName.isBlank()) {
            return null
        }

        val enabledSet = packageManager.getEnabledPackageNameSetInternal()

        fun readFromContainer(containerName: String): PackageManager.ToolPkgWasmModuleBytes? {
            val normalizedContainerName = packageManager.normalizePackageName(containerName)
            val runtime = packageManager.toolPkgContainersInternal[normalizedContainerName] ?: return null
            if (!enabledSet.contains(runtime.packageName)) {
                return null
            }
            val module =
                runtime.wasmModules.firstOrNull {
                    it.id.equals(normalizedModuleId, ignoreCase = true)
                } ?: return null
            if (
                module.exports.isNotEmpty() &&
                    module.exports.none { it.equals(normalizedExportName, ignoreCase = true) }
            ) {
                return null
            }
            val bytes = packageManager.readToolPkgResourceBytes(runtime, module.path) ?: return null
            return PackageManager.ToolPkgWasmModuleBytes(
                containerPackageName = runtime.packageName,
                moduleId = module.id,
                path = module.path,
                bytes = bytes
            )
        }

        readFromContainer(target)?.let { return it }

        val directSubpackageRuntime = packageManager.resolveToolPkgSubpackageRuntimeInternal(target)
        if (directSubpackageRuntime != null) {
            readFromContainer(directSubpackageRuntime.containerPackageName)?.let { return it }
        }

        val subpackages =
            packageManager.toolPkgSubpackageByPackageNameInternal.values.filter {
                it.subpackageId.equals(target, ignoreCase = true)
            }
        if (subpackages.isEmpty()) {
            return null
        }

        val containerNames = subpackages.map { it.containerPackageName }.distinct()
        val candidateContainers =
            if (preferEnabledContainer) {
                containerNames.filter { enabledSet.contains(it) } +
                    containerNames.filterNot { enabledSet.contains(it) }
            } else {
                containerNames
            }

        candidateContainers.forEach { containerName ->
            readFromContainer(containerName)?.let { return it }
        }

        return null
    }

    fun readToolPkgTextResource(
        packageNameOrSubpackageId: String,
        resourcePath: String,
        preferEnabledContainer: Boolean = true
    ): String? {
        packageManager.ensureInitialized()
        val target = packageNameOrSubpackageId.trim()
        val normalizedPath =
            resourcePath
                .trim()
                .replace('\\', '/')
                .trimStart('/')

        if (target.isBlank() || normalizedPath.isBlank()) {
            return null
        }

        val containerRuntime = packageManager.toolPkgContainersInternal[target]
        if (containerRuntime != null) {
            val enabledSet = packageManager.getEnabledPackageNameSetInternal()
            if (!enabledSet.contains(containerRuntime.packageName)) {
                return null
            }
            return packageManager.readToolPkgResourceBytes(containerRuntime, normalizedPath)
                ?.toString(StandardCharsets.UTF_8)
        }

        val directSubpackageRuntime = packageManager.resolveToolPkgSubpackageRuntimeInternal(target)
        if (directSubpackageRuntime != null) {
            val directContainer = packageManager.toolPkgContainersInternal[directSubpackageRuntime.containerPackageName]
            if (directContainer != null) {
                val enabledSet = packageManager.getEnabledPackageNameSetInternal()
                if (!enabledSet.contains(directContainer.packageName)) {
                    return null
                }
                return packageManager.readToolPkgResourceBytes(directContainer, normalizedPath)
                    ?.toString(StandardCharsets.UTF_8)
            }
        }

        val subpackages =
            packageManager.toolPkgSubpackageByPackageNameInternal.values.filter {
                it.subpackageId.equals(target, ignoreCase = true)
            }
        if (subpackages.isEmpty()) {
            return null
        }

        val candidateContainers =
            if (preferEnabledContainer) {
                val enabledSet = packageManager.getEnabledPackageNameSetInternal()
                subpackages
                    .map { it.containerPackageName }
                    .distinct()
                    .filter { enabledSet.contains(it) }
            } else {
                subpackages.map { it.containerPackageName }.distinct()
            }

        candidateContainers.forEach { containerName ->
            val runtime = packageManager.toolPkgContainersInternal[containerName] ?: return@forEach
            val text =
                packageManager.readToolPkgResourceBytes(runtime, normalizedPath)
                    ?.toString(StandardCharsets.UTF_8)
            if (!text.isNullOrEmpty()) {
                return text
            }
        }

        return null
    }
}
