package com.ai.assistance.operit.ui.features.storage

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.storage.WorkspaceMediaEntry
import com.ai.assistance.operit.data.storage.WorkspaceMediaInventory
import com.ai.assistance.operit.data.storage.WorkspaceMediaSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WorkspaceMediaTab {
    WORKSPACES,
    MEDIA,
}

data class WorkspaceMediaUiState(
    val snapshot: WorkspaceMediaSnapshot? = null,
    val tab: WorkspaceMediaTab = WorkspaceMediaTab.WORKSPACES,
    val selectedIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val job: StorageJobState = StorageJobState(),
    val errorMessage: String? = null,
) {
    val displayed: List<WorkspaceMediaEntry>
        get() = if (tab == WorkspaceMediaTab.WORKSPACES) {
            snapshot?.workspaces.orEmpty()
        } else {
            snapshot?.media.orEmpty()
        }
    val selected: List<WorkspaceMediaEntry>
        get() = displayed.filter { it.id in selectedIds && !it.locked }
    val selectedBytes: Long get() = selected.sumOf { it.bytes }
}

class WorkspaceMediaStorageViewModel(
    private val inventory: WorkspaceMediaInventory,
) : ViewModel() {
    private val _state = MutableStateFlow(WorkspaceMediaUiState(isLoading = true))
    val state: StateFlow<WorkspaceMediaUiState> = _state.asStateFlow()

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

    fun setTab(tab: WorkspaceMediaTab) {
        _state.update { it.copy(tab = tab, selectedIds = emptySet()) }
    }

    fun toggle(entry: WorkspaceMediaEntry) {
        if (entry.locked || _state.value.job.running) return
        _state.update {
            val next = it.selectedIds.toMutableSet()
            if (!next.add(entry.id)) next.remove(entry.id)
            it.copy(selectedIds = next)
        }
    }

    fun deleteSelected() {
        val entries = _state.value.selected
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
                    selectedIds = emptySet(),
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
            return WorkspaceMediaStorageViewModel(WorkspaceMediaInventory(appContext)) as T
        }
    }
}
