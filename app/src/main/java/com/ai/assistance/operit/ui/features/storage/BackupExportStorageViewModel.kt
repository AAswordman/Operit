package com.ai.assistance.operit.ui.features.storage

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.storage.BackupExportEntry
import com.ai.assistance.operit.data.storage.BackupExportInventory
import com.ai.assistance.operit.data.storage.BackupExportKind
import com.ai.assistance.operit.data.storage.BackupExportSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BackupExportTab {
    BACKUPS,
    EXPORTS,
}

data class BackupExportUiState(
    val snapshot: BackupExportSnapshot? = null,
    val tab: BackupExportTab = BackupExportTab.BACKUPS,
    val selectedIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val job: StorageJobState = StorageJobState(),
    val errorMessage: String? = null,
) {
    val displayed: List<BackupExportEntry>
        get() {
            val items = snapshot?.entries.orEmpty()
            return if (tab == BackupExportTab.EXPORTS) {
                items.filter { it.kind == BackupExportKind.EXPORT_FILE }
            } else {
                items.filter { it.kind != BackupExportKind.EXPORT_FILE }
            }
        }
    val selected: List<BackupExportEntry>
        get() = displayed.filter { it.id in selectedIds }
    val selectedBytes: Long get() = selected.sumOf { it.bytes }
}

class BackupExportStorageViewModel(
    private val inventory: BackupExportInventory,
) : ViewModel() {
    private val _state = MutableStateFlow(BackupExportUiState(isLoading = true))
    val state: StateFlow<BackupExportUiState> = _state.asStateFlow()

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

    fun setTab(tab: BackupExportTab) {
        _state.update { it.copy(tab = tab, selectedIds = emptySet()) }
    }

    fun toggle(entry: BackupExportEntry) {
        if (_state.value.job.running) return
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
            return BackupExportStorageViewModel(BackupExportInventory(appContext)) as T
        }
    }
}
