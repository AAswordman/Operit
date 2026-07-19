package com.ai.assistance.operit.core.tools.packTool

import android.content.Context
import com.ai.assistance.operit.core.tools.ToolPackage
import com.ai.assistance.operit.core.tools.javascript.JsEngine
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

internal class ToolPkgManager(
    private val context: Context,
    private val createExecutionEngine: () -> JsEngine = { JsEngine(context) }
) {
    internal val containersInternal = ConcurrentHashMap<String, ToolPkgContainerRuntime>()
    internal val subpackageByPackageNameInternal =
        ConcurrentHashMap<String, ToolPkgSubpackageRuntime>()

    private val runtimeChangeListeners =
        CopyOnWriteArrayList<PackageManager.ToolPkgRuntimeChangeListener>()
    private data class ExecutionEngineEntry(
        val containerPackageName: String,
        val engine: JsEngine
    )

    private val executionEngineLock = Any()
    private val executionEngines = ConcurrentHashMap<String, ExecutionEngineEntry>()
    private val destroyed = AtomicBoolean(false)

    fun isToolPkgContainer(packageName: String): Boolean {
        return containersInternal.containsKey(packageName.trim())
    }

    fun hasSubpackage(packageName: String): Boolean {
        return subpackageByPackageNameInternal.containsKey(packageName.trim())
    }

    fun getToolPkgContainerRuntimes(): List<ToolPkgContainerRuntime> {
        return containersInternal.values.sortedBy(ToolPkgContainerRuntime::packageName)
    }

    fun getToolPkgContainerRuntime(containerPackageName: String): ToolPkgContainerRuntime? {
        return containersInternal[containerPackageName.trim()]
    }

    fun canRegisterToolPkg(
        loadResult: ToolPkgLoadResult,
        availablePackages: Map<String, ToolPackage>
    ): Boolean {
        val containerName = loadResult.containerPackage.name.trim()
        if (
            containerName.isBlank() ||
                containersInternal.containsKey(containerName) ||
                availablePackages.containsKey(containerName)
        ) {
            return false
        }

        loadResult.subpackagePackages.forEach { subpackage ->
            val packageName = subpackage.name.trim()
            if (
                packageName.isBlank() ||
                    containersInternal.containsKey(packageName) ||
                    availablePackages.containsKey(packageName) ||
                    subpackageByPackageNameInternal.containsKey(packageName)
            ) {
                return false
            }
        }
        return true
    }

    fun registerToolPkg(loadResult: ToolPkgLoadResult): List<ToolPackage> {
        val containerName = loadResult.containerPackage.name
        containersInternal[containerName] = loadResult.containerRuntime
        loadResult.containerRuntime.subpackages.forEach { runtime ->
            subpackageByPackageNameInternal[runtime.packageName] = runtime
        }
        return loadResult.subpackagePackages
    }

    fun getEnabledToolPkgContainerRuntimes(
        enabledPackageNames: List<String>
    ): List<ToolPkgContainerRuntime> {
        val enabledSet = enabledPackageNames.toSet()
        return containersInternal.values
            .asSequence()
            .filter { runtime ->
                enabledSet.contains(runtime.packageName) ||
                    runtime.subpackages.any { subpackage ->
                        enabledSet.contains(subpackage.packageName)
                    }
            }
            .sortedBy(ToolPkgContainerRuntime::packageName)
            .toList()
    }

    fun addToolPkgRuntimeChangeListener(
        listener: PackageManager.ToolPkgRuntimeChangeListener,
        activeContainers: List<ToolPkgContainerRuntime>
    ) {
        if (!runtimeChangeListeners.contains(listener)) {
            runtimeChangeListeners.add(listener)
        }
        listener.onToolPkgRuntimeChanged(activeContainers)
    }

    fun removeToolPkgRuntimeChangeListener(listener: PackageManager.ToolPkgRuntimeChangeListener) {
        runtimeChangeListeners.remove(listener)
    }

    fun notifyToolPkgRuntimeChangeListeners(activeContainers: List<ToolPkgContainerRuntime>) {
        runtimeChangeListeners.forEach { listener ->
            listener.onToolPkgRuntimeChanged(activeContainers)
        }
    }

    fun getToolPkgExecutionEngine(
        contextKey: String,
        containerPackageName: String
    ): JsEngine {
        val normalizedKey = contextKey.trim()
        val normalizedContainer = containerPackageName.trim()
        require(normalizedKey.isNotBlank()) { "ToolPkg execution context key is required" }
        require(normalizedContainer.isNotBlank()) { "ToolPkg execution container is required" }

        return synchronized(executionEngineLock) {
            check(!destroyed.get()) { "ToolPkg manager already destroyed" }
            val entry =
                executionEngines.computeIfAbsent(normalizedKey) {
                    ExecutionEngineEntry(
                        containerPackageName = normalizedContainer,
                        engine = createExecutionEngine()
                    )
                }
            check(entry.containerPackageName == normalizedContainer) {
                "ToolPkg execution context '$normalizedKey' belongs to '${entry.containerPackageName}', not '$normalizedContainer'"
            }
            entry.engine
        }
    }

    fun findToolPkgExecutionEngine(contextKey: String): JsEngine? {
        val normalizedKey = contextKey.trim()
        if (normalizedKey.isBlank()) {
            return null
        }
        return executionEngines[normalizedKey]?.engine
    }

    fun releaseToolPkgExecutionEngine(
        contextKey: String,
        executionEngine: JsEngine
    ) {
        val normalizedKey = contextKey.trim()
        if (normalizedKey.isBlank()) {
            return
        }
        val removedEntry =
            synchronized(executionEngineLock) {
                val entry = executionEngines[normalizedKey]
                if (
                    entry != null &&
                        entry.engine === executionEngine &&
                        executionEngines.remove(normalizedKey, entry)
                ) {
                    entry
                } else {
                    null
                }
            }
        if (removedEntry != null) {
            removedEntry.engine.destroy()
        }
    }

    fun releaseToolPkgExecutionEngine(contextKey: String) {
        // Kept for existing callers; lifecycle-aware owners use the identity-checked overload.
        val normalizedKey = contextKey.trim()
        if (normalizedKey.isBlank()) {
            return
        }
        val removedEntry =
            synchronized(executionEngineLock) {
                executionEngines.remove(normalizedKey)
            }
        removedEntry?.engine?.destroy()
    }

    fun cancelExecutionsForChat(chatId: String, reason: String): Boolean {
        var cancelledAny = false
        executionEngines.values.forEach { entry ->
            if (entry.engine.cancelExecutionsForChat(chatId, reason)) {
                cancelledAny = true
            }
        }
        return cancelledAny
    }

    fun destroyToolPkgExecutionEngines(containerPackageName: String) {
        val normalizedContainer = containerPackageName.trim()
        if (normalizedContainer.isBlank()) {
            return
        }
        val removedEntries =
            synchronized(executionEngineLock) {
                executionEngines.entries
                    .filter { (_, entry) -> entry.containerPackageName == normalizedContainer }
                    .mapNotNull { (contextKey, entry) ->
                        if (executionEngines.remove(contextKey, entry)) {
                            entry
                        } else {
                            null
                        }
                    }
            }
        removedEntries.forEach { entry ->
            entry.engine.destroy()
        }
    }

    fun clear() {
        containersInternal.clear()
        subpackageByPackageNameInternal.clear()
    }

    fun destroy() {
        if (!destroyed.compareAndSet(false, true)) {
            return
        }
        val entries =
            synchronized(executionEngineLock) {
                executionEngines.values.toList().also {
                    executionEngines.clear()
                }
            }
        entries.forEach { entry -> entry.engine.destroy() }
    }
}
