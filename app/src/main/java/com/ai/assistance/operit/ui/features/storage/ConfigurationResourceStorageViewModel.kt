package com.ai.assistance.operit.ui.features.storage

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.storage.ConfigurationResourceEntry
import com.ai.assistance.operit.data.storage.ConfigurationResourceInventory
import com.ai.assistance.operit.data.storage.ConfigurationResourceSnapshot
import com.ai.assistance.operit.data.storage.ExtensionCategory
import com.ai.assistance.operit.data.storage.ExtensionCategoryGroup
import com.ai.assistance.operit.data.storage.ExtensionItem
import com.ai.assistance.operit.data.storage.PackageSkillInventory
import com.ai.assistance.operit.data.storage.PackageSkillSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ConfigurationResourceTab {
    CARDS,
    CONFIGS,
    EXTENSIONS,
}

data class ConfigurationResourceUiState(
    val snapshot: ConfigurationResourceSnapshot? = null,
    val packages: PackageSkillSnapshot? = null,
    val tab: ConfigurationResourceTab = ConfigurationResourceTab.CARDS,
    val selectedIds: Set<String> = emptySet(),
    val expandedCategories: Set<ExtensionCategory> = emptySet(),
    val isLoading: Boolean = false,
    val job: StorageJobState = StorageJobState(),
    val errorMessage: String? = null,
) {
    val displayedCards: List<ConfigurationResourceEntry> get() = snapshot?.cards.orEmpty()
    val displayedConfigs: List<ConfigurationResourceEntry> get() = snapshot?.configs.orEmpty()
    val extensionGroups: List<ExtensionCategoryGroup>
        get() = packages?.groups.orEmpty().filter { group ->
            group.category != ExtensionCategory.MCP_BRIDGE &&
                group.category != ExtensionCategory.THEME
        }
    val bridgeItems: List<ExtensionItem>
        get() = packages?.groups.orEmpty().firstOrNull { it.category == ExtensionCategory.MCP_BRIDGE }?.items.orEmpty()
    val selectedExtensionItems: List<ExtensionItem>
        get() {
            val selected = selectedIds
            return (packages?.items.orEmpty()).filter { item ->
                item.id in selected || item.category.name in selected
            }.distinctBy { it.id }
        }
    val selectedBytes: Long
        get() = when (tab) {
            ConfigurationResourceTab.CARDS -> displayedCards.filter { it.id in selectedIds && !it.locked }.sumOf { it.bytes }
            ConfigurationResourceTab.CONFIGS -> displayedConfigs.filter { it.id in selectedIds && !it.locked }.sumOf { it.bytes }
            ConfigurationResourceTab.EXTENSIONS -> selectedExtensionItems.sumOf { it.bytes }
        }
    val selectedCount: Int
        get() = when (tab) {
            ConfigurationResourceTab.CARDS -> displayedCards.count { it.id in selectedIds && !it.locked }
            ConfigurationResourceTab.CONFIGS -> displayedConfigs.count { it.id in selectedIds && !it.locked }
            ConfigurationResourceTab.EXTENSIONS -> selectedExtensionItems.size
        }
}

class ConfigurationResourceStorageViewModel(
    private val inventory: ConfigurationResourceInventory,
    private val packageInventory: PackageSkillInventory,
) : ViewModel() {
    private val _state = MutableStateFlow(ConfigurationResourceUiState(isLoading = true))
    val state: StateFlow<ConfigurationResourceUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.job.running) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                inventory.load() to packageInventory.load()
            }.onSuccess { (snapshot, packages) ->
                _state.update { it.copy(snapshot = snapshot, packages = packages, isLoading = false) }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                _state.update { it.copy(isLoading = false, errorMessage = error.displayMessage()) }
            }
        }
    }

    fun setTab(tab: ConfigurationResourceTab) {
        _state.update { it.copy(tab = tab, selectedIds = emptySet()) }
    }

    fun toggle(id: String, locked: Boolean) {
        if (locked || _state.value.job.running) return
        _state.update {
            val next = it.selectedIds.toMutableSet()
            if (!next.add(id)) next.remove(id)
            it.copy(selectedIds = next)
        }
    }

    fun toggleCategory(group: ExtensionCategoryGroup) {
        if (_state.value.job.running || group.items.isEmpty()) return
        _state.update {
            val next = it.selectedIds.toMutableSet()
            val itemIds = group.items.map { item -> item.id }
            val categoryKey = group.category.name
            if (categoryKey in next || itemIds.all { id -> id in next }) {
                next.remove(categoryKey)
                next.removeAll(itemIds.toSet())
            } else {
                next.add(categoryKey)
                next.addAll(itemIds)
            }
            it.copy(selectedIds = next)
        }
    }

    fun toggleItem(group: ExtensionCategoryGroup, item: ExtensionItem) {
        if (_state.value.job.running) return
        _state.update {
            val next = it.selectedIds.toMutableSet()
            if (!next.add(item.id)) next.remove(item.id)
            val allSelected = group.items.all { entry -> entry.id in next }
            if (allSelected) next.add(group.category.name) else next.remove(group.category.name)
            it.copy(selectedIds = next)
        }
    }

    fun toggleExpanded(category: ExtensionCategory) {
        _state.update {
            val next = it.expandedCategories.toMutableSet()
            if (!next.add(category)) next.remove(category)
            it.copy(expandedCategories = next)
        }
    }

    fun readPluginLogo(packageName: String): PackageManager.ToolPkgLogoBytes? {
        return packageInventory.readPluginLogo(packageName)
    }

    fun deleteSelected() {
        val current = _state.value
        if (current.selectedCount == 0 || current.job.running) return
        viewModelScope.launch {
            _state.update { it.copy(job = StorageJobState(running = true, total = current.selectedCount)) }
            val result = when (current.tab) {
                ConfigurationResourceTab.CARDS ->
                    inventory.delete(current.displayedCards.filter { it.id in current.selectedIds && !it.locked }, ::report)
                ConfigurationResourceTab.CONFIGS ->
                    inventory.delete(current.displayedConfigs.filter { it.id in current.selectedIds && !it.locked }, ::report)
                ConfigurationResourceTab.EXTENSIONS ->
                    packageInventory.delete(current.selectedExtensionItems, ::report)
            }
            _state.update {
                it.copy(
                    selectedIds = emptySet(),
                    job = StorageJobState(
                        running = false,
                        processed = result.deletedCount + result.failedCount,
                        total = current.selectedCount,
                        releasedBytes = result.releasedBytes,
                        failed = result.failedCount,
                        done = true,
                    ),
                )
            }
            refresh()
        }
    }

    private fun report(name: String, processed: Int, total: Int, released: Long) {
        _state.update {
            it.copy(job = it.job.copy(currentName = name, processed = processed, total = total, releasedBytes = released))
        }
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ConfigurationResourceStorageViewModel(
                ConfigurationResourceInventory(appContext),
                PackageSkillInventory(appContext),
            ) as T
        }
    }
}
