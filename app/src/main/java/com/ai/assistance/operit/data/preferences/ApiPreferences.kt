package com.ai.assistance.operit.data.preferences

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.toMutablePreferences
import androidx.datastore.preferences.core.toPreferences
import com.ai.assistance.operit.data.persistence.PreferenceStateRepairResult
import com.ai.assistance.operit.data.persistence.PreferenceStoreCatalog
import com.ai.assistance.operit.data.persistence.mergeNormalizedJsonFields
import com.ai.assistance.operit.data.persistence.recoverablePreferencesDataStore
import com.ai.assistance.operit.data.persistence.repairPreferenceState
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.BillingMode
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ParameterCategory
import com.ai.assistance.operit.data.model.ParameterValueType
import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.stats.ReleasedProviderModelKeyDecoder
import com.ai.assistance.operit.data.stats.ModelPriceSettings
import com.ai.assistance.operit.plugins.toolpkg.ToolPkgAiProviderRegistry
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromJsonElement
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToJsonElement
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

// Define the DataStore at the module level
private val Context.apiDataStore: DataStore<Preferences> by
        recoverablePreferencesDataStore(name = "api_settings")

private fun validReleasedUsdToCnyRate(stored: Float?): Double? =
    stored?.takeIf { it.isFinite() && it > 0f }?.toDouble()

class ApiPreferences private constructor(private val context: Context) {

    private val persistenceJson = Json { ignoreUnknownKeys = true }

    // Define our preferences keys
    companion object {
        @Volatile
        private var INSTANCE: ApiPreferences? = null

        /** JVM tests can avoid initializing the application-scoped ToolPkg runtime. */
        internal var toolPkgProviderAliasesProvider: (() -> Map<String, String>)? = null

        /** JVM tests can provide saved ToolPkg provider IDs without reading model-config DataStore. */
        internal var configuredProviderIdsProvider: (suspend () -> List<String>)? = null

        fun getInstance(context: Context): ApiPreferences {
            return INSTANCE ?: synchronized(this) {
                val instance = ApiPreferences(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
        @JvmStatic
        fun getFeatureToggleBlocking(
            context: Context,
            featureKey: String,
            defaultValue: Boolean = false
        ): Boolean {
            val normalized = featureKey.trim()
            if (normalized.isEmpty()) {
                return defaultValue
            }
            return runBlocking {
                getInstance(context).featureToggleFlow(normalized, defaultValue).first()
            }
        }

        @JvmStatic
        fun setFeatureToggleBlocking(
            context: Context,
            featureKey: String,
            enabled: Boolean
        ) {
            val normalized = featureKey.trim()
            if (normalized.isEmpty()) {
                return
            }
            runBlocking {
                getInstance(context).saveFeatureToggle(normalized, enabled)
            }
        }

        // 动态生成供应商:模型的Token键
        private fun getTokenInputKey(providerModel: String) =
                longPreferencesKey("token_input_${providerModel.replace(":", "_")}")

        private fun getTokenCachedInputKey(providerModel: String) =
                longPreferencesKey("token_cached_input_${providerModel.replace(":", "_")}")

        private fun getTokenOutputKey(providerModel: String) =
                longPreferencesKey("token_output_${providerModel.replace(":", "_")}")

        // 模型定价键
        private fun getModelInputPriceKey(providerModel: String) =
                floatPreferencesKey("model_input_price_${providerModel.replace(":", "_")}")

        private fun getModelCachedInputPriceKey(providerModel: String) =
                floatPreferencesKey("model_cached_input_price_${providerModel.replace(":", "_")}")

        private fun getModelOutputPriceKey(providerModel: String) =
                floatPreferencesKey("model_output_price_${providerModel.replace(":", "_")}")

        // 请求次数统计键
        private fun getRequestCountKey(providerModel: String) =
                intPreferencesKey("request_count_${providerModel.replace(":", "_")}")

        // 计费方式键
        private fun getBillingModeKey(providerModel: String) =
                stringPreferencesKey("billing_mode_${providerModel.replace(":", "_")}")

        // 按次计费价格键
        private fun getPricePerRequestKey(providerModel: String) =
                floatPreferencesKey("price_per_request_${providerModel.replace(":", "_")}")

        private val RELEASED_MODEL_PRICE_KEY_PREFIXES =
                listOf(
                        "model_input_price_",
                        "model_cached_input_price_",
                        "model_output_price_",
                        "billing_mode_",
                        "price_per_request_",
                )

        private val USD_TO_CNY_EXCHANGE_RATE = floatPreferencesKey("usd_to_cny_exchange_rate")

        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val FEATURE_TOGGLES_JSON = stringPreferencesKey("feature_toggles_json")
        // Default values
        const val DEFAULT_FEATURE_TOGGLE_STATE = false
        const val DEFAULT_KEEP_SCREEN_ON = true
        // Keys for Thinking Mode and Thinking Guidance
        val ENABLE_THINKING_MODE = booleanPreferencesKey("enable_thinking_mode")
        val THINKING_QUALITY_LEVEL = intPreferencesKey("thinking_quality_level")

        // Key for Memory Auto Update
        val ENABLE_MEMORY_AUTO_UPDATE = booleanPreferencesKey("enable_memory_auto_update")

        // Key for Auto Read
        val ENABLE_AUTO_READ = booleanPreferencesKey("enable_auto_read")

        // Key for Tools Enable/Disable
        val ENABLE_TOOLS = booleanPreferencesKey("enable_tools")

        // Key for per-tool prompt visibility
        val TOOL_PROMPT_VISIBILITY_JSON = stringPreferencesKey("tool_prompt_visibility_json")

        // Key for per-tool prompt order (list of tool names)
        val TOOL_PROMPT_ORDER_JSON = stringPreferencesKey("tool_prompt_order_json")

        // Key for plugin order (list of package names)
        val PLUGIN_ORDER_JSON = stringPreferencesKey("plugin_order_json")

        // Key for skill order (list of skill names)
        val SKILL_ORDER_JSON = stringPreferencesKey("skill_order_json")

        // Key for Disable Stream Output
        val DISABLE_STREAM_OUTPUT = booleanPreferencesKey("disable_stream_output")

        // Key for Disable User Preference Description
        val DISABLE_USER_PREFERENCE_DESCRIPTION = booleanPreferencesKey("disable_user_preference_description")

        // Custom System Prompt Template (Advanced Configuration)
        val CUSTOM_SYSTEM_PROMPT_TEMPLATE = stringPreferencesKey("custom_system_prompt_template")

        val MAX_IMAGE_HISTORY_USER_TURNS = intPreferencesKey("max_image_history_user_turns")
        val MAX_MEDIA_HISTORY_USER_TURNS = intPreferencesKey("max_media_history_user_turns")

        // Default values for Thinking Mode
        const val DEFAULT_ENABLE_THINKING_MODE = false
        const val MIN_THINKING_QUALITY_LEVEL = 1
        const val MAX_THINKING_QUALITY_LEVEL = 5
        const val DEFAULT_THINKING_QUALITY_LEVEL = 2

        // Default value for Memory Auto Update
        const val DEFAULT_ENABLE_MEMORY_AUTO_UPDATE = true

        // Default value for Auto Read
        const val DEFAULT_ENABLE_AUTO_READ = false

        // Default value for Tools Enable/Disable
        const val DEFAULT_ENABLE_TOOLS = true

        // Default value for Disable Stream Output (default false, meaning stream is enabled by default)
        const val DEFAULT_DISABLE_STREAM_OUTPUT = false

        // Default value for Disable User Preference Description
        const val DEFAULT_DISABLE_USER_PREFERENCE_DESCRIPTION = false

        // Default system prompt template (empty means use built-in template)
        const val DEFAULT_SYSTEM_PROMPT_TEMPLATE = ""

        const val DEFAULT_MAX_IMAGE_HISTORY_USER_TURNS = 2
        const val DEFAULT_MAX_MEDIA_HISTORY_USER_TURNS = 1

        // 自定义参数存储键
        val CUSTOM_PARAMETERS = stringPreferencesKey("custom_parameters")

        private val SAF_BOOKMARKS_JSON = stringPreferencesKey("saf_bookmarks_json")

        // 默认空的自定义参数列表
        const val DEFAULT_CUSTOM_PARAMETERS = "[]"
        const val DEFAULT_TOOL_PROMPT_VISIBILITY_JSON = "{}"
        const val DEFAULT_TOOL_PROMPT_ORDER_JSON = "[]"
        const val DEFAULT_FEATURE_TOGGLES_JSON = "{}"

        // API 配置默认值
        const val DEFAULT_API_ENDPOINT = "https://api.deepseek.com/v1/chat/completions"
        const val DEFAULT_MODEL_NAME = "deepseek-v4-flash"

        private const val TAG = "ApiPreferences"
    }

    @Serializable
    data class SafBookmark(
        val uri: String,
        val name: String
    )

    private data class PersistedSafBookmark(
        val value: SafBookmark,
        val element: JsonObject
    )

    private data class SafBookmarkNormalization(
        val encoded: String,
        val changed: Boolean
    )

    private fun decodeSafBookmarks(raw: String): List<PersistedSafBookmark> =
        persistenceJson.parseToJsonElement(raw).jsonArray.map { element ->
            PersistedSafBookmark(
                value = persistenceJson.decodeFromJsonElement(element),
                element = element.jsonObject
            )
        }

    private fun normalizeSafBookmarks(raw: String): SafBookmarkNormalization {
        val retained = mutableListOf<JsonObject>()
        var changed = false
        persistenceJson.parseToJsonElement(raw).jsonArray.forEach { element ->
            try {
                persistenceJson.decodeFromJsonElement<SafBookmark>(element)
                retained += element.jsonObject
            } catch (e: Exception) {
                changed = true
                AppLogger.e("ApiPreferences", "Repairing an invalid SAF bookmark", e)
            }
        }
        return SafBookmarkNormalization(
            encoded = if (changed) JsonArray(retained).toString() else raw,
            changed = changed
        )
    }

    suspend fun repairPersistedState(): Boolean =
        repairPreferenceState(
            context = context,
            storeName = PreferenceStoreCatalog.API_SETTINGS,
            dataStore = context.apiDataStore
        ) { current ->
            val originalValues = current.asMap().entries.associate { it.key.name to it.value }
            val mutable = current.toMutablePreferences()
            val issues = linkedSetOf<String>()

            fun removeName(name: String) {
                mutable.asMap().keys.filter { it.name == name }.forEach { mutable.remove(it) }
            }

            fun replaceString(key: Preferences.Key<String>, value: String) {
                removeName(key.name)
                mutable[key] = value
                issues += key.name
            }

            fun replaceBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
                removeName(key.name)
                mutable[key] = value
                issues += key.name
            }

            fun replaceInt(key: Preferences.Key<Int>, value: Int) {
                removeName(key.name)
                mutable[key] = value
                issues += key.name
            }

            fun replaceFloat(key: Preferences.Key<Float>, value: Float) {
                removeName(key.name)
                mutable[key] = value
                issues += key.name
            }

            fun replaceLong(name: String, value: Long) {
                removeName(name)
                mutable[longPreferencesKey(name)] = value
                issues += name
            }

            val booleanKeys =
                listOf(
                    KEEP_SCREEN_ON to DEFAULT_KEEP_SCREEN_ON,
                    ENABLE_THINKING_MODE to DEFAULT_ENABLE_THINKING_MODE,
                    ENABLE_MEMORY_AUTO_UPDATE to DEFAULT_ENABLE_MEMORY_AUTO_UPDATE,
                    ENABLE_AUTO_READ to DEFAULT_ENABLE_AUTO_READ,
                    ENABLE_TOOLS to DEFAULT_ENABLE_TOOLS,
                    DISABLE_STREAM_OUTPUT to DEFAULT_DISABLE_STREAM_OUTPUT,
                    DISABLE_USER_PREFERENCE_DESCRIPTION to
                        DEFAULT_DISABLE_USER_PREFERENCE_DESCRIPTION
                )
            booleanKeys.forEach { (key, defaultValue) ->
                val raw = originalValues[key.name]
                if (raw != null && raw !is Boolean) replaceBoolean(key, defaultValue)
            }

            val rawThinkingQuality = originalValues[THINKING_QUALITY_LEVEL.name]
            if (rawThinkingQuality != null) {
                val normalized =
                    (rawThinkingQuality as? Int)?.coerceIn(
                        MIN_THINKING_QUALITY_LEVEL,
                        MAX_THINKING_QUALITY_LEVEL
                    ) ?: DEFAULT_THINKING_QUALITY_LEVEL
                if (rawThinkingQuality !is Int || normalized != rawThinkingQuality) {
                    replaceInt(THINKING_QUALITY_LEVEL, normalized)
                }
            }

            listOf(
                MAX_IMAGE_HISTORY_USER_TURNS to DEFAULT_MAX_IMAGE_HISTORY_USER_TURNS,
                MAX_MEDIA_HISTORY_USER_TURNS to DEFAULT_MAX_MEDIA_HISTORY_USER_TURNS
            ).forEach { (key, defaultValue) ->
                val raw = originalValues[key.name]
                if (raw != null && (raw !is Int || raw < 0)) replaceInt(key, defaultValue)
            }

            val rawPrompt = originalValues[CUSTOM_SYSTEM_PROMPT_TEMPLATE.name]
            if (rawPrompt != null && rawPrompt !is String) {
                replaceString(CUSTOM_SYSTEM_PROMPT_TEMPLATE, DEFAULT_SYSTEM_PROMPT_TEMPLATE)
            }

            fun validateJson(
                key: Preferences.Key<String>,
                defaultValue: String,
                validator: (String) -> Unit
            ) {
                val raw = originalValues[key.name] ?: return
                val valid =
                    if (raw is String) {
                        try {
                            validator(raw)
                            true
                        } catch (e: Exception) {
                            AppLogger.e("ApiPreferences", "Repairing invalid JSON in ${key.name}", e)
                            false
                        }
                    } else {
                        false
                    }
                if (!valid) replaceString(key, defaultValue)
            }

            val rawBookmarks = originalValues[SAF_BOOKMARKS_JSON.name]
            if (rawBookmarks != null) {
                val normalized =
                    if (rawBookmarks is String) {
                        try {
                            normalizeSafBookmarks(rawBookmarks)
                        } catch (e: Exception) {
                            AppLogger.e("ApiPreferences", "Repairing invalid SAF bookmarks", e)
                            null
                        }
                    } else {
                        null
                    }
                when {
                    normalized == null -> replaceString(SAF_BOOKMARKS_JSON, "[]")
                    normalized.changed -> replaceString(SAF_BOOKMARKS_JSON, normalized.encoded)
                }
            }
            validateJson(FEATURE_TOGGLES_JSON, DEFAULT_FEATURE_TOGGLES_JSON) {
                Json.decodeFromString<Map<String, Boolean>>(it)
            }
            validateJson(TOOL_PROMPT_VISIBILITY_JSON, DEFAULT_TOOL_PROMPT_VISIBILITY_JSON) {
                Json.decodeFromString<Map<String, Boolean>>(it)
            }
            listOf(TOOL_PROMPT_ORDER_JSON, PLUGIN_ORDER_JSON, SKILL_ORDER_JSON).forEach { key ->
                validateJson(key, DEFAULT_TOOL_PROMPT_ORDER_JSON) {
                    Json.decodeFromString<List<String>>(it)
                }
            }
            validateJson(CUSTOM_PARAMETERS, DEFAULT_CUSTOM_PARAMETERS) {
                Json.parseToJsonElement(it).jsonArray
            }

            val exchangeRateRaw = originalValues[USD_TO_CNY_EXCHANGE_RATE.name]
            if (exchangeRateRaw != null &&
                (exchangeRateRaw !is Float || !exchangeRateRaw.isFinite() || exchangeRateRaw <= 0f)
            ) {
                replaceFloat(USD_TO_CNY_EXCHANGE_RATE, 7.2f)
            }

            originalValues.forEach { (name, raw) ->
                when {
                    name.startsWith("token_input_") ||
                        name.startsWith("token_cached_input_") ||
                        name.startsWith("token_output_") -> {
                        val normalized = readTokenCountValue(raw).coerceAtLeast(0L)
                        if (raw !is Long || raw != normalized) replaceLong(name, normalized)
                    }
                    name.startsWith("request_count_") -> {
                        val normalized = (raw as? Int)?.coerceAtLeast(0) ?: 0
                        if (raw !is Int || raw != normalized) {
                            removeName(name)
                            mutable[intPreferencesKey(name)] = normalized
                            issues += name
                        }
                    }
                    name.startsWith("model_input_price_") ||
                        name.startsWith("model_cached_input_price_") ||
                        name.startsWith("model_output_price_") ||
                        name.startsWith("price_per_request_") -> {
                        val normalized =
                            (raw as? Float)?.takeIf { it.isFinite() && it >= 0f } ?: 0f
                        if (raw !is Float || raw != normalized) {
                            removeName(name)
                            mutable[floatPreferencesKey(name)] = normalized
                            issues += name
                        }
                    }
                    name.startsWith("billing_mode_") -> {
                        val normalized =
                            (raw as? String)?.uppercase(Locale.ROOT)
                                ?.takeIf { it == "TOKEN" || it == "COUNT" }
                                ?: "TOKEN"
                        if (raw !is String || raw != normalized) {
                            removeName(name)
                            mutable[stringPreferencesKey(name)] = normalized
                            issues += name
                        }
                    }
                }
            }

            PreferenceStateRepairResult(mutable.toPreferences(), issues)
        }

    val safBookmarksFlow: Flow<List<SafBookmark>> =
        context.apiDataStore.data.map { preferences ->
            val raw = preferences[SAF_BOOKMARKS_JSON] ?: "[]"
            try {
                decodeSafBookmarks(raw).map(PersistedSafBookmark::value)
            } catch (e: Exception) {
                AppLogger.e("ApiPreferences", "Invalid SAF bookmarks reached a live reader", e)
                emptyList()
            }
        }

    suspend fun addSafBookmark(uri: String, name: String) {
        context.apiDataStore.edit { preferences ->
            val existing = decodeSafBookmarks(preferences[SAF_BOOKMARKS_JSON] ?: "[]")
            val bookmark = SafBookmark(uri = uri, name = name)
            val previous = existing.firstOrNull { it.value.uri == uri }
            val encodedBookmark =
                if (previous == null) {
                    persistenceJson.encodeToJsonElement(bookmark).jsonObject
                } else {
                    mergeNormalizedJsonFields(
                        persisted = previous.element,
                        decoded = persistenceJson.encodeToJsonElement(previous.value),
                        normalized = persistenceJson.encodeToJsonElement(bookmark)
                    ).jsonObject
                }
            val updated =
                (existing.filterNot { it.value.uri == uri } +
                    PersistedSafBookmark(bookmark, encodedBookmark))
                    .sortedBy { it.value.name.lowercase(Locale.ROOT) }
            preferences[SAF_BOOKMARKS_JSON] =
                JsonArray(updated.map(PersistedSafBookmark::element)).toString()
        }
    }

    suspend fun removeSafBookmark(uri: String) {
        context.apiDataStore.edit { preferences ->
            val existing = decodeSafBookmarks(preferences[SAF_BOOKMARKS_JSON] ?: "[]")
            val updated = existing.filterNot { it.value.uri == uri }
            preferences[SAF_BOOKMARKS_JSON] =
                JsonArray(updated.map(PersistedSafBookmark::element)).toString()
        }
    }

    val featureTogglesFlow: Flow<Map<String, Boolean>> =
        context.apiDataStore.data.map { preferences ->
            val json = preferences[FEATURE_TOGGLES_JSON] ?: DEFAULT_FEATURE_TOGGLES_JSON
            runCatching {
                Json.decodeFromString<Map<String, Boolean>>(json)
            }.getOrElse { emptyMap() }
        }

    fun featureToggleFlow(featureKey: String, defaultValue: Boolean = false): Flow<Boolean> {
        val normalizedKey = featureKey.trim()
        if (normalizedKey.isEmpty()) {
            return featureTogglesFlow.map { defaultValue }
        }
        return featureTogglesFlow.map { toggles ->
            toggles[normalizedKey] ?: defaultValue
        }
    }

    // Get Keep Screen On setting as Flow
    val keepScreenOnFlow: Flow<Boolean> =
            context.apiDataStore.data.map { preferences ->
                preferences[KEEP_SCREEN_ON] ?: DEFAULT_KEEP_SCREEN_ON
            }

    // Flow for Thinking Mode
    val enableThinkingModeFlow: Flow<Boolean> =
        context.apiDataStore.data.map { preferences ->
            preferences[ENABLE_THINKING_MODE] ?: DEFAULT_ENABLE_THINKING_MODE
        }

    val thinkingQualityLevelFlow: Flow<Int> =
        context.apiDataStore.data.map { preferences ->
            (preferences[THINKING_QUALITY_LEVEL] ?: DEFAULT_THINKING_QUALITY_LEVEL).coerceIn(
                MIN_THINKING_QUALITY_LEVEL,
                MAX_THINKING_QUALITY_LEVEL
            )
        }

    // Flow for Memory Auto Update
    val enableMemoryAutoUpdateFlow: Flow<Boolean> =
        context.apiDataStore.data.map { preferences ->
            preferences[ENABLE_MEMORY_AUTO_UPDATE] ?: DEFAULT_ENABLE_MEMORY_AUTO_UPDATE
        }

    // Flow for Auto Read
    val enableAutoReadFlow: Flow<Boolean> =
        context.apiDataStore.data.map { preferences ->
            preferences[ENABLE_AUTO_READ] ?: DEFAULT_ENABLE_AUTO_READ
        }

    // Flow for Tools Enable/Disable
    val enableToolsFlow: Flow<Boolean> =
        context.apiDataStore.data.map { preferences ->
            preferences[ENABLE_TOOLS] ?: DEFAULT_ENABLE_TOOLS
        }

    // Flow for per-tool prompt visibility
    val toolPromptVisibilityFlow: Flow<Map<String, Boolean>> =
        context.apiDataStore.data.map { preferences ->
            val json = preferences[TOOL_PROMPT_VISIBILITY_JSON] ?: DEFAULT_TOOL_PROMPT_VISIBILITY_JSON
            runCatching {
                Json.decodeFromString<Map<String, Boolean>>(json)
            }.getOrElse { emptyMap() }
        }

    // Flow for per-tool prompt order
    val toolPromptOrderFlow: Flow<List<String>> =
        context.apiDataStore.data.map { preferences ->
            val json = preferences[TOOL_PROMPT_ORDER_JSON] ?: DEFAULT_TOOL_PROMPT_ORDER_JSON
            runCatching {
                Json.decodeFromString<List<String>>(json)
            }.getOrElse { emptyList() }
        }

    // Flow for plugin order
    val pluginOrderFlow: Flow<List<String>> =
        context.apiDataStore.data.map { preferences ->
            val json = preferences[PLUGIN_ORDER_JSON] ?: DEFAULT_TOOL_PROMPT_ORDER_JSON
            runCatching {
                Json.decodeFromString<List<String>>(json)
            }.getOrElse { emptyList() }
        }

    // Flow for skill order
    val skillOrderFlow: Flow<List<String>> =
        context.apiDataStore.data.map { preferences ->
            val json = preferences[SKILL_ORDER_JSON] ?: DEFAULT_TOOL_PROMPT_ORDER_JSON
            runCatching {
                Json.decodeFromString<List<String>>(json)
            }.getOrElse { emptyList() }
        }

    // Flow for Disable Stream Output
    val disableStreamOutputFlow: Flow<Boolean> =
        context.apiDataStore.data.map { preferences ->
            preferences[DISABLE_STREAM_OUTPUT] ?: DEFAULT_DISABLE_STREAM_OUTPUT
        }

    // Flow for Disable User Preference Description
    val disableUserPreferenceDescriptionFlow: Flow<Boolean> =
        context.apiDataStore.data.map { preferences ->
            preferences[DISABLE_USER_PREFERENCE_DESCRIPTION] ?: DEFAULT_DISABLE_USER_PREFERENCE_DESCRIPTION
        }

    // Custom System Prompt Template Flow
    val customSystemPromptTemplateFlow: Flow<String> =
            context.apiDataStore.data.map { preferences ->
                preferences[CUSTOM_SYSTEM_PROMPT_TEMPLATE] ?: DEFAULT_SYSTEM_PROMPT_TEMPLATE
            }

    val maxImageHistoryUserTurnsFlow: Flow<Int> =
        context.apiDataStore.data.map { preferences ->
            preferences[MAX_IMAGE_HISTORY_USER_TURNS] ?: DEFAULT_MAX_IMAGE_HISTORY_USER_TURNS
        }

    val maxMediaHistoryUserTurnsFlow: Flow<Int> =
        context.apiDataStore.data.map { preferences ->
            preferences[MAX_MEDIA_HISTORY_USER_TURNS] ?: DEFAULT_MAX_MEDIA_HISTORY_USER_TURNS
        }

    suspend fun saveFeatureToggle(featureKey: String, isEnabled: Boolean) {
        val normalizedKey = featureKey.trim()
        if (normalizedKey.isEmpty()) return

        context.apiDataStore.edit { preferences ->
            val currentMap =
                runCatching {
                    val json = preferences[FEATURE_TOGGLES_JSON] ?: DEFAULT_FEATURE_TOGGLES_JSON
                    Json.decodeFromString<Map<String, Boolean>>(json)
                }.getOrElse { emptyMap() }

            preferences[FEATURE_TOGGLES_JSON] =
                Json.encodeToString(currentMap + (normalizedKey to isEnabled))
        }
    }

    // Save Keep Screen On setting
    suspend fun saveKeepScreenOn(isEnabled: Boolean) {
        context.apiDataStore.edit { preferences -> preferences[KEEP_SCREEN_ON] = isEnabled }
    }

    // Save Thinking Mode setting
    suspend fun saveEnableThinkingMode(isEnabled: Boolean) {
        context.apiDataStore.edit { preferences -> preferences[ENABLE_THINKING_MODE] = isEnabled }
    }

    suspend fun saveThinkingQualityLevel(level: Int) {
        context.apiDataStore.edit { preferences ->
            preferences[THINKING_QUALITY_LEVEL] = level.coerceIn(
                MIN_THINKING_QUALITY_LEVEL,
                MAX_THINKING_QUALITY_LEVEL
            )
        }
    }

    suspend fun updateThinkingSettings(
        enableThinkingMode: Boolean? = null,
        thinkingQualityLevel: Int? = null
    ) {
        context.apiDataStore.edit { preferences ->
            enableThinkingMode?.let { preferences[ENABLE_THINKING_MODE] = it }

            thinkingQualityLevel?.let {
                preferences[THINKING_QUALITY_LEVEL] = it.coerceIn(
                    MIN_THINKING_QUALITY_LEVEL,
                    MAX_THINKING_QUALITY_LEVEL
                )
            }
        }
    }

    // Save Memory Auto Update setting
    suspend fun saveEnableMemoryAutoUpdate(isEnabled: Boolean) {
        context.apiDataStore.edit { preferences -> preferences[ENABLE_MEMORY_AUTO_UPDATE] = isEnabled }
    }

    // Save Auto Read setting
    suspend fun saveEnableAutoRead(isEnabled: Boolean) {
        context.apiDataStore.edit { preferences -> preferences[ENABLE_AUTO_READ] = isEnabled }
    }

    // Save Tools Enable/Disable setting
    suspend fun saveEnableTools(isEnabled: Boolean) {
        context.apiDataStore.edit { preferences -> preferences[ENABLE_TOOLS] = isEnabled }
    }

    // Save prompt visibility for a single tool
    suspend fun saveToolPromptVisibility(toolName: String, isVisible: Boolean) {
        context.apiDataStore.edit { preferences ->
            val currentMap = runCatching {
                val json = preferences[TOOL_PROMPT_VISIBILITY_JSON] ?: DEFAULT_TOOL_PROMPT_VISIBILITY_JSON
                Json.decodeFromString<Map<String, Boolean>>(json)
            }.getOrElse { emptyMap() }
            preferences[TOOL_PROMPT_VISIBILITY_JSON] = Json.encodeToString(currentMap + (toolName to isVisible))
        }
    }

    // Save prompt visibility map for all tools
    suspend fun saveToolPromptVisibilityMap(visibilityMap: Map<String, Boolean>) {
        context.apiDataStore.edit { preferences ->
            preferences[TOOL_PROMPT_VISIBILITY_JSON] = Json.encodeToString(visibilityMap)
        }
    }

    suspend fun getToolPromptVisibilityMap(): Map<String, Boolean> {
        val preferences = context.apiDataStore.data.first()
        val json = preferences[TOOL_PROMPT_VISIBILITY_JSON] ?: DEFAULT_TOOL_PROMPT_VISIBILITY_JSON
        return runCatching {
            Json.decodeFromString<Map<String, Boolean>>(json)
        }.getOrElse { emptyMap() }
    }

    // Save tool prompt order (list of tool names)
    suspend fun saveToolPromptOrder(order: List<String>) {
        context.apiDataStore.edit { preferences ->
            preferences[TOOL_PROMPT_ORDER_JSON] = Json.encodeToString(order)
        }
    }

    suspend fun getToolPromptOrder(): List<String> {
        val preferences = context.apiDataStore.data.first()
        val json = preferences[TOOL_PROMPT_ORDER_JSON] ?: DEFAULT_TOOL_PROMPT_ORDER_JSON
        return runCatching {
            Json.decodeFromString<List<String>>(json)
        }.getOrElse { emptyList() }
    }

    // Save plugin order (list of package names)
    suspend fun savePluginOrder(order: List<String>) {
        context.apiDataStore.edit { preferences ->
            preferences[PLUGIN_ORDER_JSON] = Json.encodeToString(order)
        }
    }

    suspend fun getPluginOrder(): List<String> {
        val preferences = context.apiDataStore.data.first()
        val json = preferences[PLUGIN_ORDER_JSON] ?: DEFAULT_TOOL_PROMPT_ORDER_JSON
        return runCatching {
            Json.decodeFromString<List<String>>(json)
        }.getOrElse { emptyList() }
    }

    // Save skill order (list of skill names)
    suspend fun saveSkillOrder(order: List<String>) {
        context.apiDataStore.edit { preferences ->
            preferences[SKILL_ORDER_JSON] = Json.encodeToString(order)
        }
    }

    suspend fun getSkillOrder(): List<String> {
        val preferences = context.apiDataStore.data.first()
        val json = preferences[SKILL_ORDER_JSON] ?: DEFAULT_TOOL_PROMPT_ORDER_JSON
        return runCatching {
            Json.decodeFromString<List<String>>(json)
        }.getOrElse { emptyList() }
    }

    // Save Disable Stream Output setting
    suspend fun saveDisableStreamOutput(isDisabled: Boolean) {
        context.apiDataStore.edit { preferences -> preferences[DISABLE_STREAM_OUTPUT] = isDisabled }
    }

    // Save Disable User Preference Description setting
    suspend fun saveDisableUserPreferenceDescription(isDisabled: Boolean) {
        context.apiDataStore.edit { preferences ->
            preferences[DISABLE_USER_PREFERENCE_DESCRIPTION] = isDisabled
        }
    }

    // Save custom system prompt template
    suspend fun saveCustomSystemPromptTemplate(template: String) {
        context.apiDataStore.edit { preferences ->
            preferences[CUSTOM_SYSTEM_PROMPT_TEMPLATE] = template
        }
    }

    // Reset custom system prompt template to default
    suspend fun resetCustomSystemPromptTemplate() {
        context.apiDataStore.edit { preferences ->
            preferences[CUSTOM_SYSTEM_PROMPT_TEMPLATE] = DEFAULT_SYSTEM_PROMPT_TEMPLATE
        }
    }

    /** One read of the released main token data before ownership moves to Room. */
    suspend fun readTokenStatsMigrationSnapshot(): TokenStatsMigrationSnapshot {
        val preferences = context.apiDataStore.data.first()
        val counterPrefixes = listOf(
            "token_input_",
            "token_cached_input_",
            "token_output_",
            "request_count_",
        )
        val encodedTotals = linkedSetOf<String>()
        val encodedPrices = linkedSetOf<String>()
        preferences.asMap().keys.forEach { key ->
            counterPrefixes.firstOrNull { key.name.startsWith(it) }?.let { prefix ->
                encodedTotals += key.name.removePrefix(prefix)
            }
            RELEASED_MODEL_PRICE_KEY_PREFIXES
                .firstOrNull { key.name.startsWith(it) }
                ?.let { prefix -> encodedPrices += key.name.removePrefix(prefix) }
        }
        val providerAliases = releasedTokenProviderAliases()
        val totals = encodedTotals.mapNotNull { encoded ->
            val key = ReleasedProviderModelKeyDecoder.decode(encoded, providerAliases)
            val input = readTokenCount(preferences, getTokenInputKey(key.storedProviderModel).name)
            val cached = readTokenCount(preferences, getTokenCachedInputKey(key.storedProviderModel).name)
            val output = readTokenCount(preferences, getTokenOutputKey(key.storedProviderModel).name)
            val requestCount = preferences.asMap().entries
                .firstOrNull { it.key.name == getRequestCountKey(key.storedProviderModel).name }
                ?.value
                .let { it as? Number }
                ?.toLong()
                ?.coerceAtLeast(0L)
                ?: 0L
            ReleasedTokenUsageTotal(
                provider = key.provider,
                model = key.model,
                inputTokens = input,
                cachedInputTokens = cached,
                outputTokens = output,
                requestCount = requestCount,
            ).takeIf { input > 0L || cached > 0L || output > 0L || requestCount > 0L }
        }.groupBy { it.provider to it.model }
            .map { (_, totals) -> totals.reduce(ReleasedTokenUsageTotal::plus) }
        val prices = encodedPrices.mapNotNull { encoded ->
            val key = ReleasedProviderModelKeyDecoder.decode(encoded, providerAliases)
            val billingMode = preferences[getBillingModeKey(key.storedProviderModel)]?.let(BillingMode::valueOf)
            val inputPrice = positivePrice(preferences[getModelInputPriceKey(key.storedProviderModel)])
            val cachedInputPrice = positivePrice(preferences[getModelCachedInputPriceKey(key.storedProviderModel)])
            val outputPrice = positivePrice(preferences[getModelOutputPriceKey(key.storedProviderModel)])
            val pricePerRequest = positivePrice(preferences[getPricePerRequestKey(key.storedProviderModel)])
            if (
                billingMode == null &&
                    inputPrice == null &&
                    cachedInputPrice == null &&
                    outputPrice == null &&
                    pricePerRequest == null
            ) {
                return@mapNotNull null
            }
            val settings = ModelPriceSettings(
                billingMode = billingMode,
                currency = if (billingMode == BillingMode.COUNT) PricingCurrency.CNY else PricingCurrency.USD,
                inputPricePerMillion = inputPrice,
                cachedInputPricePerMillion = cachedInputPrice,
                outputPricePerMillion = outputPrice,
                pricePerRequest = pricePerRequest,
            )
            ReleasedTokenPriceSetting(key.provider, key.model, settings)
        }.groupBy { it.provider to it.model }
            .map { (identity, prices) ->
                val settings = prices.map(ReleasedTokenPriceSetting::settings).distinct()
                require(settings.size == 1) {
                    "Conflicting released prices for ${identity.first}:${identity.second}"
                }
                ReleasedTokenPriceSetting(identity.first, identity.second, settings.single())
            }
        return TokenStatsMigrationSnapshot(
            totals = totals,
            prices = prices,
            usdToCnyRate = validReleasedUsdToCnyRate(preferences[USD_TO_CNY_EXCHANGE_RATE]),
        )
    }

    /** Remove released keys and every unpublished token-statistics key after import. */
    suspend fun clearMigratedTokenStatsData() {
        context.apiDataStore.edit { preferences ->
            val keyPrefixes = listOf(
                "token_input_",
                "token_cached_input_",
                "token_output_",
                "request_count_",
                "model_input_price_",
                "model_cached_input_price_",
                "model_cache_write_price_",
                "model_output_price_",
                "model_pricing_currency_",
                "billing_mode_",
                "price_per_request_",
                "stats_",
            )
            val keys = preferences.asMap().keys.filter { key ->
                key == USD_TO_CNY_EXCHANGE_RATE || keyPrefixes.any(key.name::startsWith)
            }
            keys.forEach { key ->
                @Suppress("UNCHECKED_CAST")
                preferences.remove(key as Preferences.Key<Any>)
            }
        }
    }

    private fun readTokenCount(preferences: Preferences, keyName: String): Long {
        val values = preferences.asMap().entries
                .filter { it.key.name == keyName }
                .map { it.value }
        val value = values.firstOrNull { it is Long } ?: values.firstOrNull()
        return readTokenCountValue(value)
    }

    private fun readTokenCountValue(value: Any?): Long {
        return when (value) {
            is Long -> value
            is Int -> if (value < 0) value.toLong() and 0xFFFF_FFFFL else value.toLong()
            else -> 0L
        }
    }

    private suspend fun releasedTokenProviderAliases(): Map<String, String> {
        val registered = toolPkgProviderAliasesProvider?.invoke()
            ?: ToolPkgAiProviderRegistry.releasedTokenProviderAliases()
        val configured = configuredProviderIdsProvider?.invoke()
            ?: ModelConfigManager(context).getAllConfigSummaries().map { it.apiProviderTypeId }
        val configuredAliases =
            configured
                .map(String::trim)
                .filter(String::isNotEmpty)
                .filter { ApiProviderType.fromProviderTypeId(it) == null }
                .associateWith { it }
        return buildMap {
            configuredAliases.forEach { (providerId, identity) ->
                put(providerId, identity)
                put("TOOLPKG_${providerId.lowercase()}", identity)
            }
            // The active registration supplies the display identity used by new requests.
            putAll(registered)
        }
    }

    private fun splitProviderModel(providerModel: String): Pair<String, String>? {
        val separator = providerModel.indexOf(':')
        if (separator <= 0 || separator == providerModel.lastIndex) return null
        return providerModel.substring(0, separator) to providerModel.substring(separator + 1)
    }

    private fun positivePrice(value: Float?): Double? =
        value?.toDouble()?.takeIf { it.isFinite() && it > 0.0 }

    suspend fun saveMaxImageHistoryUserTurns(turns: Int) {
        context.apiDataStore.edit { preferences ->
            preferences[MAX_IMAGE_HISTORY_USER_TURNS] = turns
        }
    }

    suspend fun saveMaxMediaHistoryUserTurns(turns: Int) {
        context.apiDataStore.edit { preferences ->
            preferences[MAX_MEDIA_HISTORY_USER_TURNS] = turns
        }
    }

    suspend fun getMaxImageHistoryUserTurns(): Int {
        val preferences = context.apiDataStore.data.first()
        return preferences[MAX_IMAGE_HISTORY_USER_TURNS] ?: DEFAULT_MAX_IMAGE_HISTORY_USER_TURNS
    }

    suspend fun getMaxMediaHistoryUserTurns(): Int {
        val preferences = context.apiDataStore.data.first()
        return preferences[MAX_MEDIA_HISTORY_USER_TURNS] ?: DEFAULT_MAX_MEDIA_HISTORY_USER_TURNS
    }

    suspend fun resetHistoryRetentionSettings() {
        context.apiDataStore.edit { preferences ->
            preferences[MAX_IMAGE_HISTORY_USER_TURNS] = DEFAULT_MAX_IMAGE_HISTORY_USER_TURNS
            preferences[MAX_MEDIA_HISTORY_USER_TURNS] = DEFAULT_MAX_MEDIA_HISTORY_USER_TURNS
        }
    }
}

data class TokenStatsMigrationSnapshot(
    val totals: List<ReleasedTokenUsageTotal>,
    val prices: List<ReleasedTokenPriceSetting>,
    val usdToCnyRate: Double?,
)

data class ReleasedTokenUsageTotal(
    val provider: String,
    val model: String,
    val inputTokens: Long,
    val cachedInputTokens: Long,
    val outputTokens: Long,
    val requestCount: Long,
) {
    operator fun plus(other: ReleasedTokenUsageTotal): ReleasedTokenUsageTotal {
        require(provider == other.provider && model == other.model)
        return copy(
            inputTokens = Math.addExact(inputTokens, other.inputTokens),
            cachedInputTokens = Math.addExact(cachedInputTokens, other.cachedInputTokens),
            outputTokens = Math.addExact(outputTokens, other.outputTokens),
            requestCount = Math.addExact(requestCount, other.requestCount),
        )
    }
}

data class ReleasedTokenPriceSetting(
    val provider: String,
    val model: String,
    val settings: ModelPriceSettings,
)
