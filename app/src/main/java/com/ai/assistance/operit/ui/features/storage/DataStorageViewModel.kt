package com.ai.assistance.operit.ui.features.storage

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.storage.CleanupTarget
import com.ai.assistance.operit.data.storage.DataStorageCleanupResult
import com.ai.assistance.operit.data.storage.DataStorageRepository
import com.ai.assistance.operit.data.storage.DataStorageSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DataStorageUiState(
    val snapshot: DataStorageSnapshot? = null,
    val isScanning: Boolean = false,
    val isCleaning: Boolean = false,
    val errorMessage: String? = null,
    val cleanupResult: DataStorageCleanupResult? = null,
)

class DataStorageViewModel(
    private val repository: DataStorageRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DataStorageUiState())
    val state: StateFlow<DataStorageUiState> = _state.asStateFlow()

    private var scanJob: Job? = null

    init {
        val cachedSnapshot = repository.cachedSnapshot()
        if (cachedSnapshot != null) {
            _state.value = DataStorageUiState(snapshot = cachedSnapshot)
        } else {
            scan(forceRefresh = false)
        }
    }

    fun refresh() {
        if (_state.value.isCleaning) return
        scan(forceRefresh = true)
    }

    fun refreshIfInvalidated() {
        if (_state.value.isCleaning || _state.value.isScanning) return
        if (repository.cachedSnapshot() != null) return
        scan(forceRefresh = false)
    }

    private fun scan(forceRefresh: Boolean) {
        scanJob?.cancel()
        scanJob =
            viewModelScope.launch {
                _state.update { it.copy(isScanning = true, errorMessage = null) }
                try {
                    val snapshot =
                        if (forceRefresh) repository.refresh() else repository.scan()
                    _state.update {
                        it.copy(snapshot = snapshot, isScanning = false, errorMessage = null)
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    _state.update {
                        it.copy(isScanning = false, errorMessage = error.displayMessage())
                    }
                }
            }
    }

    fun cleanup(targets: Set<CleanupTarget>) {
        if (targets.isEmpty() || _state.value.isCleaning) return
        scanJob?.cancel()
        scanJob =
            viewModelScope.launch {
                _state.update {
                    it.copy(
                        isCleaning = true,
                        isScanning = false,
                        errorMessage = null,
                        cleanupResult = null,
                    )
                }
                try {
                    val result = repository.cleanup(targets)
                    val refreshedSnapshot =
                        try {
                            repository.refresh()
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (error: Exception) {
                            _state.update {
                                it.copy(
                                    isCleaning = false,
                                    errorMessage = error.displayMessage(),
                                    cleanupResult = result,
                                )
                            }
                            return@launch
                        }
                    _state.update {
                        it.copy(
                            snapshot = refreshedSnapshot,
                            isCleaning = false,
                            errorMessage = null,
                            cleanupResult = result,
                        )
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    _state.update {
                        it.copy(isCleaning = false, errorMessage = error.displayMessage())
                    }
                }
            }
    }

    private fun Exception.displayMessage(): String =
        localizedMessage?.takeIf { it.isNotBlank() } ?: javaClass.simpleName

    fun consumeCleanupResult() {
        _state.update { it.copy(cleanupResult = null) }
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DataStorageViewModel::class.java)) {
                return DataStorageViewModel(DataStorageRepository(appContext)) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}