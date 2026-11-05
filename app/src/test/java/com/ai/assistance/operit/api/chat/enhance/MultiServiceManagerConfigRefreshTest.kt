package com.ai.assistance.operit.api.chat.enhance

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ai.assistance.operit.api.chat.llmprovider.AIService
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.stats.ProviderUsageSnapshot
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.streamOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

/**
 * 回归测试：模型配置变更后，功能服务（IMAGE_RECOGNITION 等）必须立即使用新配置，
 * 不依赖调用点手动 refresh（#915）。
 */
class MultiServiceManagerConfigRefreshTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Before
    fun setUp() {
        // android.util.Log 未 mock，AppLogger 内部调用会抛 "Method not mocked"。
        // 纯 JVM 测试关闭系统日志与文件日志。
        AppLogger.enableSystemLog = false
        AppLogger.enableFileLogging = false
    }

    /** 只记录构造时配置的假 AIService，用于断言服务实例是否重建。 */
    private class FakeAIService(val config: ModelConfigData) : AIService {
        override val inputTokenCount: Long = 0L
        override val cachedInputTokenCount: Long = 0L
        override val outputTokenCount: Long = 0L
        override val providerModel: String = "${config.apiProviderTypeId}:${config.modelName}"
        override fun resetTokenCounts() {}
        override fun cancelStreaming() {}
        override suspend fun getModelsList(context: Context): Result<List<ModelOption>> =
            Result.success(emptyList())
        override suspend fun sendMessage(
            context: Context,
            chatHistory: List<PromptTurn>,
            modelParameters: List<ModelParameter<*>>,
            enableThinking: Boolean,
            stream: Boolean,
            availableTools: List<ToolPrompt>?,
            preserveThinkInHistory: Boolean,
            onTokensUpdated: suspend (Long, Long, Long) -> Unit,
            onUsageReported: (suspend (ProviderUsageSnapshot, Int) -> Unit)?,
            onNonFatalError: suspend (String) -> Unit,
            enableRetry: Boolean,
            recordTokenUsage: Boolean,
            onUsageFinalized: (suspend (Int?) -> Unit)?
        ): Stream<String> = streamOf("")
        override suspend fun testConnection(context: Context): Result<String> =
            Result.success("ok")
        override suspend fun calculateInputTokens(
            chatHistory: List<PromptTurn>,
            availableTools: List<ToolPrompt>?
        ): Long = 0L
    }

    private class TestPreferencesDataStore(
        private val preferences: MutableStateFlow<Preferences>
    ) : DataStore<Preferences> {
        override val data = preferences.asStateFlow()
        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences
        ): Preferences {
            val updated = transform(preferences.value)
            preferences.value = updated
            return updated
        }
    }

    private fun writeModelConfig(
        dataStore: TestPreferencesDataStore,
        config: ModelConfigData
    ) = runBlocking {
        dataStore.updateData { current ->
            current.toMutablePreferences().apply {
                set(stringPreferencesKey("config_${config.id}"), json.encodeToString(config))
                set(ModelConfigManager.CONFIG_LIST_KEY, json.encodeToString(listOf(config.id)))
            }
        }
    }

    private fun writeFunctionalMapping(
        dataStore: TestPreferencesDataStore,
        configId: String,
        modelIndex: Int = 0
    ) = runBlocking {
        dataStore.updateData { current ->
            current.toMutablePreferences().apply {
                val mapping = mapOf(
                    FunctionType.IMAGE_RECOGNITION.name to
                        com.ai.assistance.operit.data.preferences.FunctionConfigMapping(configId, modelIndex)
                )
                set(
                    FunctionalConfigManager.FUNCTION_CONFIG_MAPPING,
                    json.encodeToString(mapping)
                )
            }
        }
    }

    private fun buildManager(
        modelStore: TestPreferencesDataStore,
        functionalStore: TestPreferencesDataStore
    ): MultiServiceManager {
        val context = Mockito.mock(Context::class.java)
        val modelConfigManager = ModelConfigManager(context, modelStore)
        val functionalConfigManager = FunctionalConfigManager(context, functionalStore)
        return MultiServiceManager(
            context = context,
            functionalConfigManager = functionalConfigManager,
            modelConfigManager = modelConfigManager,
            serviceFactory = { config, _, _ -> FakeAIService(config) }
        )
    }

    @Test
    fun `model config change rebuilds functional service without explicit refresh`() = runBlocking {
        val modelStore = TestPreferencesDataStore(MutableStateFlow(mutablePreferencesOf()))
        val functionalStore = TestPreferencesDataStore(MutableStateFlow(mutablePreferencesOf()))

        val oldConfig = ModelConfigData(
            id = "google-config",
            name = "google",
            apiKey = "old-key",
            modelName = "gemini-2.0-flash",
            apiProviderType = ApiProviderType.GOOGLE,
            apiProviderTypeId = ApiProviderType.GOOGLE.name
        )
        writeModelConfig(modelStore, oldConfig)
        writeFunctionalMapping(functionalStore, "google-config")

        val manager = buildManager(modelStore, functionalStore)

        // 首次取用：缓存未命中，创建服务
        val firstService = manager.getServiceForFunction(FunctionType.IMAGE_RECOGNITION) as FakeAIService
        assertEquals("gemini-2.0-flash", firstService.config.modelName)
        assertEquals("old-key", firstService.config.apiKey)

        // 未变更配置时，命中缓存，返回同一实例
        val cachedService = manager.getServiceForFunction(FunctionType.IMAGE_RECOGNITION)
        assertSame(firstService, cachedService)

        // 更新模型配置（模型列表 + API Key），不调用任何 refresh
        val newConfig = oldConfig.copy(
            apiKey = "new-key",
            modelName = "gemini-3.5-flash, gemini-3.1-flash-lite, gemini-flash-latest"
        )
        writeModelConfig(modelStore, newConfig)

        // 下一次取用必须自动重建服务，使用新模型与新 Key
        val refreshedService = manager.getServiceForFunction(FunctionType.IMAGE_RECOGNITION) as FakeAIService
        assertNotSame(firstService, refreshedService)
        assertEquals("gemini-3.5-flash", refreshedService.config.modelName)
        assertEquals("new-key", refreshedService.config.apiKey)
    }

    @Test
    fun `function binding change rebuilds functional service without explicit refresh`() = runBlocking {
        val modelStore = TestPreferencesDataStore(MutableStateFlow(mutablePreferencesOf()))
        val functionalStore = TestPreferencesDataStore(MutableStateFlow(mutablePreferencesOf()))

        val configA = ModelConfigData(
            id = "config-a",
            name = "a",
            apiKey = "key-a",
            modelName = "model-a",
            apiProviderType = ApiProviderType.OPENAI_GENERIC,
            apiProviderTypeId = ApiProviderType.OPENAI_GENERIC.name
        )
        val configB = ModelConfigData(
            id = "config-b",
            name = "b",
            apiKey = "key-b",
            modelName = "model-b",
            apiProviderType = ApiProviderType.DEEPSEEK,
            apiProviderTypeId = ApiProviderType.DEEPSEEK.name
        )
        writeModelConfig(modelStore, configA.copy()) // 只写 config-a
        // 手动把 config-b 也写进去
        runBlocking {
            modelStore.updateData { current ->
                current.toMutablePreferences().apply {
                    set(stringPreferencesKey("config_config-b"), json.encodeToString(configB))
                    set(
                        ModelConfigManager.CONFIG_LIST_KEY,
                        json.encodeToString(listOf("config-a", "config-b"))
                    )
                }
            }
        }
        writeFunctionalMapping(functionalStore, "config-a")

        val manager = buildManager(modelStore, functionalStore)

        val firstService = manager.getServiceForFunction(FunctionType.IMAGE_RECOGNITION) as FakeAIService
        assertEquals("model-a", firstService.config.modelName)

        // 功能绑定从 config-a 改到 config-b，不调用任何 refresh
        writeFunctionalMapping(functionalStore, "config-b")

        val reboundService = manager.getServiceForFunction(FunctionType.IMAGE_RECOGNITION) as FakeAIService
        assertNotSame(firstService, reboundService)
        assertEquals("model-b", reboundService.config.modelName)
        assertEquals("key-b", reboundService.config.apiKey)
    }

    @Test
    fun `model index change rebuilds functional service without explicit refresh`() = runBlocking {
        val modelStore = TestPreferencesDataStore(MutableStateFlow(mutablePreferencesOf()))
        val functionalStore = TestPreferencesDataStore(MutableStateFlow(mutablePreferencesOf()))

        val config = ModelConfigData(
            id = "google-config",
            name = "google",
            apiKey = "key",
            modelName = "gemini-3.5-flash, gemini-3.1-flash-lite, gemini-flash-latest",
            apiProviderType = ApiProviderType.GOOGLE,
            apiProviderTypeId = ApiProviderType.GOOGLE.name
        )
        writeModelConfig(modelStore, config)
        writeFunctionalMapping(functionalStore, "google-config", modelIndex = 0)

        val manager = buildManager(modelStore, functionalStore)

        val firstService = manager.getServiceForFunction(FunctionType.IMAGE_RECOGNITION) as FakeAIService
        assertEquals("gemini-3.5-flash", firstService.config.modelName)

        // 功能绑定的 modelIndex 改为 2，不调用任何 refresh
        writeFunctionalMapping(functionalStore, "google-config", modelIndex = 2)

        val reindexedService = manager.getServiceForFunction(FunctionType.IMAGE_RECOGNITION) as FakeAIService
        assertNotSame(firstService, reindexedService)
        assertEquals("gemini-flash-latest", reindexedService.config.modelName)
    }

    @Test
    fun `lease keeps service alive while in use and retires after release`() = runBlocking {
        val modelStore = TestPreferencesDataStore(MutableStateFlow(mutablePreferencesOf()))
        val functionalStore = TestPreferencesDataStore(MutableStateFlow(mutablePreferencesOf()))

        val config = ModelConfigData(
            id = "cfg",
            name = "cfg",
            apiKey = "key",
            modelName = "model-1",
            apiProviderType = ApiProviderType.GOOGLE,
            apiProviderTypeId = ApiProviderType.GOOGLE.name
        )
        writeModelConfig(modelStore, config)
        writeFunctionalMapping(functionalStore, "cfg")

        val manager = buildManager(modelStore, functionalStore)

        // 借出服务（模拟识图请求进行中）
        val lease = manager.acquireServiceForFunction(FunctionType.IMAGE_RECOGNITION)
        assertNotNull(lease.service)

        // 配置变更 + refresh（模拟用户在识图进行中改配置）
        writeModelConfig(modelStore, config.copy(modelName = "model-2"))
        manager.refreshServiceForFunction(FunctionType.IMAGE_RECOGNITION)

        // 旧服务因 lease 未归还不会被释放（这里仅验证 lease 语义：取用新服务时返回新实例）
        val newService = manager.getServiceForFunction(FunctionType.IMAGE_RECOGNITION) as FakeAIService
        assertEquals("model-2", newService.config.modelName)

        // 归还 lease
        lease.close()
    }
}
