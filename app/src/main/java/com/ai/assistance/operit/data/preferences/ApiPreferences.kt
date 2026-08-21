package com.ai.assistance.operit.data.preferences

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ai.assistance.operit.data.persistence.PreferenceStateRepairResult
import com.ai.assistance.operit.data.persistence.PreferenceStoreCatalog
import com.ai.assistance.operit.data.persistence.mergeNormalizedJsonFields
import com.ai.assistance.operit.data.persistence.recoverablePreferencesDataStore
import com.ai.assistance.operit.data.persistence.repairPreferenceState
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ParameterCategory
import com.ai.assistance.operit.data.model.ParameterValueType
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

// Define the DataStore at the module level
private val Context.apiDataStore: DataStore<Preferences> by
        recoverablePreferencesDataStore(name = "api_settings")

class ApiPreferences private constructor(private val context: Context) {

    private val persistenceJson = Json { ignoreUnknownKeys = true }

    // Define our preferences keys
    companion object {
        @Volatile
        private var INSTANCE: ApiPreferences? = null

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

            val booleanKeys =
                listOf(
                    // DataStore 1.0 defines Key.to as Preferences.Pair, which is not destructurable.
                    kotlin.Pair(KEEP_SCREEN_ON, DEFAULT_KEEP_SCREEN_ON),
                    kotlin.Pair(ENABLE_THINKING_MODE, DEFAULT_ENABLE_THINKING_MODE),
                    kotlin.Pair(ENABLE_MEMORY_AUTO_UPDATE, DEFAULT_ENABLE_MEMORY_AUTO_UPDATE),
                    kotlin.Pair(ENABLE_AUTO_READ, DEFAULT_ENABLE_AUTO_READ),
                    kotlin.Pair(ENABLE_TOOLS, DEFAULT_ENABLE_TOOLS),
                    kotlin.Pair(DISABLE_STREAM_OUTPUT, DEFAULT_DISABLE_STREAM_OUTPUT),
                    kotlin.Pair(
                        DISABLE_USER_PREFERENCE_DESCRIPTION,
                        DEFAULT_DISABLE_USER_PREFERENCE_DESCRIPTION
                    )
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
                kotlin.Pair(MAX_IMAGE_HISTORY_USER_TURNS, DEFAULT_MAX_IMAGE_HISTORY_USER_TURNS),
                kotlin.Pair(MAX_MEDIA_HISTORY_USER_TURNS, DEFAULT_MAX_MEDIA_HISTORY_USER_TURNS)
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
