package com.ai.assistance.operit.ui.features.storage

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.mnn.MnnModelDownloadManager
import com.ai.assistance.operit.data.storage.LocalModelDeleteOutcome
import com.ai.assistance.operit.data.storage.LocalModelEntry
import com.ai.assistance.operit.data.storage.LocalModelInventory
import com.ai.assistance.operit.data.storage.LocalModelKind
import com.ai.assistance.operit.data.storage.LocalModelRuntimeRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LocalModelFilter {
    ALL,
    MNN,
    LLAMA,
    SPEECH,
}

data class LocalModelStorageUiState(
    val models: List<LocalModelEntry> = emptyList(),
    val filter: LocalModelFilter = LocalModelFilter.ALL,
    val selectedIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val isDeleting: Boolean = false,
    val job: StorageJobState = StorageJobState(),
    val scannedAtMillis: Long? = null,
    val errorMessage: String? = null,
) {
    val displayedModels: List<LocalModelEntry>
        get() = when (filter) {
            LocalModelFilter.ALL -> models
            LocalModelFilter.MNN -> models.filter { it.kind == LocalModelKind.MNN }
            LocalModelFilter.LLAMA -> models.filter { it.kind == LocalModelKind.LLAMA }
            LocalModelFilter.SPEECH -> models.filter { it.kind == LocalModelKind.SPEECH }
        }

    val totalBytes: Long
        get() = displayedModels.sumOf { it.bytes }
}

class LocalModelStorageViewModel(
    private val inventory: LocalModelInventory,
    private val downloadManager: MnnModelDownloadManager,
) : ViewModel() {
    private val _state = MutableStateFlow(LocalModelStorageUiState(isLoading = true))
    val state: StateFlow<LocalModelStorageUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        observeRuntimeUsage()
        refresh()
    }

    fun setFilter(filter: LocalModelFilter) {
        _state.update { it.copy(filter = filter, selectedIds = emptySet()) }
    }

    fun toggle(entry: LocalModelEntry) {
        if (!entry.canDelete || _state.value.isDeleting || _state.value.job.running) return
        _state.update {
            val next = it.selectedIds.toMutableSet()
            if (!next.add(entry.id)) next.remove(entry.id)
            it.copy(selectedIds = next)
        }
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true, errorMessage = null) }
                try {
                    val models = inventory.listModels()
                    _state.update {
                        it.copy(
                            models = models,
                            isLoading = false,
                            scannedAtMillis = System.currentTimeMillis(),
                            errorMessage = null,
                        )
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage?.takeIf { message ->
                                message.isNotBlank()
                            } ?: error.javaClass.simpleName,
                        )
                    }
                }
            }
    }

    fun deleteSelected() {
        val selected = _state.value.displayedModels.filter { it.id in _state.value.selectedIds && it.canDelete }
        if (selected.isEmpty() || _state.value.isDeleting || _state.value.job.running) return
        viewModelScope.launch {
            var released = 0L
            var failed = 0
            _state.update {
                it.copy(
                    isDeleting = true,
                    errorMessage = null,
                    job = StorageJobState(running = true, total = selected.size),
                )
            }
            selected.forEachIndexed { index, entry ->
                _state.update {
                    it.copy(
                        job = it.job.copy(
                            currentName = entry.displayName,
                            processed = index,
                            total = selected.size,
                            releasedBytes = released,
                            failed = failed,
                            currentItemProgress = 0f,
                            currentItemDeletedBytes = 0L,
                            currentItemTotalBytes = entry.bytes,
                        ),
                    )
                }
                val outcome = runCatching {
                    inventory.delete(entry) { deletedBytes, totalBytes ->
                        val safeTotal = totalBytes.coerceAtLeast(1L)
                        _state.update { current ->
                            current.copy(
                                job = current.job.copy(
                                    currentItemProgress = (deletedBytes.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f),
                                    currentItemDeletedBytes = deletedBytes,
                                    currentItemTotalBytes = totalBytes,
                                ),
                            )
                        }
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                }.getOrNull()
                if (outcome == LocalModelDeleteOutcome.DELETED) {
                    released += entry.bytes
                    _state.update {
                        it.copy(
                            job = it.job.copy(
                                processed = index + 1,
                                currentItemProgress = 1f,
                                currentItemDeletedBytes = entry.bytes,
                                currentItemTotalBytes = entry.bytes,
                                releasedBytes = released,
                            ),
                        )
                    }
                } else {
                    failed++
                }
            }
            val models = runCatching { inventory.listModels() }.getOrDefault(_state.value.models)
            _state.update {
                it.copy(
                    models = models,
                    selectedIds = emptySet(),
                    isDeleting = false,
                    scannedAtMillis = System.currentTimeMillis(),
                    job = StorageJobState(
                        running = false,
                        processed = selected.size,
                        total = selected.size,
                        releasedBytes = released,
                        failed = failed,
                        done = true,
                    ),
                )
            }
        }
    }

    private fun observeRuntimeUsage() {
        viewModelScope.launch {
            LocalModelRuntimeRegistry.activePaths.collect {
                if (!_state.value.isDeleting && !_state.value.job.running) {
                    refresh()
                }
            }
        }
        viewModelScope.launch {
            downloadManager.downloadSnapshots
                .debounce(400)
                .collect {
                    if (!_state.value.isDeleting && !_state.value.job.running) {
                        refresh()
                    }
                }
        }
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LocalModelStorageViewModel::class.java)) {
                val downloadManager = MnnModelDownloadManager.getInstance(appContext)
                return LocalModelStorageViewModel(
                    LocalModelInventory(appContext, downloadManager),
                    downloadManager,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
