package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.model.LegacyUserProfile
import com.ai.assistance.operit.data.model.MemorySpace
import com.ai.assistance.operit.data.model.CharacterCardMemoryProfileBindingMode
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.util.LocaleUtils.LanguageCodes

private val Context.userPreferencesDataStore: DataStore<Preferences> by
        preferencesDataStore(name = "user_preferences")

// 向后兼容的全局实例访问方式
val preferencesManager: UserPreferencesManager
    get() = UserPreferencesManager.instance ?: throw IllegalStateException(
        "UserPreferencesManager not initialized. Call UserPreferencesManager.getInstance(context) first."
    )

fun initUserPreferencesManager(context: Context, defaultProfileName: String = "Default") {
    val manager = UserPreferencesManager.getInstance(context)

    // Migration must finish before the default memory space is created. Otherwise a fresh default
    // entry could hide released profile metadata that still owns existing ObjectBox databases.
    GlobalScope.launch {
        MemorySpaceProfileDocumentRepository.getInstance(context).initialize()
        manager.ensureDefaultMemorySpace(defaultProfileName)
    }
}

data class LegacyUserProfileSnapshot(
    val activeProfileId: String,
    val profiles: List<LegacyUserProfile>,
    val hasLegacyCategoryLocks: Boolean = false
)

class UserPreferencesManager private constructor(private val context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: UserPreferencesManager? = null

        internal val instance: UserPreferencesManager?
            get() = INSTANCE

        fun getInstance(context: Context): UserPreferencesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val appContext = context.applicationContext ?: context
                    UserPreferencesManager(appContext).also { INSTANCE = it }
                }
            }
        }

        // Released structured-profile keys. These are read only by schema-v2 migration.
        private val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        private val PROFILE_LIST = stringPreferencesKey("profile_list")

        // Memory spaces replace preference profiles while retaining their stable identifiers.
        private val ACTIVE_MEMORY_SPACE_ID = stringPreferencesKey("active_memory_space_id")
        private val MEMORY_SPACE_LIST = stringPreferencesKey("memory_space_list")

        // 应用语言设置
        private val APP_LANGUAGE = stringPreferencesKey("app_language")

        // 分类锁定状态
        private val BIRTH_DATE_LOCKED = booleanPreferencesKey("birth_date_locked")
        private val GENDER_LOCKED = booleanPreferencesKey("gender_locked")
        private val PERSONALITY_LOCKED = booleanPreferencesKey("personality_locked")
        private val IDENTITY_LOCKED = booleanPreferencesKey("identity_locked")
        private val OCCUPATION_LOCKED = booleanPreferencesKey("occupation_locked")
        private val AI_STYLE_LOCKED = booleanPreferencesKey("ai_style_locked")

        // 主题设置相关键
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val USE_SYSTEM_THEME = booleanPreferencesKey("use_system_theme")
        private val CUSTOM_PRIMARY_COLOR = intPreferencesKey("custom_primary_color")
        private val CUSTOM_SECONDARY_COLOR = intPreferencesKey("custom_secondary_color")
        private val USE_CUSTOM_COLORS = booleanPreferencesKey("use_custom_colors")
        private val CHARACTER_THEME_DEFAULT_MIGRATION_COMPLETED =
            booleanPreferencesKey("character_theme_default_migration_completed")
        private val USE_BACKGROUND_IMAGE = booleanPreferencesKey("use_background_image")
        private val BACKGROUND_IMAGE_URI = stringPreferencesKey("background_image_uri")
        private val BACKGROUND_IMAGE_OPACITY = floatPreferencesKey("background_image_opacity")

        // 背景媒体类型和视频设置
        private val BACKGROUND_MEDIA_TYPE = stringPreferencesKey("background_media_type")
        private val VIDEO_BACKGROUND_MUTED = booleanPreferencesKey("video_background_muted")
        private val VIDEO_BACKGROUND_LOOP = booleanPreferencesKey("video_background_loop")

        // 工具栏透明度设置
        private val TOOLBAR_TRANSPARENT = booleanPreferencesKey("toolbar_transparent")
        private val TOOLBAR_OPACITY = floatPreferencesKey("toolbar_opacity")

        // 侧滑菜单玻璃效果设置
        private val NAVIGATION_DRAWER_WATER_GLASS =
            booleanPreferencesKey("navigation_drawer_water_glass")
        private val NAVIGATION_DRAWER_BUTTON_LIQUID_GLASS =
            booleanPreferencesKey("navigation_drawer_button_liquid_glass")

        // 侧滑菜单背景色设置
        private val USE_CUSTOM_NAVIGATION_DRAWER_BACKGROUND_COLOR =
            booleanPreferencesKey("use_custom_navigation_drawer_background_color")
        private val CUSTOM_NAVIGATION_DRAWER_BACKGROUND_COLOR =
            intPreferencesKey("custom_navigation_drawer_background_color")

        // 侧滑菜单强调色设置（品牌标识/小标题/网络状态/分隔线共用）
        private val USE_CUSTOM_NAVIGATION_DRAWER_ACCENT_COLOR =
            booleanPreferencesKey("use_custom_navigation_drawer_accent_color")
        private val CUSTOM_NAVIGATION_DRAWER_ACCENT_COLOR =
            intPreferencesKey("custom_navigation_drawer_accent_color")
        
        // AppBar 自定义颜色设置
        private val USE_CUSTOM_APP_BAR_COLOR = booleanPreferencesKey("use_custom_app_bar_color")
        private val CUSTOM_APP_BAR_COLOR = intPreferencesKey("custom_app_bar_color")

        // 状态栏颜色设置
        private val USE_CUSTOM_STATUS_BAR_COLOR = booleanPreferencesKey("use_custom_status_bar_color")
        private val CUSTOM_STATUS_BAR_COLOR = intPreferencesKey("custom_status_bar_color")
        private val STATUS_BAR_TRANSPARENT = booleanPreferencesKey("status_bar_transparent")
        private val STATUS_BAR_OPACITY = floatPreferencesKey("status_bar_opacity")
        private val STATUS_BAR_HIDDEN = booleanPreferencesKey("status_bar_hidden")
        private val CHAT_HEADER_TRANSPARENT = booleanPreferencesKey("chat_header_transparent")
        private val CHAT_HEADER_OPACITY = floatPreferencesKey("chat_header_opacity")
        private val CHAT_INPUT_TRANSPARENT = booleanPreferencesKey("chat_input_transparent")
        private val CHAT_INPUT_OPACITY = floatPreferencesKey("chat_input_opacity")
        private val CHAT_INPUT_FLOATING = booleanPreferencesKey("chat_input_floating")
        private val CHAT_INPUT_LIQUID_GLASS = booleanPreferencesKey("chat_input_liquid_glass")
        private val CHAT_INPUT_WATER_GLASS = booleanPreferencesKey("chat_input_water_glass")

        // AppBar 内容颜色设置
        private val FORCE_APP_BAR_CONTENT_COLOR_ENABLED = booleanPreferencesKey("force_app_bar_content_color_enabled")
        private val APP_BAR_CONTENT_COLOR_MODE = stringPreferencesKey("app_bar_content_color_mode")

        // ChatHeader 图标颜色设置
        private val CHAT_HEADER_HISTORY_ICON_COLOR = intPreferencesKey("chat_header_history_icon_color")
        private val CHAT_HEADER_PIP_ICON_COLOR = intPreferencesKey("chat_header_pip_icon_color")
        private val CHAT_HEADER_OVERLAY_MODE = booleanPreferencesKey("chat_header_overlay_mode")

        // 背景模糊设置
        private val USE_BACKGROUND_BLUR = booleanPreferencesKey("use_background_blur")
        private val BACKGROUND_BLUR_RADIUS = floatPreferencesKey("background_blur_radius")

        // 字体设置
        private val USE_CUSTOM_FONT = booleanPreferencesKey("use_custom_font")
        private val FONT_TYPE = stringPreferencesKey("font_type")  // "system" or "file"
        private val SYSTEM_FONT_NAME = stringPreferencesKey("system_font_name")
        private val CUSTOM_FONT_PATH = stringPreferencesKey("custom_font_path")
        private val FONT_SCALE = floatPreferencesKey("font_scale")

        // Chat style preference
        private val CHAT_STYLE = stringPreferencesKey("chat_style")
        private val INPUT_STYLE = stringPreferencesKey("input_style")

        private val BUBBLE_SHOW_AVATAR = booleanPreferencesKey("bubble_show_avatar")
        private val BUBBLE_WIDE_LAYOUT_ENABLED =
            booleanPreferencesKey("bubble_wide_layout_enabled")
        private val CURSOR_USER_BUBBLE_FOLLOW_THEME =
            booleanPreferencesKey("cursor_user_bubble_follow_theme")
        private val CURSOR_USER_BUBBLE_LIQUID_GLASS =
            booleanPreferencesKey("cursor_user_bubble_liquid_glass")
        private val CURSOR_USER_BUBBLE_WATER_GLASS =
            booleanPreferencesKey("cursor_user_bubble_water_glass")
        private val CURSOR_USER_BUBBLE_COLOR = intPreferencesKey("cursor_user_bubble_color")
        private val BUBBLE_USER_BUBBLE_LIQUID_GLASS =
            booleanPreferencesKey("bubble_user_bubble_liquid_glass")
        private val BUBBLE_USER_BUBBLE_WATER_GLASS =
            booleanPreferencesKey("bubble_user_bubble_water_glass")
        private val BUBBLE_AI_BUBBLE_LIQUID_GLASS =
            booleanPreferencesKey("bubble_ai_bubble_liquid_glass")
        private val BUBBLE_AI_BUBBLE_WATER_GLASS =
            booleanPreferencesKey("bubble_ai_bubble_water_glass")
        private val BUBBLE_USER_BUBBLE_COLOR = intPreferencesKey("bubble_user_bubble_color")
        private val BUBBLE_AI_BUBBLE_COLOR = intPreferencesKey("bubble_ai_bubble_color")
        private val BUBBLE_USER_TEXT_COLOR = intPreferencesKey("bubble_user_text_color")
        private val BUBBLE_AI_TEXT_COLOR = intPreferencesKey("bubble_ai_text_color")
        private val BUBBLE_USER_USE_CUSTOM_FONT =
            booleanPreferencesKey("bubble_user_use_custom_font")
        private val BUBBLE_USER_FONT_TYPE = stringPreferencesKey("bubble_user_font_type")
        private val BUBBLE_USER_SYSTEM_FONT_NAME =
            stringPreferencesKey("bubble_user_system_font_name")
        private val BUBBLE_USER_CUSTOM_FONT_PATH =
            stringPreferencesKey("bubble_user_custom_font_path")
        private val BUBBLE_AI_USE_CUSTOM_FONT =
            booleanPreferencesKey("bubble_ai_use_custom_font")
        private val BUBBLE_AI_FONT_TYPE = stringPreferencesKey("bubble_ai_font_type")
        private val BUBBLE_AI_SYSTEM_FONT_NAME =
            stringPreferencesKey("bubble_ai_system_font_name")
        private val BUBBLE_AI_CUSTOM_FONT_PATH =
            stringPreferencesKey("bubble_ai_custom_font_path")
        private val BUBBLE_USER_USE_IMAGE = booleanPreferencesKey("bubble_user_use_image")
        private val BUBBLE_AI_USE_IMAGE = booleanPreferencesKey("bubble_ai_use_image")
        private val BUBBLE_USER_IMAGE_URI = stringPreferencesKey("bubble_user_image_uri")
        private val BUBBLE_AI_IMAGE_URI = stringPreferencesKey("bubble_ai_image_uri")
        private val BUBBLE_USER_IMAGE_CROP_LEFT = floatPreferencesKey("bubble_user_image_crop_left")
        private val BUBBLE_USER_IMAGE_CROP_TOP = floatPreferencesKey("bubble_user_image_crop_top")
        private val BUBBLE_USER_IMAGE_CROP_RIGHT = floatPreferencesKey("bubble_user_image_crop_right")
        private val BUBBLE_USER_IMAGE_CROP_BOTTOM = floatPreferencesKey("bubble_user_image_crop_bottom")
        private val BUBBLE_USER_IMAGE_REPEAT_START =
            floatPreferencesKey("bubble_user_image_repeat_start")
        private val BUBBLE_USER_IMAGE_REPEAT_END =
            floatPreferencesKey("bubble_user_image_repeat_end")
        private val BUBBLE_USER_IMAGE_REPEAT_Y_START =
            floatPreferencesKey("bubble_user_image_repeat_y_start")
        private val BUBBLE_USER_IMAGE_REPEAT_Y_END =
            floatPreferencesKey("bubble_user_image_repeat_y_end")
        private val BUBBLE_USER_IMAGE_SCALE =
            floatPreferencesKey("bubble_user_image_scale")
        private val BUBBLE_AI_IMAGE_CROP_LEFT = floatPreferencesKey("bubble_ai_image_crop_left")
        private val BUBBLE_AI_IMAGE_CROP_TOP = floatPreferencesKey("bubble_ai_image_crop_top")
        private val BUBBLE_AI_IMAGE_CROP_RIGHT = floatPreferencesKey("bubble_ai_image_crop_right")
        private val BUBBLE_AI_IMAGE_CROP_BOTTOM = floatPreferencesKey("bubble_ai_image_crop_bottom")
        private val BUBBLE_AI_IMAGE_REPEAT_START =
            floatPreferencesKey("bubble_ai_image_repeat_start")
        private val BUBBLE_AI_IMAGE_REPEAT_END =
            floatPreferencesKey("bubble_ai_image_repeat_end")
        private val BUBBLE_AI_IMAGE_REPEAT_Y_START =
            floatPreferencesKey("bubble_ai_image_repeat_y_start")
        private val BUBBLE_AI_IMAGE_REPEAT_Y_END =
            floatPreferencesKey("bubble_ai_image_repeat_y_end")
        private val BUBBLE_AI_IMAGE_SCALE =
            floatPreferencesKey("bubble_ai_image_scale")
        private val BUBBLE_IMAGE_RENDER_MODE =
            stringPreferencesKey("bubble_image_render_mode")
        private val BUBBLE_USER_ROUNDED_CORNERS_ENABLED =
            booleanPreferencesKey("bubble_rounded_corners_enabled")
        private val BUBBLE_AI_ROUNDED_CORNERS_ENABLED =
            booleanPreferencesKey("bubble_ai_rounded_corners_enabled")
        private val BUBBLE_USER_CONTENT_PADDING_LEFT =
            floatPreferencesKey("bubble_content_padding_left")
        private val BUBBLE_USER_CONTENT_PADDING_RIGHT =
            floatPreferencesKey("bubble_content_padding_right")
        private val BUBBLE_AI_CONTENT_PADDING_LEFT =
            floatPreferencesKey("bubble_ai_content_padding_left")
        private val BUBBLE_AI_CONTENT_PADDING_RIGHT =
            floatPreferencesKey("bubble_ai_content_padding_right")

        // 默认配置文件ID
        private const val DEFAULT_PROFILE_ID = "default"

        // 主题模式常量
        const val THEME_MODE_LIGHT = "light"
        const val THEME_MODE_DARK = "dark"

        // AppBar 内容颜色模式常量
        const val APP_BAR_CONTENT_COLOR_MODE_LIGHT = "light"
        const val APP_BAR_CONTENT_COLOR_MODE_DARK = "dark"

        // 背景媒体类型常量
        const val MEDIA_TYPE_IMAGE = "image"
        const val MEDIA_TYPE_VIDEO = "video"
        
        // 默认语言
        const val DEFAULT_LANGUAGE = LanguageCodes.AUTO

        // Sidebar software identity (drawer header brand text)
        const val SOFTWARE_IDENTITY_OPERIT = "operit_ai"
        const val SOFTWARE_IDENTITY_LINGSHU = "lingshu_ai"

        const val CHAT_STYLE_CURSOR = "cursor"
        const val CHAT_STYLE_BUBBLE = "bubble"

        const val INPUT_STYLE_CLASSIC = "classic"
        const val INPUT_STYLE_AGENT = "agent"
        const val BUBBLE_IMAGE_RENDER_MODE_TILED_NINE_SLICE = "tiled_nine_slice"
        const val BUBBLE_IMAGE_RENDER_MODE_NINE_PATCH = "nine_patch"

        private val KEY_SHOW_THINKING_PROCESS = booleanPreferencesKey("show_thinking_process")
        private val KEY_SHOW_STATUS_TAGS = booleanPreferencesKey("show_status_tags")
        private val KEY_SHOW_MODEL_PROVIDER = booleanPreferencesKey("show_model_provider")
        private val KEY_SHOW_MODEL_NAME = booleanPreferencesKey("show_model_name")
        private val KEY_SHOW_ROLE_NAME = booleanPreferencesKey("show_role_name")
        private val KEY_SHOW_USER_NAME = booleanPreferencesKey("show_user_name")
        private val KEY_SHOW_MESSAGE_TOKEN_STATS = booleanPreferencesKey("show_message_token_stats")
        private val KEY_SHOW_MESSAGE_TIMING_STATS = booleanPreferencesKey("show_message_timing_stats")
        private val KEY_SHOW_MESSAGE_TIMESTAMP = booleanPreferencesKey("show_message_timestamp")
        private val KEY_CUSTOM_USER_AVATAR_URI = stringPreferencesKey("custom_user_avatar_uri")
        private val KEY_CUSTOM_AI_AVATAR_URI = stringPreferencesKey("custom_ai_avatar_uri")
        private val KEY_AVATAR_SHAPE = stringPreferencesKey("avatar_shape")
        private val KEY_AVATAR_CORNER_RADIUS = floatPreferencesKey("avatar_corner_radius")
        private val KEY_ON_COLOR_MODE = stringPreferencesKey("on_color_mode")
        private val KEY_CUSTOM_CHAT_TITLE = stringPreferencesKey("custom_chat_title")
        private val KEY_SHOW_INPUT_PROCESSING_STATUS = booleanPreferencesKey("show_input_processing_status")
        private val KEY_SHOW_CHAT_FLOATING_DOTS_ANIMATION = booleanPreferencesKey("show_chat_floating_dots_animation")
        private val KEY_AI_MESSAGE_MARKDOWN_LATEX_ENABLED =
            booleanPreferencesKey("ai_message_markdown_latex_enabled")
        private val KEY_USER_MESSAGE_MARKDOWN_LATEX_ENABLED =
            booleanPreferencesKey("user_message_markdown_latex_enabled")
        private val KEY_UI_ACCESSIBILITY_MODE = booleanPreferencesKey("ui_accessibility_mode")
        private val KEY_BETA_PLAN_ENABLED = booleanPreferencesKey("beta_plan_enabled")
        private val KEY_SOFTWARE_IDENTITY = stringPreferencesKey("software_identity")


        // 布局调整设置
        private val CHAT_SETTINGS_BUTTON_END_PADDING = floatPreferencesKey("chat_settings_button_end_padding")
        private val CHAT_AREA_HORIZONTAL_PADDING = floatPreferencesKey("chat_area_horizontal_padding")
        private val AI_MARKDOWN_LINE_HEIGHT_MULTIPLIER =
            floatPreferencesKey("global_text_line_height_multiplier")
        private val AI_MARKDOWN_LETTER_SPACING =
            floatPreferencesKey("global_text_letter_spacing")
        private val AI_MARKDOWN_PARAGRAPH_SPACING =
            floatPreferencesKey("ai_markdown_paragraph_spacing")
        private val CONVERT_LONG_PASTED_TEXT_TO_FILE =
            booleanPreferencesKey("convert_long_pasted_text_to_file")
        private val LONG_PASTED_TEXT_FILE_THRESHOLD =
            intPreferencesKey("long_pasted_text_file_threshold")

        // 最近使用颜色
        private val RECENT_COLORS = stringPreferencesKey("recent_colors")


        const val AVATAR_SHAPE_CIRCLE = "circle"
        const val AVATAR_SHAPE_SQUARE = "square"

        const val ON_COLOR_MODE_AUTO = "auto"
        const val ON_COLOR_MODE_LIGHT = "light"
        const val ON_COLOR_MODE_DARK = "dark"

        // 字体类型常量
        const val FONT_TYPE_SYSTEM = "system"
        const val FONT_TYPE_FILE = "file"
        
        // 系统字体名称常量
        const val SYSTEM_FONT_DEFAULT = "default"
        const val SYSTEM_FONT_SERIF = "serif"
        const val SYSTEM_FONT_SANS_SERIF = "sans-serif"
        const val SYSTEM_FONT_MONOSPACE = "monospace"
        const val SYSTEM_FONT_CURSIVE = "cursive"

        const val DEFAULT_LONG_PASTED_TEXT_FILE_THRESHOLD = 3000
    }

    // 获取应用语言设置
    val appLanguage: Flow<String> = 
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[APP_LANGUAGE] ?: DEFAULT_LANGUAGE
            }
    
    // 保存应用语言设置
    suspend fun saveAppLanguage(languageCode: String) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[APP_LANGUAGE] = languageCode
        }
    }
    
    // 同步获取当前语言设置
    fun getCurrentLanguage(): String {
        return runBlocking {
            appLanguage.first()
        }
    }

    suspend fun saveUiAccessibilityMode(enabled: Boolean) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[KEY_UI_ACCESSIBILITY_MODE] = enabled
        }
    }

    suspend fun saveBetaPlanEnabled(enabled: Boolean) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[KEY_BETA_PLAN_ENABLED] = enabled
        }
    }

    suspend fun saveSoftwareIdentity(identity: String) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[KEY_SOFTWARE_IDENTITY] = identity
        }
    }

    fun isUiAccessibilityModeEnabled(): Boolean {
        return runBlocking {
            uiAccessibilityMode.first()
        }
    }

    fun isBetaPlanEnabled(): Boolean {
        return runBlocking {
            betaPlanEnabled.first()
        }
    }

    val activeMemorySpaceIdFlow: Flow<String> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[ACTIVE_MEMORY_SPACE_ID] ?: DEFAULT_PROFILE_ID
        }

    val memorySpaceListFlow: Flow<List<String>> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[MEMORY_SPACE_LIST]
                ?.let { Json.decodeFromString<List<String>>(it) }
                .orEmpty()
        }

    suspend fun hasMemorySpaceMetadata(): Boolean {
        return context.userPreferencesDataStore.data.first().contains(MEMORY_SPACE_LIST)
    }

    /**
     * A raw +4 snapshot can be restored while a newer process has already created a default
     * memory space. The legacy list is still authoritative until its records are consumed.
     */
    suspend fun hasLegacyUserProfileMetadata(): Boolean {
        return context.userPreferencesDataStore.data.first().contains(PROFILE_LIST)
    }

    fun getMemorySpaceFlow(memorySpaceId: String = ""): Flow<MemorySpace> {
        return context.userPreferencesDataStore.data.map { preferences ->
            val targetId =
                memorySpaceId.ifBlank {
                    preferences[ACTIVE_MEMORY_SPACE_ID] ?: DEFAULT_PROFILE_ID
                }
            val encoded =
                requireNotNull(preferences[stringPreferencesKey("memory_space_$targetId")]) {
                    "Missing memory space metadata: $targetId"
                }
            Json.decodeFromString<MemorySpace>(encoded)
        }
    }

    suspend fun ensureDefaultMemorySpace(defaultName: String) {
        val ids = memorySpaceListFlow.first()
        val storedDefault =
            context.userPreferencesDataStore.data.first()[stringPreferencesKey("memory_space_$DEFAULT_PROFILE_ID")]
        if (!ids.contains(DEFAULT_PROFILE_ID) || storedDefault == null) {
            createMemorySpace(defaultName, isDefault = true)
        }
    }

    suspend fun createMemorySpace(name: String, isDefault: Boolean = false): String {
        val id = if (isDefault) DEFAULT_PROFILE_ID else "memory_${System.currentTimeMillis()}"
        val space = MemorySpace(id, name)
        context.userPreferencesDataStore.edit { preferences ->
            val ids = decodeIdList(preferences[MEMORY_SPACE_LIST]).toMutableList()
            if (!ids.contains(id)) ids.add(id)
            preferences[MEMORY_SPACE_LIST] = Json.encodeToString(ids)
            preferences[stringPreferencesKey("memory_space_$id")] = Json.encodeToString(space)
            if (preferences[ACTIVE_MEMORY_SPACE_ID] == null) {
                preferences[ACTIVE_MEMORY_SPACE_ID] = id
            }
        }
        MemorySpaceProfileDocumentRepository.getInstance(context).load(id)
        return id
    }

    suspend fun setActiveMemorySpace(memorySpaceId: String) {
        context.userPreferencesDataStore.edit { preferences ->
            val ids = decodeIdList(preferences[MEMORY_SPACE_LIST])
            require(ids.contains(memorySpaceId)) { "Unknown memory space: $memorySpaceId" }
            preferences[ACTIVE_MEMORY_SPACE_ID] = memorySpaceId
        }
    }

    suspend fun updateMemorySpace(space: MemorySpace) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[stringPreferencesKey("memory_space_${space.id}")] = Json.encodeToString(space)
        }
    }

    suspend fun deleteMemorySpace(memorySpaceId: String) {
        if (memorySpaceId == DEFAULT_PROFILE_ID) return
        val characterCardManager = CharacterCardManager.getInstance(context)
        characterCardManager.getAllCharacterCards()
            .filter { it.memoryProfileId == memorySpaceId }
            .forEach { card ->
                characterCardManager.updateCharacterCard(
                    card.copy(
                        memoryProfileBindingMode = CharacterCardMemoryProfileBindingMode.FOLLOW_GLOBAL,
                        memoryProfileId = null
                    )
                )
            }
        context.userPreferencesDataStore.edit { preferences ->
            val ids = decodeIdList(preferences[MEMORY_SPACE_LIST]).toMutableList()
            ids.remove(memorySpaceId)
            preferences[MEMORY_SPACE_LIST] = Json.encodeToString(ids)
            preferences.remove(stringPreferencesKey("memory_space_$memorySpaceId"))
            if (preferences[ACTIVE_MEMORY_SPACE_ID] == memorySpaceId) {
                preferences[ACTIVE_MEMORY_SPACE_ID] = DEFAULT_PROFILE_ID
            }
        }
        MemorySpaceProfileDocumentRepository.getInstance(context).delete(memorySpaceId)
        ObjectBoxManager.delete(context, memorySpaceId)
    }

    suspend fun readLegacyUserProfiles(): LegacyUserProfileSnapshot {
        val preferences = context.userPreferencesDataStore.data.first()
        if (preferences[PROFILE_LIST] == null && preferences[MEMORY_SPACE_LIST] != null) {
            // A process may stop after the DataStore rewrite and before the separate schema marker
            // is committed. Reconstructing the snapshot from the new keys makes migration
            // idempotent and prevents a retry from collapsing existing spaces to only "default".
            val memorySpaceIds = decodeIdList(preferences[MEMORY_SPACE_LIST]).toMutableList()
            if (!memorySpaceIds.contains(DEFAULT_PROFILE_ID)) {
                memorySpaceIds.add(0, DEFAULT_PROFILE_ID)
            }
            val spaces = memorySpaceIds.distinct().map { id ->
                val encoded =
                    requireNotNull(preferences[stringPreferencesKey("memory_space_$id")]) {
                        "Missing migrated memory space metadata: $id"
                    }
                val name = Json.decodeFromString<MemorySpace>(encoded).name
                LegacyUserProfile(id = id, name = name)
            }
            return LegacyUserProfileSnapshot(
                activeProfileId = preferences[ACTIVE_MEMORY_SPACE_ID] ?: DEFAULT_PROFILE_ID,
                profiles = spaces
            )
        }

        val activeId = preferences[ACTIVE_PROFILE_ID] ?: DEFAULT_PROFILE_ID
        val ids = decodeIdList(preferences[PROFILE_LIST]).toMutableList()
        if (!ids.contains(DEFAULT_PROFILE_ID)) ids.add(0, DEFAULT_PROFILE_ID)
        val profiles = ids.distinct().map { id ->
            val encoded = preferences[stringPreferencesKey("profile_$id")]
            if (encoded == null) {
                createDefaultProfile(id)
            } else {
                Json.decodeFromString<LegacyUserProfile>(encoded)
            }
        }
        val hasLegacyCategoryLocks =
            listOf(
                BIRTH_DATE_LOCKED,
                GENDER_LOCKED,
                PERSONALITY_LOCKED,
                IDENTITY_LOCKED,
                OCCUPATION_LOCKED,
                AI_STYLE_LOCKED
            ).any { preferences[it] == true }
        return LegacyUserProfileSnapshot(activeId, profiles, hasLegacyCategoryLocks)
    }

    suspend fun migrateLegacyProfilesToMemorySpaces(snapshot: LegacyUserProfileSnapshot) {
        context.userPreferencesDataStore.edit { preferences ->
            val profiles =
                snapshot.profiles.ifEmpty {
                    listOf(createDefaultProfile(DEFAULT_PROFILE_ID))
                }
            val ids = profiles.map { it.id }.distinct().toMutableList()
            if (!ids.contains(DEFAULT_PROFILE_ID)) ids.add(0, DEFAULT_PROFILE_ID)
            preferences[MEMORY_SPACE_LIST] = Json.encodeToString(ids)
            val activeId = snapshot.activeProfileId.takeIf(ids::contains) ?: DEFAULT_PROFILE_ID
            preferences[ACTIVE_MEMORY_SPACE_ID] = activeId
            profiles.forEach { profile ->
                // A released partial category lock cannot be represented as a document-wide
                // lock. Locking the document avoids rewriting a field the user previously
                // protected; the new memory-space UI lets the user choose the new policy.
                val space = MemorySpace(
                    id = profile.id,
                    name = profile.name,
                    profileAutoUpdateLocked = snapshot.hasLegacyCategoryLocks
                )
                preferences[stringPreferencesKey("memory_space_${profile.id}")] =
                    Json.encodeToString(space)
                preferences.remove(stringPreferencesKey("profile_${profile.id}"))
            }
            preferences.remove(ACTIVE_PROFILE_ID)
            preferences.remove(PROFILE_LIST)
            preferences.remove(BIRTH_DATE_LOCKED)
            preferences.remove(GENDER_LOCKED)
            preferences.remove(PERSONALITY_LOCKED)
            preferences.remove(IDENTITY_LOCKED)
            preferences.remove(OCCUPATION_LOCKED)
            preferences.remove(AI_STYLE_LOCKED)
        }
    }

    private fun decodeIdList(encoded: String?): List<String> {
        return encoded?.let { Json.decodeFromString<List<String>>(it) }.orEmpty()
    }

    private fun createDefaultProfile(profileId: String): LegacyUserProfile {
        return LegacyUserProfile(
            id = profileId,
            name = if (profileId == DEFAULT_PROFILE_ID) "Default" else profileId
        )
    }

    val uiAccessibilityMode: Flow<Boolean> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[KEY_UI_ACCESSIBILITY_MODE] ?: false
        }

    val betaPlanEnabled: Flow<Boolean> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[KEY_BETA_PLAN_ENABLED] ?: false
        }

    val softwareIdentity: Flow<String> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[KEY_SOFTWARE_IDENTITY] ?: SOFTWARE_IDENTITY_OPERIT
        }

    // 布局调整设置
    val chatSettingsButtonEndPadding: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[CHAT_SETTINGS_BUTTON_END_PADDING] ?: 2f // 默认2dp
        }

    val chatAreaHorizontalPadding: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[CHAT_AREA_HORIZONTAL_PADDING] ?: 16f // 默认16dp
        }

    val aiMarkdownLineHeightMultiplier: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[AI_MARKDOWN_LINE_HEIGHT_MULTIPLIER] ?: 1f
        }

    val aiMarkdownLetterSpacing: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[AI_MARKDOWN_LETTER_SPACING] ?: 0f
        }

    val aiMarkdownParagraphSpacing: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[AI_MARKDOWN_PARAGRAPH_SPACING] ?: 12f
        }

    val convertLongPastedTextToFile: Flow<Boolean> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[CONVERT_LONG_PASTED_TEXT_TO_FILE] ?: true
        }

    val longPastedTextFileThreshold: Flow<Int> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[LONG_PASTED_TEXT_FILE_THRESHOLD]
                ?: DEFAULT_LONG_PASTED_TEXT_FILE_THRESHOLD
        }

    // 获取最近使用颜色
    val recentColorsFlow: Flow<List<Int>> =
        context.userPreferencesDataStore.data.map { preferences ->
            val colorsString = preferences[RECENT_COLORS] ?: ""
            if (colorsString.isBlank()) {
                emptyList()
            } else {
                colorsString.split(",").mapNotNull { it.toIntOrNull() }
            }
        }

    // 添加最近使用颜色
    suspend fun addRecentColor(color: Int) {
        context.userPreferencesDataStore.edit { preferences ->
            val currentColorsString = preferences[RECENT_COLORS] ?: ""
            val currentColors =
                if (currentColorsString.isBlank()) {
                    mutableListOf()
                } else {
                    currentColorsString.split(",").mapNotNull { it.toIntOrNull() }.toMutableList()
                }

            // 移除已存在的相同颜色，以确保新添加的在最前面
            currentColors.remove(color)
            // 添加新颜色到列表开头
            currentColors.add(0, color)

            // 限制历史记录数量，例如最多14个
            val trimmedColors = currentColors.take(14)

            preferences[RECENT_COLORS] = trimmedColors.joinToString(",")
        }
    }

    // 保存聊天设置按钮右边距
    suspend fun saveChatSettingsButtonEndPadding(padding: Float) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[CHAT_SETTINGS_BUTTON_END_PADDING] = padding
        }
    }

    // 保存聊天区域水平内边距
    suspend fun saveChatAreaHorizontalPadding(padding: Float) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[CHAT_AREA_HORIZONTAL_PADDING] = padding
        }
    }

    suspend fun saveAiMarkdownLineHeightMultiplier(value: Float) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[AI_MARKDOWN_LINE_HEIGHT_MULTIPLIER] = value
        }
    }

    suspend fun saveAiMarkdownLetterSpacing(value: Float) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[AI_MARKDOWN_LETTER_SPACING] = value
        }
    }

    suspend fun saveAiMarkdownParagraphSpacing(value: Float) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[AI_MARKDOWN_PARAGRAPH_SPACING] = value
        }
    }

    suspend fun saveConvertLongPastedTextToFile(enabled: Boolean) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[CONVERT_LONG_PASTED_TEXT_TO_FILE] = enabled
        }
    }

    suspend fun saveLongPastedTextFileThreshold(threshold: Int) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[LONG_PASTED_TEXT_FILE_THRESHOLD] = threshold
        }
    }

    // 重置布局设置
    suspend fun resetLayoutSettings() {
        context.userPreferencesDataStore.edit { preferences ->
            preferences.remove(CHAT_SETTINGS_BUTTON_END_PADDING)
            preferences.remove(CHAT_AREA_HORIZONTAL_PADDING)
            preferences.remove(AI_MARKDOWN_LINE_HEIGHT_MULTIPLIER)
            preferences.remove(AI_MARKDOWN_LETTER_SPACING)
            preferences.remove(AI_MARKDOWN_PARAGRAPH_SPACING)
        }
    }

    fun getAiAvatarForCharacterCardFlow(characterCardId: String): Flow<String?> {
        return context.userPreferencesDataStore.data.map { preferences ->
            val prefix = getCharacterCardThemePrefix(characterCardId)
            val key = stringPreferencesKey("${prefix}${KEY_CUSTOM_AI_AVATAR_URI.name}")
            preferences[key]
        }
    }
    
    suspend fun saveAiAvatarForCharacterCard(characterCardId: String, avatarUri: String?) {
        context.userPreferencesDataStore.edit { preferences ->
            val prefix = getCharacterCardThemePrefix(characterCardId)
            val key = stringPreferencesKey("${prefix}${KEY_CUSTOM_AI_AVATAR_URI.name}")
            if (avatarUri != null) {
                preferences[key] = avatarUri
            } else {
                preferences.remove(key)
            }
        }
    }

    fun getAiAvatarForCharacterGroupFlow(characterGroupId: String): Flow<String?> {
        return context.userPreferencesDataStore.data.map { preferences ->
            val prefix = getCharacterGroupThemePrefix(characterGroupId)
            val key = stringPreferencesKey("${prefix}${KEY_CUSTOM_AI_AVATAR_URI.name}")
            preferences[key]
        }
    }

    suspend fun saveAiAvatarForCharacterGroup(characterGroupId: String, avatarUri: String?) {
        context.userPreferencesDataStore.edit { preferences ->
            val prefix = getCharacterGroupThemePrefix(characterGroupId)
            val key = stringPreferencesKey("${prefix}${KEY_CUSTOM_AI_AVATAR_URI.name}")
            if (avatarUri != null) {
                preferences[key] = avatarUri
            } else {
                preferences.remove(key)
            }
        }
    }

    suspend fun saveCustomChatTitleForCharacterCard(characterCardId: String, title: String?) {
        context.userPreferencesDataStore.edit { preferences ->
            val prefix = getCharacterCardThemePrefix(characterCardId)
            val key = stringPreferencesKey("${prefix}${KEY_CUSTOM_CHAT_TITLE.name}")
            if (!title.isNullOrEmpty()) {
                preferences[key] = title
            } else {
                preferences.remove(key)
            }
        }
    }

    suspend fun saveCustomChatTitleForCharacterGroup(characterGroupId: String, title: String?) {
        context.userPreferencesDataStore.edit { preferences ->
            val prefix = getCharacterGroupThemePrefix(characterGroupId)
            val key = stringPreferencesKey("${prefix}${KEY_CUSTOM_CHAT_TITLE.name}")
            if (!title.isNullOrEmpty()) {
                preferences[key] = title
            } else {
                preferences.remove(key)
            }
        }
    }

    // ========== 角色卡/群组主题绑定功能 ==========

    private fun getCharacterCardThemePrefix(characterCardId: String): String =
        "character_card_theme_${characterCardId}_"

    private fun getCharacterGroupThemePrefix(characterGroupId: String): String =
        "character_group_theme_${characterGroupId}_"

    private fun getAllStringThemeKeys(): List<Preferences.Key<String>> {
        return listOf(
            THEME_MODE, BACKGROUND_IMAGE_URI, BACKGROUND_MEDIA_TYPE, APP_BAR_CONTENT_COLOR_MODE,
            CHAT_STYLE, KEY_CUSTOM_USER_AVATAR_URI, KEY_CUSTOM_AI_AVATAR_URI, KEY_AVATAR_SHAPE,
            KEY_ON_COLOR_MODE, KEY_CUSTOM_CHAT_TITLE, INPUT_STYLE, FONT_TYPE, SYSTEM_FONT_NAME,
            CUSTOM_FONT_PATH, BUBBLE_USER_FONT_TYPE, BUBBLE_USER_SYSTEM_FONT_NAME,
            BUBBLE_USER_CUSTOM_FONT_PATH, BUBBLE_AI_FONT_TYPE, BUBBLE_AI_SYSTEM_FONT_NAME,
            BUBBLE_AI_CUSTOM_FONT_PATH, BUBBLE_USER_IMAGE_URI, BUBBLE_AI_IMAGE_URI,
            BUBBLE_IMAGE_RENDER_MODE
        )
    }

    private fun getAllBooleanThemeKeys(): List<Preferences.Key<Boolean>> {
        return listOf(
            USE_SYSTEM_THEME, USE_CUSTOM_COLORS, USE_BACKGROUND_IMAGE, VIDEO_BACKGROUND_MUTED,
            VIDEO_BACKGROUND_LOOP, TOOLBAR_TRANSPARENT, NAVIGATION_DRAWER_WATER_GLASS,
            NAVIGATION_DRAWER_BUTTON_LIQUID_GLASS,
            USE_CUSTOM_NAVIGATION_DRAWER_BACKGROUND_COLOR,
            USE_CUSTOM_NAVIGATION_DRAWER_ACCENT_COLOR,
            USE_CUSTOM_APP_BAR_COLOR, USE_CUSTOM_STATUS_BAR_COLOR,
            STATUS_BAR_TRANSPARENT, STATUS_BAR_HIDDEN, CHAT_HEADER_TRANSPARENT, CHAT_INPUT_TRANSPARENT, CHAT_INPUT_FLOATING,
            CHAT_INPUT_LIQUID_GLASS,
            CHAT_INPUT_WATER_GLASS,
            FORCE_APP_BAR_CONTENT_COLOR_ENABLED, CHAT_HEADER_OVERLAY_MODE, USE_BACKGROUND_BLUR,
            BUBBLE_SHOW_AVATAR, BUBBLE_WIDE_LAYOUT_ENABLED, CURSOR_USER_BUBBLE_FOLLOW_THEME, CURSOR_USER_BUBBLE_LIQUID_GLASS,
            CURSOR_USER_BUBBLE_WATER_GLASS, BUBBLE_USER_BUBBLE_LIQUID_GLASS, BUBBLE_USER_BUBBLE_WATER_GLASS,
            BUBBLE_AI_BUBBLE_LIQUID_GLASS, BUBBLE_AI_BUBBLE_WATER_GLASS, BUBBLE_USER_USE_IMAGE,
            BUBBLE_AI_USE_IMAGE, BUBBLE_USER_ROUNDED_CORNERS_ENABLED, BUBBLE_AI_ROUNDED_CORNERS_ENABLED, KEY_SHOW_THINKING_PROCESS, KEY_SHOW_STATUS_TAGS,
            KEY_SHOW_INPUT_PROCESSING_STATUS, KEY_SHOW_CHAT_FLOATING_DOTS_ANIMATION,
            KEY_AI_MESSAGE_MARKDOWN_LATEX_ENABLED, KEY_USER_MESSAGE_MARKDOWN_LATEX_ENABLED,
            USE_CUSTOM_FONT,
            BUBBLE_USER_USE_CUSTOM_FONT, BUBBLE_AI_USE_CUSTOM_FONT, KEY_SHOW_MODEL_PROVIDER,
            KEY_SHOW_MODEL_NAME, KEY_SHOW_ROLE_NAME, KEY_SHOW_USER_NAME,
            KEY_SHOW_MESSAGE_TOKEN_STATS, KEY_SHOW_MESSAGE_TIMING_STATS,
            KEY_SHOW_MESSAGE_TIMESTAMP
        )
    }

    private fun getAllIntThemeKeys(): List<Preferences.Key<Int>> {
        return listOf(
            CUSTOM_PRIMARY_COLOR, CUSTOM_SECONDARY_COLOR, CUSTOM_NAVIGATION_DRAWER_BACKGROUND_COLOR,
            CUSTOM_NAVIGATION_DRAWER_ACCENT_COLOR, CUSTOM_APP_BAR_COLOR,
            CUSTOM_STATUS_BAR_COLOR, CHAT_HEADER_HISTORY_ICON_COLOR, CHAT_HEADER_PIP_ICON_COLOR,
            CURSOR_USER_BUBBLE_COLOR, BUBBLE_USER_BUBBLE_COLOR, BUBBLE_AI_BUBBLE_COLOR,
            BUBBLE_USER_TEXT_COLOR, BUBBLE_AI_TEXT_COLOR
        )
    }

    private fun getAllFloatThemeKeys(): List<Preferences.Key<Float>> {
        return listOf(
            BACKGROUND_IMAGE_OPACITY, TOOLBAR_OPACITY, STATUS_BAR_OPACITY, CHAT_HEADER_OPACITY,
            CHAT_INPUT_OPACITY, BACKGROUND_BLUR_RADIUS, KEY_AVATAR_CORNER_RADIUS, FONT_SCALE,
            BUBBLE_USER_IMAGE_CROP_LEFT, BUBBLE_USER_IMAGE_CROP_TOP, BUBBLE_USER_IMAGE_CROP_RIGHT,
            BUBBLE_USER_IMAGE_CROP_BOTTOM, BUBBLE_USER_IMAGE_REPEAT_START, BUBBLE_USER_IMAGE_REPEAT_END,
            BUBBLE_USER_IMAGE_REPEAT_Y_START, BUBBLE_USER_IMAGE_REPEAT_Y_END, BUBBLE_USER_IMAGE_SCALE,
            BUBBLE_AI_IMAGE_CROP_LEFT, BUBBLE_AI_IMAGE_CROP_TOP, BUBBLE_AI_IMAGE_CROP_RIGHT,
            BUBBLE_AI_IMAGE_CROP_BOTTOM, BUBBLE_AI_IMAGE_REPEAT_START, BUBBLE_AI_IMAGE_REPEAT_END,
            BUBBLE_AI_IMAGE_REPEAT_Y_START, BUBBLE_AI_IMAGE_REPEAT_Y_END, BUBBLE_AI_IMAGE_SCALE,
            BUBBLE_USER_CONTENT_PADDING_LEFT, BUBBLE_USER_CONTENT_PADDING_RIGHT,
            BUBBLE_AI_CONTENT_PADDING_LEFT, BUBBLE_AI_CONTENT_PADDING_RIGHT
        )
    }

    private fun copyThemeValues(
        preferences: MutablePreferences,
        sourcePrefix: String?,
        targetPrefix: String,
        clearMissingTargetValues: Boolean,
    ) {
        getAllStringThemeKeys().forEach { key ->
            val sourceKey = sourcePrefix?.let { stringPreferencesKey("${it}${key.name}") } ?: key
            val targetKey = stringPreferencesKey("${targetPrefix}${key.name}")
            if (preferences.contains(sourceKey)) {
                preferences[targetKey] = preferences[sourceKey]!!
            } else if (clearMissingTargetValues) {
                preferences.remove(targetKey)
            }
        }
        getAllBooleanThemeKeys().forEach { key ->
            val sourceKey = sourcePrefix?.let { booleanPreferencesKey("${it}${key.name}") } ?: key
            val targetKey = booleanPreferencesKey("${targetPrefix}${key.name}")
            if (preferences.contains(sourceKey)) {
                preferences[targetKey] = preferences[sourceKey]!!
            } else if (clearMissingTargetValues) {
                preferences.remove(targetKey)
            }
        }
        getAllIntThemeKeys().forEach { key ->
            val sourceKey = sourcePrefix?.let { intPreferencesKey("${it}${key.name}") } ?: key
            val targetKey = intPreferencesKey("${targetPrefix}${key.name}")
            if (preferences.contains(sourceKey)) {
                preferences[targetKey] = preferences[sourceKey]!!
            } else if (clearMissingTargetValues) {
                preferences.remove(targetKey)
            }
        }
        getAllFloatThemeKeys().forEach { key ->
            val sourceKey = sourcePrefix?.let { floatPreferencesKey("${it}${key.name}") } ?: key
            val targetKey = floatPreferencesKey("${targetPrefix}${key.name}")
            if (preferences.contains(sourceKey)) {
                preferences[targetKey] = preferences[sourceKey]!!
            } else if (clearMissingTargetValues) {
                preferences.remove(targetKey)
            }
        }
    }

    private fun themePrefixForPrompt(target: ActivePrompt): String {
        return when (target) {
            is ActivePrompt.CharacterCard -> getCharacterCardThemePrefix(target.id)
            is ActivePrompt.CharacterGroup -> getCharacterGroupThemePrefix(target.id)
        }
    }

    private fun isVisualThemeStringKey(key: Preferences.Key<String>): Boolean {
        return key != KEY_CUSTOM_AI_AVATAR_URI && key != KEY_CUSTOM_CHAT_TITLE
    }

    private fun clearVisualThemeValues(preferences: MutablePreferences, prefix: String) {
        getAllStringThemeKeys()
            .filter(::isVisualThemeStringKey)
            .forEach { key ->
                val targetKey = stringPreferencesKey("${prefix}${key.name}")
                preferences.remove(targetKey)
            }
        getAllBooleanThemeKeys().forEach { key ->
            val targetKey = booleanPreferencesKey("${prefix}${key.name}")
            preferences.remove(targetKey)
        }
        getAllIntThemeKeys().forEach { key ->
            val targetKey = intPreferencesKey("${prefix}${key.name}")
            preferences.remove(targetKey)
        }
        getAllFloatThemeKeys().forEach { key ->
            val targetKey = floatPreferencesKey("${prefix}${key.name}")
            preferences.remove(targetKey)
        }
    }

    private fun readThemePreferenceValues(
        preferences: Preferences,
        prefix: String,
    ): ThemePreferenceValues {
        val defaults = ThemePreferenceValues.defaultVisual()
        val strings = defaults.strings.toMutableMap()
        val booleans = defaults.booleans.toMutableMap()
        val ints = defaults.ints.toMutableMap()
        val floats = defaults.floats.toMutableMap()

        getAllStringThemeKeys().forEach { key ->
            val sourceKey = stringPreferencesKey("${prefix}${key.name}")
            preferences[sourceKey]?.let { strings[key.name] = it }
        }
        getAllBooleanThemeKeys().forEach { key ->
            val sourceKey = booleanPreferencesKey("${prefix}${key.name}")
            preferences[sourceKey]?.let { booleans[key.name] = it }
        }
        getAllIntThemeKeys().forEach { key ->
            val sourceKey = intPreferencesKey("${prefix}${key.name}")
            preferences[sourceKey]?.let { ints[key.name] = it }
        }
        getAllFloatThemeKeys().forEach { key ->
            val sourceKey = floatPreferencesKey("${prefix}${key.name}")
            preferences[sourceKey]?.let { floats[key.name] = it }
        }

        fun copyLegacyRepeatYValue(
            verticalKey: Preferences.Key<Float>,
            horizontalKey: Preferences.Key<Float>,
        ) {
            val verticalSourceKey = floatPreferencesKey("${prefix}${verticalKey.name}")
            if (!preferences.contains(verticalSourceKey)) {
                val horizontalSourceKey = floatPreferencesKey("${prefix}${horizontalKey.name}")
                preferences[horizontalSourceKey]?.let { floats[verticalKey.name] = it }
            }
        }

        copyLegacyRepeatYValue(BUBBLE_USER_IMAGE_REPEAT_Y_START, BUBBLE_USER_IMAGE_REPEAT_START)
        copyLegacyRepeatYValue(BUBBLE_USER_IMAGE_REPEAT_Y_END, BUBBLE_USER_IMAGE_REPEAT_END)
        copyLegacyRepeatYValue(BUBBLE_AI_IMAGE_REPEAT_Y_START, BUBBLE_AI_IMAGE_REPEAT_START)
        copyLegacyRepeatYValue(BUBBLE_AI_IMAGE_REPEAT_Y_END, BUBBLE_AI_IMAGE_REPEAT_END)

        fun copyLegacyTransparencyValue(
            opacityKey: Preferences.Key<Float>,
            transparentKey: Preferences.Key<Boolean>,
        ) {
            val opacitySourceKey = floatPreferencesKey("${prefix}${opacityKey.name}")
            if (preferences.contains(opacitySourceKey)) return

            val transparentSourceKey = booleanPreferencesKey("${prefix}${transparentKey.name}")
            if (preferences[transparentSourceKey] == true) {
                floats[opacityKey.name] = 0f
            }
        }

        copyLegacyTransparencyValue(TOOLBAR_OPACITY, TOOLBAR_TRANSPARENT)
        copyLegacyTransparencyValue(STATUS_BAR_OPACITY, STATUS_BAR_TRANSPARENT)
        copyLegacyTransparencyValue(CHAT_HEADER_OPACITY, CHAT_HEADER_TRANSPARENT)
        copyLegacyTransparencyValue(CHAT_INPUT_OPACITY, CHAT_INPUT_TRANSPARENT)

        return ThemePreferenceValues(
            strings = strings,
            booleans = booleans,
            ints = ints,
            floats = floats,
        )
    }

    private fun writeVisualThemeValues(
        preferences: MutablePreferences,
        prefix: String,
        values: ThemePreferenceValues,
    ) {
        getAllStringThemeKeys()
            .filter(::isVisualThemeStringKey)
            .forEach { key ->
                val targetKey = stringPreferencesKey("${prefix}${key.name}")
                val value = values.string(key.name)
                if (value == null) {
                    preferences.remove(targetKey)
                } else {
                    preferences[targetKey] = value
                }
            }
        getAllBooleanThemeKeys().forEach { key ->
            val targetKey = booleanPreferencesKey("${prefix}${key.name}")
            val value = values.boolean(key.name)
            if (value == null) {
                preferences.remove(targetKey)
            } else {
                preferences[targetKey] = value
            }
        }
        getAllIntThemeKeys().forEach { key ->
            val targetKey = intPreferencesKey("${prefix}${key.name}")
            val value = values.int(key.name)
            if (value == null) {
                preferences.remove(targetKey)
            } else {
                preferences[targetKey] = value
            }
        }
        getAllFloatThemeKeys().forEach { key ->
            val targetKey = floatPreferencesKey("${prefix}${key.name}")
            val value = values.float(key.name)
            if (value == null) {
                preferences.remove(targetKey)
            } else {
                preferences[targetKey] = value
            }
        }
    }

    private fun writeThemeTargetMetadata(
        preferences: MutablePreferences,
        prefix: String,
        values: ThemePreferenceValues,
    ) {
        listOf(KEY_CUSTOM_AI_AVATAR_URI, KEY_CUSTOM_CHAT_TITLE).forEach { key ->
            val targetKey = stringPreferencesKey("${prefix}${key.name}")
            val value = values.string(key.name)
            if (value == null) {
                preferences.remove(targetKey)
            } else {
                preferences[targetKey] = value
            }
        }
    }

    suspend fun replaceThemeForPrompt(
        target: ActivePrompt,
        values: ThemePreferenceValues,
    ) {
        context.userPreferencesDataStore.edit { preferences ->
            val prefix = themePrefixForPrompt(target)
            writeVisualThemeValues(preferences, prefix, values)
            writeThemeTargetMetadata(preferences, prefix, values)
        }
    }

    suspend fun mutateThemeForPrompt(
        target: ActivePrompt,
        transform: (ThemePreferenceValues) -> ThemePreferenceValues,
    ) {
        context.userPreferencesDataStore.edit { preferences ->
            val prefix = themePrefixForPrompt(target)
            val values = transform(readThemePreferenceValues(preferences, prefix))
            writeVisualThemeValues(preferences, prefix, values)
            writeThemeTargetMetadata(preferences, prefix, values)
        }
    }

    suspend fun resetVisualThemeForPrompt(
        target: ActivePrompt,
        values: ThemePreferenceValues,
    ) {
        context.userPreferencesDataStore.edit { preferences ->
            val prefix = themePrefixForPrompt(target)
            clearVisualThemeValues(preferences, prefix)
            writeThemeTargetMetadata(preferences, prefix, values)
        }
    }

    private suspend fun cloneThemeBetweenPrefixes(sourcePrefix: String, targetPrefix: String) {
        context.userPreferencesDataStore.edit { preferences ->
            copyThemeValues(
                preferences,
                sourcePrefix,
                targetPrefix,
                clearMissingTargetValues = false,
            )
        }
    }

    private suspend fun deleteThemeByPrefix(prefix: String) {
        context.userPreferencesDataStore.edit { preferences ->
            getAllStringThemeKeys().forEach { key ->
                preferences.remove(stringPreferencesKey("${prefix}${key.name}"))
            }
            getAllBooleanThemeKeys().forEach { key ->
                preferences.remove(booleanPreferencesKey("${prefix}${key.name}"))
            }
            getAllIntThemeKeys().forEach { key ->
                preferences.remove(intPreferencesKey("${prefix}${key.name}"))
            }
            getAllFloatThemeKeys().forEach { key ->
                preferences.remove(floatPreferencesKey("${prefix}${key.name}"))
            }
        }
    }

    private fun hasThemeByPrefix(preferences: Preferences, prefix: String): Boolean {
        return getAllStringThemeKeys().any { key -> preferences.contains(stringPreferencesKey("${prefix}${key.name}")) } ||
                getAllBooleanThemeKeys().any { key -> preferences.contains(booleanPreferencesKey("${prefix}${key.name}")) } ||
                getAllIntThemeKeys().any { key -> preferences.contains(intPreferencesKey("${prefix}${key.name}")) } ||
                getAllFloatThemeKeys().any { key -> preferences.contains(floatPreferencesKey("${prefix}${key.name}")) }
    }

    private suspend fun hasThemeByPrefix(prefix: String): Boolean {
        return hasThemeByPrefix(context.userPreferencesDataStore.data.first(), prefix)
    }

    private fun hasThemeContentByPrefix(preferences: Preferences, prefix: String): Boolean {
        return getAllStringThemeKeys()
            .filterNot { key -> key == KEY_CUSTOM_AI_AVATAR_URI || key == KEY_CUSTOM_CHAT_TITLE }
            .any { key -> preferences.contains(stringPreferencesKey("${prefix}${key.name}")) } ||
                getAllBooleanThemeKeys().any { key ->
                    preferences.contains(booleanPreferencesKey("${prefix}${key.name}"))
                } ||
                getAllIntThemeKeys().any { key ->
                    preferences.contains(intPreferencesKey("${prefix}${key.name}"))
                } ||
                getAllFloatThemeKeys().any { key ->
                    preferences.contains(floatPreferencesKey("${prefix}${key.name}"))
                }
    }

    private fun hasAnyScopedThemeContent(preferences: Preferences): Boolean {
        return preferences.asMap().keys.any { key ->
            val isScopedThemeKey =
                key.name.startsWith("character_card_theme_") ||
                    key.name.startsWith("character_group_theme_")
            isScopedThemeKey &&
                !key.name.endsWith("_${KEY_CUSTOM_AI_AVATAR_URI.name}") &&
                !key.name.endsWith("_${KEY_CUSTOM_CHAT_TITLE.name}")
        }
    }

    suspend fun migrateLegacyDefaultCharacterThemeIfEligible(
        activeCharacterCardId: String?,
        defaultCharacterWasCreated: Boolean,
    ) {
        context.userPreferencesDataStore.edit { preferences ->
            val defaultPrefix = getCharacterCardThemePrefix(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID)
            val shouldMigrate = ThemeScopeMigrationPolicy.shouldCopyLegacyThemeToDefaultCharacter(
                migrationCompleted = preferences[CHARACTER_THEME_DEFAULT_MIGRATION_COMPLETED] ?: false,
                activeCharacterCardId = activeCharacterCardId,
                defaultCharacterId = CharacterCardManager.DEFAULT_CHARACTER_CARD_ID,
                hasDefaultCharacterTheme = hasThemeContentByPrefix(preferences, defaultPrefix),
                hasAnyScopedTheme = hasAnyScopedThemeContent(preferences),
                defaultCharacterWasCreated = defaultCharacterWasCreated,
            )
            if (shouldMigrate) {
                copyThemeValues(
                    preferences,
                    sourcePrefix = null,
                    targetPrefix = defaultPrefix,
                    clearMissingTargetValues = false,
                )
            }
            preferences[CHARACTER_THEME_DEFAULT_MIGRATION_COMPLETED] = true
        }
    }

    suspend fun cloneThemeBetweenCharacterCards(sourceCharacterCardId: String, targetCharacterCardId: String) {
        cloneThemeBetweenPrefixes(
            getCharacterCardThemePrefix(sourceCharacterCardId),
            getCharacterCardThemePrefix(targetCharacterCardId)
        )
    }

    suspend fun deleteCharacterCardTheme(characterCardId: String) {
        deleteThemeByPrefix(getCharacterCardThemePrefix(characterCardId))
    }

    suspend fun hasCharacterCardTheme(characterCardId: String): Boolean {
        return hasThemeByPrefix(getCharacterCardThemePrefix(characterCardId))
    }

    suspend fun cloneThemeBetweenCharacterGroups(
        sourceCharacterGroupId: String,
        targetCharacterGroupId: String
    ) {
        cloneThemeBetweenPrefixes(
            getCharacterGroupThemePrefix(sourceCharacterGroupId),
            getCharacterGroupThemePrefix(targetCharacterGroupId)
        )
    }

    suspend fun deleteCharacterGroupTheme(characterGroupId: String) {
        deleteThemeByPrefix(getCharacterGroupThemePrefix(characterGroupId))
    }

    fun observeThemePreferenceSnapshot(
        characterCardId: String? = null,
        characterGroupId: String? = null
    ): Flow<ThemePreferenceSnapshot> {
        val normalizedGroupId = characterGroupId?.trim()?.takeIf { it.isNotBlank() }
        val normalizedCardId = characterCardId?.trim()?.takeIf { it.isNotBlank() }

        val (source, sourceId, prefix) = when {
            normalizedGroupId != null -> Triple(
                "character_group",
                normalizedGroupId,
                getCharacterGroupThemePrefix(normalizedGroupId),
            )

            normalizedCardId != null -> Triple(
                "character_card",
                normalizedCardId,
                getCharacterCardThemePrefix(normalizedCardId),
            )

            else -> error("ThemePreferenceSnapshot requires a character card or group target.")
        }
        return context.userPreferencesDataStore.data
            .map { preferences ->
                ThemePreferenceSnapshot(
                    source = source,
                    sourceId = sourceId,
                    values = readThemePreferenceValues(preferences, prefix),
                )
            }
            .distinctUntilChanged()
    }

    suspend fun resolveThemePreferenceSnapshot(
        characterCardId: String? = null,
        characterGroupId: String? = null
    ): ThemePreferenceSnapshot {
        return observeThemePreferenceSnapshot(
            characterCardId = characterCardId,
            characterGroupId = characterGroupId,
        ).first()
    }
}
