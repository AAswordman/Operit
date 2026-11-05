package com.ai.assistance.operit.api.chat.runtime

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ai.assistance.operit.api.chat.AIService
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.tools.prompt.ToolPrompt
import com.ai.assistance.operit.data.config.model.ApiProviderType
import com.ai.assistance.operit.data.config.model.ModelConfigData
import com.ai.assistance.operit.data.config.model.ModelOption
import com.ai.assistance.operit.data.config.model.ModelParameter
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.preferences.FunctionConfigMapping
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.stats.ProviderUsageSnapshot
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.streamOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

/** 上游配置刷新回归适配：同时覆盖本地功能思考装饰器和取消时归还租约。 */
class MultiServiceManagerConfigRefreshTest {
    private var systemLog = false
    private var fileLog = false

    @Before
    fun disableAndroidLogging() {
        systemLog = AppLogger.enableSystemLog
        fileLog = AppLogger.enableFileLogging
        AppLogger.enableSystemLog = false
        AppLogger.enableFileLogging = false
    }

    @After
    fun restoreLogging() {
        AppLogger.enableSystemLog = systemLog
        AppLogger.enableFileLogging = fileLog
    }

    @Test
    fun `saved model endpoint and key changes rebuild the service while unchanged config reuses it`() = runBlocking {
        val f = Fixture()
        val config = config()
        f.put(config)
        f.bind(FunctionType.IMAGE_RECOGNITION, config.id)
        val first = f.manager.getServiceForFunction(FunctionType.IMAGE_RECOGNITION)
        assertSame(first, f.manager.getServiceForFunction(FunctionType.IMAGE_RECOGNITION))
        val updated = config.copy(modelName = "model-c", apiKey = "new-key", apiEndpoint = "https://new.example.invalid/v1")
        f.put(updated)
        val next = f.manager.getServiceForFunction(FunctionType.IMAGE_RECOGNITION)
        assertNotSame(first, next)
        assertEquals(updated, f.created.last().config)
        assertEquals(1, f.created.first().releases)
        assertEquals(2, f.created.size)
    }

    @Test
    fun `changing function binding takes the other saved config without refresh`() = runBlocking {
        val f = Fixture()
        val a = config("a")
        val b = config("b").copy(modelName = "other-model", apiProviderType = ApiProviderType.DEEPSEEK, apiProviderTypeId = ApiProviderType.DEEPSEEK.name)
        f.put(a)
        f.put(b)
        f.bind(FunctionType.IMAGE_RECOGNITION, a.id)
        val first = f.manager.getServiceForFunction(FunctionType.IMAGE_RECOGNITION)
        f.bind(FunctionType.IMAGE_RECOGNITION, b.id)
        assertNotSame(first, f.manager.getServiceForFunction(FunctionType.IMAGE_RECOGNITION))
        assertEquals(b, f.created.last().config)
    }

    @Test
    fun `changing bound model index selects the new model from the same config`() = runBlocking {
        val f = Fixture()
        val config = config().copy(modelName = "first, second, third")
        f.put(config)
        f.bind(FunctionType.IMAGE_RECOGNITION, config.id, 0)
        val first = f.manager.getServiceForFunction(FunctionType.IMAGE_RECOGNITION)
        assertEquals("first", f.created.last().config.modelName)
        f.bind(FunctionType.IMAGE_RECOGNITION, config.id, 2)
        assertNotSame(first, f.manager.getServiceForFunction(FunctionType.IMAGE_RECOGNITION))
        assertEquals("third", f.created.last().config.modelName)
    }

    @Test
    fun `default chat service also validates the persisted config`() = runBlocking {
        val f = Fixture()
        val config = config()
        f.put(config)
        f.bind(FunctionType.CHAT, config.id)
        val first = f.manager.getDefaultService()
        assertSame(first, f.manager.getDefaultService())
        f.put(config.copy(modelName = "new-chat-model"))
        val next = f.manager.getDefaultService()
        assertNotSame(first, next)
        assertSame(next, f.manager.getServiceForFunction(FunctionType.CHAT))
        assertEquals("new-chat-model", f.created.last().config.modelName)
    }

    @Test
    fun `custom config caches each index and refreshes its saved config on lookup`() = runBlocking {
        val f = Fixture()
        val config = config().copy(modelName = "first, second")
        f.put(config)
        val first = f.manager.getServiceForConfig(config.id, 0)
        val second = f.manager.getServiceForConfig(config.id, 1)
        assertSame(first, f.manager.getServiceForConfig(config.id, 0))
        assertSame(second, f.manager.getServiceForConfig(config.id, 1))
        f.put(config.copy(apiKey = "updated-key"))
        assertNotSame(first, f.manager.getServiceForConfig(config.id, 0))
        assertEquals("first", f.created.last().config.modelName)
        assertEquals("updated-key", f.created.last().config.apiKey)
        assertNotSame(second, f.manager.getServiceForConfig(config.id, 1))
        assertEquals("second", f.created.last().config.modelName)
        assertEquals("updated-key", f.created.last().config.apiKey)
    }

    @Test
    fun `retired service stays alive until all leases close and release is idempotent`() = runBlocking {
        val f = Fixture()
        val config = config()
        f.put(config)
        f.bind(FunctionType.IMAGE_RECOGNITION, config.id)
        val first = f.manager.acquireServiceForFunction(FunctionType.IMAGE_RECOGNITION)
        val second = f.manager.acquireServiceForFunction(FunctionType.IMAGE_RECOGNITION)
        val old = f.created.single()
        assertSame(first.service, second.service)
        f.put(config.copy(modelName = "new-model"))
        assertNotSame(first.service, f.manager.getServiceForFunction(FunctionType.IMAGE_RECOGNITION))
        assertEquals(0, old.releases)
        first.close()
        assertEquals(0, old.releases)
        second.close()
        assertEquals(1, old.releases)
        second.close()
        assertEquals(1, old.releases)
    }

    @Test
    fun `refreshing a non chat function also retires custom caches without interrupting leases`() = runBlocking {
        val f = Fixture()
        val config = config()
        f.put(config)
        f.bind(FunctionType.IMAGE_RECOGNITION, config.id)
        val lease = f.manager.acquireServiceForConfig(config.id, 0)
        val old = f.created.single()
        f.manager.refreshServiceForFunction(FunctionType.IMAGE_RECOGNITION)
        assertEquals(0, old.releases)
        assertNotSame(lease.service, f.manager.getServiceForConfig(config.id, 0))
        lease.close()
        assertEquals(1, old.releases)
    }

    @Test
    fun `failed replacement cannot leave a released service cached when the config changes back`() = runBlocking {
        val f = Fixture()
        val config = config()
        f.put(config)
        f.bind(FunctionType.IMAGE_RECOGNITION, config.id)
        val first = f.manager.getServiceForFunction(FunctionType.IMAGE_RECOGNITION)
        val old = f.created.single()
        f.put(config.copy(modelName = "unavailable-model"))
        f.failConstruction = true
        val failure = runCatching { f.manager.getServiceForFunction(FunctionType.IMAGE_RECOGNITION) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals(1, old.releases)
        f.failConstruction = false
        f.put(config)
        assertNotSame(first, f.manager.getServiceForFunction(FunctionType.IMAGE_RECOGNITION))
        assertEquals(2, f.created.size)
    }

    @Test
    fun `cancelled parameter loading returns the lease before the caller receives it`() = runBlocking {
        val f = Fixture()
        val config = config()
        f.put(config)
        f.bind(FunctionType.IMAGE_RECOGNITION, config.id)
        f.models.failOnCollection = 2
        val failure = runCatching { f.manager.acquireServiceForFunction(FunctionType.IMAGE_RECOGNITION) }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        f.models.failOnCollection = null
        f.manager.refreshServiceForFunction(FunctionType.IMAGE_RECOGNITION)
        assertEquals(1, f.created.single().releases)
    }

    @Test
    fun `functional thinking settings still reach the provider after a cache rebuild`() = runBlocking {
        val f = Fixture()
        val config = config()
        f.put(config)
        f.bind(FunctionType.IMAGE_RECOGNITION, config.id, thinking = true)
        f.manager.getServiceForFunction(FunctionType.IMAGE_RECOGNITION).sendMessage(context = f.context, enableThinking = false)
        assertEquals(true, f.created.last().thinking)
        f.put(config.copy(modelName = "updated-model"))
        f.manager.getServiceForFunction(FunctionType.IMAGE_RECOGNITION).sendMessage(context = f.context, enableThinking = false)
        assertEquals(true, f.created.last().thinking)
        assertEquals(2, f.created.size)
    }

    @Test
    fun `model flow decodes its emitted snapshot instead of collecting the store again`() = runBlocking {
        val first = config().copy(modelName = "snapshot-one")
        val later = first.copy(modelName = "snapshot-two")
        var collections = 0
        val snapshots = listOf(first, later).map { config ->
            mutablePreferencesOf(stringPreferencesKey("config_${config.id}") to Json.encodeToString(config))
        }
        val store = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow {
                val snapshot = snapshots[collections.coerceAtMost(1)]
                collections++
                emit(snapshot)
            }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                error("This read-only store must not be written")
        }
        val manager = ModelConfigManager(Mockito.mock(Context::class.java), store)
        assertEquals(first, manager.getModelConfigFlow(first.id).first())
        assertEquals(1, collections)
    }

    private fun config(id: String = "cfg") = ModelConfigData(
        id = id,
        name = id,
        modelName = "model-a",
        apiKey = "test-key",
        apiEndpoint = "https://example.invalid/v1",
        apiProviderType = ApiProviderType.OPENAI_GENERIC,
        apiProviderTypeId = ApiProviderType.OPENAI_GENERIC.name,
        requestLimitPerMinute = 0,
        maxConcurrentRequests = 0,
    )

    private class Fixture {
        val context = Mockito.mock(Context::class.java)
        val models = TestPreferencesDataStore()
        private val functions = TestPreferencesDataStore()
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val created = mutableListOf<FakeAIService>()
        var failConstruction = false
        val manager = MultiServiceManager(
            context,
            FunctionalConfigManager(context, functions),
            ModelConfigManager(context, models),
            serviceFactory = { config, _, _ ->
                check(!failConstruction) { "Service construction failed" }
                FakeAIService(config).also { created.add(it) }
            },
        )

        suspend fun put(config: ModelConfigData) {
            models.updateData { current ->
                current.toMutablePreferences().apply {
                    set(stringPreferencesKey("config_${config.id}"), json.encodeToString(config))
                    val ids = current[ModelConfigManager.CONFIG_LIST_KEY]?.let { json.decodeFromString<List<String>>(it) }.orEmpty()
                    set(ModelConfigManager.CONFIG_LIST_KEY, json.encodeToString((ids + config.id).distinct()))
                }
            }
        }

        suspend fun bind(type: FunctionType, configId: String, index: Int = 0, thinking: Boolean = false) {
            functions.updateData { current ->
                current.toMutablePreferences().apply {
                    set(FunctionalConfigManager.FUNCTION_CONFIG_MAPPING, json.encodeToString(
                        mapOf(type.name to FunctionConfigMapping(configId, index, enableThinking = thinking))
                    ))
                }
            }
        }
    }

    private class TestPreferencesDataStore : DataStore<Preferences> {
        private val preferences = MutableStateFlow<Preferences>(mutablePreferencesOf())
        var failOnCollection: Int? = null
        private var collections = 0
        override val data: Flow<Preferences> = flow {
            collections++
            if (collections == failOnCollection) throw CancellationException("Parameter loading cancelled")
            emitAll(preferences)
        }
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val next = transform(preferences.value)
            preferences.value = next
            return next
        }
    }

    private class FakeAIService(val config: ModelConfigData) : AIService {
        var releases = 0
        var thinking: Boolean? = null
        override val inputTokenCount = 0L
        override val cachedInputTokenCount = 0L
        override val outputTokenCount = 0L
        override val providerModel = "${config.apiProviderTypeId}:${config.modelName}"
        override fun resetTokenCounts() = Unit
        override fun cancelStreaming() = Unit
        override fun release() { releases++ }
        override suspend fun getModelsList(context: Context): Result<List<ModelOption>> = Result.success(emptyList())
        override suspend fun sendMessage(
            context: Context,
            chatHistory: List<PromptTurn>,
            modelParameters: List<ModelParameter<*>>,
            enableThinking: Boolean,
            thinkingOptionId: String?,
            stream: Boolean,
            availableTools: List<ToolPrompt>?,
            preserveThinkInHistory: Boolean,
            onTokensUpdated: suspend (Long, Long, Long) -> Unit,
            onUsageReported: (suspend (ProviderUsageSnapshot, Int) -> Unit)?,
            onNonFatalError: suspend (String) -> Unit,
            enableRetry: Boolean,
            recordTokenUsage: Boolean,
            onUsageFinalized: (suspend (Int?) -> Unit)?,
        ): Stream<String> {
            thinking = enableThinking
            return streamOf("")
        }
        override suspend fun testConnection(context: Context): Result<String> = Result.success("ok")
        override suspend fun calculateInputTokens(chatHistory: List<PromptTurn>, availableTools: List<ToolPrompt>?): Long = 0L
    }
}
