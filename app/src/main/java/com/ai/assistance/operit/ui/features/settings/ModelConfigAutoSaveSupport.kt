package com.ai.assistance.operit.ui.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll

private const val DEFAULT_MODEL_CONFIG_AUTO_SAVE_DEBOUNCE_MS = 700L

private val modelConfigSaveCoordinatorScope =
    CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

@Stable
class ModelConfigSaveCoordinator {
    private val saveActions = LinkedHashMap<String, suspend (Boolean) -> Unit>()
    private val backgroundFlushJobs = LinkedHashSet<Job>()
    private val backgroundFlushFailures = LinkedHashMap<String, Exception>()
    private val lock = Any()

    fun register(key: String, action: suspend (Boolean) -> Unit) {
        synchronized(lock) {
            saveActions[key] = action
        }
    }

    fun unregister(key: String) {
        synchronized(lock) {
            saveActions.remove(key)
        }
    }

    suspend fun flushAll(showSuccess: Boolean = false) {
        while (true) {
            val pendingJobs = synchronized(lock) { backgroundFlushJobs.toList() }
            if (pendingJobs.isEmpty()) {
                break
            }
            pendingJobs.joinAll()
        }

        val actions =
            synchronized(lock) {
                saveActions.entries.map { it.key to it.value }
            }
        var firstFailure: Exception? = null
        actions.forEach { (key, action) ->
            try {
                action(showSuccess)
                synchronized(lock) {
                    backgroundFlushFailures.remove(key)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                synchronized(lock) {
                    backgroundFlushFailures[key] = e
                }
                if (firstFailure == null) {
                    firstFailure = e
                }
            }
        }
        val unresolvedFailure =
            firstFailure ?: synchronized(lock) { backgroundFlushFailures.values.firstOrNull() }
        unresolvedFailure?.let { throw it }
    }

    fun flushAllInBackground(showSuccess: Boolean = false) {
        modelConfigSaveCoordinatorScope.launch {
            runCatching {
                flushAll(showSuccess)
            }
        }
    }

    fun flushInBackground(key: String, showSuccess: Boolean = false) {
        val action =
            synchronized(lock) {
                saveActions[key]
            } ?: return

        val job = modelConfigSaveCoordinatorScope.launch(start = CoroutineStart.LAZY) {
            try {
                action(showSuccess)
                synchronized(lock) {
                    backgroundFlushFailures.remove(key)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                synchronized(lock) {
                    backgroundFlushFailures[key] = e
                }
            }
        }
        synchronized(lock) {
            backgroundFlushJobs.add(job)
        }
        job.invokeOnCompletion {
            synchronized(lock) {
                backgroundFlushJobs.remove(job)
            }
        }
        job.start()
    }
}

@Composable
fun rememberModelConfigSaveCoordinator(): ModelConfigSaveCoordinator {
    return remember { ModelConfigSaveCoordinator() }
}

@Composable
fun RegisterModelConfigSaveAction(
    coordinator: ModelConfigSaveCoordinator,
    key: String,
    action: suspend (Boolean) -> Unit
) {
    val latestAction by rememberUpdatedState(action)

    DisposableEffect(coordinator, key) {
        coordinator.register(key) { showSuccess ->
            latestAction(showSuccess)
        }

        onDispose {
            coordinator.flushInBackground(key, showSuccess = false)
            coordinator.unregister(key)
        }
    }
}

@Composable
fun <T> DebouncedModelConfigAutoSaveEffect(
    effectKey: Any,
    valueProvider: () -> T,
    persist: suspend (T) -> Unit,
    onError: (Exception) -> Unit,
    debounceMillis: Long = DEFAULT_MODEL_CONFIG_AUTO_SAVE_DEBOUNCE_MS
) {
    val latestPersist by rememberUpdatedState(persist)
    val latestOnError by rememberUpdatedState(onError)

    androidx.compose.runtime.LaunchedEffect(effectKey) {
        snapshotFlow { valueProvider() }
            .drop(1)
            .debounce(debounceMillis)
            .distinctUntilChanged()
            .collectLatest { value ->
                try {
                    latestPersist(value)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    latestOnError(e)
                }
            }
    }
}
