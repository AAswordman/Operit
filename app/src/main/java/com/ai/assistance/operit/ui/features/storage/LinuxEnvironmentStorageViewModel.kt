package com.ai.assistance.operit.ui.features.storage

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.storage.LinuxEnvironmentInventory
import com.ai.assistance.operit.data.storage.LinuxEnvironmentSnapshot
import com.ai.assistance.operit.data.storage.LinuxStorageUnit
import com.ai.assistance.operit.data.storage.LinuxStorageUnitKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LinuxEnvironmentUiState(
    val snapshot: LinuxEnvironmentSnapshot? = null,
    val selectedKinds: Set<LinuxStorageUnitKind> = emptySet(),
    val isLoading: Boolean = false,
    val job: StorageJobState = StorageJobState(),
    val errorMessage: String? = null,
) {
    val units: List<LinuxStorageUnit> get() = snapshot?.units.orEmpty()
    val selectedUnits: List<LinuxStorageUnit>
        get() = units.filter { it.kind in selectedKinds && !it.locked && it.exists && it.bytes > 0L }
    val selectedBytes: Long get() = selectedUnits.sumOf { it.bytes }
}

class LinuxEnvironmentStorageViewModel(
    private val inventory: LinuxEnvironmentInventory,
) : ViewModel() {
    private val _state = MutableStateFlow(LinuxEnvironmentUiState(isLoading = true))
    val state: StateFlow<LinuxEnvironmentUiState> = _state.asStateFlow()

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
                            selectedKinds = it.selectedKinds.filter { kind ->
                                snapshot.units.any { unit -> unit.kind == kind && !unit.locked }
                            }.toSet(),
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _state.update { it.copy(isLoading = false, errorMessage = error.displayMessage()) }
                }
        }
    }

    fun toggle(unit: LinuxStorageUnit) {
        if (unit.locked || !unit.exists || unit.bytes <= 0L || _state.value.job.running) return
        _state.update {
            val next = it.selectedKinds.toMutableSet()
            if (!next.add(unit.kind)) next.remove(unit.kind)
            it.copy(selectedKinds = next)
        }
    }

    fun deleteSelected() {
        val units = _state.value.selectedUnits
        if (units.isEmpty() || _state.value.job.running) return
        viewModelScope.launch {
            var released = 0L
            var failed = 0
            units.forEachIndexed { index, unit ->
                _state.update {
                    it.copy(
                        job = StorageJobState(
                            running = true,
                            currentName = unit.path.absolutePath,
                            processed = index,
                            total = units.size,
                            releasedBytes = released,
                            failed = failed,
                        ),
                    )
                }
                val result = runCatching { inventory.delete(unit) }.getOrNull()
                if (result == null || result.failedCount > 0) {
                    failed++
                } else {
                    released += maxOf(result.releasedBytes, unit.bytes)
                }
            }
            _state.update {
                it.copy(
                    selectedKinds = emptySet(),
                    job = StorageJobState(
                        running = false,
                        processed = units.size,
                        total = units.size,
                        releasedBytes = released,
                        failed = failed,
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
            return LinuxEnvironmentStorageViewModel(LinuxEnvironmentInventory(appContext)) as T
        }
    }
}

internal fun Throwable.displayMessage(): String =
    localizedMessage?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
