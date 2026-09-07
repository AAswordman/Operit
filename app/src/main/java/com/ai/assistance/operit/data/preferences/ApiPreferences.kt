package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.BillingMode
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ParameterCategory
import com.ai.assistance.operit.data.model.ParameterValueType
import com.ai.assistance.operit.data.stats.ModelPriceSettings
import com.ai.assistance.operit.data.stats.ReleasedProviderModelKey
import com.ai.assistance.operit.data.stats.ReleasedProviderModelKeyDecoder
import com.ai.assistance.operit.plugins.toolpkg.ToolPkgAiProviderRegistry
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Define the DataStore at the module level
private val Context.apiDataStore: DataStore<Preferences> by
        preferencesDataStore(name = "api_settings")

class ApiPreferences private constructor(private val context: Context) {

    // Define our preferences keys
    companion object {
        @Volatile
        private var INSTANCE: ApiPreferences? = null

        internal var toolPkgProviderAliasesProvider: (() -> Map<String, String>)? = null
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

        private fun getTokenInputKey(providerModel: String) =
            longPreferencesKey("token_input_${providerModel.replace(":", "_")}")
        private fun getTokenCachedInputKey(providerModel: String) =
            longPreferencesKey("token_cached_input_${providerModel.replace(":", "_")}")
        private fun getTokenOutputKey(providerModel: String) =
            longPreferencesKey("token_output_${providerModel.replace(":", "_")}")
        private fun getModelInputPriceKey(providerModel: String) =
            floatPreferencesKey("model_input_price_${providerModel.replace(":", "_")}")
        private fun getModelCachedInputPriceKey(providerModel: String) =
            floatPreferencesKey("model_cached_input_price_${providerModel.replace(":", "_")}")
        private fun getModelOutputPriceKey(providerModel: String) =
            floatPreferencesKey("model_output_price_${providerModel.replace(":", "_")}")
        private fun getRequestCountKey(providerModel: String) =
            intPreferencesKey("request_count_${providerModel.replace(":", "_")}")
        private fun getBillingModeKey(providerModel: String) =
            stringPreferencesKey("billing_mode_${providerModel.replace(":", "_")}")
        private fun getPricePerRequestKey(providerModel: String) =
            floatPreferencesKey("price_per_request_${providerModel.replace(":", "_")}")
        private val RELEASED_MODEL_PRICE_KEY_PREFIXES = listOf(
            "model_input_price_", "model_cached_input_price_", "model_output_price_",
            "billing_mode_", "price_per_request_",
        )
        private val USD_TO_CNY_EXCHANGE_RATE = floatPreferencesKey("usd_to_cny_exchange_rate")

        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val FEATURE_TOGGLES_JSON = stringPreferencesKey("feature_toggles_json")
        // Default values
        const val DEFAULT_FEATURE_TOGGLE_STATE = false
        const val DEFAULT_KEEP_SCREEN_ON = true
        // Key for the global thinking-mode toggle
        val ENABLE_THINKING_MODE = booleanPreferencesKey("enable_thinking_mode")

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

        // Default value for the global Thinking Mode toggle
        const val DEFAULT_ENABLE_THINKING_MODE = false

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

    val safBookmarksFlow: Flow<List<SafBookmark>> =
        context.apiDataStore.data.map { preferences ->
            val json = preferences[SAF_BOOKMARKS_JSON] ?: "[]"
            runCatching { Json.decodeFromString<List<SafBookmark>>(json) }.getOrElse { emptyList() }
        }

    suspend fun addSafBookmark(uri: String, name: String) {
        context.apiDataStore.edit { preferences ->
            val existing =
                runCatching {
                    val json = preferences[SAF_BOOKMARKS_JSON] ?: "[]"
                    Json.decodeFromString<List<SafBookmark>>(json)
                }.getOrElse { emptyList() }

            val updated = (existing.filterNot { it.uri == uri } + SafBookmark(uri = uri, name = name))
                .sortedBy { it.name.lowercase() }
            preferences[SAF_BOOKMARKS_JSON] = Json.encodeToString(updated)
        }
    }

    suspend fun removeSafBookmark(uri: String) {
        context.apiDataStore.edit { preferences ->
            val existing =
                runCatching {
                    val json = preferences[SAF_BOOKMARKS_JSON] ?: "[]"
                    Json.decodeFromString<List<SafBookmark>>(json)
                }.getOrElse { emptyList() }
            val updated = existing.filterNot { it.uri == uri }
            preferences[SAF_BOOKMARKS_JSON] = Json.encodeToString(updated)
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

    suspend fun updateThinkingSettings(
        enableThinkingMode: Boolean? = null
    ) {
        context.apiDataStore.edit { preferences ->
            enableThinkingMode?.let { preferences[ENABLE_THINKING_MODE] = it }
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

    /** Reads released cumulative statistics once before Room takes ownership. */
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
        val skippedLegacyKeys = linkedSetOf<String>()

        // These keys are from the pre-provider/model format. They cannot be assigned
        // accurately, so skip them while allowing the rest of the migration to finish.
        fun decodeReleasedKeyOrSkip(encoded: String): ReleasedProviderModelKey? {
            val decoded = ReleasedProviderModelKeyDecoder.decodeOrNull(encoded, providerAliases)
            if (decoded == null && skippedLegacyKeys.add(encoded)) {
                AppLogger.w(
                    "ApiPreferences",
                    "Skipping legacy token statistics key without provider/model: $encoded",
                )
            }
            return decoded
        }

        val totals = encodedTotals.mapNotNull { encoded ->
            val key = decodeReleasedKeyOrSkip(encoded) ?: return@mapNotNull null
            val input = readTokenCount(preferences, getTokenInputKey(key.storedProviderModel).name)
            val cached = readTokenCount(preferences, getTokenCachedInputKey(key.storedProviderModel).name)
            val output = readTokenCount(preferences, getTokenOutputKey(key.storedProviderModel).name)
            val requestCount = preferences.asMap().entries
                .firstOrNull { it.key.name == getRequestCountKey(key.storedProviderModel).name }
                ?.value
                ?.let { it as? Number }
                ?.toLong()
                ?.coerceAtLeast(0L)
                ?: 0L
            ReleasedTokenUsageTotal(key.provider, key.model, input, cached, output, requestCount)
                .takeIf { input > 0L || cached > 0L || output > 0L || requestCount > 0L }
        }.groupBy { it.provider to it.model }
            .map { (_, values) -> values.reduce(ReleasedTokenUsageTotal::plus) }
        val prices = encodedPrices.mapNotNull { encoded ->
            val key = decodeReleasedKeyOrSkip(encoded) ?: return@mapNotNull null
            val billingMode = preferences[getBillingModeKey(key.storedProviderModel)]?.let(BillingMode::valueOf)
            val inputPrice = positivePrice(preferences[getModelInputPriceKey(key.storedProviderModel)])
            val cachedInputPrice = positivePrice(preferences[getModelCachedInputPriceKey(key.storedProviderModel)])
            val outputPrice = positivePrice(preferences[getModelOutputPriceKey(key.storedProviderModel)])
            val pricePerRequest = positivePrice(preferences[getPricePerRequestKey(key.storedProviderModel)])
            if (billingMode == null && inputPrice == null && cachedInputPrice == null &&
                outputPrice == null && pricePerRequest == null) return@mapNotNull null
            ReleasedTokenPriceSetting(
                key.provider,
                key.model,
                ModelPriceSettings(
                    billingMode = billingMode,
                    currency = if (billingMode == BillingMode.COUNT) PricingCurrency.CNY else PricingCurrency.USD,
                    inputPricePerMillion = inputPrice,
                    cachedInputPricePerMillion = cachedInputPrice,
                    outputPricePerMillion = outputPrice,
                    pricePerRequest = pricePerRequest,
                )
            )
        }.groupBy { it.provider to it.model }.map { (identity, values) ->
            val settings = values.map(ReleasedTokenPriceSetting::settings).distinct()
            require(settings.size == 1) { "Conflicting released prices for ${identity.first}:${identity.second}" }
            ReleasedTokenPriceSetting(identity.first, identity.second, settings.single())
        }
        return TokenStatsMigrationSnapshot(
            totals = totals,
            prices = prices,
            usdToCnyRate = preferences[USD_TO_CNY_EXCHANGE_RATE]
                ?.takeIf { it.isFinite() && it > 0f }
                ?.toDouble(),
        )
    }

    /** Removes only old statistics keys after their Room import is complete. */
    suspend fun clearMigratedTokenStatsData() {
        context.apiDataStore.edit { preferences ->
            val prefixes = listOf(
                "token_input_", "token_cached_input_", "token_output_", "request_count_",
                "model_input_price_", "model_cached_input_price_", "model_cache_write_price_",
                "model_output_price_", "model_pricing_currency_", "billing_mode_",
                "price_per_request_", "stats_",
            )
            preferences.asMap().keys
                .filter { it == USD_TO_CNY_EXCHANGE_RATE || prefixes.any(it.name::startsWith) }
                .forEach { key ->
                    @Suppress("UNCHECKED_CAST")
                    preferences.remove(key as Preferences.Key<Any>)
                }
        }
    }

    private fun readTokenCount(preferences: Preferences, keyName: String): Long {
        val values = preferences.asMap().entries.filter { it.key.name == keyName }.map { it.value }
        return when (val value = values.firstOrNull { it is Long } ?: values.firstOrNull()) {
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
        return buildMap {
            configured.map(String::trim).filter(String::isNotEmpty)
                .filter { ApiProviderType.fromProviderTypeId(it) == null }
                .forEach { providerId ->
                    put(providerId, providerId)
                    put("TOOLPKG_${providerId.lowercase()}", providerId)
                }
            putAll(registered)
        }
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
