package com.ai.assistance.operit.core.tools.packTool.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shared by installers, retained UI and execution gates. Updating never changes enabled preferences. */
object PackageUpdateState {
    private val mutable = MutableStateFlow<Set<String>>(emptySet())
    val updating = mutable.asStateFlow()
    private var checkingCatalog = false
    @Synchronized fun beginCatalogCheck(): Boolean {
        if (checkingCatalog || mutable.value.isNotEmpty()) return false
        checkingCatalog = true
        return true
    }
    @Synchronized fun endCatalogCheck() { checkingCatalog = false }
    private var pluginNames = emptySet<String>()
    fun isPlugin(name: String): Boolean = synchronized(this) { name in pluginNames }
    private val completed = MutableStateFlow(0L)
    val revision = completed.asStateFlow()
    fun contains(name: String): Boolean = name in mutable.value
    @Synchronized fun start(names: Set<String>, plugins: Set<String> = emptySet()): Boolean {
        require(names.isNotEmpty())
        if (checkingCatalog || names.any { it in mutable.value }) return false
        pluginNames = pluginNames + plugins
        mutable.value = mutable.value + names
        return true
    }
    @Synchronized fun finish(names: Set<String>) {
        mutable.value = mutable.value - names
        pluginNames = pluginNames - names
        completed.value++
    }
}
