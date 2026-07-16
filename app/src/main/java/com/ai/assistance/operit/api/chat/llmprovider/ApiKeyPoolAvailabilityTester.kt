package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.data.model.ApiKeyAvailabilityStatus
import com.ai.assistance.operit.data.model.ApiKeyInfo
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.getModelByIndex
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.min

data class ApiKeyPoolTestState(
    val totalToTest: Int = 0,
    val tested: Int = 0,
    val available: Int = 0,
    val unavailable: Int = 0,
    val running: Boolean = false,
    val paused: Boolean = false,
    val lastError: String? = null
)

class ApiKeyPoolAvailabilityTester(
    private val configId: String,
    private val configManager: ModelConfigManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(ApiKeyPoolTestState())
    val state: StateFlow<ApiKeyPoolTestState> = _state.asStateFlow()

    private var job: Job? = null

    fun pause() {
        job?.cancel()
    }

    suspend fun pauseAndJoin() {
        job?.cancelAndJoin()
    }

    fun close() {
        scope.cancel()
    }

    fun isRunning(): Boolean = job?.isActive == true

    fun startOrResume(
        context: Context,
        baseConfig: ModelConfigData,
        useMultipleApiKeys: Boolean,
        apiKeyPool: List<ApiKeyInfo>,
        concurrency: Int,
        onPoolUpdated: (List<ApiKeyInfo>) -> Unit
    ) {
        if (isRunning()) return

        val keysToTest = apiKeyPool
            .filter { it.isEnabled }
            .filter { it.availabilityStatus == ApiKeyAvailabilityStatus.UNTESTED }

        if (keysToTest.isEmpty()) {
            _state.update {
                it.copy(
                    totalToTest = 0,
                    tested = 0,
                    available = 0,
                    unavailable = 0,
                    running = false,
                    paused = false,
                    lastError = null
                )
            }
            return
        }

        _state.update {
            it.copy(
                totalToTest = keysToTest.size,
                tested = 0,
                available = 0,
                unavailable = 0,
                running = true,
                paused = false,
                lastError = null
            )
        }

        job = scope.launch {
            try {
                val channel = Channel<ApiKeyInfo>(Channel.UNLIMITED)
                keysToTest.forEach { channel.trySend(it) }
                channel.close()

                var workingPool = apiKeyPool
                val poolUpdateMutex = Mutex()

                val workerCount = maxOf(1, min(concurrency, keysToTest.size))
                val workers = (0 until workerCount).map {
                    launch {
                        for (keyInfo in channel) {
                            val status =
                                if (
                                    testSingleKey(
                                        context = context,
                                        baseConfig = baseConfig,
                                        apiKey = keyInfo.key
                                    )
                                ) {
                                    ApiKeyAvailabilityStatus.AVAILABLE
                                } else {
                                    ApiKeyAvailabilityStatus.UNAVAILABLE
                                }

                            poolUpdateMutex.withLock {
                                workingPool = workingPool.map { existing ->
                                    if (existing.id == keyInfo.id) {
                                        existing.copy(availabilityStatus = status)
                                    } else {
                                        existing
                                    }
                                }

                                withContext(Dispatchers.Main) {
                                    onPoolUpdated(workingPool)
                                }

                                configManager.updateApiKeyPoolSettings(
                                    configId = configId,
                                    useMultipleApiKeys = useMultipleApiKeys,
                                    apiKeyPool = workingPool
                                )
                            }

                            _state.update { current ->
                                current.copy(
                                    tested = current.tested + 1,
                                    available = current.available + if (status == ApiKeyAvailabilityStatus.AVAILABLE) 1 else 0,
                                    unavailable = current.unavailable + if (status == ApiKeyAvailabilityStatus.UNAVAILABLE) 1 else 0
                                )
                            }
                        }
                    }
                }

                workers.joinAll()

                _state.update { it.copy(running = false, paused = false) }
            } catch (e: CancellationException) {
                _state.update { it.copy(running = false, paused = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        running = false,
                        paused = false,
                        lastError = e.message ?: e::class.java.simpleName
                    )
                }
            }
        }
    }

    private suspend fun testSingleKey(
        context: Context,
        baseConfig: ModelConfigData,
        apiKey: String
    ): Boolean {
        return kotlin.runCatching {
            val modelNameToTest = getModelByIndex(baseConfig.modelName, 0)
            val configForTest = baseConfig.copy(
                apiKey = apiKey,
                modelName = modelNameToTest,
                useMultipleApiKeys = false,
                apiKeyPool = emptyList()
            )

            val service = AIServiceFactory.createService(
                config = configForTest,
                modelConfigManager = configManager,
                context = context
            )

            try {
                val result = service.testConnection(context)
                result.getOrThrow()
            } finally {
                service.release()
            }
        }.isSuccess
    }
}
