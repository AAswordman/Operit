package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class GlobalThemeMode(val value: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun fromValue(raw: String): GlobalThemeMode = entries.first { it.value == raw }
    }
}

enum class GlobalChatStyle(val value: String) {
    CURSOR("cursor"),
    BUBBLE("bubble"),
    ;

    companion object {
        fun fromValue(raw: String): GlobalChatStyle = entries.first { it.value == raw }
    }
}

enum class GlobalInputStyle(val value: String) {
    AGENT("agent"),
    CLASSIC("classic"),
    ;

    companion object {
        fun fromValue(raw: String): GlobalInputStyle = entries.first { it.value == raw }
    }
}

data class GlobalPresentationSnapshot(
    val themeMode: GlobalThemeMode = GlobalThemeMode.SYSTEM,
    val fontScale: Float = DEFAULT_FONT_SCALE,
    val chatStyle: GlobalChatStyle = GlobalChatStyle.CURSOR,
    val inputStyle: GlobalInputStyle = GlobalInputStyle.AGENT,
    val showThinkingProcess: Boolean = true,
    val showStatusTags: Boolean = true,
    val showInputProcessingStatus: Boolean = true,
    val showChatFloatingDotsAnimation: Boolean = true,
    val showModelProvider: Boolean = false,
    val showModelName: Boolean = false,
    val showRoleName: Boolean = true,
    val showUserName: Boolean = true,
    val showMessageTokenStats: Boolean = false,
    val showMessageTimingStats: Boolean = false,
    val showMessageTimestamp: Boolean = false,
    val bubbleShowAvatar: Boolean = true,
    val bubbleWideLayoutEnabled: Boolean = false,
    val cursorUserBubbleFollowTheme: Boolean = true,
) {
    companion object {
        const val MIN_FONT_SCALE = 0.5f
        const val MAX_FONT_SCALE = 2.0f
        const val DEFAULT_FONT_SCALE = 1.0f

        fun default(): GlobalPresentationSnapshot = GlobalPresentationSnapshot()
    }
}

private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
private val KEY_FONT_SCALE = floatPreferencesKey("font_scale")
private val KEY_CHAT_STYLE = stringPreferencesKey("chat_style")
private val KEY_INPUT_STYLE = stringPreferencesKey("input_style")
private val KEY_SHOW_THINKING_PROCESS = booleanPreferencesKey("show_thinking_process")
private val KEY_SHOW_STATUS_TAGS = booleanPreferencesKey("show_status_tags")
private val KEY_SHOW_INPUT_PROCESSING_STATUS = booleanPreferencesKey("show_input_processing_status")
private val KEY_SHOW_CHAT_FLOATING_DOTS_ANIMATION =
    booleanPreferencesKey("show_chat_floating_dots_animation")
private val KEY_SHOW_MODEL_PROVIDER = booleanPreferencesKey("show_model_provider")
private val KEY_SHOW_MODEL_NAME = booleanPreferencesKey("show_model_name")
private val KEY_SHOW_ROLE_NAME = booleanPreferencesKey("show_role_name")
private val KEY_SHOW_USER_NAME = booleanPreferencesKey("show_user_name")
private val KEY_SHOW_MESSAGE_TOKEN_STATS = booleanPreferencesKey("show_message_token_stats")
private val KEY_SHOW_MESSAGE_TIMING_STATS = booleanPreferencesKey("show_message_timing_stats")
private val KEY_SHOW_MESSAGE_TIMESTAMP = booleanPreferencesKey("show_message_timestamp")
private val KEY_BUBBLE_SHOW_AVATAR = booleanPreferencesKey("bubble_show_avatar")
private val KEY_BUBBLE_WIDE_LAYOUT_ENABLED = booleanPreferencesKey("bubble_wide_layout_enabled")
private val KEY_CURSOR_USER_BUBBLE_FOLLOW_THEME =
    booleanPreferencesKey("cursor_user_bubble_follow_theme")

private val Context.globalPresentationDataStore by versionedPreferencesDataStore(
    name = "global_presentation",
    currentVersion = 1,
    createMigration = {
        preferenceSchemaMigration { version, _ ->
            check(version == 0) {
                "Unsupported global presentation schema migration from version $version"
            }
        }
    },
)

/**
 * Application-level presentation settings kept for end users while full visual theming moves
 * into declarative theme packages. Independent from per-target theme storage.
 */
class GlobalPresentationManager private constructor(private val context: Context) {
    val snapshotFlow: Flow<GlobalPresentationSnapshot> =
        context.globalPresentationDataStore.data.map(::readSnapshot)

    suspend fun setThemeMode(mode: GlobalThemeMode) {
        edit { it[KEY_THEME_MODE] = mode.value }
    }

    suspend fun setFontScale(scale: Float) {
        edit {
            it[KEY_FONT_SCALE] =
                scale.coerceIn(
                    GlobalPresentationSnapshot.MIN_FONT_SCALE,
                    GlobalPresentationSnapshot.MAX_FONT_SCALE,
                )
        }
    }

    suspend fun setChatStyle(style: GlobalChatStyle) {
        edit { it[KEY_CHAT_STYLE] = style.value }
    }

    suspend fun setInputStyle(style: GlobalInputStyle) {
        edit { it[KEY_INPUT_STYLE] = style.value }
    }

    suspend fun setShowThinkingProcess(value: Boolean) = edit { it[KEY_SHOW_THINKING_PROCESS] = value }

    suspend fun setShowStatusTags(value: Boolean) = edit { it[KEY_SHOW_STATUS_TAGS] = value }

    suspend fun setShowInputProcessingStatus(value: Boolean) =
        edit { it[KEY_SHOW_INPUT_PROCESSING_STATUS] = value }

    suspend fun setShowChatFloatingDotsAnimation(value: Boolean) =
        edit { it[KEY_SHOW_CHAT_FLOATING_DOTS_ANIMATION] = value }

    suspend fun setShowModelProvider(value: Boolean) = edit { it[KEY_SHOW_MODEL_PROVIDER] = value }

    suspend fun setShowModelName(value: Boolean) = edit { it[KEY_SHOW_MODEL_NAME] = value }

    suspend fun setShowRoleName(value: Boolean) = edit { it[KEY_SHOW_ROLE_NAME] = value }

    suspend fun setShowUserName(value: Boolean) = edit { it[KEY_SHOW_USER_NAME] = value }

    suspend fun setShowMessageTokenStats(value: Boolean) =
        edit { it[KEY_SHOW_MESSAGE_TOKEN_STATS] = value }

    suspend fun setShowMessageTimingStats(value: Boolean) =
        edit { it[KEY_SHOW_MESSAGE_TIMING_STATS] = value }

    suspend fun setShowMessageTimestamp(value: Boolean) =
        edit { it[KEY_SHOW_MESSAGE_TIMESTAMP] = value }

    suspend fun setBubbleShowAvatar(value: Boolean) = edit { it[KEY_BUBBLE_SHOW_AVATAR] = value }

    suspend fun setBubbleWideLayoutEnabled(value: Boolean) =
        edit { it[KEY_BUBBLE_WIDE_LAYOUT_ENABLED] = value }

    suspend fun setCursorUserBubbleFollowTheme(value: Boolean) =
        edit { it[KEY_CURSOR_USER_BUBBLE_FOLLOW_THEME] = value }

    private suspend fun edit(transform: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.globalPresentationDataStore.edit(transform)
    }

    companion object {
        @Volatile
        private var instance: GlobalPresentationManager? = null

        fun getInstance(context: Context): GlobalPresentationManager =
            instance ?: synchronized(this) {
                instance ?: GlobalPresentationManager(context.applicationContext).also {
                    instance = it
                }
            }
    }
}

private fun readSnapshot(preferences: Preferences): GlobalPresentationSnapshot =
    GlobalPresentationSnapshot(
        themeMode = GlobalThemeMode.fromValue(preferences[KEY_THEME_MODE] ?: GlobalThemeMode.SYSTEM.value),
        fontScale = (preferences[KEY_FONT_SCALE] ?: GlobalPresentationSnapshot.DEFAULT_FONT_SCALE)
            .coerceIn(
                GlobalPresentationSnapshot.MIN_FONT_SCALE,
                GlobalPresentationSnapshot.MAX_FONT_SCALE,
            ),
        chatStyle = GlobalChatStyle.fromValue(preferences[KEY_CHAT_STYLE] ?: GlobalChatStyle.CURSOR.value),
        inputStyle = GlobalInputStyle.fromValue(preferences[KEY_INPUT_STYLE] ?: GlobalInputStyle.AGENT.value),
        showThinkingProcess = preferences[KEY_SHOW_THINKING_PROCESS] ?: true,
        showStatusTags = preferences[KEY_SHOW_STATUS_TAGS] ?: true,
        showInputProcessingStatus = preferences[KEY_SHOW_INPUT_PROCESSING_STATUS] ?: true,
        showChatFloatingDotsAnimation = preferences[KEY_SHOW_CHAT_FLOATING_DOTS_ANIMATION] ?: true,
        showModelProvider = preferences[KEY_SHOW_MODEL_PROVIDER] ?: false,
        showModelName = preferences[KEY_SHOW_MODEL_NAME] ?: false,
        showRoleName = preferences[KEY_SHOW_ROLE_NAME] ?: true,
        showUserName = preferences[KEY_SHOW_USER_NAME] ?: true,
        showMessageTokenStats = preferences[KEY_SHOW_MESSAGE_TOKEN_STATS] ?: false,
        showMessageTimingStats = preferences[KEY_SHOW_MESSAGE_TIMING_STATS] ?: false,
        showMessageTimestamp = preferences[KEY_SHOW_MESSAGE_TIMESTAMP] ?: false,
        bubbleShowAvatar = preferences[KEY_BUBBLE_SHOW_AVATAR] ?: true,
        bubbleWideLayoutEnabled = preferences[KEY_BUBBLE_WIDE_LAYOUT_ENABLED] ?: false,
        cursorUserBubbleFollowTheme = preferences[KEY_CURSOR_USER_BUBBLE_FOLLOW_THEME] ?: true,
    )
