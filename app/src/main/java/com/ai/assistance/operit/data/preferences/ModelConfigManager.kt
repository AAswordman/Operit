package com.ai.assistance.operit.data.preferences

import android.content.Context
import com.ai.assistance.operit.api.chat.llmprovider.ThinkingQualityMappingRegistry
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.R
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ai.assistance.operit.data.collects.ModelThinkingConfigDefaults
import com.ai.assistance.operit.data.model.CustomParameterData
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.ModelConfigDefaults
import com.ai.assistance.operit.data.model.ModelConfigSummary
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ParameterCategory
import com.ai.assistance.operit.data.model.ParameterValueType
import com.ai.assistance.operit.data.model.StandardModelParameters
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ApiKeyInfo
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

// 为ModelConfig创建专用的DataStore
private val Context.modelConfigDataStore: DataStore<Preferences> by
        versionedPreferencesDataStore(
                name = "model_configs",
                currentVersion = 4,

        ) { appContext ->
            preferenceSchemaMigration { version, preferences ->
                when (version) {
                    0 -> ModelConfigManager.migratePreferencesFromVersionZero(appContext, preferences)
                    1 -> ModelConfigManager.migratePreferencesFromVersionOne(preferences)
                    2 -> ModelConfigManager.migratePreferencesFromVersionTwo(preferences)
                    3 -> ModelConfigManager.migratePreferencesFromVersionThree(preferences)
                    else -> missingPreferencesSchemaMigration(version)

                }
            }
        }

class ModelConfigManager(
        private val context: Context,
        configDataStore: DataStore<Preferences> = context.modelConfigDataStore
) {

    private val configDataStore = configDataStore

    // 提供context访问器
    val appContext: Context
        get() = context

    // 定义key
    companion object {
        // 配置相关key
        val CONFIG_LIST_KEY = stringPreferencesKey("config_list")

        // 默认值
        const val DEFAULT_CONFIG_ID = "default"
        const val DEFAULT_CONFIG_NAME = "model_config_default_name"

        // Default API provider type
        private val DEFAULT_API_PROVIDER_TYPE = ApiProviderType.DEEPSEEK
        private const val OPENAI_CHAT_REASONING_EFFORT_RULE_ID = "openai-chat-reasoning-effort"
        private val OPENAI_CHAT_PROVIDER_TYPES =
                setOf(ApiProviderType.OPENAI.name, ApiProviderType.OPENAI_GENERIC.name)
        private val OPENAI_CHAT_MATCHER_FIELDS =
                setOf(
                        "match",
                        "modelPrefix",
                        "modelContains",
                        "modelSuffix",
                        "modelRegex",
                        "firstSegment",
                        "lastSegmentPrefix",
                        "lastSegmentContains",
                        "lastSegmentRegex",
                )

        internal val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        internal fun migratePreferencesFromVersionZero(
                context: Context,
                preferences: MutablePreferences
        ) {
            val configList = preferences[CONFIG_LIST_KEY]?.let { json.decodeFromString<List<String>>(it) }
                    ?: emptyList()
            if (configList.isNotEmpty()) return

            val defaultConfigKey = stringPreferencesKey("config_${DEFAULT_CONFIG_ID}")
            if (preferences[defaultConfigKey].isNullOrBlank()) {
                val defaultConfig = createFreshDefaultConfig(context)
                preferences[defaultConfigKey] = json.encodeToString(defaultConfig)
            }
            preferences[CONFIG_LIST_KEY] = json.encodeToString(listOf(DEFAULT_CONFIG_ID))
        }

        internal fun createFreshDefaultConfig(context: Context): ModelConfigData {
            return ModelConfigData(
                    id = DEFAULT_CONFIG_ID,
                    name = context.getString(R.string.model_config_default_name),
                    apiKey = "",
                    apiEndpoint = ApiPreferences.DEFAULT_API_ENDPOINT,
                    modelName = ApiPreferences.DEFAULT_MODEL_NAME,
                    apiProviderType = DEFAULT_API_PROVIDER_TYPE,
                    apiProviderTypeId = DEFAULT_API_PROVIDER_TYPE.name,
                    enableToolCall = ModelConfigDefaults.DEFAULT_ENABLE_TOOL_CALL,
                    hasCustomParameters = false,
                    maxTokensEnabled = false,
                    temperatureEnabled = false,
                    topPEnabled = false,
                    topKEnabled = false,
                    presencePenaltyEnabled = false,
                    frequencyPenaltyEnabled = false,
                    repetitionPenaltyEnabled = false,
                    maxTokens = StandardModelParameters.DEFAULT_MAX_TOKENS,
                    temperature = StandardModelParameters.DEFAULT_TEMPERATURE,
                    topP = StandardModelParameters.DEFAULT_TOP_P,
                    topK = StandardModelParameters.DEFAULT_TOP_K,
                    presencePenalty = StandardModelParameters.DEFAULT_PRESENCE_PENALTY,
                    frequencyPenalty = StandardModelParameters.DEFAULT_FREQUENCY_PENALTY,
                    repetitionPenalty = StandardModelParameters.DEFAULT_REPETITION_PENALTY,
                    customParameters = "[]",
                    thinkingConfigurations = thinkingRulesForProvider(DEFAULT_API_PROVIDER_TYPE.name),
                    thinkingOptionId = firstThinkingOptionIdForModel(
                            DEFAULT_API_PROVIDER_TYPE.name,
                            ApiPreferences.DEFAULT_MODEL_NAME,
                            ApiPreferences.DEFAULT_API_ENDPOINT
                    )
            )
        }

        internal fun migratePreferencesFromVersionOne(preferences: MutablePreferences) {
            val configIds = preferences[CONFIG_LIST_KEY]?.let { json.decodeFromString<List<String>>(it) }
                    ?: emptyList()

            configIds.forEach { configId ->
                val configKey = stringPreferencesKey("config_${configId}")
                val configJson = preferences[configKey] ?: return@forEach
                val config = json.decodeFromString<ModelConfigData>(configJson)
                val currentThinkingConfigurations = config.thinkingConfigurations.trim()
                val thinkingConfigurations =
                        if (currentThinkingConfigurations.isNotEmpty() && currentThinkingConfigurations != "[]") {
                            config.thinkingConfigurations
                        } else {
                            thinkingRulesForProvider(config.apiProviderTypeId)
                        }
                val thinkingOptionId =
                        if (currentThinkingConfigurations.isNotEmpty() && currentThinkingConfigurations != "[]") {
                            config.thinkingOptionId
                        } else {
                            firstThinkingOptionIdForModel(
                                    config.apiProviderTypeId,
                                    config.modelName,
                                    config.apiEndpoint
                            )
                        }
                if (thinkingConfigurations == config.thinkingConfigurations &&
                                thinkingOptionId == config.thinkingOptionId) {
                    return@forEach
                }
                preferences[configKey] =
                        json.encodeToString(
                                config.copy(
                                        thinkingConfigurations = thinkingConfigurations,
                                        thinkingOptionId = thinkingOptionId
                                )
                        )
            }
        }

        internal fun migratePreferencesFromVersionTwo(preferences: MutablePreferences) {
            val configIds = preferences[CONFIG_LIST_KEY]?.let { json.decodeFromString<List<String>>(it) }
                    ?: emptyList()

            configIds.forEach { configId ->
                val configKey = stringPreferencesKey("config_${configId}")
                val configJson = preferences[configKey] ?: return@forEach
                val config = json.decodeFromString<ModelConfigData>(configJson)
                val providerTypeId = config.apiProviderTypeId.trim().uppercase(Locale.US)
                if (providerTypeId !in OPENAI_CHAT_PROVIDER_TYPES) {
                    return@forEach
                }

                val migratedThinkingConfigurations =
                        migrateOpenAiChatThinkingConfigurations(
                                providerTypeId,
                                config.thinkingConfigurations
                        )
                if (migratedThinkingConfigurations == config.thinkingConfigurations) {
                    return@forEach
                }

                // Version 2 stored OpenAI Chat's built-in reasoning rule as model-agnostic.
                // Version 3 narrows the built-in rule set while leaving custom rules in place.
                val migratedMapping =
                        ThinkingQualityMappingRegistry.resolve(
                                config.apiProviderTypeId,
                                config.modelName,
                                migratedThinkingConfigurations
                        )
                val migratedThinkingOptionId =
                        if (migratedMapping.optionFor(config.thinkingOptionId) != null) {
                            config.thinkingOptionId
                        } else {
                            migratedMapping.options.firstOrNull()?.id.orEmpty()
                        }

                preferences[configKey] =
                        json.encodeToString(
                                config.copy(
                                        thinkingConfigurations = migratedThinkingConfigurations,
                                        thinkingOptionId = migratedThinkingOptionId
                                )
                        )
            }
        }

        private fun migrateOpenAiChatThinkingConfigurations(
                providerTypeId: String,
                thinkingConfigurations: String
        ): String {
            val sourceRules = thinkingRulesJsonArray(thinkingConfigurations)
            val currentRules = thinkingRulesJsonArray(thinkingRulesForProvider(providerTypeId))
            val existingCurrentRuleIds = currentRuleIds(sourceRules)
            val targetRules = JSONArray()
            var changed = false

            for (index in 0 until sourceRules.length()) {
                val sourceRule = sourceRules.optJSONObject(index)
                if (sourceRule == null) {
                    targetRules.put(sourceRules.get(index))
                    continue
                }

                if (isLegacyOpenAiChatReasoningRule(sourceRule)) {
                    for (currentIndex in 0 until currentRules.length()) {
                        val currentRule = currentRules.optJSONObject(currentIndex) ?: continue
                        val currentRuleId = currentRule.optString("id", "").trim()
                        if (
                                currentRuleId !in existingCurrentRuleIds &&
                                        !isLegacyOpenAiChatReasoningRule(currentRule)
                        ) {
                            targetRules.put(JSONObject(currentRule.toString()))
                        }
                    }
                    changed = true
                } else {
                    targetRules.put(JSONObject(sourceRule.toString()))
                }
            }

            return if (changed) targetRules.toString() else thinkingConfigurations
        }

        private fun currentRuleIds(rules: JSONArray): Set<String> {
            val ruleIds = mutableSetOf<String>()
            for (index in 0 until rules.length()) {
                val rule = rules.optJSONObject(index) ?: continue
                val ruleId = rule.optString("id", "").trim()
                if (ruleId.isNotEmpty() && !isLegacyOpenAiChatReasoningRule(rule)) {
                    ruleIds.add(ruleId)
                }
            }
            return ruleIds
        }

        private fun isLegacyOpenAiChatReasoningRule(rule: JSONObject): Boolean {
            // The built-in id has been superseded by model-specific rules (gpt5/o-series and
            // the generic reasoning family). Any rule carrying this id is stale, regardless of
            // whether it already carries a matcher, and must be replaced by the current set.
            return rule.optString("id", "").trim() == OPENAI_CHAT_REASONING_EFFORT_RULE_ID
        }

        private fun thinkingRulesJsonArray(thinkingConfigurations: String): JSONArray {
            val text = thinkingConfigurations.trim().ifEmpty { "[]" }
            return when {
                text.startsWith("[") -> JSONArray(text)
                text.startsWith("{") -> {
                    val objectValue = JSONObject(text)
                    objectValue.optJSONArray("rules") ?: JSONArray().put(objectValue)
                }
                else -> JSONArray(text)
            }
        }

        internal fun migratePreferencesFromVersionThree(preferences: MutablePreferences) {
            val configIds = preferences[CONFIG_LIST_KEY]?.let { json.decodeFromString<List<String>>(it) }
                ?: emptyList()

            configIds.forEach { configId ->
                val configKey = stringPreferencesKey("config_" + configId)
                val configJson = preferences[configKey] ?: return@forEach
                val config = runCatching { json.decodeFromString<ModelConfigData>(configJson) }
                    .getOrNull()
                    ?: return@forEach
                if (!isDeepSeekProvider(config.apiProviderTypeId)) return@forEach

                val thinkingConfigurations = ensureDeepSeekResponsesRule(config.thinkingConfigurations)
                val thinkingOptionId = runCatching {
                    val mapping = ThinkingQualityMappingRegistry.resolve(
                        providerTypeId = config.apiProviderTypeId,
                        modelName = config.modelName,
                        apiEndpoint = config.apiEndpoint,
                        thinkingConfigurations = thinkingConfigurations,
                    )
                    mapping.optionFor(config.thinkingOptionId)?.id
                        ?: mapping.defaultOption()?.id
                        ?: config.thinkingOptionId
                }.getOrDefault(config.thinkingOptionId)

                if (thinkingConfigurations == config.thinkingConfigurations &&
                    thinkingOptionId == config.thinkingOptionId
                ) {
                    return@forEach
                }

                preferences[configKey] = json.encodeToString(
                    config.copy(
                        thinkingConfigurations = thinkingConfigurations,
                        thinkingOptionId = thinkingOptionId,
                    )
                )
            }
        }

        private const val DEEPSEEK_RESPONSES_RULE_ID = "deepseek-responses-reasoning-effort"


        private fun isDeepSeekProvider(providerTypeId: String): Boolean =
                providerTypeId.equals(ApiProviderType.DEEPSEEK.name, ignoreCase = true)

        private fun isDeepSeekResponsesEndpoint(apiEndpoint: String): Boolean =
                apiEndpoint.trim()
                        .substringBefore('?')
                        .substringBefore('#')
                        .trimEnd('/')
                        .endsWith("/responses", ignoreCase = true)

        private fun thinkingRulesArray(raw: String): JSONArray? {
            val text = raw.trim().ifEmpty { "[]" }
            return runCatching {
                when {
                    text.startsWith("[") -> JSONArray(text)
                    text.startsWith("{") -> {
                        val objectValue = JSONObject(text)
                        objectValue.optJSONArray("rules") ?: JSONArray().put(objectValue)
                    }
                    else -> JSONArray(text)
                }
            }.getOrNull()
        }

        private fun hasDeepSeekResponsesRule(raw: String): Boolean {
            val rules = thinkingRulesArray(raw) ?: return false
            for (index in 0 until rules.length()) {
                val rule = rules.optJSONObject(index) ?: continue
                if (rule.optString("id", "") == DEEPSEEK_RESPONSES_RULE_ID) return true
                val match = rule.optJSONObject("match")
                val values = listOf(match?.opt("endpointSuffix"), rule.opt("endpointSuffix"))
                if (values.any { value ->
                            when (value) {
                                is JSONArray -> (0 until value.length()).any {
                                    isDeepSeekResponsesEndpoint(value.optString(it, ""))
                                }
                                is String -> isDeepSeekResponsesEndpoint(value)
                                else -> false
                            }
                        }) {
                    return true
                }
            }
            return false
        }
        private fun ensureDeepSeekResponsesRule(raw: String): String {
            val rules = thinkingRulesArray(raw) ?: return raw
            val defaults = thinkingRulesArray(
                    ModelThinkingConfigDefaults.forProvider(ApiProviderType.DEEPSEEK.name)
            ) ?: return raw
            val defaultResponseRule = (0 until defaults.length())
                    .mapNotNull { defaults.optJSONObject(it) }
                    .firstOrNull { it.optString("id", "") == DEEPSEEK_RESPONSES_RULE_ID }
                    ?: return raw

            var responseRuleIndex = -1
            var responseRule: JSONObject? = null
            for (index in 0 until rules.length()) {
                val rule = rules.optJSONObject(index) ?: continue
                if (rule.optString("id", "") == DEEPSEEK_RESPONSES_RULE_ID) {
                    responseRuleIndex = index
                    responseRule = rule
                    break
                }
            }

            if (responseRule == null && hasDeepSeekResponsesRule(raw)) {
                for (index in 0 until rules.length()) {
                    val rule = rules.optJSONObject(index) ?: continue
                    val match = rule.optJSONObject("match")
                    val values = listOf(match?.opt("endpointSuffix"), rule.opt("endpointSuffix"))
                    if (values.any { value ->
                                when (value) {
                                    is JSONArray -> (0 until value.length()).any {
                                        isDeepSeekResponsesEndpoint(value.optString(it, ""))
                                    }
                                    is String -> isDeepSeekResponsesEndpoint(value)
                                    else -> false
                                }
                            }) {
                        responseRuleIndex = index
                        responseRule = rule
                        break
                    }
                }
            }

            val updated = JSONArray().put(
                    JSONObject((responseRule ?: defaultResponseRule).toString())
            )
            for (index in 0 until rules.length()) {
                if (index == responseRuleIndex) continue
                rules.optJSONObject(index)?.let { updated.put(JSONObject(it.toString())) }
            }
            return updated.toString()
        }


        internal fun thinkingRulesForProvider(providerTypeId: String): String =
                ModelThinkingConfigDefaults.forProvider(providerTypeId)

        internal fun firstThinkingOptionIdForProvider(
                providerTypeId: String,
                apiEndpoint: String = ""
        ): String = firstThinkingOptionIdForModel(providerTypeId, "", apiEndpoint)

        internal fun firstThinkingOptionIdForModel(
                providerTypeId: String,
                modelName: String,
                apiEndpoint: String = ""
        ): String {
                val rules = thinkingRulesForProvider(providerTypeId)
                return firstThinkingOptionIdForRules(providerTypeId, modelName, rules, apiEndpoint)
        }

        internal fun firstThinkingOptionIdForRules(
                providerTypeId: String,
                modelName: String,
                thinkingConfigurations: String,
                apiEndpoint: String = ""
        ): String {
                return ThinkingQualityMappingRegistry
                        .resolve(providerTypeId, modelName, apiEndpoint, thinkingConfigurations)
                        .defaultOption()
                        ?.id
                        .orEmpty()

        }

        internal fun nextThinkingRulesForProvider(
                current: ModelConfigData,
                providerTypeId: String,
                apiEndpoint: String = ""
        ): String =
                if (current.apiProviderTypeId == providerTypeId) {
                    if (isDeepSeekProvider(providerTypeId) && isDeepSeekResponsesEndpoint(apiEndpoint)) {
                        ensureDeepSeekResponsesRule(current.thinkingConfigurations)
                    } else {
                        current.thinkingConfigurations
                    }
                } else {
                    thinkingRulesForProvider(providerTypeId)
                }

        internal fun nextThinkingOptionIdForProvider(
                current: ModelConfigData,
                providerTypeId: String,
                modelName: String,
                apiEndpoint: String = ""
        ): String =
                // Keep a model's choice while editing it; built-in defaults apply only on provider changes.
                if (current.apiProviderTypeId == providerTypeId) {
                    val switchedToDeepSeekResponses =
                            isDeepSeekProvider(providerTypeId) &&
                                    isDeepSeekResponsesEndpoint(apiEndpoint) &&
                                    !isDeepSeekResponsesEndpoint(current.apiEndpoint)
                    if (switchedToDeepSeekResponses) {
                        firstThinkingOptionIdForModel(providerTypeId, modelName, apiEndpoint)
                    } else {
                        current.thinkingOptionId
                    }
                } else {
                    firstThinkingOptionIdForModel(providerTypeId, modelName, apiEndpoint)
                }
    }

    // Json解析器，支持宽松模式
    private val json = ModelConfigManager.json

    // 获取所有配置ID列表
    val configListFlow: Flow<List<String>> =
            configDataStore.data.map { preferences ->
                val configList = preferences[CONFIG_LIST_KEY] ?: ""
                if (configList.isEmpty()) emptyList()
                else json.decodeFromString<List<String>>(configList)
            }.flowOn(Dispatchers.IO)

    // 所有配置摘要的响应式版本。主界面模型选择器必须持续收集它，才能在配置页
    // 新增或修改模型后立即更新，而不需要重新进入配置页。
    val configSummariesFlow: Flow<List<ModelConfigSummary>> =
            configDataStore.data.map { preferences ->
                readConfigSummariesFromPrefs(preferences)
            }

    private fun readConfigListFromPrefs(prefs: Preferences): List<String> {
        val configList = prefs[CONFIG_LIST_KEY] ?: ""
        return if (configList.isEmpty()) emptyList()
        else json.decodeFromString<List<String>>(configList)
    }

    private fun readConfigSummariesFromPrefs(prefs: Preferences): List<ModelConfigSummary> {
        return readConfigListFromPrefs(prefs).map { configId ->
            val configJson = prefs[stringPreferencesKey("config_${configId}")]
            val config =
                    if (configJson != null) {
                        try {
                            json.decodeFromString<ModelConfigData>(configJson)
                        } catch (_: Exception) {
                            fallbackConfigFor(configId)
                        }
                    } else {
                        fallbackConfigFor(configId)
                    }
            ModelConfigSummary(
                    id = config.id,
                    name = config.name,
                    modelName = config.modelName,
                    apiEndpoint = config.apiEndpoint,
                    apiProviderType = config.apiProviderType,
                    apiProviderTypeId = config.apiProviderTypeId,
                    thinkingConfigurations = config.thinkingConfigurations,
                    thinkingOptionId = config.thinkingOptionId
            )
        }
    }

    private fun fallbackConfigFor(configId: String): ModelConfigData {
        return if (configId == DEFAULT_CONFIG_ID) {
            createFreshDefaultConfig()
        } else {
            ModelConfigData(id = configId, name = context.getString(R.string.model_config_config_id, configId))
        }
    }

    // 从原有ApiPreferences创建默认配置
    private fun createFreshDefaultConfig(): ModelConfigData {
        return createFreshDefaultConfig(context)
    }

    // 保存配置
    suspend fun saveModelConfig(config: ModelConfigData) {
        val configKey = stringPreferencesKey("config_${config.id}")
        configDataStore.edit { preferences ->
            preferences[configKey] = json.encodeToString(config)
        }
    }
    // From DataStore, keeping all blocking preference reads off the caller context.
    private suspend fun loadConfigFromDataStore(configId: String): ModelConfigData? {
        return withContext(Dispatchers.IO) {
            val configKey = stringPreferencesKey("config_${configId}")
            val configJson = configDataStore.data.first()[configKey]
            if (configJson != null) {
                runCatching { json.decodeFromString<ModelConfigData>(configJson) }
                    .getOrElse { fallbackConfigFor(configId) }
            } else {
                fallbackConfigFor(configId)
            }
        }
    }


    // 将配置保存到DataStore
    private suspend fun saveConfigToDataStore(config: ModelConfigData) {
        val configKey = stringPreferencesKey("config_${config.id}")
        configDataStore.edit { preferences ->
            preferences[configKey] = json.encodeToString(config)
        }
    }

    private suspend fun updateConfigInternal(
            configId: String,
            transform: (ModelConfigData) -> ModelConfigData
    ): ModelConfigData {
        val configKey = stringPreferencesKey("config_${configId}")
        var updated: ModelConfigData? = null
        configDataStore.edit { preferences ->
            val current =
                    run {
                        val configJson = preferences[configKey]
                        if (configJson != null) {
                            try {
                                json.decodeFromString<ModelConfigData>(configJson)
                            } catch (e: Exception) {
                                if (configId == DEFAULT_CONFIG_ID) {
                                    createFreshDefaultConfig()
                                } else {
                                    ModelConfigData(id = configId, name = context.getString(R.string.model_config_config_id, configId))
                                }
                            }
                        } else {
                            if (configId == DEFAULT_CONFIG_ID) {
                                createFreshDefaultConfig()
                            } else {
                                ModelConfigData(id = configId, name = context.getString(R.string.model_config_config_id, configId))
                            }
                        }
                    }

            val newConfig = transform(current)
            preferences[configKey] = json.encodeToString(newConfig)
            updated = newConfig
        }
        return updated ?: ModelConfigData(id = configId, name = context.getString(R.string.model_config_config_id, configId))
    }
    // Get one configuration directly from the current DataStore emission.
    fun getModelConfigFlow(configId: String): Flow<ModelConfigData> {
        return configDataStore.data.map { preferences ->
            val configJson = preferences[stringPreferencesKey("config_${configId}")]
            if (configJson != null) {
                runCatching { json.decodeFromString<ModelConfigData>(configJson) }
                    .getOrElse { fallbackConfigFor(configId) }
            } else {
                fallbackConfigFor(configId)
            }
        }.flowOn(Dispatchers.IO)
    }


    // 获取指定ID的配置的非Flow版本
    suspend fun getModelConfig(configId: String): ModelConfigData? {
        return loadConfigFromDataStore(configId)
    }

    // 更新API Key池的当前索引
    suspend fun updateConfigKeyIndex(configId: String, newIndex: Int) {
        updateConfigInternal(configId) { it.copy(currentKeyIndex = newIndex) }
    }

    suspend fun updateSingleApiKey(configId: String, apiKey: String): ModelConfigData {
        return updateConfigInternal(configId) {
            it.copy(apiKey = apiKey, useMultipleApiKeys = false)
        }
    }
    // Get all summaries from one DataStore snapshot.
    suspend fun getAllConfigSummaries(): List<ModelConfigSummary> {
        return withContext(Dispatchers.IO) {
            readConfigSummariesFromPrefs(configDataStore.data.first())
        }
    }


    // 创建新配置
    suspend fun createConfig(name: String): String {
        val configId = UUID.randomUUID().toString()
        val configList = configListFlow.first().toMutableList()

        val newConfig =
                ModelConfigData(
                        id = configId,
                        name = name,
                        apiProviderType = ApiProviderType.OPENAI_GENERIC,
                        apiProviderTypeId = ApiProviderType.OPENAI_GENERIC.name,
                        thinkingConfigurations = thinkingRulesForProvider(ApiProviderType.OPENAI_GENERIC.name),
                        thinkingOptionId = firstThinkingOptionIdForProvider(ApiProviderType.OPENAI_GENERIC.name),
                        enableToolCall = ModelConfigDefaults.DEFAULT_ENABLE_TOOL_CALL
                )

        // 保存新配置
        saveConfigToDataStore(newConfig)

        // 更新配置列表
        configList.add(configId)
        configDataStore.edit { preferences ->
            preferences[CONFIG_LIST_KEY] = json.encodeToString(configList)
        }

        return configId
    }

    // 删除配置并清理所有功能对该配置的引用
    suspend fun deleteConfig(configId: String): List<FunctionType> {
        if (configId == DEFAULT_CONFIG_ID) {
            // 不允许删除默认配置
            return emptyList()
        }

        val configList = configListFlow.first().toMutableList()
        if (!configList.remove(configId)) {
            return emptyList()
        }

        val functionalConfigManager = FunctionalConfigManager(context)
        val mappingRepair = remapDeletedConfigReferences(
            mapping = functionalConfigManager.functionConfigMappingWithIndexFlow.first(),
            deletedConfigId = configId,
        )
        if (mappingRepair.affectedFunctions.isNotEmpty()) {
            functionalConfigManager.saveFunctionConfigMappingWithIndex(mappingRepair.mapping)
        }

        configDataStore.edit { preferences ->
            // 删除配置记录 - 修复null赋值问题
            preferences.remove(stringPreferencesKey("config_${configId}"))
            // 更新配置列表
            preferences[CONFIG_LIST_KEY] = json.encodeToString(configList)
        }
        return mappingRepair.affectedFunctions
    }

    // 更新配置基本信息（名称等）
    suspend fun updateConfigBase(configId: String, name: String): ModelConfigData {
        return updateConfigInternal(configId) { it.copy(name = name) }
    }

    // 更新模型配置
    suspend fun updateModelConfig(
            configId: String,
            apiKey: String,
            apiEndpoint: String,
            modelName: String
    ): ModelConfigData {
        return updateConfigInternal(configId) {
            it.copy(apiKey = apiKey, apiEndpoint = apiEndpoint, modelName = modelName)
        }
    }

    // 更新模型配置 - 包含API提供商类型
    suspend fun updateModelConfig(
            configId: String,
            apiKey: String,
            apiEndpoint: String,
            modelName: String,
            apiProviderType: com.ai.assistance.operit.data.model.ApiProviderType,
            apiProviderTypeId: String = apiProviderType.name
    ): ModelConfigData {
        return updateConfigInternal(configId) {
            it.copy(
                    apiKey = apiKey,
                    apiEndpoint = apiEndpoint,
                    modelName = modelName,
                    apiProviderType = apiProviderType,
                    apiProviderTypeId = apiProviderTypeId,
                    thinkingConfigurations = nextThinkingRulesForProvider(it, apiProviderTypeId, apiEndpoint),
                    thinkingOptionId = nextThinkingOptionIdForProvider(it, apiProviderTypeId, modelName, apiEndpoint)
            )
        }
    }

    // 更新模型配置 - 包含API提供商类型和MNN配置
    suspend fun updateModelConfig(
            configId: String,
            apiKey: String,
            apiEndpoint: String,
            modelName: String,
            apiProviderType: com.ai.assistance.operit.data.model.ApiProviderType,
            apiProviderTypeId: String = apiProviderType.name,
            mnnForwardType: Int,
            mnnThreadCount: Int
    ): ModelConfigData {
        return updateConfigInternal(configId) {
            it.copy(
                    apiKey = apiKey,
                    apiEndpoint = apiEndpoint,
                    modelName = modelName,
                    apiProviderType = apiProviderType,
                    apiProviderTypeId = apiProviderTypeId,
                    thinkingConfigurations = nextThinkingRulesForProvider(it, apiProviderTypeId, apiEndpoint),
                    thinkingOptionId = nextThinkingOptionIdForProvider(it, apiProviderTypeId, modelName, apiEndpoint),
                    mnnForwardType = mnnForwardType,
                    mnnThreadCount = mnnThreadCount
            )
        }
    }

    suspend fun updateApiSettingsFull(
            configId: String,
            apiKey: String,
            apiEndpoint: String,
            modelName: String,
            apiProviderType: ApiProviderType,
            apiProviderTypeId: String = apiProviderType.name,
            mnnForwardType: Int,
            mnnThreadCount: Int,
            llamaThreadCount: Int,
            llamaContextSize: Int,
            llamaGpuLayers: Int,
            enableDirectImageProcessing: Boolean,
            enableDirectAudioProcessing: Boolean,
            enableDirectVideoProcessing: Boolean,
            enableGoogleSearch: Boolean,
            enableDeepSeekWebSearch: Boolean,
            enableCodexWebSearch: Boolean,
            enableClaude1hPromptCache: Boolean,
            enableToolCall: Boolean
    ): ModelConfigData {
        return updateConfigInternal(configId) {
            it.copy(
                    apiKey = apiKey,
                    apiEndpoint = apiEndpoint,
                    modelName = modelName,
                    apiProviderType = apiProviderType,
                    apiProviderTypeId = apiProviderTypeId,
                    thinkingConfigurations = nextThinkingRulesForProvider(it, apiProviderTypeId, apiEndpoint),
                    thinkingOptionId = nextThinkingOptionIdForProvider(it, apiProviderTypeId, modelName, apiEndpoint),
                    mnnForwardType = mnnForwardType,
                    mnnThreadCount = mnnThreadCount,
                    llamaThreadCount = llamaThreadCount.coerceAtLeast(1),
                    llamaContextSize = llamaContextSize.coerceAtLeast(1),
                    llamaGpuLayers = llamaGpuLayers.coerceAtLeast(0),
                    enableDirectImageProcessing = enableDirectImageProcessing,
                    enableDirectAudioProcessing = enableDirectAudioProcessing,
                    enableDirectVideoProcessing = enableDirectVideoProcessing,
                    enableGoogleSearch = enableGoogleSearch,
                    enableDeepSeekWebSearch = enableDeepSeekWebSearch,
                    enableCodexWebSearch = enableCodexWebSearch,
                    enableClaude1hPromptCache = enableClaude1hPromptCache,
                    enableToolCall = enableToolCall
            )
        }
    }

    suspend fun updateThinkingConfigurations(configId: String, thinkingConfigurations: String): ModelConfigData {
        return updateConfigInternal(configId) {
            it.copy(thinkingConfigurations = thinkingConfigurations)
        }
    }

    suspend fun updateThinkingOptionId(configId: String, thinkingOptionId: String): ModelConfigData {
        return updateConfigInternal(configId) {
            it.copy(thinkingOptionId = thinkingOptionId.trim())
        }
    }

    suspend fun updateCustomHeaders(configId: String, customHeaders: String): ModelConfigData {
        return updateConfigInternal(configId) {
            it.copy(customHeaders = customHeaders)
        }
    }

    suspend fun updateRequestQueueSettings(
            configId: String,
            requestLimitPerMinute: Int,
            maxConcurrentRequests: Int
    ): ModelConfigData {
        return updateConfigInternal(configId) {
            it.copy(
                    requestLimitPerMinute = requestLimitPerMinute.coerceAtLeast(0),
                    maxConcurrentRequests = maxConcurrentRequests.coerceAtLeast(0)
            )
        }
    }

    suspend fun updateApiKeyPoolSettings(
            configId: String,
            useMultipleApiKeys: Boolean,
            apiKeyPool: List<ApiKeyInfo>
    ): ModelConfigData {
        return updateConfigInternal(configId) {
            it.copy(
                    useMultipleApiKeys = useMultipleApiKeys,
                    apiKeyPool = apiKeyPool
            )
        }
    }

    // 更新自定义参数
    suspend fun updateCustomParameters(configId: String, parametersJson: String): ModelConfigData {
        return updateConfigInternal(configId) {
            it.copy(
                    customParameters = parametersJson,
                    hasCustomParameters = parametersJson.isNotBlank() && parametersJson != "[]"
            )
        }
    }

    // 更新参数 - 新增方法
    suspend fun updateParameters(configId: String, parameters: List<ModelParameter<*>>) {
        // 提取自定义参数并序列化
        val customParams = parameters.filter { it.isCustom }
        val customParamsJson = if (customParams.isNotEmpty()) {
            val customParamsData = customParams.map { it.toCustomParameterData() }
            json.encodeToString(customParamsData)
        } else {
            "[]"
        }

        updateConfigInternal(configId) { current ->
            current.copy(
                    maxTokens =
                            parameters.find { it.id == "max_tokens" }?.currentValue as Int?
                                    ?: current.maxTokens,
                    maxTokensEnabled =
                            parameters.find { it.id == "max_tokens" }?.isEnabled
                                    ?: current.maxTokensEnabled,
                    temperature =
                            parameters.find { it.id == "temperature" }?.currentValue as Float?
                                    ?: current.temperature,
                    temperatureEnabled =
                            parameters.find { it.id == "temperature" }?.isEnabled
                                    ?: current.temperatureEnabled,
                    topP =
                            parameters.find { it.id == "top_p" }?.currentValue as Float?
                                    ?: current.topP,
                    topPEnabled =
                            parameters.find { it.id == "top_p" }?.isEnabled
                                    ?: current.topPEnabled,
                    topK =
                            parameters.find { it.id == "top_k" }?.currentValue as Int?
                                    ?: current.topK,
                    topKEnabled =
                            parameters.find { it.id == "top_k" }?.isEnabled
                                    ?: current.topKEnabled,
                    presencePenalty =
                            parameters.find { it.id == "presence_penalty" }?.currentValue as Float?
                                    ?: current.presencePenalty,
                    presencePenaltyEnabled =
                            parameters.find { it.id == "presence_penalty" }?.isEnabled
                                    ?: current.presencePenaltyEnabled,
                    frequencyPenalty =
                            parameters.find { it.id == "frequency_penalty" }?.currentValue as Float?
                                    ?: current.frequencyPenalty,
                    frequencyPenaltyEnabled =
                            parameters.find { it.id == "frequency_penalty" }?.isEnabled
                                    ?: current.frequencyPenaltyEnabled,
                    repetitionPenalty =
                            parameters.find { it.id == "repetition_penalty" }?.currentValue as Float?
                                    ?: current.repetitionPenalty,
                    repetitionPenaltyEnabled =
                            parameters.find { it.id == "repetition_penalty" }?.isEnabled
                                    ?: current.repetitionPenaltyEnabled,
                    customParameters = customParamsJson,
                    hasCustomParameters = customParams.isNotEmpty()
            )
        }
    }

    // 更新图片直接处理配置
    suspend fun updateDirectImageProcessing(configId: String, enableDirectImageProcessing: Boolean): ModelConfigData {
        return updateConfigInternal(configId) {
            it.copy(enableDirectImageProcessing = enableDirectImageProcessing)
        }
    }

    suspend fun updateDirectAudioProcessing(configId: String, enableDirectAudioProcessing: Boolean): ModelConfigData {
        return updateConfigInternal(configId) {
            it.copy(enableDirectAudioProcessing = enableDirectAudioProcessing)
        }
    }

    suspend fun updateDirectVideoProcessing(configId: String, enableDirectVideoProcessing: Boolean): ModelConfigData {
        return updateConfigInternal(configId) {
            it.copy(enableDirectVideoProcessing = enableDirectVideoProcessing)
        }
    }

    // 更新 Google Search Grounding 配置 (仅Gemini支持)
    suspend fun updateGoogleSearch(configId: String, enableGoogleSearch: Boolean): ModelConfigData {
        return updateConfigInternal(configId) { it.copy(enableGoogleSearch = enableGoogleSearch) }
    }

    suspend fun updateDeepSeekWebSearch(configId: String, enableDeepSeekWebSearch: Boolean): ModelConfigData {
        return updateConfigInternal(configId) { it.copy(enableDeepSeekWebSearch = enableDeepSeekWebSearch) }
    }

    suspend fun updateCodexWebSearch(configId: String, enableCodexWebSearch: Boolean): ModelConfigData {
        return updateConfigInternal(configId) { it.copy(enableCodexWebSearch = enableCodexWebSearch) }
    }

    suspend fun updateClaude1hPromptCache(configId: String, enableClaude1hPromptCache: Boolean): ModelConfigData {
        return updateConfigInternal(configId) {
            it.copy(enableClaude1hPromptCache = enableClaude1hPromptCache)
        }
    }

    // 更新 Tool Call 配置
    suspend fun updateToolCall(configId: String, enableToolCall: Boolean): ModelConfigData {
        return updateConfigInternal(configId) {
            it.copy(enableToolCall = enableToolCall)
        }
    }

    suspend fun updateContextSettings(
            configId: String,
            contextLength: Float,
            maxContextLength: Float,
            enableMaxContextMode: Boolean
    ): ModelConfigData {
        return updateConfigInternal(configId) {
            it.copy(
                    contextLength = contextLength,
                    maxContextLength = maxContextLength,
                    enableMaxContextMode = enableMaxContextMode
            )
        }
    }

    suspend fun updateSummarySettings(
            configId: String,
            enableSummary: Boolean,
            summaryTokenThreshold: Float,
            enableSummaryByMessageCount: Boolean,
            summaryMessageCountThreshold: Int,
            summaryCustomRules: String = ""
    ): ModelConfigData {
        return updateConfigInternal(configId) {
            it.copy(
                    enableSummary = enableSummary,
                    summaryTokenThreshold = summaryTokenThreshold,
                    enableSummaryByMessageCount = enableSummaryByMessageCount,
                    summaryMessageCountThreshold = summaryMessageCountThreshold,
                    summaryCustomRules = summaryCustomRules
            )
        }
    }

    /**
     * 根据配置ID获取完整的模型参数列表（包括标准和自定义参数）
     * @param configId 配置ID
     * @return 模型参数列表
     */
    suspend fun getModelParametersForConfig(configId: String): List<ModelParameter<*>> {
        val config = getModelConfigFlow(configId).first()
        val parameters = mutableListOf<ModelParameter<*>>()

        // 映射标准参数
        StandardModelParameters.DEFINITIONS.forEach { def ->
            val (currentValue, isEnabled) =
                    when (def.id) {
                        "max_tokens" -> config.maxTokens to config.maxTokensEnabled
                        "temperature" -> config.temperature to config.temperatureEnabled
                        "top_p" -> config.topP to config.topPEnabled
                        "top_k" -> config.topK to config.topKEnabled
                        "presence_penalty" -> config.presencePenalty to config.presencePenaltyEnabled
                        "frequency_penalty" ->
                                config.frequencyPenalty to config.frequencyPenaltyEnabled
                        "repetition_penalty" ->
                                config.repetitionPenalty to config.repetitionPenaltyEnabled
                        else -> null to null
                    }

            if (currentValue != null && isEnabled != null) {
                parameters.add(
                        ModelParameter(
                                id = def.id,
                                name = def.name,
                                apiName = def.apiName,
                                description = def.description,
                                defaultValue = def.defaultValue,
                                currentValue = currentValue,
                                isEnabled = isEnabled,
                                valueType = def.valueType,
                                minValue = def.minValue,
                                maxValue = def.maxValue,
                                category = def.category
                        )
                )
            }
        }

        // 添加自定义参数
        if (config.hasCustomParameters &&
                        config.customParameters.isNotBlank() &&
                        config.customParameters != "[]"
        ) {
            try {
                val customParamsData =
                        json.decodeFromString<List<com.ai.assistance.operit.data.model.CustomParameterData>>(
                                config.customParameters
                        )
                customParamsData.forEach { data ->
                    val valueType = ParameterValueType.valueOf(data.valueType)
                    val category = ParameterCategory.valueOf(data.category)

                    val convertedParam =
                            when (valueType) {
                                ParameterValueType.INT ->
                                        ModelParameter(
                                                id = data.id,
                                                name = data.name,
                                                apiName = data.apiName,
                                                description = data.description,
                                                defaultValue = data.defaultValue.toInt(),
                                                currentValue = data.currentValue.toInt(),
                                                isEnabled = data.isEnabled,
                                                valueType = valueType,
                                                minValue = data.minValue?.toInt(),
                                                maxValue = data.maxValue?.toInt(),
                                                category = category,
                                                isCustom = true
                                        )
                                ParameterValueType.FLOAT ->
                                        ModelParameter(
                                                id = data.id,
                                                name = data.name,
                                                apiName = data.apiName,
                                                description = data.description,
                                                defaultValue = data.defaultValue.toFloat(),
                                                currentValue = data.currentValue.toFloat(),
                                                isEnabled = data.isEnabled,
                                                valueType = valueType,
                                                minValue = data.minValue?.toFloat(),
                                                maxValue = data.maxValue?.toFloat(),
                                                category = category,
                                                isCustom = true
                                        )
                                ParameterValueType.BOOLEAN ->
                                        ModelParameter(
                                                id = data.id,
                                                name = data.name,
                                                apiName = data.apiName,
                                                description = data.description,
                                                defaultValue = data.defaultValue.toBoolean(),
                                                currentValue = data.currentValue.toBoolean(),
                                                isEnabled = data.isEnabled,
                                                valueType = valueType,
                                                category = category,
                                                isCustom = true
                                        )
                                ParameterValueType.STRING ->
                                        ModelParameter(
                                                id = data.id,
                                                name = data.name,
                                                apiName = data.apiName,
                                                description = data.description,
                                                defaultValue = data.defaultValue,
                                                currentValue = data.currentValue,
                                                isEnabled = data.isEnabled,
                                                valueType = valueType,
                                                category = category,
                                                isCustom = true
                                        )
                                ParameterValueType.OBJECT ->
                                        ModelParameter(
                                                id = data.id,
                                                name = data.name,
                                                apiName = data.apiName,
                                                description = data.description,
                                                defaultValue = data.defaultValue,
                                                currentValue = data.currentValue,
                                                isEnabled = data.isEnabled,
                                                valueType = valueType,
                                                category = category,
                                                isCustom = true
                                        )
                            }
                    parameters.add(convertedParam)
                }
            } catch (e: Exception) {
                AppLogger.e("ModelConfigManager", "Failed to parse or convert custom parameters", e)
            }
        }

        return parameters
    }
    
    /**
     * 导出所有模型配置为JSON字符串
     * @return JSON格式的所有配置数据
     */
    suspend fun exportAllConfigs(): String {
        val configList = configListFlow.first()
        val allConfigs = mutableListOf<ModelConfigData>()
        
        for (configId in configList) {
            val config = getModelConfigFlow(configId).first()
            allConfigs.add(config)
        }
        
        val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }
        
        return json.encodeToString(allConfigs)
    }
    
    /**
     * 从JSON字符串导入模型配置
     * @param jsonContent JSON格式的配置数据
     * @return 导入结果统计 (新增数量, 更新数量, 跳过数量)
     */
    suspend fun importConfigs(jsonContent: String): Triple<Int, Int, Int> {
        try {
            val importedConfigs = json.decodeFromString<List<ModelConfigData>>(jsonContent)
            val existingConfigList = configListFlow.first().toMutableList()
            val existingConfigIds = existingConfigList.toSet()
            
            var newCount = 0
            var updatedCount = 0
            var skippedCount = 0
            
            for (config in importedConfigs) {
                if (config.id.isEmpty() || config.name.isEmpty()) {
                    skippedCount++
                    continue
                }
                
                // 保存配置
                saveConfigToDataStore(config)
                
                if (existingConfigIds.contains(config.id)) {
                    updatedCount++
                } else {
                    newCount++
                    existingConfigList.add(config.id)
                }
            }
            
            // 更新配置列表
            if (newCount > 0) {
                configDataStore.edit { preferences ->
                    preferences[CONFIG_LIST_KEY] = json.encodeToString(existingConfigList)
                }
            }
            
            return Triple(newCount, updatedCount, skippedCount)
        } catch (e: Exception) {
            AppLogger.e("ModelConfigManager", "导入配置失败", e)
            throw Exception(context.getString(R.string.model_config_import_failed, e.localizedMessage ?: e.message))
        }
    }
}

// 扩展函数，用于将ModelParameter转换为CustomParameterData
private fun ModelParameter<*>.toCustomParameterData(): com.ai.assistance.operit.data.model.CustomParameterData {
    return com.ai.assistance.operit.data.model.CustomParameterData(
        id = this.id,
        name = this.name,
        apiName = this.apiName,
        description = this.description,
        defaultValue = this.defaultValue.toString(),
        currentValue = this.currentValue.toString(),
        isEnabled = this.isEnabled,
        valueType = this.valueType.name,
        minValue = this.minValue?.toString(),
        maxValue = this.maxValue?.toString(),
        category = this.category.name
    )
}
