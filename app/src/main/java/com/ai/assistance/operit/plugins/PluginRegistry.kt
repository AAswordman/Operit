package com.ai.assistance.operit.plugins

import com.ai.assistance.operit.plugins.toolpkg.ToolPkgLifecyclePlugin
import com.ai.assistance.operit.plugins.toolpkg.ToolPkgCommonBridgePlugin
import com.ai.assistance.operit.plugins.workflow.WorkflowLifecyclePlugin
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap

interface OperitPlugin {
    val id: String

    fun register()
}

object PluginRegistry {
    private val plugins = CopyOnWriteArrayList<OperitPlugin>()
    private val installedPluginIds = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var builtinsInitialized = false

    @Synchronized
    fun register(plugin: OperitPlugin) {
        plugins.removeAll { it.id == plugin.id }
        plugins.add(plugin)
    }

    @Synchronized
    fun initializeBuiltins() {
        if (builtinsInitialized) return
        builtinsInitialized = true

        register(ToolPkgLifecyclePlugin)
        register(ToolPkgCommonBridgePlugin)
        register(WorkflowLifecyclePlugin)
        installAll()
    }

    @Synchronized
    fun installAll() {
        for (plugin in plugins) {
            if (installedPluginIds.add(plugin.id)) {
                plugin.register()
            }
        }
    }
}
