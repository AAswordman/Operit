package com.ai.assistance.operit.ui.features.storage

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.storage.ChatHistoryInventory
import com.ai.assistance.operit.data.storage.ChatHistorySnapshot
import com.ai.assistance.operit.data.storage.ChatStorageEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ChatStorageSort {
    SIZE_DESC,
    UPDATED_DESC,
    MESSAGES_DESC,
}

data class ChatHistoryStorageUiState(
    val snapshot: ChatHistorySnapshot? = null,
    val selectedIds: Set<String> = emptySet(),
    val sort: ChatStorageSort = ChatStorageSort.SIZE_DESC,
    val isLoading: Boolean = false,
    val job: StorageJobState = StorageJobState(),
    val errorMessage: String? = null,
) {
    val chats: List<ChatStorageEntry>
        get() {
            val items = snapshot?.chats.orEmpty()
            return when (sort) {
                ChatStorageSort.SIZE_DESC -> items.sortedByDescending { it.estimatedBytes }
                ChatStorageSort.UPDATED_DESC -> items.sortedByDescending { it.updatedAtMillis }
                ChatStorageSort.MESSAGES_DESC -> items.sortedByDescending { it.messageCount }
            }
        }
    val selected: List<ChatStorageEntry>
        get() = chats.filter { it.id in selectedIds && it.canDelete }
    val selectedBytes: Long get() = selected.sumOf { it.estimatedBytes }
}

class ChatHistoryStorageViewModel(
    private val inventory: ChatHistoryInventory,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatHistoryStorageUiState(isLoading = true))
    val state: StateFlow<ChatHistoryStorageUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.job.running) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { inventory.load() }
                .onSuccess { snapshot ->
                    _state.update {
                        it.copy(
                            snapshot = snapshot,
                            isLoading = false,
                            selectedIds = it.selectedIds.intersect(snapshot.chats.map { chat -> chat.id }.toSet()),
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _state.update { it.copy(isLoading = false, errorMessage = error.displayMessage()) }
                }
        }
    }

    fun setSort(sort: ChatStorageSort) {
        _state.update { it.copy(sort = sort) }
    }

    fun toggle(entry: ChatStorageEntry) {
        if (!entry.canDelete || _state.value.job.running) return
        _state.update {
            val next = it.selectedIds.toMutableSet()
            if (!next.add(entry.id)) next.remove(entry.id)
            it.copy(selectedIds = next)
        }
    }

    fun deleteSelected() {
        val ids = _state.value.selected.map { it.id }
        if (ids.isEmpty() || _state.value.job.running) return
        viewModelScope.launch {
            _state.update {
                it.copy(job = StorageJobState(running = true, total = ids.size))
            }
            val result = inventory.delete(ids) { name, processed, total, released ->
                _state.update {
                    it.copy(
                        job = it.job.copy(
                            currentName = name,
                            processed = processed,
                            total = total,
                            releasedBytes = released,
                        ),
                    )
                }
            }
            _state.update {
                it.copy(
                    selectedIds = emptySet(),
                    job = StorageJobState(
                        running = false,
                        processed = result.deletedCount + result.failedCount,
                        total = ids.size,
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
            return ChatHistoryStorageViewModel(ChatHistoryInventory(appContext)) as T
        }
    }
}
