package com.ai.assistance.operit.data.persistence

/**
 * Stable names of every released Preferences DataStore file.
 *
 * Keeping the names in one catalog makes duplicate ownership reviewable without changing the
 * on-disk paths used by existing installations.
 */
object PreferenceStoreCatalog {
    const val ANDROID_PERMISSION = "android_permission_preferences"
    const val API_SETTINGS = "api_settings"
    const val CHARACTER_CARDS = "character_cards"
    const val CHARACTER_GROUPS = "character_groups"
    const val CURRENT_CHAT_ID = "current_chat_id"
    const val CUSTOM_EMOJI = "custom_emoji_settings"
    const val DATABASE_BACKUP_SETTINGS = "database_backup_settings"
    const val DISPLAY_PREFERENCES = "display_preferences"
    const val EXTERNAL_HTTP_API = "external_http_api_preferences"
    const val FUNCTIONAL_CONFIGS = "functional_configs"
    const val GITHUB_AUTH = "github_auth_preferences"
    const val MODEL_CONFIGS = "model_configs"
    const val PERSONA_CARD_CHAT_HISTORY = "persona_card_chat_history"
    const val PROMPT_TAGS = "prompt_tags"
    const val SPEECH_SERVICES = "speech_services_preferences"
    const val TOOL_PERMISSIONS = "tool_permissions"
    const val UI_PREFERENCES = "ui_preferences"
    const val URL_CONFIG = "url_config"
    const val USER_PREFERENCES = "user_preferences"
    const val WAIFU_SETTINGS = "waifu_settings"
    const val WAKE_WORD = "wake_word_preferences"
    const val WEB_SESSION_BROWSER = "web_session_browser_store"

    val all: List<String> =
        listOf(
            ANDROID_PERMISSION,
            API_SETTINGS,
            CHARACTER_CARDS,
            CHARACTER_GROUPS,
            CURRENT_CHAT_ID,
            CUSTOM_EMOJI,
            DATABASE_BACKUP_SETTINGS,
            DISPLAY_PREFERENCES,
            EXTERNAL_HTTP_API,
            FUNCTIONAL_CONFIGS,
            GITHUB_AUTH,
            MODEL_CONFIGS,
            PERSONA_CARD_CHAT_HISTORY,
            PROMPT_TAGS,
            SPEECH_SERVICES,
            TOOL_PERMISSIONS,
            UI_PREFERENCES,
            URL_CONFIG,
            USER_PREFERENCES,
            WAIFU_SETTINGS,
            WAKE_WORD,
            WEB_SESSION_BROWSER
        )

    init {
        check(all.size == all.distinct().size) { "Duplicate Preferences DataStore name" }
    }
}
