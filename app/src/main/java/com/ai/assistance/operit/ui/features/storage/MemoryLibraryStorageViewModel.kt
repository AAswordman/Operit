package com.ai.assistance.operit.ui.features.storage

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.storage.MemoryFolderGroup
import com.ai.assistance.operit.data.storage.MemoryLibraryInventory
import com.ai.assistance.operit.data.storage.MemoryLibrarySnapshot
import com.ai.assistance.operit.data.storage.MemoryStorageEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MemoryLibraryUiState(
    val snapshot: MemoryLibrarySnapshot? = null,
    val selectedKeys: Set<String> = emptySet(),
    val expandedKeys: Set<String> = emptySet(),
    val profileFilter: String = ALL,
    val isLoading: Boolean = false,
    val job: StorageJobState = StorageJobState(),
    val errorMessage: String? = null,
) {
    val folders: List<MemoryFolderGroup>
        get() = snapshot?.folders.orEmpty().filter { profileFilter == ALL || it.profileId == profileFilter }

    val selectedEntries: List<MemoryStorageEntry>
        get() {
            val selected = selectedKeys
            return folders.flatMap { folder ->
                folder.entries.filter { entry ->
                    folder.key in selected || entry.key in selected
                }
            }.distinctBy { it.key }
        }

    val selectedBytes: Long get() = selectedEntries.sumOf { it.estimatedBytes }
    val selectedCount: Int get() = selectedEntries.size

    companion object {
        const val ALL = "all"
    }
}

val MemoryStorageEntry.key: String get() = "$profileId:$uuid"

class MemoryLibraryStorageViewModel(
    private val inventory: MemoryLibraryInventory,
) : ViewModel() {
    private val _state = MutableStateFlow(MemoryLibraryUiState(isLoading = true))
    val state: StateFlow<MemoryLibraryUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.job.running) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { inventory.load() }
                .onSuccess { snapshot ->
                    _state.update { it.copy(snapshot = snapshot, isLoading = false) }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _state.update { it.copy(isLoading = false, errorMessage = error.displayMessage()) }
                }
        }
    }

    fun setProfileFilter(profileId: String) {
        _state.update { it.copy(profileFilter = profileId, selectedKeys = emptySet(), expandedKeys = emptySet()) }
    }

    fun toggleFolder(folder: MemoryFolderGroup) {
        if (_state.value.job.running) return
        _state.update {
            val next = it.selectedKeys.toMutableSet()
            val entryKeys = folder.entries.map { entry -> entry.key }
            if (folder.key in next || entryKeys.all { key -> key in next }) {
                next.remove(folder.key)
                next.removeAll(entryKeys.toSet())
            } else {
                next.add(folder.key)
                next.addAll(entryKeys)
            }
            it.copy(selectedKeys = next)
        }
    }

    fun toggleEntry(folder: MemoryFolderGroup, entry: MemoryStorageEntry) {
        if (_state.value.job.running) return
        _state.update {
            val next = it.selectedKeys.toMutableSet()
            if (!next.add(entry.key)) next.remove(entry.key)
            val allSelected = folder.entries.all { item -> item.key in next }
            if (allSelected) next.add(folder.key) else next.remove(folder.key)
            it.copy(selectedKeys = next)
        }
    }

    fun toggleExpanded(folder: MemoryFolderGroup) {
        _state.update {
            val next = it.expandedKeys.toMutableSet()
            if (!next.add(folder.key)) next.remove(folder.key)
            it.copy(expandedKeys = next)
        }
    }

    fun deleteSelected() {
        val entries = _state.value.selectedEntries
        if (entries.isEmpty() || _state.value.job.running) return
        viewModelScope.launch {
            _state.update { it.copy(job = StorageJobState(running = true, total = entries.size)) }
            val result = inventory.delete(entries) { name, processed, total, released ->
                _state.update {
                    it.copy(job = it.job.copy(currentName = name, processed = processed, total = total, releasedBytes = released))
                }
            }
            _state.update {
                it.copy(
                    selectedKeys = emptySet(),
                    job = StorageJobState(
                        running = false,
                        processed = result.deletedCount + result.failedCount,
                        total = entries.size,
                        releasedBytes = result.releasedBytes,
                        failed = result.failedCount,
                        done = true,
                    ),
                )
            }
            refresh()
        }
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MemoryLibraryStorageViewModel(MemoryLibraryInventory(appContext)) as T
        }
    }
}
