package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.model.LegacyUserProfile
import com.ai.assistance.operit.data.model.MemorySpace
import com.ai.assistance.operit.data.model.CharacterCardMemoryProfileBindingMode
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
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
    // entry could hide the released profile metadata that still owns existing ObjectBox databases.
    GlobalScope.launch {
        UserProfileDocumentRepository.getInstance(context).initialize()
        manager.ensureDefaultMemorySpace(defaultProfileName)
    }
}

data class LegacyUserProfileSnapshot(
    val activeProfileId: String,
    val profiles: List<LegacyUserProfile>
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
        private val USE_BACKGROUND_IMAGE = booleanPreferencesKey("use_background_image")
        private val BACKGROUND_IMAGE_URI = stringPreferencesKey("background_image_uri")
        private val BACKGROUND_IMAGE_OPACITY = floatPreferencesKey("background_image_opacity")

        // 背景媒体类型和视频设置
        private val BACKGROUND_MEDIA_TYPE = stringPreferencesKey("background_media_type")
        private val VIDEO_BACKGROUND_MUTED = booleanPreferencesKey("video_background_muted")
        private val VIDEO_BACKGROUND_LOOP = booleanPreferencesKey("video_background_loop")

        // 工具栏透明度设置
        private val TOOLBAR_TRANSPARENT = booleanPreferencesKey("toolbar_transparent")

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
        private val STATUS_BAR_HIDDEN = booleanPreferencesKey("status_bar_hidden")
        private val CHAT_HEADER_TRANSPARENT = booleanPreferencesKey("chat_header_transparent")
        private val CHAT_INPUT_TRANSPARENT = booleanPreferencesKey("chat_input_transparent")
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

        private val KEY_BACKGROUND_BLUR_RADIUS = floatPreferencesKey("background_blur_radius")
        private val KEY_CHAT_STYLE = stringPreferencesKey("chat_style")
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
        return LegacyUserProfileSnapshot(activeId, profiles)
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
                val space = MemorySpace(profile.id, profile.name)
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

    // 主题相关Flow
    val themeMode: Flow<String> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[THEME_MODE] ?: THEME_MODE_LIGHT
            }

    val useSystemTheme: Flow<Boolean> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[USE_SYSTEM_THEME] ?: true
            }

    val customPrimaryColor: Flow<Int?> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[CUSTOM_PRIMARY_COLOR]
            }

    val customSecondaryColor: Flow<Int?> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[CUSTOM_SECONDARY_COLOR]
            }

    val useCustomColors: Flow<Boolean> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[USE_CUSTOM_COLORS] ?: false
            }

    // 背景图片相关Flow
    val useBackgroundImage: Flow<Boolean> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[USE_BACKGROUND_IMAGE] ?: false
            }

    val backgroundImageUri: Flow<String?> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[BACKGROUND_IMAGE_URI]
            }

    val backgroundImageOpacity: Flow<Float> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[BACKGROUND_IMAGE_OPACITY] ?: 0.3f
            }

    // 背景媒体类型相关Flow
    val backgroundMediaType: Flow<String> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[BACKGROUND_MEDIA_TYPE] ?: MEDIA_TYPE_IMAGE
            }

    val videoBackgroundMuted: Flow<Boolean> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[VIDEO_BACKGROUND_MUTED] ?: true
            }

    val videoBackgroundLoop: Flow<Boolean> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[VIDEO_BACKGROUND_LOOP] ?: true
            }

    val toolbarTransparent: Flow<Boolean> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[TOOLBAR_TRANSPARENT] ?: false
            }

    val navigationDrawerWaterGlass: Flow<Boolean> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[NAVIGATION_DRAWER_WATER_GLASS] ?: false
            }

    val navigationDrawerButtonLiquidGlass: Flow<Boolean> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[NAVIGATION_DRAWER_BUTTON_LIQUID_GLASS] ?: false
            }

    val useCustomNavigationDrawerBackgroundColor: Flow<Boolean> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[USE_CUSTOM_NAVIGATION_DRAWER_BACKGROUND_COLOR] ?: false
            }

    val customNavigationDrawerBackgroundColor: Flow<Int?> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[CUSTOM_NAVIGATION_DRAWER_BACKGROUND_COLOR]
            }

    val customNavigationDrawerAccentColor: Flow<Int?> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[CUSTOM_NAVIGATION_DRAWER_ACCENT_COLOR]
            }

    val useCustomNavigationDrawerAccentColor: Flow<Boolean> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[USE_CUSTOM_NAVIGATION_DRAWER_ACCENT_COLOR] ?: false
            }

    val useCustomAppBarColor: Flow<Boolean> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[USE_CUSTOM_APP_BAR_COLOR] ?: false
            }
    
    val customAppBarColor: Flow<Int?> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[CUSTOM_APP_BAR_COLOR]
            }

    val useCustomStatusBarColor: Flow<Boolean> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[USE_CUSTOM_STATUS_BAR_COLOR] ?: false
            }
    
    val customStatusBarColor: Flow<Int?> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[CUSTOM_STATUS_BAR_COLOR]
            }

    val statusBarTransparent: Flow<Boolean> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[STATUS_BAR_TRANSPARENT] ?: false
            }

    val statusBarHidden: Flow<Boolean> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[STATUS_BAR_HIDDEN] ?: false
            }

    val chatHeaderTransparent: Flow<Boolean> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[CHAT_HEADER_TRANSPARENT] ?: false
            }

    val chatInputTransparent: Flow<Boolean> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[CHAT_INPUT_TRANSPARENT] ?: false
            }

    val chatInputFloating: Flow<Boolean> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[CHAT_INPUT_FLOATING] ?: false
            }

    val chatInputLiquidGlass: Flow<Boolean> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[CHAT_INPUT_LIQUID_GLASS] ?: false
            }

    val chatInputWaterGlass: Flow<Boolean> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[CHAT_INPUT_WATER_GLASS] ?: false
            }

    val forceAppBarContentColor: Flow<Boolean> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[FORCE_APP_BAR_CONTENT_COLOR_ENABLED] ?: false
            }

    val appBarContentColorMode: Flow<String> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[APP_BAR_CONTENT_COLOR_MODE] ?: APP_BAR_CONTENT_COLOR_MODE_LIGHT
            }

    val chatHeaderHistoryIconColor: Flow<Int?> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[CHAT_HEADER_HISTORY_ICON_COLOR]
            }

    val chatHeaderPipIconColor: Flow<Int?> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[CHAT_HEADER_PIP_ICON_COLOR]
            }

    val chatHeaderOverlayMode: Flow<Boolean> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[CHAT_HEADER_OVERLAY_MODE] ?: false
            }

    val useBackgroundBlur: Flow<Boolean> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[USE_BACKGROUND_BLUR] ?: false
            }

    val backgroundBlurRadius: Flow<Float> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[BACKGROUND_BLUR_RADIUS] ?: 10f
            }

    // Chat style preference
    val chatStyle: Flow<String> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[CHAT_STYLE] ?: CHAT_STYLE_CURSOR
            }

    val inputStyle: Flow<String> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[INPUT_STYLE] ?: INPUT_STYLE_AGENT
            }

    val bubbleShowAvatar: Flow<Boolean> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[BUBBLE_SHOW_AVATAR] ?: true
            }

    val bubbleWideLayoutEnabled: Flow<Boolean> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_WIDE_LAYOUT_ENABLED] ?: false
        }

    val cursorUserBubbleFollowTheme: Flow<Boolean> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[CURSOR_USER_BUBBLE_FOLLOW_THEME] ?: true
        }

    val cursorUserBubbleLiquidGlass: Flow<Boolean> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[CURSOR_USER_BUBBLE_LIQUID_GLASS] ?: false
        }

    val cursorUserBubbleWaterGlass: Flow<Boolean> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[CURSOR_USER_BUBBLE_WATER_GLASS] ?: false
        }

    val cursorUserBubbleColor: Flow<Int?> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[CURSOR_USER_BUBBLE_COLOR]
        }

    val bubbleUserBubbleLiquidGlass: Flow<Boolean> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_USER_BUBBLE_LIQUID_GLASS] ?: false
        }

    val bubbleUserBubbleWaterGlass: Flow<Boolean> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_USER_BUBBLE_WATER_GLASS] ?: false
        }

    val bubbleUserBubbleColor: Flow<Int?> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_USER_BUBBLE_COLOR]
        }

    val bubbleAiBubbleLiquidGlass: Flow<Boolean> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_AI_BUBBLE_LIQUID_GLASS] ?: false
        }

    val bubbleAiBubbleWaterGlass: Flow<Boolean> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_AI_BUBBLE_WATER_GLASS] ?: false
        }

    val bubbleAiBubbleColor: Flow<Int?> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_AI_BUBBLE_COLOR]
        }

    val bubbleUserTextColor: Flow<Int?> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_USER_TEXT_COLOR]
        }

    val bubbleAiTextColor: Flow<Int?> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_AI_TEXT_COLOR]
        }

    val bubbleUserUseCustomFont: Flow<Boolean> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_USER_USE_CUSTOM_FONT] ?: false
        }

    val bubbleUserFontType: Flow<String> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_USER_FONT_TYPE] ?: FONT_TYPE_SYSTEM
        }

    val bubbleUserSystemFontName: Flow<String> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_USER_SYSTEM_FONT_NAME] ?: SYSTEM_FONT_DEFAULT
        }

    val bubbleUserCustomFontPath: Flow<String?> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_USER_CUSTOM_FONT_PATH]
        }

    val bubbleAiUseCustomFont: Flow<Boolean> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_AI_USE_CUSTOM_FONT] ?: false
        }

    val bubbleAiFontType: Flow<String> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_AI_FONT_TYPE] ?: FONT_TYPE_SYSTEM
        }

    val bubbleAiSystemFontName: Flow<String> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_AI_SYSTEM_FONT_NAME] ?: SYSTEM_FONT_DEFAULT
        }

    val bubbleAiCustomFontPath: Flow<String?> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_AI_CUSTOM_FONT_PATH]
        }

    val bubbleUserUseImage: Flow<Boolean> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_USER_USE_IMAGE] ?: false
        }

    val bubbleAiUseImage: Flow<Boolean> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_AI_USE_IMAGE] ?: false
        }

    val bubbleUserImageUri: Flow<String?> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_USER_IMAGE_URI]
        }

    val bubbleAiImageUri: Flow<String?> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_AI_IMAGE_URI]
        }

    val bubbleUserImageCropLeft: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_USER_IMAGE_CROP_LEFT] ?: 0f
        }

    val bubbleUserImageCropTop: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_USER_IMAGE_CROP_TOP] ?: 0f
        }

    val bubbleUserImageCropRight: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_USER_IMAGE_CROP_RIGHT] ?: 0f
        }

    val bubbleUserImageCropBottom: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_USER_IMAGE_CROP_BOTTOM] ?: 0f
        }

    val bubbleUserImageRepeatStart: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_USER_IMAGE_REPEAT_START] ?: 0.35f
        }

    val bubbleUserImageRepeatEnd: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_USER_IMAGE_REPEAT_END] ?: 0.65f
        }

    val bubbleUserImageRepeatYStart: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_USER_IMAGE_REPEAT_Y_START]
                ?: (preferences[BUBBLE_USER_IMAGE_REPEAT_START] ?: 0.35f)
        }

    val bubbleUserImageRepeatYEnd: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_USER_IMAGE_REPEAT_Y_END]
                ?: (preferences[BUBBLE_USER_IMAGE_REPEAT_END] ?: 0.65f)
        }

    val bubbleUserImageScale: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_USER_IMAGE_SCALE] ?: 1f
        }

    val bubbleAiImageCropLeft: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_AI_IMAGE_CROP_LEFT] ?: 0f
        }

    val bubbleAiImageCropTop: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_AI_IMAGE_CROP_TOP] ?: 0f
        }

    val bubbleAiImageCropRight: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_AI_IMAGE_CROP_RIGHT] ?: 0f
        }

    val bubbleAiImageCropBottom: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_AI_IMAGE_CROP_BOTTOM] ?: 0f
        }

    val bubbleAiImageRepeatStart: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_AI_IMAGE_REPEAT_START] ?: 0.35f
        }

    val bubbleAiImageRepeatEnd: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_AI_IMAGE_REPEAT_END] ?: 0.65f
        }

    val bubbleAiImageRepeatYStart: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_AI_IMAGE_REPEAT_Y_START]
                ?: (preferences[BUBBLE_AI_IMAGE_REPEAT_START] ?: 0.35f)
        }

    val bubbleAiImageRepeatYEnd: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_AI_IMAGE_REPEAT_Y_END]
                ?: (preferences[BUBBLE_AI_IMAGE_REPEAT_END] ?: 0.65f)
        }

    val bubbleAiImageScale: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_AI_IMAGE_SCALE] ?: 1f
        }

    val bubbleImageRenderMode: Flow<String> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_IMAGE_RENDER_MODE] ?: BUBBLE_IMAGE_RENDER_MODE_TILED_NINE_SLICE
        }

    val bubbleUserRoundedCornersEnabled: Flow<Boolean> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_USER_ROUNDED_CORNERS_ENABLED] ?: true
        }

    val bubbleAiRoundedCornersEnabled: Flow<Boolean> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_AI_ROUNDED_CORNERS_ENABLED] ?: true
        }

    val bubbleUserContentPaddingLeft: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_USER_CONTENT_PADDING_LEFT] ?: 12f
        }

    val bubbleUserContentPaddingRight: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_USER_CONTENT_PADDING_RIGHT] ?: 12f
        }

    val bubbleAiContentPaddingLeft: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_AI_CONTENT_PADDING_LEFT] ?: 12f
        }

    val bubbleAiContentPaddingRight: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[BUBBLE_AI_CONTENT_PADDING_RIGHT] ?: 12f
        }

    val showThinkingProcess: Flow<Boolean> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[KEY_SHOW_THINKING_PROCESS] ?: true
            }

    val showStatusTags: Flow<Boolean> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[KEY_SHOW_STATUS_TAGS] ?: true
            }

    val showModelProvider: Flow<Boolean> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[KEY_SHOW_MODEL_PROVIDER] ?: false
        }

    val showModelName: Flow<Boolean> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[KEY_SHOW_MODEL_NAME] ?: false
        }

    val showRoleName: Flow<Boolean> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[KEY_SHOW_ROLE_NAME] ?: true
        }

    val showUserName: Flow<Boolean> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[KEY_SHOW_USER_NAME] ?: true
        }

    val showMessageTokenStats: Flow<Boolean> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[KEY_SHOW_MESSAGE_TOKEN_STATS] ?: false
        }

    val showMessageTimingStats: Flow<Boolean> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[KEY_SHOW_MESSAGE_TIMING_STATS] ?: false
        }

    val showMessageTimestamp: Flow<Boolean> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[KEY_SHOW_MESSAGE_TIMESTAMP] ?: false
        }

    val customUserAvatarUri: Flow<String?> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[KEY_CUSTOM_USER_AVATAR_URI]
            }

    val customAiAvatarUri: Flow<String?> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[KEY_CUSTOM_AI_AVATAR_URI]
            }

    val avatarShape: Flow<String> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[KEY_AVATAR_SHAPE] ?: AVATAR_SHAPE_CIRCLE
            }

    val avatarCornerRadius: Flow<Float> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[KEY_AVATAR_CORNER_RADIUS] ?: 8f
            }

    val onColorMode: Flow<String> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[KEY_ON_COLOR_MODE] ?: ON_COLOR_MODE_AUTO
            }

    val customChatTitle: Flow<String?> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[KEY_CUSTOM_CHAT_TITLE]
            }

    val showInputProcessingStatus: Flow<Boolean> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[KEY_SHOW_INPUT_PROCESSING_STATUS] ?: true
        }

    val showChatFloatingDotsAnimation: Flow<Boolean> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[KEY_SHOW_CHAT_FLOATING_DOTS_ANIMATION] ?: true
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

    // 字体设置相关Flow
    val useCustomFont: Flow<Boolean> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[USE_CUSTOM_FONT] ?: false
        }

    val fontType: Flow<String> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[FONT_TYPE] ?: FONT_TYPE_SYSTEM
        }

    val systemFontName: Flow<String> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[SYSTEM_FONT_NAME] ?: SYSTEM_FONT_DEFAULT
        }

    val customFontPath: Flow<String?> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[CUSTOM_FONT_PATH]
        }

    val fontScale: Flow<Float> =
        context.userPreferencesDataStore.data.map { preferences ->
            preferences[FONT_SCALE] ?: 1.0f
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

    fun getCustomChatTitleForCharacterCardFlow(characterCardId: String): Flow<String?> {
        return context.userPreferencesDataStore.data.map { preferences ->
            val prefix = getCharacterCardThemePrefix(characterCardId)
            val key = stringPreferencesKey("${prefix}${KEY_CUSTOM_CHAT_TITLE.name}")
            preferences[key]
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

    fun getCustomChatTitleForCharacterGroupFlow(characterGroupId: String): Flow<String?> {
        return context.userPreferencesDataStore.data.map { preferences ->
            val prefix = getCharacterGroupThemePrefix(characterGroupId)
            val key = stringPreferencesKey("${prefix}${KEY_CUSTOM_CHAT_TITLE.name}")
            preferences[key]
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

    // 保存主题设置
    suspend fun saveThemeSettings(
            themeMode: String? = null,
            useSystemTheme: Boolean? = null,
            customPrimaryColor: Int? = null,
            customSecondaryColor: Int? = null,
            useCustomColors: Boolean? = null,
            useBackgroundImage: Boolean? = null,
            backgroundImageUri: String? = null,
            backgroundImageOpacity: Float? = null,
            backgroundMediaType: String? = null,
            videoBackgroundMuted: Boolean? = null,
            videoBackgroundLoop: Boolean? = null,
            toolbarTransparent: Boolean? = null,
            navigationDrawerWaterGlass: Boolean? = null,
            navigationDrawerButtonLiquidGlass: Boolean? = null,
            useCustomNavigationDrawerBackgroundColor: Boolean? = null,
            customNavigationDrawerBackgroundColor: Int? = null,
            useCustomNavigationDrawerAccentColor: Boolean? = null,
            customNavigationDrawerAccentColor: Int? = null,
            useCustomAppBarColor: Boolean? = null,
            customAppBarColor: Int? = null,
            useCustomStatusBarColor: Boolean? = null,
            customStatusBarColor: Int? = null,
            statusBarTransparent: Boolean? = null,
            statusBarHidden: Boolean? = null,
            chatHeaderTransparent: Boolean? = null,
            chatInputTransparent: Boolean? = null,
            chatInputFloating: Boolean? = null,
            chatInputLiquidGlass: Boolean? = null,
            chatInputWaterGlass: Boolean? = null,
            forceAppBarContentColor: Boolean? = null,
            appBarContentColorMode: String? = null,
            chatHeaderHistoryIconColor: Int? = null,
            chatHeaderPipIconColor: Int? = null,
            chatHeaderOverlayMode: Boolean? = null,
            useBackgroundBlur: Boolean? = null,
            backgroundBlurRadius: Float? = null,
            chatStyle: String? = null,
            bubbleShowAvatar: Boolean? = null,
            bubbleWideLayoutEnabled: Boolean? = null,
            cursorUserBubbleFollowTheme: Boolean? = null,
            cursorUserBubbleLiquidGlass: Boolean? = null,
            cursorUserBubbleWaterGlass: Boolean? = null,
            cursorUserBubbleColor: Int? = null,
            bubbleUserBubbleLiquidGlass: Boolean? = null,
            bubbleUserBubbleWaterGlass: Boolean? = null,
            bubbleUserBubbleColor: Int? = null,
            bubbleAiBubbleLiquidGlass: Boolean? = null,
            bubbleAiBubbleWaterGlass: Boolean? = null,
            bubbleAiBubbleColor: Int? = null,
            bubbleUserTextColor: Int? = null,
            bubbleAiTextColor: Int? = null,
            bubbleUserUseCustomFont: Boolean? = null,
            bubbleUserFontType: String? = null,
            bubbleUserSystemFontName: String? = null,
            bubbleUserCustomFontPath: String? = null,
            bubbleAiUseCustomFont: Boolean? = null,
            bubbleAiFontType: String? = null,
            bubbleAiSystemFontName: String? = null,
            bubbleAiCustomFontPath: String? = null,
            bubbleUserUseImage: Boolean? = null,
            bubbleAiUseImage: Boolean? = null,
            bubbleUserImageUri: String? = null,
            bubbleAiImageUri: String? = null,
            bubbleUserImageCropLeft: Float? = null,
            bubbleUserImageCropTop: Float? = null,
            bubbleUserImageCropRight: Float? = null,
            bubbleUserImageCropBottom: Float? = null,
            bubbleUserImageRepeatStart: Float? = null,
            bubbleUserImageRepeatEnd: Float? = null,
            bubbleUserImageRepeatYStart: Float? = null,
            bubbleUserImageRepeatYEnd: Float? = null,
            bubbleUserImageScale: Float? = null,
            bubbleAiImageCropLeft: Float? = null,
            bubbleAiImageCropTop: Float? = null,
            bubbleAiImageCropRight: Float? = null,
            bubbleAiImageCropBottom: Float? = null,
            bubbleAiImageRepeatStart: Float? = null,
            bubbleAiImageRepeatEnd: Float? = null,
            bubbleAiImageRepeatYStart: Float? = null,
            bubbleAiImageRepeatYEnd: Float? = null,
            bubbleAiImageScale: Float? = null,
            bubbleImageRenderMode: String? = null,
            bubbleUserRoundedCornersEnabled: Boolean? = null,
            bubbleAiRoundedCornersEnabled: Boolean? = null,
            bubbleUserContentPaddingLeft: Float? = null,
            bubbleUserContentPaddingRight: Float? = null,
            bubbleAiContentPaddingLeft: Float? = null,
            bubbleAiContentPaddingRight: Float? = null,
            showThinkingProcess: Boolean? = null,
            showStatusTags: Boolean? = null,
            showModelProvider: Boolean? = null,
            showModelName: Boolean? = null,
            showRoleName: Boolean? = null,
            showUserName: Boolean? = null,
            showMessageTokenStats: Boolean? = null,
            showMessageTimingStats: Boolean? = null,
            showMessageTimestamp: Boolean? = null,
            customUserAvatarUri: String? = null,
            customAiAvatarUri: String? = null,
            avatarShape: String? = null,
            avatarCornerRadius: Float? = null,
            onColorMode: String? = null,
            customChatTitle: String? = null,
            showInputProcessingStatus: Boolean? = null,
            showChatFloatingDotsAnimation: Boolean? = null,
            inputStyle: String? = null,
            useCustomFont: Boolean? = null,
            fontType: String? = null,
            systemFontName: String? = null,
            customFontPath: String? = null,
            fontScale: Float? = null
    ) {
        context.userPreferencesDataStore.edit { preferences ->
            themeMode?.let { preferences[THEME_MODE] = it }
            useSystemTheme?.let { preferences[USE_SYSTEM_THEME] = it }
            customPrimaryColor?.let { preferences[CUSTOM_PRIMARY_COLOR] = it }
            customSecondaryColor?.let { preferences[CUSTOM_SECONDARY_COLOR] = it }
            useCustomColors?.let { preferences[USE_CUSTOM_COLORS] = it }
            useBackgroundImage?.let { preferences[USE_BACKGROUND_IMAGE] = it }
            backgroundImageUri?.let {
                // Simply store the URI as a string in preferences
                // No need to take persistent permissions as we're using internal storage
                preferences[BACKGROUND_IMAGE_URI] = it
            }
            backgroundImageOpacity?.let { preferences[BACKGROUND_IMAGE_OPACITY] = it }
            backgroundMediaType?.let { preferences[BACKGROUND_MEDIA_TYPE] = it }
            videoBackgroundMuted?.let { preferences[VIDEO_BACKGROUND_MUTED] = it }
            videoBackgroundLoop?.let { preferences[VIDEO_BACKGROUND_LOOP] = it }
            toolbarTransparent?.let { preferences[TOOLBAR_TRANSPARENT] = it }
            navigationDrawerWaterGlass?.let { preferences[NAVIGATION_DRAWER_WATER_GLASS] = it }
            navigationDrawerButtonLiquidGlass?.let {
                preferences[NAVIGATION_DRAWER_BUTTON_LIQUID_GLASS] = it
            }
            useCustomNavigationDrawerBackgroundColor?.let {
                preferences[USE_CUSTOM_NAVIGATION_DRAWER_BACKGROUND_COLOR] = it
            }
            customNavigationDrawerBackgroundColor?.let {
                preferences[CUSTOM_NAVIGATION_DRAWER_BACKGROUND_COLOR] = it
            }
            useCustomNavigationDrawerAccentColor?.let {
                preferences[USE_CUSTOM_NAVIGATION_DRAWER_ACCENT_COLOR] = it
            }
            customNavigationDrawerAccentColor?.let {
                preferences[CUSTOM_NAVIGATION_DRAWER_ACCENT_COLOR] = it
            }
            useCustomAppBarColor?.let { preferences[USE_CUSTOM_APP_BAR_COLOR] = it }
            customAppBarColor?.let { preferences[CUSTOM_APP_BAR_COLOR] = it }
            useCustomStatusBarColor?.let { preferences[USE_CUSTOM_STATUS_BAR_COLOR] = it }
            customStatusBarColor?.let { preferences[CUSTOM_STATUS_BAR_COLOR] = it }
            statusBarTransparent?.let { preferences[STATUS_BAR_TRANSPARENT] = it }
            statusBarHidden?.let { preferences[STATUS_BAR_HIDDEN] = it }
            chatHeaderTransparent?.let { preferences[CHAT_HEADER_TRANSPARENT] = it }
            chatInputTransparent?.let { preferences[CHAT_INPUT_TRANSPARENT] = it }
            chatInputFloating?.let { preferences[CHAT_INPUT_FLOATING] = it }
            chatInputLiquidGlass?.let {
                preferences[CHAT_INPUT_LIQUID_GLASS] = it
                if (it) {
                    preferences[CHAT_INPUT_WATER_GLASS] = false
                }
            }
            chatInputWaterGlass?.let {
                preferences[CHAT_INPUT_WATER_GLASS] = it
                if (it) {
                    preferences[CHAT_INPUT_LIQUID_GLASS] = false
                }
            }
            forceAppBarContentColor?.let { preferences[FORCE_APP_BAR_CONTENT_COLOR_ENABLED] = it }
            appBarContentColorMode?.let { preferences[APP_BAR_CONTENT_COLOR_MODE] = it }
            chatHeaderHistoryIconColor?.let { preferences[CHAT_HEADER_HISTORY_ICON_COLOR] = it }
            chatHeaderPipIconColor?.let { preferences[CHAT_HEADER_PIP_ICON_COLOR] = it }
            chatHeaderOverlayMode?.let { preferences[CHAT_HEADER_OVERLAY_MODE] = it }
            useBackgroundBlur?.let { preferences[USE_BACKGROUND_BLUR] = it }
            backgroundBlurRadius?.let { preferences[BACKGROUND_BLUR_RADIUS] = it }
            chatStyle?.let { preferences[CHAT_STYLE] = it }
            bubbleShowAvatar?.let { preferences[BUBBLE_SHOW_AVATAR] = it }
            bubbleWideLayoutEnabled?.let { preferences[BUBBLE_WIDE_LAYOUT_ENABLED] = it }
            cursorUserBubbleFollowTheme?.let { preferences[CURSOR_USER_BUBBLE_FOLLOW_THEME] = it }
            cursorUserBubbleLiquidGlass?.let {
                preferences[CURSOR_USER_BUBBLE_LIQUID_GLASS] = it
                if (it) {
                    preferences[CURSOR_USER_BUBBLE_WATER_GLASS] = false
                }
            }
            cursorUserBubbleWaterGlass?.let {
                preferences[CURSOR_USER_BUBBLE_WATER_GLASS] = it
                if (it) {
                    preferences[CURSOR_USER_BUBBLE_LIQUID_GLASS] = false
                }
            }
            cursorUserBubbleColor?.let { preferences[CURSOR_USER_BUBBLE_COLOR] = it }
            bubbleUserBubbleLiquidGlass?.let {
                preferences[BUBBLE_USER_BUBBLE_LIQUID_GLASS] = it
                if (it) {
                    preferences[BUBBLE_USER_BUBBLE_WATER_GLASS] = false
                    preferences[BUBBLE_USER_USE_IMAGE] = false
                }
            }
            bubbleUserBubbleWaterGlass?.let {
                preferences[BUBBLE_USER_BUBBLE_WATER_GLASS] = it
                if (it) {
                    preferences[BUBBLE_USER_BUBBLE_LIQUID_GLASS] = false
                    preferences[BUBBLE_USER_USE_IMAGE] = false
                }
            }
            bubbleUserBubbleColor?.let { preferences[BUBBLE_USER_BUBBLE_COLOR] = it }
            bubbleAiBubbleLiquidGlass?.let {
                preferences[BUBBLE_AI_BUBBLE_LIQUID_GLASS] = it
                if (it) {
                    preferences[BUBBLE_AI_BUBBLE_WATER_GLASS] = false
                    preferences[BUBBLE_AI_USE_IMAGE] = false
                }
            }
            bubbleAiBubbleWaterGlass?.let {
                preferences[BUBBLE_AI_BUBBLE_WATER_GLASS] = it
                if (it) {
                    preferences[BUBBLE_AI_BUBBLE_LIQUID_GLASS] = false
                    preferences[BUBBLE_AI_USE_IMAGE] = false
                }
            }
            bubbleAiBubbleColor?.let { preferences[BUBBLE_AI_BUBBLE_COLOR] = it }
            bubbleUserTextColor?.let { preferences[BUBBLE_USER_TEXT_COLOR] = it }
            bubbleAiTextColor?.let { preferences[BUBBLE_AI_TEXT_COLOR] = it }
            bubbleUserUseCustomFont?.let { preferences[BUBBLE_USER_USE_CUSTOM_FONT] = it }
            bubbleUserFontType?.let { preferences[BUBBLE_USER_FONT_TYPE] = it }
            bubbleUserSystemFontName?.let { preferences[BUBBLE_USER_SYSTEM_FONT_NAME] = it }
            bubbleUserCustomFontPath?.let { preferences[BUBBLE_USER_CUSTOM_FONT_PATH] = it }
            bubbleAiUseCustomFont?.let { preferences[BUBBLE_AI_USE_CUSTOM_FONT] = it }
            bubbleAiFontType?.let { preferences[BUBBLE_AI_FONT_TYPE] = it }
            bubbleAiSystemFontName?.let { preferences[BUBBLE_AI_SYSTEM_FONT_NAME] = it }
            bubbleAiCustomFontPath?.let { preferences[BUBBLE_AI_CUSTOM_FONT_PATH] = it }
            bubbleUserUseImage?.let { preferences[BUBBLE_USER_USE_IMAGE] = it }
            bubbleAiUseImage?.let { preferences[BUBBLE_AI_USE_IMAGE] = it }
            bubbleUserImageUri?.let { preferences[BUBBLE_USER_IMAGE_URI] = it }
            bubbleAiImageUri?.let { preferences[BUBBLE_AI_IMAGE_URI] = it }
            bubbleUserImageCropLeft?.let { preferences[BUBBLE_USER_IMAGE_CROP_LEFT] = it }
            bubbleUserImageCropTop?.let { preferences[BUBBLE_USER_IMAGE_CROP_TOP] = it }
            bubbleUserImageCropRight?.let { preferences[BUBBLE_USER_IMAGE_CROP_RIGHT] = it }
            bubbleUserImageCropBottom?.let { preferences[BUBBLE_USER_IMAGE_CROP_BOTTOM] = it }
            bubbleUserImageRepeatStart?.let { preferences[BUBBLE_USER_IMAGE_REPEAT_START] = it }
            bubbleUserImageRepeatEnd?.let { preferences[BUBBLE_USER_IMAGE_REPEAT_END] = it }
            bubbleUserImageRepeatYStart?.let { preferences[BUBBLE_USER_IMAGE_REPEAT_Y_START] = it }
            bubbleUserImageRepeatYEnd?.let { preferences[BUBBLE_USER_IMAGE_REPEAT_Y_END] = it }
            bubbleUserImageScale?.let { preferences[BUBBLE_USER_IMAGE_SCALE] = it }
            bubbleAiImageCropLeft?.let { preferences[BUBBLE_AI_IMAGE_CROP_LEFT] = it }
            bubbleAiImageCropTop?.let { preferences[BUBBLE_AI_IMAGE_CROP_TOP] = it }
            bubbleAiImageCropRight?.let { preferences[BUBBLE_AI_IMAGE_CROP_RIGHT] = it }
            bubbleAiImageCropBottom?.let { preferences[BUBBLE_AI_IMAGE_CROP_BOTTOM] = it }
            bubbleAiImageRepeatStart?.let { preferences[BUBBLE_AI_IMAGE_REPEAT_START] = it }
            bubbleAiImageRepeatEnd?.let { preferences[BUBBLE_AI_IMAGE_REPEAT_END] = it }
            bubbleAiImageRepeatYStart?.let { preferences[BUBBLE_AI_IMAGE_REPEAT_Y_START] = it }
            bubbleAiImageRepeatYEnd?.let { preferences[BUBBLE_AI_IMAGE_REPEAT_Y_END] = it }
            bubbleAiImageScale?.let { preferences[BUBBLE_AI_IMAGE_SCALE] = it }
            bubbleImageRenderMode?.let { preferences[BUBBLE_IMAGE_RENDER_MODE] = it }
            bubbleUserRoundedCornersEnabled?.let { preferences[BUBBLE_USER_ROUNDED_CORNERS_ENABLED] = it }
            bubbleAiRoundedCornersEnabled?.let { preferences[BUBBLE_AI_ROUNDED_CORNERS_ENABLED] = it }
            bubbleUserContentPaddingLeft?.let { preferences[BUBBLE_USER_CONTENT_PADDING_LEFT] = it }
            bubbleUserContentPaddingRight?.let { preferences[BUBBLE_USER_CONTENT_PADDING_RIGHT] = it }
            bubbleAiContentPaddingLeft?.let { preferences[BUBBLE_AI_CONTENT_PADDING_LEFT] = it }
            bubbleAiContentPaddingRight?.let { preferences[BUBBLE_AI_CONTENT_PADDING_RIGHT] = it }
            showThinkingProcess?.let { preferences[KEY_SHOW_THINKING_PROCESS] = it }
            showStatusTags?.let { preferences[KEY_SHOW_STATUS_TAGS] = it }
            showModelProvider?.let { preferences[KEY_SHOW_MODEL_PROVIDER] = it }
            showModelName?.let { preferences[KEY_SHOW_MODEL_NAME] = it }
            showRoleName?.let { preferences[KEY_SHOW_ROLE_NAME] = it }
            showUserName?.let { preferences[KEY_SHOW_USER_NAME] = it }
            showMessageTokenStats?.let { preferences[KEY_SHOW_MESSAGE_TOKEN_STATS] = it }
            showMessageTimingStats?.let { preferences[KEY_SHOW_MESSAGE_TIMING_STATS] = it }
            showMessageTimestamp?.let { preferences[KEY_SHOW_MESSAGE_TIMESTAMP] = it }
            customUserAvatarUri?.let { preferences[KEY_CUSTOM_USER_AVATAR_URI] = it }
            customAiAvatarUri?.let { preferences[KEY_CUSTOM_AI_AVATAR_URI] = it }
            avatarShape?.let { preferences[KEY_AVATAR_SHAPE] = it }
            avatarCornerRadius?.let { preferences[KEY_AVATAR_CORNER_RADIUS] = it }
            onColorMode?.let { preferences[KEY_ON_COLOR_MODE] = it }
            customChatTitle?.let { preferences[KEY_CUSTOM_CHAT_TITLE] = it }
            showInputProcessingStatus?.let { preferences[KEY_SHOW_INPUT_PROCESSING_STATUS] = it }
            showChatFloatingDotsAnimation?.let { preferences[KEY_SHOW_CHAT_FLOATING_DOTS_ANIMATION] = it }
            inputStyle?.let { preferences[INPUT_STYLE] = it }
            // 字体设置
            useCustomFont?.let { preferences[USE_CUSTOM_FONT] = it }
            fontType?.let { preferences[FONT_TYPE] = it }
            systemFontName?.let { preferences[SYSTEM_FONT_NAME] = it }
            customFontPath?.let { preferences[CUSTOM_FONT_PATH] = it }
            fontScale?.let { preferences[FONT_SCALE] = it }
        }
    }

    // 重置主题设置到默认值
    suspend fun resetThemeSettings() {
        context.userPreferencesDataStore.edit { preferences ->
            preferences.remove(THEME_MODE)
            preferences.remove(USE_SYSTEM_THEME)
            preferences.remove(CUSTOM_PRIMARY_COLOR)
            preferences.remove(CUSTOM_SECONDARY_COLOR)
            preferences.remove(USE_CUSTOM_COLORS)
            preferences.remove(USE_BACKGROUND_IMAGE)
            preferences.remove(BACKGROUND_IMAGE_URI)
            preferences.remove(BACKGROUND_IMAGE_OPACITY)
            preferences.remove(BACKGROUND_MEDIA_TYPE)
            preferences.remove(VIDEO_BACKGROUND_MUTED)
            preferences.remove(VIDEO_BACKGROUND_LOOP)
            preferences.remove(TOOLBAR_TRANSPARENT)
            preferences.remove(NAVIGATION_DRAWER_WATER_GLASS)
            preferences.remove(NAVIGATION_DRAWER_BUTTON_LIQUID_GLASS)
            preferences.remove(USE_CUSTOM_NAVIGATION_DRAWER_BACKGROUND_COLOR)
            preferences.remove(CUSTOM_NAVIGATION_DRAWER_BACKGROUND_COLOR)
            preferences.remove(USE_CUSTOM_NAVIGATION_DRAWER_ACCENT_COLOR)
            preferences.remove(CUSTOM_NAVIGATION_DRAWER_ACCENT_COLOR)
            preferences.remove(USE_CUSTOM_STATUS_BAR_COLOR)
            preferences.remove(CUSTOM_STATUS_BAR_COLOR)
            preferences.remove(STATUS_BAR_TRANSPARENT)
            preferences.remove(STATUS_BAR_HIDDEN)
            preferences.remove(CHAT_HEADER_TRANSPARENT)
            preferences.remove(CHAT_INPUT_TRANSPARENT)
            preferences.remove(CHAT_INPUT_FLOATING)
            preferences.remove(CHAT_INPUT_LIQUID_GLASS)
            preferences.remove(CHAT_INPUT_WATER_GLASS)
            preferences.remove(FORCE_APP_BAR_CONTENT_COLOR_ENABLED)
            preferences.remove(APP_BAR_CONTENT_COLOR_MODE)
            preferences.remove(CHAT_HEADER_HISTORY_ICON_COLOR)
            preferences.remove(CHAT_HEADER_PIP_ICON_COLOR)
            preferences.remove(CHAT_HEADER_OVERLAY_MODE)
            preferences.remove(USE_BACKGROUND_BLUR)
            preferences.remove(BACKGROUND_BLUR_RADIUS)
            preferences.remove(CHAT_STYLE)
            preferences.remove(BUBBLE_SHOW_AVATAR)
            preferences.remove(BUBBLE_WIDE_LAYOUT_ENABLED)
            preferences.remove(CURSOR_USER_BUBBLE_FOLLOW_THEME)
            preferences.remove(CURSOR_USER_BUBBLE_LIQUID_GLASS)
            preferences.remove(CURSOR_USER_BUBBLE_WATER_GLASS)
            preferences.remove(CURSOR_USER_BUBBLE_COLOR)
            preferences.remove(BUBBLE_USER_BUBBLE_LIQUID_GLASS)
            preferences.remove(BUBBLE_USER_BUBBLE_WATER_GLASS)
            preferences.remove(BUBBLE_USER_BUBBLE_COLOR)
            preferences.remove(BUBBLE_AI_BUBBLE_LIQUID_GLASS)
            preferences.remove(BUBBLE_AI_BUBBLE_WATER_GLASS)
            preferences.remove(BUBBLE_AI_BUBBLE_COLOR)
            preferences.remove(BUBBLE_USER_TEXT_COLOR)
            preferences.remove(BUBBLE_AI_TEXT_COLOR)
            preferences.remove(BUBBLE_USER_USE_CUSTOM_FONT)
            preferences.remove(BUBBLE_USER_FONT_TYPE)
            preferences.remove(BUBBLE_USER_SYSTEM_FONT_NAME)
            preferences.remove(BUBBLE_USER_CUSTOM_FONT_PATH)
            preferences.remove(BUBBLE_AI_USE_CUSTOM_FONT)
            preferences.remove(BUBBLE_AI_FONT_TYPE)
            preferences.remove(BUBBLE_AI_SYSTEM_FONT_NAME)
            preferences.remove(BUBBLE_AI_CUSTOM_FONT_PATH)
            preferences.remove(BUBBLE_USER_USE_IMAGE)
            preferences.remove(BUBBLE_AI_USE_IMAGE)
            preferences.remove(BUBBLE_USER_IMAGE_URI)
            preferences.remove(BUBBLE_AI_IMAGE_URI)
            preferences.remove(BUBBLE_USER_IMAGE_CROP_LEFT)
            preferences.remove(BUBBLE_USER_IMAGE_CROP_TOP)
            preferences.remove(BUBBLE_USER_IMAGE_CROP_RIGHT)
            preferences.remove(BUBBLE_USER_IMAGE_CROP_BOTTOM)
            preferences.remove(BUBBLE_USER_IMAGE_REPEAT_START)
            preferences.remove(BUBBLE_USER_IMAGE_REPEAT_END)
            preferences.remove(BUBBLE_USER_IMAGE_REPEAT_Y_START)
            preferences.remove(BUBBLE_USER_IMAGE_REPEAT_Y_END)
            preferences.remove(BUBBLE_USER_IMAGE_SCALE)
            preferences.remove(BUBBLE_AI_IMAGE_CROP_LEFT)
            preferences.remove(BUBBLE_AI_IMAGE_CROP_TOP)
            preferences.remove(BUBBLE_AI_IMAGE_CROP_RIGHT)
            preferences.remove(BUBBLE_AI_IMAGE_CROP_BOTTOM)
            preferences.remove(BUBBLE_AI_IMAGE_REPEAT_START)
            preferences.remove(BUBBLE_AI_IMAGE_REPEAT_END)
            preferences.remove(BUBBLE_AI_IMAGE_REPEAT_Y_START)
            preferences.remove(BUBBLE_AI_IMAGE_REPEAT_Y_END)
            preferences.remove(BUBBLE_AI_IMAGE_SCALE)
            preferences.remove(BUBBLE_IMAGE_RENDER_MODE)
            preferences.remove(BUBBLE_USER_ROUNDED_CORNERS_ENABLED)
            preferences.remove(BUBBLE_AI_ROUNDED_CORNERS_ENABLED)
            preferences.remove(BUBBLE_USER_CONTENT_PADDING_LEFT)
            preferences.remove(BUBBLE_USER_CONTENT_PADDING_RIGHT)
            preferences.remove(BUBBLE_AI_CONTENT_PADDING_LEFT)
            preferences.remove(BUBBLE_AI_CONTENT_PADDING_RIGHT)
            preferences.remove(KEY_SHOW_THINKING_PROCESS)
            preferences.remove(KEY_SHOW_STATUS_TAGS)
            preferences.remove(KEY_SHOW_MODEL_PROVIDER)
            preferences.remove(KEY_SHOW_MODEL_NAME)
            preferences.remove(KEY_SHOW_ROLE_NAME)
            preferences.remove(KEY_SHOW_USER_NAME)
            preferences.remove(KEY_SHOW_MESSAGE_TOKEN_STATS)
            preferences.remove(KEY_SHOW_MESSAGE_TIMING_STATS)
            preferences.remove(KEY_SHOW_MESSAGE_TIMESTAMP)
            preferences.remove(KEY_CUSTOM_USER_AVATAR_URI)
            preferences.remove(KEY_CUSTOM_AI_AVATAR_URI)
            preferences.remove(KEY_AVATAR_SHAPE)
            preferences.remove(KEY_AVATAR_CORNER_RADIUS)
            preferences.remove(KEY_ON_COLOR_MODE)
            preferences.remove(KEY_CUSTOM_CHAT_TITLE)
            preferences.remove(KEY_SHOW_INPUT_PROCESSING_STATUS)
            preferences.remove(KEY_SHOW_CHAT_FLOATING_DOTS_ANIMATION)
            preferences.remove(INPUT_STYLE)
            // 重置字体设置
            preferences.remove(USE_CUSTOM_FONT)
            preferences.remove(FONT_TYPE)
            preferences.remove(SYSTEM_FONT_NAME)
            preferences.remove(CUSTOM_FONT_PATH)
            preferences.remove(FONT_SCALE)
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
            KEY_SHOW_INPUT_PROCESSING_STATUS, KEY_SHOW_CHAT_FLOATING_DOTS_ANIMATION, USE_CUSTOM_FONT,
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
            BACKGROUND_IMAGE_OPACITY, BACKGROUND_BLUR_RADIUS, KEY_AVATAR_CORNER_RADIUS, FONT_SCALE,
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

    private suspend fun copyCurrentThemeToPrefix(prefix: String) {
        context.userPreferencesDataStore.edit { preferences ->
            getAllStringThemeKeys().forEach { key ->
                preferences[key]?.let { value ->
                    preferences[stringPreferencesKey("${prefix}${key.name}")] = value
                }
            }
            getAllBooleanThemeKeys().forEach { key ->
                preferences[key]?.let { value ->
                    preferences[booleanPreferencesKey("${prefix}${key.name}")] = value
                }
            }
            getAllIntThemeKeys().forEach { key ->
                preferences[key]?.let { value ->
                    preferences[intPreferencesKey("${prefix}${key.name}")] = value
                }
            }
            getAllFloatThemeKeys().forEach { key ->
                preferences[key]?.let { value ->
                    preferences[floatPreferencesKey("${prefix}${key.name}")] = value
                }
            }
        }
    }

    private suspend fun cloneThemeBetweenPrefixes(sourcePrefix: String, targetPrefix: String) {
        context.userPreferencesDataStore.edit { preferences ->
            getAllStringThemeKeys().forEach { key ->
                val sourceKey = stringPreferencesKey("${sourcePrefix}${key.name}")
                preferences[sourceKey]?.let { value ->
                    val targetKey = stringPreferencesKey("${targetPrefix}${key.name}")
                    preferences[targetKey] = value
                }
            }

            getAllBooleanThemeKeys().forEach { key ->
                val sourceKey = booleanPreferencesKey("${sourcePrefix}${key.name}")
                preferences[sourceKey]?.let { value ->
                    val targetKey = booleanPreferencesKey("${targetPrefix}${key.name}")
                    preferences[targetKey] = value
                }
            }

            getAllIntThemeKeys().forEach { key ->
                val sourceKey = intPreferencesKey("${sourcePrefix}${key.name}")
                preferences[sourceKey]?.let { value ->
                    val targetKey = intPreferencesKey("${targetPrefix}${key.name}")
                    preferences[targetKey] = value
                }
            }

            getAllFloatThemeKeys().forEach { key ->
                val sourceKey = floatPreferencesKey("${sourcePrefix}${key.name}")
                preferences[sourceKey]?.let { value ->
                    val targetKey = floatPreferencesKey("${targetPrefix}${key.name}")
                    preferences[targetKey] = value
                }
            }
        }
    }

    private suspend fun switchToThemeByPrefix(prefix: String) {
        context.userPreferencesDataStore.edit { preferences ->
            getAllStringThemeKeys().forEach { key ->
                val cardKey = stringPreferencesKey("${prefix}${key.name}")
                if (preferences.contains(cardKey)) {
                    preferences[key] = preferences[cardKey]!!
                } else {
                    preferences.remove(key)
                }
            }
            getAllBooleanThemeKeys().forEach { key ->
                val cardKey = booleanPreferencesKey("${prefix}${key.name}")
                if (preferences.contains(cardKey)) {
                    preferences[key] = preferences[cardKey]!!
                } else {
                    preferences.remove(key)
                }
            }
            getAllIntThemeKeys().forEach { key ->
                val cardKey = intPreferencesKey("${prefix}${key.name}")
                if (preferences.contains(cardKey)) {
                    preferences[key] = preferences[cardKey]!!
                } else {
                    preferences.remove(key)
                }
            }
            getAllFloatThemeKeys().forEach { key ->
                val cardKey = floatPreferencesKey("${prefix}${key.name}")
                if (preferences.contains(cardKey)) {
                    preferences[key] = preferences[cardKey]!!
                } else {
                    preferences.remove(key)
                }
            }
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

    private suspend fun hasThemeByPrefix(prefix: String): Boolean {
        val preferences = context.userPreferencesDataStore.data.first()
        return getAllStringThemeKeys().any { key -> preferences.contains(stringPreferencesKey("${prefix}${key.name}")) } ||
                getAllBooleanThemeKeys().any { key -> preferences.contains(booleanPreferencesKey("${prefix}${key.name}")) } ||
                getAllIntThemeKeys().any { key -> preferences.contains(intPreferencesKey("${prefix}${key.name}")) } ||
                getAllFloatThemeKeys().any { key -> preferences.contains(floatPreferencesKey("${prefix}${key.name}")) }
    }

    suspend fun copyCurrentThemeToCharacterCard(characterCardId: String) {
        copyCurrentThemeToPrefix(getCharacterCardThemePrefix(characterCardId))
    }

    suspend fun cloneThemeBetweenCharacterCards(sourceCharacterCardId: String, targetCharacterCardId: String) {
        cloneThemeBetweenPrefixes(
            getCharacterCardThemePrefix(sourceCharacterCardId),
            getCharacterCardThemePrefix(targetCharacterCardId)
        )
    }

    suspend fun switchToCharacterCardTheme(characterCardId: String) {
        switchToThemeByPrefix(getCharacterCardThemePrefix(characterCardId))
    }

    suspend fun saveCurrentThemeToCharacterCard(characterCardId: String) {
        copyCurrentThemeToCharacterCard(characterCardId)
    }

    suspend fun deleteCharacterCardTheme(characterCardId: String) {
        deleteThemeByPrefix(getCharacterCardThemePrefix(characterCardId))
    }

    suspend fun hasCharacterCardTheme(characterCardId: String): Boolean {
        return hasThemeByPrefix(getCharacterCardThemePrefix(characterCardId))
    }

    suspend fun copyCurrentThemeToCharacterGroup(characterGroupId: String) {
        copyCurrentThemeToPrefix(getCharacterGroupThemePrefix(characterGroupId))
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

    suspend fun switchToCharacterGroupTheme(characterGroupId: String) {
        switchToThemeByPrefix(getCharacterGroupThemePrefix(characterGroupId))
    }

    suspend fun saveCurrentThemeToCharacterGroup(characterGroupId: String) {
        copyCurrentThemeToCharacterGroup(characterGroupId)
    }

    suspend fun deleteCharacterGroupTheme(characterGroupId: String) {
        deleteThemeByPrefix(getCharacterGroupThemePrefix(characterGroupId))
    }

    suspend fun hasCharacterGroupTheme(characterGroupId: String): Boolean {
        return hasThemeByPrefix(getCharacterGroupThemePrefix(characterGroupId))
    }

    suspend fun resolveThemePreferenceSnapshot(
        characterCardId: String? = null,
        characterGroupId: String? = null
    ): ThemePreferenceSnapshot {
        val normalizedGroupId = characterGroupId?.trim()?.takeIf { it.isNotBlank() }
        val normalizedCardId = characterCardId?.trim()?.takeIf { it.isNotBlank() }

        val sourcePrefix = when {
            normalizedGroupId != null && hasCharacterGroupTheme(normalizedGroupId) ->
                "character_group" to getCharacterGroupThemePrefix(normalizedGroupId)

            normalizedCardId != null && hasCharacterCardTheme(normalizedCardId) ->
                "character_card" to getCharacterCardThemePrefix(normalizedCardId)

            else -> "global" to null
        }

        val source = sourcePrefix.first
        val prefix = sourcePrefix.second
        val sourceId = when (source) {
            "character_group" -> normalizedGroupId
            "character_card" -> normalizedCardId
            else -> null
        }
        val preferences = context.userPreferencesDataStore.data.first()

        fun stringValue(key: Preferences.Key<String>, defaultValue: String? = null): String? {
            val resolvedKey = if (prefix != null) {
                stringPreferencesKey("${prefix}${key.name}")
            } else {
                key
            }
            return preferences[resolvedKey] ?: defaultValue
        }

        fun booleanValue(key: Preferences.Key<Boolean>, defaultValue: Boolean): Boolean {
            val resolvedKey = if (prefix != null) {
                booleanPreferencesKey("${prefix}${key.name}")
            } else {
                key
            }
            return preferences[resolvedKey] ?: defaultValue
        }

        fun intValue(key: Preferences.Key<Int>): Int? {
            val resolvedKey = if (prefix != null) {
                intPreferencesKey("${prefix}${key.name}")
            } else {
                key
            }
            return preferences[resolvedKey]
        }

        fun floatValue(key: Preferences.Key<Float>, defaultValue: Float): Float {
            val resolvedKey = if (prefix != null) {
                floatPreferencesKey("${prefix}${key.name}")
            } else {
                key
            }
            return preferences[resolvedKey] ?: defaultValue
        }

        return ThemePreferenceSnapshot(
            source = source,
            sourceId = sourceId,
            themeMode = stringValue(THEME_MODE, THEME_MODE_LIGHT) ?: THEME_MODE_LIGHT,
            useSystemTheme = booleanValue(USE_SYSTEM_THEME, true),
            useCustomColors = booleanValue(USE_CUSTOM_COLORS, false),
            customPrimaryColor = intValue(CUSTOM_PRIMARY_COLOR),
            customSecondaryColor = intValue(CUSTOM_SECONDARY_COLOR),
            onColorMode = stringValue(KEY_ON_COLOR_MODE, ON_COLOR_MODE_AUTO) ?: ON_COLOR_MODE_AUTO,
            useBackgroundImage = booleanValue(USE_BACKGROUND_IMAGE, false),
            backgroundImageUri = stringValue(BACKGROUND_IMAGE_URI),
            backgroundMediaType = stringValue(BACKGROUND_MEDIA_TYPE, MEDIA_TYPE_IMAGE)
                ?: MEDIA_TYPE_IMAGE,
            backgroundImageOpacity = floatValue(BACKGROUND_IMAGE_OPACITY, 0.3f),
            chatHeaderTransparent = booleanValue(CHAT_HEADER_TRANSPARENT, false),
            chatHeaderOverlayMode = booleanValue(CHAT_HEADER_OVERLAY_MODE, false),
            chatInputTransparent = booleanValue(CHAT_INPUT_TRANSPARENT, false),
            chatInputFloating = booleanValue(CHAT_INPUT_FLOATING, false),
            chatInputLiquidGlass = booleanValue(CHAT_INPUT_LIQUID_GLASS, false),
            chatInputWaterGlass = booleanValue(CHAT_INPUT_WATER_GLASS, false),
            chatStyle = stringValue(CHAT_STYLE, CHAT_STYLE_CURSOR) ?: CHAT_STYLE_CURSOR,
            inputStyle = stringValue(INPUT_STYLE, INPUT_STYLE_AGENT) ?: INPUT_STYLE_AGENT,
            bubbleShowAvatar = booleanValue(BUBBLE_SHOW_AVATAR, true),
            bubbleWideLayoutEnabled = booleanValue(BUBBLE_WIDE_LAYOUT_ENABLED, false),
            cursorUserBubbleFollowTheme = booleanValue(CURSOR_USER_BUBBLE_FOLLOW_THEME, true),
            cursorUserBubbleColor = intValue(CURSOR_USER_BUBBLE_COLOR),
            bubbleUserBubbleColor = intValue(BUBBLE_USER_BUBBLE_COLOR),
            bubbleAiBubbleColor = intValue(BUBBLE_AI_BUBBLE_COLOR),
            bubbleUserTextColor = intValue(BUBBLE_USER_TEXT_COLOR),
            bubbleAiTextColor = intValue(BUBBLE_AI_TEXT_COLOR),
            bubbleUserUseImage = booleanValue(BUBBLE_USER_USE_IMAGE, false),
            bubbleAiUseImage = booleanValue(BUBBLE_AI_USE_IMAGE, false),
            bubbleUserImageUri = stringValue(BUBBLE_USER_IMAGE_URI),
            bubbleAiImageUri = stringValue(BUBBLE_AI_IMAGE_URI),
            bubbleImageRenderMode =
                stringValue(
                    BUBBLE_IMAGE_RENDER_MODE,
                    BUBBLE_IMAGE_RENDER_MODE_TILED_NINE_SLICE
                ) ?: BUBBLE_IMAGE_RENDER_MODE_TILED_NINE_SLICE,
            bubbleUserRoundedCornersEnabled =
                booleanValue(BUBBLE_USER_ROUNDED_CORNERS_ENABLED, true),
            bubbleAiRoundedCornersEnabled =
                booleanValue(BUBBLE_AI_ROUNDED_CORNERS_ENABLED, true),
            bubbleUserContentPaddingLeft = floatValue(BUBBLE_USER_CONTENT_PADDING_LEFT, 12f),
            bubbleUserContentPaddingRight = floatValue(BUBBLE_USER_CONTENT_PADDING_RIGHT, 12f),
            bubbleAiContentPaddingLeft = floatValue(BUBBLE_AI_CONTENT_PADDING_LEFT, 12f),
            bubbleAiContentPaddingRight = floatValue(BUBBLE_AI_CONTENT_PADDING_RIGHT, 12f),
            customUserAvatarUri = stringValue(KEY_CUSTOM_USER_AVATAR_URI),
            customAiAvatarUri = stringValue(KEY_CUSTOM_AI_AVATAR_URI),
            avatarShape = stringValue(KEY_AVATAR_SHAPE, AVATAR_SHAPE_CIRCLE) ?: AVATAR_SHAPE_CIRCLE,
            avatarCornerRadius = floatValue(KEY_AVATAR_CORNER_RADIUS, 8f),
            fontType = stringValue(FONT_TYPE, FONT_TYPE_SYSTEM) ?: FONT_TYPE_SYSTEM,
            systemFontName = stringValue(SYSTEM_FONT_NAME),
            customFontPath = stringValue(CUSTOM_FONT_PATH),
            fontScale = floatValue(FONT_SCALE, 1.0f),
            showThinkingProcess = booleanValue(KEY_SHOW_THINKING_PROCESS, true),
            showStatusTags = booleanValue(KEY_SHOW_STATUS_TAGS, true),
            showModelProvider = booleanValue(KEY_SHOW_MODEL_PROVIDER, false),
            showModelName = booleanValue(KEY_SHOW_MODEL_NAME, false),
            showRoleName = booleanValue(KEY_SHOW_ROLE_NAME, true),
            showUserName = booleanValue(KEY_SHOW_USER_NAME, true),
            showMessageTokenStats = booleanValue(KEY_SHOW_MESSAGE_TOKEN_STATS, false),
            showMessageTimingStats = booleanValue(KEY_SHOW_MESSAGE_TIMING_STATS, false),
            showMessageTimestamp = booleanValue(KEY_SHOW_MESSAGE_TIMESTAMP, false),
            showInputProcessingStatus = booleanValue(KEY_SHOW_INPUT_PROCESSING_STATUS, true)
        )
    }
}
