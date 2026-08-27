package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.application.OperitApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.wakeWordPreferencesDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "wake_word_preferences")

class WakeWordPreferences(private val context: Context) {

    private val dataStore = context.wakeWordPreferencesDataStore

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Serializable
    enum class WakeRecognitionMode {
        STT,
        PERSONAL_TEMPLATE,
    }

    @Serializable
    data class PersonalWakeTemplate(
        val features: List<Float>,
    )

    companion object {
        private val KEY_ALWAYS_LISTENING_ENABLED = booleanPreferencesKey("always_listening_enabled")
        private val KEY_WAKE_PHRASE = stringPreferencesKey("wake_phrase")
        private val KEY_WAKE_PHRASE_REGEX_ENABLED = booleanPreferencesKey("wake_phrase_regex_enabled")
        private val KEY_WAKE_RECOGNITION_MODE = stringPreferencesKey("wake_recognition_mode")
        private val KEY_PERSONAL_WAKE_TEMPLATES_JSON = stringPreferencesKey("personal_wake_templates_json")
        private val KEY_VOICE_CALL_INACTIVITY_TIMEOUT_SECONDS =
            intPreferencesKey("voice_call_inactivity_timeout_seconds")
        private val KEY_WAKE_GREETING_ENABLED = booleanPreferencesKey("wake_greeting_enabled")
        private val KEY_WAKE_GREETING_TEXT = stringPreferencesKey("wake_greeting_text")

        private val KEY_WAKE_CREATE_NEW_CHAT_ON_WAKE_ENABLED =
            booleanPreferencesKey("wake_create_new_chat_on_wake_enabled")
        private val KEY_AUTO_NEW_CHAT_GROUP = stringPreferencesKey("auto_new_chat_group")

        private val KEY_VOICE_AUTO_ATTACH_ENABLED = booleanPreferencesKey("voice_auto_attach_enabled")
        private val KEY_VOICE_AUTO_ATTACH_ITEMS_JSON = stringPreferencesKey("voice_auto_attach_items_json")
        private val KEY_VOICE_AUTO_ATTACH_ITEMS_MIGRATION_VERSION =
            intPreferencesKey("voice_auto_attach_items_migration_version")

        const val DEFAULT_WAKE_PHRASE_REGEX_ENABLED = false
        const val DEFAULT_ALWAYS_LISTENING_ENABLED = false
        const val DEFAULT_WAKE_RECOGNITION_MODE = "stt"
        const val DEFAULT_VOICE_CALL_INACTIVITY_TIMEOUT_SECONDS = 5
        const val DEFAULT_WAKE_GREETING_ENABLED = true

        const val DEFAULT_WAKE_CREATE_NEW_CHAT_ON_WAKE_ENABLED = false

        // Default string values using R.string references
        val DEFAULT_WAKE_PHRASE: String by lazy {
            runCatching { OperitApplication.instance.getString(R.string.wake_word_default) }
                .getOrDefault("")
        }
        val DEFAULT_WAKE_GREETING_TEXT: String by lazy {
            runCatching { OperitApplication.instance.getString(R.string.wake_word_response) }
                .getOrDefault("")
        }
        val DEFAULT_AUTO_NEW_CHAT_GROUP: String by lazy {
            runCatching { OperitApplication.instance.getString(R.string.wake_word_global_assistant) }
                .getOrDefault("")
        }

        const val DEFAULT_VOICE_AUTO_ATTACH_ENABLED = true

        private const val LATEST_VOICE_AUTO_ATTACH_ITEMS_MIGRATION_VERSION = 1

        fun getDefaultVoiceAutoAttachItems(context: Context): List<VoiceAutoAttachItem> =
            listOf(
                VoiceAutoAttachItem(
                    id = "screen_ocr",
                    type = VoiceAutoAttachType.SCREEN_OCR,
                    enabled = true,
                    keywords = context.getString(R.string.wake_word_screen)
                ),
                VoiceAutoAttachItem(
                    id = "notifications",
                    type = VoiceAutoAttachType.NOTIFICATIONS,
                    enabled = true,
                    keywords = context.getString(R.string.wake_word_notification)
                ),
                VoiceAutoAttachItem(
                    id = "location",
                    type = VoiceAutoAttachType.LOCATION,
                    enabled = true,
                    keywords = context.getString(R.string.wake_word_which_location)
                ),
                VoiceAutoAttachItem(
                    id = "time",
                    type = VoiceAutoAttachType.TIME,
                    enabled = true,
                    keywords = context.getString(R.string.wake_word_time_query)
                )
            )
    }

    @Serializable
    enum class VoiceAutoAttachType {
        SCREEN_OCR,
        NOTIFICATIONS,
        LOCATION,
        TIME
    }

    @Serializable
    data class VoiceAutoAttachItem(
        val id: String,
        val type: VoiceAutoAttachType,
        val enabled: Boolean = true,
        val keywords: String = "",
        val params: Map<String, String> = emptyMap()
    )

    val alwaysListeningEnabledFlow: Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[KEY_ALWAYS_LISTENING_ENABLED] ?: DEFAULT_ALWAYS_LISTENING_ENABLED
        }

    val wakePhraseFlow: Flow<String> =
        dataStore.data.map { prefs ->
            prefs[KEY_WAKE_PHRASE] ?: DEFAULT_WAKE_PHRASE
        }

    val wakePhraseRegexEnabledFlow: Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[KEY_WAKE_PHRASE_REGEX_ENABLED] ?: DEFAULT_WAKE_PHRASE_REGEX_ENABLED
        }

    val wakeRecognitionModeFlow: Flow<WakeRecognitionMode> =
        dataStore.data.map { prefs ->
            val raw = prefs[KEY_WAKE_RECOGNITION_MODE] ?: DEFAULT_WAKE_RECOGNITION_MODE
            when (raw.lowercase()) {
                "personal_template" -> WakeRecognitionMode.PERSONAL_TEMPLATE
                "stt" -> WakeRecognitionMode.STT
                else -> WakeRecognitionMode.STT
            }
        }

    val personalWakeTemplatesFlow: Flow<List<PersonalWakeTemplate>> =
        dataStore.data.map { prefs ->
            val raw = prefs[KEY_PERSONAL_WAKE_TEMPLATES_JSON]
            if (raw.isNullOrBlank()) {
                emptyList()
            } else {
                runCatching { json.decodeFromString<List<PersonalWakeTemplate>>(raw) }
                    .getOrDefault(emptyList())
            }
        }

    val voiceCallInactivityTimeoutSecondsFlow: Flow<Int> =
        dataStore.data.map { prefs ->
            prefs[KEY_VOICE_CALL_INACTIVITY_TIMEOUT_SECONDS]
                ?: DEFAULT_VOICE_CALL_INACTIVITY_TIMEOUT_SECONDS
        }

    val wakeGreetingEnabledFlow: Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[KEY_WAKE_GREETING_ENABLED] ?: DEFAULT_WAKE_GREETING_ENABLED
        }

    val wakeGreetingTextFlow: Flow<String> =
        dataStore.data.map { prefs ->
            prefs[KEY_WAKE_GREETING_TEXT] ?: DEFAULT_WAKE_GREETING_TEXT
        }

    val wakeCreateNewChatOnWakeEnabledFlow: Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[KEY_WAKE_CREATE_NEW_CHAT_ON_WAKE_ENABLED]
                ?: DEFAULT_WAKE_CREATE_NEW_CHAT_ON_WAKE_ENABLED
        }

    val autoNewChatGroupFlow: Flow<String> =
        dataStore.data.map { prefs ->
            prefs[KEY_AUTO_NEW_CHAT_GROUP] ?: DEFAULT_AUTO_NEW_CHAT_GROUP
        }

    val voiceAutoAttachEnabledFlow: Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[KEY_VOICE_AUTO_ATTACH_ENABLED] ?: DEFAULT_VOICE_AUTO_ATTACH_ENABLED
        }

    val voiceAutoAttachItemsFlow: Flow<List<VoiceAutoAttachItem>> =
        dataStore.data.map { prefs ->
            val raw = prefs[KEY_VOICE_AUTO_ATTACH_ITEMS_JSON]
            val defaultItems = getDefaultVoiceAutoAttachItems(context)
            if (raw.isNullOrBlank()) {
                defaultItems
            } else {
                runCatching { json.decodeFromString<List<VoiceAutoAttachItem>>(raw) }
                    .getOrDefault(defaultItems)
            }
        }

    suspend fun migrateVoiceAutoAttachItemsIfNeeded() {
        dataStore.edit { prefs ->
            val currentVersion = prefs[KEY_VOICE_AUTO_ATTACH_ITEMS_MIGRATION_VERSION] ?: 0
            if (currentVersion >= LATEST_VOICE_AUTO_ATTACH_ITEMS_MIGRATION_VERSION) return@edit

            val raw = prefs[KEY_VOICE_AUTO_ATTACH_ITEMS_JSON]
            if (raw.isNullOrBlank()) {
                prefs[KEY_VOICE_AUTO_ATTACH_ITEMS_MIGRATION_VERSION] =
                    LATEST_VOICE_AUTO_ATTACH_ITEMS_MIGRATION_VERSION
                return@edit
            }

            val existingItems =
                runCatching { json.decodeFromString<List<VoiceAutoAttachItem>>(raw) }
                    .getOrNull()
            if (existingItems == null) {
                prefs[KEY_VOICE_AUTO_ATTACH_ITEMS_MIGRATION_VERSION] =
                    LATEST_VOICE_AUTO_ATTACH_ITEMS_MIGRATION_VERSION
                return@edit
            }

            val usedTypes = existingItems.map { it.type }.toSet()
            val missingDefaults = getDefaultVoiceAutoAttachItems(context).filterNot { usedTypes.contains(it.type) }

            if (missingDefaults.isNotEmpty()) {
                prefs[KEY_VOICE_AUTO_ATTACH_ITEMS_JSON] =
                    json.encodeToString(existingItems + missingDefaults)
            }

            prefs[KEY_VOICE_AUTO_ATTACH_ITEMS_MIGRATION_VERSION] =
                LATEST_VOICE_AUTO_ATTACH_ITEMS_MIGRATION_VERSION
        }
    }

    suspend fun saveAlwaysListeningEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_ALWAYS_LISTENING_ENABLED] = enabled
        }
    }

    suspend fun saveWakePhrase(phrase: String) {
        dataStore.edit { prefs ->
            prefs[KEY_WAKE_PHRASE] = phrase
        }
    }

    suspend fun saveWakePhraseRegexEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_WAKE_PHRASE_REGEX_ENABLED] = enabled
        }
    }

    suspend fun saveWakeRecognitionMode(mode: WakeRecognitionMode) {
        dataStore.edit { prefs ->
            prefs[KEY_WAKE_RECOGNITION_MODE] =
                when (mode) {
                    WakeRecognitionMode.STT -> "stt"
                    WakeRecognitionMode.PERSONAL_TEMPLATE -> "personal_template"
                }
        }
    }

    suspend fun savePersonalWakeTemplates(templates: List<PersonalWakeTemplate>) {
        val raw = json.encodeToString(templates)
        dataStore.edit { prefs ->
            prefs[KEY_PERSONAL_WAKE_TEMPLATES_JSON] = raw
        }
    }

    suspend fun saveVoiceCallInactivityTimeoutSeconds(seconds: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_VOICE_CALL_INACTIVITY_TIMEOUT_SECONDS] = seconds
        }
    }

    suspend fun saveWakeGreetingEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_WAKE_GREETING_ENABLED] = enabled
        }
    }

    suspend fun saveWakeGreetingText(text: String) {
        dataStore.edit { prefs ->
            prefs[KEY_WAKE_GREETING_TEXT] = text
        }
    }

    suspend fun saveWakeCreateNewChatOnWakeEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_WAKE_CREATE_NEW_CHAT_ON_WAKE_ENABLED] = enabled
        }
    }

    suspend fun saveAutoNewChatGroup(group: String) {
        dataStore.edit { prefs ->
            prefs[KEY_AUTO_NEW_CHAT_GROUP] = group
        }
    }

    suspend fun saveVoiceAutoAttachEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_VOICE_AUTO_ATTACH_ENABLED] = enabled
        }
    }

    suspend fun saveVoiceAutoAttachItems(items: List<VoiceAutoAttachItem>) {
        val raw = json.encodeToString(items)
        dataStore.edit { prefs ->
            prefs[KEY_VOICE_AUTO_ATTACH_ITEMS_JSON] = raw
        }
    }
}
