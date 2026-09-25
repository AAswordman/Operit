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
import com.ai.assistance.operit.data.model.ModelConfigExport
import com.ai.assistance.operit.data.model.ModelConfigGroup
import com.ai.assistance.operit.data.model.ModelConfigSelection
import com.ai.assistance.operit.data.model.ModelConfigSummary
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ParameterCategory
import com.ai.assistance.operit.data.model.ParameterValueType
import com.ai.assistance.operit.data.model.StandardModelParameters
import com.ai.assistance.operit.data.model.SummarySectionOverride
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ApiKeyInfo
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
        val CONFIG_GROUPS_KEY = stringPreferencesKey("config_groups")
        val SELECTED_CONFIG_GROUP_KEY = stringPreferencesKey("selected_config_group")

        // 默认值
        const val DEFAULT_CONFIG_ID = "default"
        const val DEFAULT_CONFIG_NAME = "model_config_default_name"

        // Default API provider type
        private val DEFAULT_API_PROVIDER_TYPE = ApiProviderType.DEEPSEEK
        private const val OPENAI_CHAT_REASONING_EFFORT_RULE_ID = "openai-chat-reasoning-effort"
        private const val DEEPSEEK_CHAT_REASONING_EFFORT_RULE_ID = "deepseek-reasoning-effort"
        private const val DEEPSEEK_RESPONSES_REASONING_EFFORT_RULE_ID = "deepseek-responses-reasoning-effort"
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
                            ApiPreferences.DEFAULT_MODEL_NAME
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
                            firstThinkingOptionIdForModel(config.apiProviderTypeId, config.modelName)
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
                        if (currentRuleId !in existingCurrentRuleIds) {
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
            val ruleId = rule.optString("id", "").trim()
            return ruleId == OPENAI_CHAT_REASONING_EFFORT_RULE_ID &&
                    OPENAI_CHAT_MATCHER_FIELDS.none(rule::has)
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
                val configKey = stringPreferencesKey("config_${configId}")
                val configJson = preferences[configKey] ?: return@forEach
                val config = json.decodeFromString<ModelConfigData>(configJson)
                if (!isDeepSeekProvider(config.apiProviderTypeId)) {
                    return@forEach
                }

                val thinkingConfigurations =
                        addDeepSeekResponsesThinkingRule(config.thinkingConfigurations)
                if (thinkingConfigurations == config.thinkingConfigurations) {
                    return@forEach
                }

                preferences[configKey] =
                        json.encodeToString(
                                config.copy(thinkingConfigurations = thinkingConfigurations)
                        )
            }
        }

        private fun isDeepSeekProvider(providerTypeId: String): Boolean =
                providerTypeId.equals(ApiProviderType.DEEPSEEK.name, ignoreCase = true)

        private fun isDeepSeekResponsesEndpoint(apiEndpoint: String): Boolean =
                apiEndpoint.trim()
                        .substringBefore('?')
                        .substringBefore('#')
                        .trimEnd('/')
                        .endsWith("/responses", ignoreCase = true)

        internal fun addDeepSeekResponsesThinkingRule(thinkingConfigurations: String): String {
            val sourceRules = thinkingRulesJsonArray(thinkingConfigurations)
            if (sourceRules.hasThinkingRuleId(DEEPSEEK_RESPONSES_REASONING_EFFORT_RULE_ID)) {
                return thinkingConfigurations
            }

            val defaultRules = thinkingRulesJsonArray(thinkingRulesForProvider(ApiProviderType.DEEPSEEK.name))
            val responsesRule = defaultRules.firstThinkingRuleById(DEEPSEEK_RESPONSES_REASONING_EFFORT_RULE_ID)
                    ?: return thinkingConfigurations

            val targetRules = JSONArray()
            var inserted = false
            for (index in 0 until sourceRules.length()) {
                val rule = sourceRules.optJSONObject(index)
                if (!inserted && rule?.optString("id", "") == DEEPSEEK_CHAT_REASONING_EFFORT_RULE_ID) {
                    targetRules.put(JSONObject(responsesRule.toString()))
                    inserted = true
                }
                targetRules.put(sourceRules.get(index))
            }

            return if (inserted) targetRules.toString() else thinkingConfigurations
        }

        private fun JSONArray.hasThinkingRuleId(ruleId: String): Boolean =
                firstThinkingRuleById(ruleId) != null

        private fun JSONArray.firstThinkingRuleById(ruleId: String): JSONObject? {
            for (index in 0 until length()) {
                val rule = optJSONObject(index) ?: continue
                if (rule.optString("id", "") == ruleId) {
                    return rule
                }
            }
            return null
        }

        internal fun thinkingRulesForProvider(providerTypeId: String): String =
                ModelThinkingConfigDefaults.forProvider(providerTypeId)

        internal fun firstThinkingOptionIdForProvider(providerTypeId: String): String =
                firstThinkingOptionIdForModel(providerTypeId, "")

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
                        .options
                        .firstOrNull()
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
                        addDeepSeekResponsesThinkingRule(current.thinkingConfigurations)
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
                    current.thinkingOptionId
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
            }

    // 所有配置摘要的响应式版本，保留完整列表以解析已有绑定。
    // 按分组筛选的选择器使用 configSelectionFlow 同步读取当前组与候选。
    val configSummariesFlow: Flow<List<ModelConfigSummary>> =
            configDataStore.data.map { preferences ->
                readConfigSummariesFromPrefs(preferences)
            }
    val configGroupsFlow: Flow<List<ModelConfigGroup>> =
            configDataStore.data.map { preferences ->
                preferences[CONFIG_GROUPS_KEY]
                        ?.let {
                            runCatching {
                                json.decodeFromString<List<ModelConfigGroup>>(it)
                            }.getOrNull()
                        }
                        .orEmpty()
            }

    val selectedConfigGroupFlow: Flow<String?> =
            configDataStore.data.map { preferences ->
                val validGroupIds = readGroups(preferences).mapTo(mutableSetOf()) { it.id }
                normalizeConfigGroupId(preferences[SELECTED_CONFIG_GROUP_KEY])
                        ?.takeIf { it in validGroupIds }
            }

    // Read the filter and candidates together so a group switch cannot display the previous group's list.
    val configSelectionFlow: Flow<ModelConfigSelection> =
            configDataStore.data.map { preferences ->
                val groups = readGroups(preferences)
                val selectedGroupId = normalizeConfigGroupId(preferences[SELECTED_CONFIG_GROUP_KEY])
                        ?.takeIf { id -> groups.any { it.id == id } }
                ModelConfigSelection(
                        allConfigs = readConfigSummariesFromPrefs(preferences),
                        groups = groups,
                        selectedGroupId = selectedGroupId
                )
            }

    suspend fun setSelectedConfigGroup(groupId: String?) {
        val normalizedGroupId = normalizeConfigGroupId(groupId)
        configDataStore.edit { preferences ->
            val validGroupIds = readGroups(preferences).mapTo(mutableSetOf()) { it.id }
            require(normalizedGroupId == null || normalizedGroupId in validGroupIds) {
                "Unknown config group ID: $groupId"
            }
            if (normalizedGroupId == null) preferences.remove(SELECTED_CONFIG_GROUP_KEY)
            else preferences[SELECTED_CONFIG_GROUP_KEY] = normalizedGroupId
        }
    }

    private fun readConfigListFromPrefs(prefs: Preferences): List<String> {
        val configList = prefs[CONFIG_LIST_KEY] ?: ""
        return if (configList.isEmpty()) emptyList()
        else json.decodeFromString<List<String>>(configList)
    }

    private fun readConfigSummariesFromPrefs(prefs: Preferences): List<ModelConfigSummary> {
        val validGroupIds = readGroups(prefs).mapTo(mutableSetOf()) { it.id }
        return readConfigListFromPrefs(prefs).map { configId ->
            val configJson = prefs[stringPreferencesKey("config_${configId}")]
            val config =
                    if (configJson != null) {
                        try {
                            normalizeConfig(json.decodeFromString<ModelConfigData>(configJson))
                        } catch (_: Exception) {
                            fallbackConfigFor(configId)
                        }
                    } else {
                        fallbackConfigFor(configId)
                    }
            ModelConfigSummary(
                    id = config.id,
                    name = config.name,
                    groupId = config.groupId?.takeIf { it in validGroupIds },
                    modelName = config.modelName,
                    apiEndpoint = config.apiEndpoint,
                    apiProviderType = config.apiProviderType,
                    apiProviderTypeId = config.apiProviderTypeId,
                    thinkingConfigurations = config.thinkingConfigurations,
                    thinkingOptionId = config.thinkingOptionId
            )
        }
    }

    private fun normalizeConfigGroupId(groupId: String?): String? {
        return groupId?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun normalizeConfig(config: ModelConfigData): ModelConfigData {
        return config.copy(groupId = normalizeConfigGroupId(config.groupId))
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
            preferences[configKey] = json.encodeToString(normalizeConfig(config))
        }
    }

    // 从DataStore加载配置
    private suspend fun loadConfigFromDataStore(configId: String): ModelConfigData? {
        val configKey = stringPreferencesKey("config_${configId}")
        return configDataStore.data.first().let { preferences ->
            val configJson = preferences[configKey]
            if (configJson != null) {
                try {
                    normalizeConfig(json.decodeFromString<ModelConfigData>(configJson))
                } catch (e: Exception) {
                    // 如果解析失败，回退到创建一个新配置
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

            val newConfig = normalizeConfig(transform(current))
            preferences[configKey] = json.encodeToString(newConfig)
            updated = newConfig
        }
        return updated ?: ModelConfigData(id = configId, name = context.getString(R.string.model_config_config_id, configId))
    }

    // 获取指定ID的配置
    fun getModelConfigFlow(configId: String): Flow<ModelConfigData> {
        return configDataStore.data.map { preferences ->
            val config = loadConfigFromDataStore(configId) ?: ModelConfigData(id = configId, name = context.getString(R.string.model_config_config_id, configId))
            config
        }
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

    // 获取所有配置的摘要信息
    suspend fun getAllConfigSummaries(): List<ModelConfigSummary> {
        return readConfigSummariesFromPrefs(configDataStore.data.first())
    }

    // 创建新配置
    suspend fun createConfig(name: String, groupId: String? = null): String {
        val configId = UUID.randomUUID().toString()
        val normalizedGroupId = normalizeConfigGroupId(groupId)
        val newConfig =
                ModelConfigData(
                        id = configId,
                        name = name,
                        groupId = normalizedGroupId,
                        apiProviderType = ApiProviderType.OPENAI_GENERIC,
                        apiProviderTypeId = ApiProviderType.OPENAI_GENERIC.name,
                        thinkingConfigurations = thinkingRulesForProvider(ApiProviderType.OPENAI_GENERIC.name),
                        thinkingOptionId = firstThinkingOptionIdForProvider(ApiProviderType.OPENAI_GENERIC.name),
                        enableToolCall = ModelConfigDefaults.DEFAULT_ENABLE_TOOL_CALL
                )

        configDataStore.edit { preferences ->
            val validGroupIds = readGroups(preferences).mapTo(mutableSetOf()) { it.id }
            require(normalizedGroupId == null || normalizedGroupId in validGroupIds) {
                "Unknown config group ID: $groupId"
            }
            val configList = readConfigListFromPrefs(preferences)
            preferences[stringPreferencesKey("config_${configId}")] = json.encodeToString(newConfig)
            preferences[CONFIG_LIST_KEY] = json.encodeToString(configList + configId)
        }

        return configId
    }

    suspend fun reorderConfigs(configIds: List<String>) {
        configDataStore.edit { preferences ->
            val currentIds = readConfigListFromPrefs(preferences)
            require(configIds.size == currentIds.size && configIds.toSet() == currentIds.toSet()) {
                "Reordered config IDs must match the existing config list"
            }
            preferences[CONFIG_LIST_KEY] = json.encodeToString(configIds)
        }
    }

    suspend fun createConfigGroup(name: String): String {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Config group name must not be blank" }
        val group = ModelConfigGroup(UUID.randomUUID().toString(), normalizedName)
        configDataStore.edit { preferences ->
            preferences[CONFIG_GROUPS_KEY] = json.encodeToString(readGroups(preferences) + group)
        }
        return group.id
    }

    suspend fun renameConfigGroup(groupId: String, name: String) {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Config group name must not be blank" }
        configDataStore.edit { preferences ->
            preferences[CONFIG_GROUPS_KEY] = json.encodeToString(
                    readGroups(preferences).map {
                        if (it.id == groupId) it.copy(name = normalizedName) else it
                    }
            )
        }
    }

    suspend fun reorderConfigGroups(groupIds: List<String>) {
        configDataStore.edit { preferences ->
            val groups = readGroups(preferences)
            require(groupIds.size == groups.size && groupIds.toSet() == groups.map { it.id }.toSet()) {
                "Reordered group IDs must match the existing group list"
            }
            val groupsById = groups.associateBy { it.id }
            preferences[CONFIG_GROUPS_KEY] =
                    json.encodeToString(groupIds.mapNotNull(groupsById::get))
        }
    }

    suspend fun updateConfigOrganization(
            groups: List<ModelConfigGroup>,
            orderedConfigs: List<Pair<String, String?>>
    ) {
        configDataStore.edit { preferences ->
            val currentIds = readConfigListFromPrefs(preferences)
            val orderedIds = orderedConfigs.map { it.first }
            require(orderedIds.size == currentIds.size && orderedIds.toSet() == currentIds.toSet()) {
                "Organized config IDs must match the existing config list"
            }
            require(groups.map { it.id }.distinct().size == groups.size) {
                "Config group IDs must be unique"
            }
            val validGroupIds = groups.map { it.id }.toSet()
            orderedConfigs.forEach { (configId, groupId) ->
                val normalizedGroupId = normalizeConfigGroupId(groupId)
                require(normalizedGroupId == null || normalizedGroupId in validGroupIds) {
                    "Unknown config group ID: $groupId"
                }
                val configKey = stringPreferencesKey("config_${configId}")
                val config = readConfig(preferences, configId)
                preferences[configKey] = json.encodeToString(config.copy(groupId = normalizedGroupId))
            }
            preferences[CONFIG_GROUPS_KEY] = json.encodeToString(groups)
            preferences[CONFIG_LIST_KEY] = json.encodeToString(orderedIds)
        }
    }

    suspend fun moveConfig(configId: String, groupId: String?, targetIndex: Int) {
        configDataStore.edit { preferences ->
            val normalizedGroupId = normalizeConfigGroupId(groupId)
            val validGroupIds = readGroups(preferences).mapTo(mutableSetOf()) { it.id }
            require(normalizedGroupId == null || normalizedGroupId in validGroupIds) {
                "Unknown config group ID: $groupId"
            }
            val configIds = readConfigListFromPrefs(preferences).toMutableList()
            require(configIds.remove(configId)) { "Unknown config ID: $configId" }
            val configKey = stringPreferencesKey("config_${configId}")
            val config = readConfig(preferences, configId)
            val destinationIds =
                configIds.filter { readConfig(preferences, it).groupId == normalizedGroupId }
            val boundedIndex = targetIndex.coerceIn(0, destinationIds.size)
            val insertionIndex =
                    if (boundedIndex < destinationIds.size) {
                        configIds.indexOf(destinationIds[boundedIndex])
                    } else {
                        destinationIds.lastOrNull()?.let { configIds.indexOf(it) + 1 }
                                ?: configIds.size
                    }
            configIds.add(insertionIndex, configId)
            preferences[configKey] = json.encodeToString(config.copy(groupId = normalizedGroupId))
            preferences[CONFIG_LIST_KEY] = json.encodeToString(configIds)
        }
    }

    suspend fun deleteConfigGroup(groupId: String) {
        configDataStore.edit { preferences ->
            preferences[CONFIG_GROUPS_KEY] =
                    json.encodeToString(readGroups(preferences).filterNot { it.id == groupId })
            if (preferences[SELECTED_CONFIG_GROUP_KEY] == groupId) {
                preferences.remove(SELECTED_CONFIG_GROUP_KEY)
            }
            readConfigListFromPrefs(preferences).forEach { configId ->
                val config = readConfig(preferences, configId)
                if (config.groupId == groupId) {
                    preferences[stringPreferencesKey("config_${configId}")] =
                            json.encodeToString(config.copy(groupId = null))
                }
            }
        }
    }

    suspend fun updateModelOrder(configId: String, modelOrder: List<String>): ModelConfigData {
        return updateConfigInternal(configId) {
            it.copy(modelOrder = modelOrder.map(String::trim).filter(String::isNotEmpty).distinct())
        }
    }

    private fun decodeImportedConfigs(jsonContent: String): ModelConfigExport {
        val trimmed = jsonContent.trim()
        if (trimmed.startsWith("{")) {
            return json.decodeFromString<ModelConfigExport>(trimmed)
        }
        // 旧导出只有配置数组，不会带分组定义。
        return ModelConfigExport(configs = json.decodeFromString<List<ModelConfigData>>(trimmed))
    }

    private fun mergeImportedGroups(
        existing: List<ModelConfigGroup>,
        imported: List<ModelConfigGroup>
    ): List<ModelConfigGroup> {
        val merged = existing.toMutableList()
        val ids = merged.mapTo(mutableSetOf()) { it.id }
        imported.forEach { rawGroup ->
            val id = rawGroup.id.trim()
            val name = rawGroup.name.trim()
            if (id.isEmpty() || name.isEmpty() || !ids.add(id)) return@forEach
            merged.add(ModelConfigGroup(id = id, name = name))
        }
        return merged
    }

    private fun readGroups(preferences: Preferences): List<ModelConfigGroup> {
        return preferences[CONFIG_GROUPS_KEY]
                ?.let {
                    runCatching { json.decodeFromString<List<ModelConfigGroup>>(it) }.getOrNull()
                }
                .orEmpty()
    }

    private fun readConfig(preferences: Preferences, configId: String): ModelConfigData {
        return preferences[stringPreferencesKey("config_${configId}")]
                ?.let {
                    runCatching {
                        normalizeConfig(json.decodeFromString<ModelConfigData>(it))
                    }.getOrNull()
                }
                ?: fallbackConfigFor(configId)
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
            summaryCustomRules: String? = null,
            summarySectionOverrides: List<SummarySectionOverride>? = null
    ): ModelConfigData {
        return updateConfigInternal(configId) { current ->
            current.copy(
                    enableSummary = enableSummary,
                    summaryTokenThreshold = summaryTokenThreshold,
                    enableSummaryByMessageCount = enableSummaryByMessageCount,
                    summaryMessageCountThreshold = summaryMessageCountThreshold,
                    summaryCustomRules = summaryCustomRules ?: current.summaryCustomRules,
                    summarySectionOverrides =
                        summarySectionOverrides ?: current.summarySectionOverrides
            )
        }
    }

    suspend fun updateSummaryDialogueReviewSettings(
            configId: String,
            enabled: Boolean,
            title: String
    ): ModelConfigData {
        return updateConfigInternal(configId) {
            it.copy(
                    enableSummaryDialogueReview = enabled,
                    summaryDialogueReviewTitle = title
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
        val groups = configGroupsFlow.first()
        
        val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }
        
        return json.encodeToString(ModelConfigExport(groups = groups, configs = allConfigs))
    }
    
    /**
     * 从JSON字符串导入模型配置
     * @param jsonContent JSON格式的配置数据
     * @return 导入结果统计 (新增数量, 更新数量, 跳过数量)
     */
    suspend fun importConfigs(jsonContent: String): Triple<Int, Int, Int> {
        try {
            val imported = decodeImportedConfigs(jsonContent)
            val importedConfigs = imported.configs
            var newCount = 0
            var updatedCount = 0
            var skippedCount = 0

            configDataStore.edit { preferences ->
                val existingConfigList = readConfigListFromPrefs(preferences).toMutableList()
                val existingConfigIds = existingConfigList.toMutableSet()
                val mergedGroups = mergeImportedGroups(readGroups(preferences), imported.groups)
                preferences[CONFIG_GROUPS_KEY] = json.encodeToString(mergedGroups)
                val validGroupIds = mergedGroups.mapTo(mutableSetOf()) { it.id }

                importedConfigs.forEach { rawConfig ->
                    val configId = rawConfig.id.trim()
                    val configName = rawConfig.name.trim()
                    if (configId.isEmpty() || configName.isEmpty()) {
                        skippedCount++
                        return@forEach
                    }

                    // Old exports do not have groupId. Empty or unknown IDs must remain visible as ungrouped.
                    val groupId =
                            normalizeConfigGroupId(rawConfig.groupId)
                                    ?.takeIf { it in validGroupIds }
                    val config = normalizeConfig(
                            rawConfig.copy(
                                    id = configId,
                                    name = configName,
                                    groupId = groupId
                            )
                    )
                    preferences[stringPreferencesKey("config_${config.id}")] =
                            json.encodeToString(config)

                    if (existingConfigIds.add(config.id)) {
                        existingConfigList.add(config.id)
                        newCount++
                    } else {
                        updatedCount++
                    }
                }

                // Always rewrite the list in the same transaction as the config records.
                preferences[CONFIG_LIST_KEY] = json.encodeToString(existingConfigList)
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
