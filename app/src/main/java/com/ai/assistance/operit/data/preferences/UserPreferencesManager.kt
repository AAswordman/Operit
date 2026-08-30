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
        private val THEME_MODE = stringPreferencesKey(NativeThemePreferenceSchemaV1.themeMode.name)
        private val USE_SYSTEM_THEME =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.useSystemTheme.name)
        private val CUSTOM_PRIMARY_COLOR =
            intPreferencesKey(NativeThemePreferenceSchemaV1.customPrimaryColor.name)
        private val CUSTOM_SECONDARY_COLOR =
            intPreferencesKey(NativeThemePreferenceSchemaV1.customSecondaryColor.name)
        private val USE_CUSTOM_COLORS =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.useCustomColors.name)
        private val CHARACTER_THEME_DEFAULT_MIGRATION_COMPLETED =
            booleanPreferencesKey("character_theme_default_migration_completed")
        private val USE_BACKGROUND_IMAGE =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.useBackgroundImage.name)
        private val BACKGROUND_IMAGE_URI =
            stringPreferencesKey(NativeThemePreferenceSchemaV1.backgroundImageUri.name)
        private val BACKGROUND_IMAGE_OPACITY =
            floatPreferencesKey(NativeThemePreferenceSchemaV1.backgroundImageOpacity.name)

        // 背景媒体类型和视频设置
        private val BACKGROUND_MEDIA_TYPE =
            stringPreferencesKey(NativeThemePreferenceSchemaV1.backgroundMediaType.name)
        private val VIDEO_BACKGROUND_MUTED =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.videoBackgroundMuted.name)
        private val VIDEO_BACKGROUND_LOOP =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.videoBackgroundLoop.name)

        // 工具栏透明度设置
        private val TOOLBAR_TRANSPARENT =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.toolbarTransparent.name)

        // 侧滑菜单玻璃效果设置
        private val NAVIGATION_DRAWER_WATER_GLASS =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.navigationDrawerWaterGlass.name)
        private val NAVIGATION_DRAWER_BUTTON_LIQUID_GLASS =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.navigationDrawerButtonLiquidGlass.name)

        // 侧滑菜单背景色设置
        private val USE_CUSTOM_NAVIGATION_DRAWER_BACKGROUND_COLOR =
            booleanPreferencesKey(
                NativeThemePreferenceSchemaV1.useCustomNavigationDrawerBackgroundColor.name
            )
        private val CUSTOM_NAVIGATION_DRAWER_BACKGROUND_COLOR =
            intPreferencesKey(NativeThemePreferenceSchemaV1.customNavigationDrawerBackgroundColor.name)

        // 侧滑菜单强调色设置（品牌标识/小标题/网络状态/分隔线共用）
        private val USE_CUSTOM_NAVIGATION_DRAWER_ACCENT_COLOR =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.useCustomNavigationDrawerAccentColor.name)
        private val CUSTOM_NAVIGATION_DRAWER_ACCENT_COLOR =
            intPreferencesKey(NativeThemePreferenceSchemaV1.customNavigationDrawerAccentColor.name)
        
        // AppBar 自定义颜色设置
        private val USE_CUSTOM_APP_BAR_COLOR =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.useCustomAppBarColor.name)
        private val CUSTOM_APP_BAR_COLOR =
            intPreferencesKey(NativeThemePreferenceSchemaV1.customAppBarColor.name)

        // 状态栏颜色设置
        private val USE_CUSTOM_STATUS_BAR_COLOR =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.useCustomStatusBarColor.name)
        private val CUSTOM_STATUS_BAR_COLOR =
            intPreferencesKey(NativeThemePreferenceSchemaV1.customStatusBarColor.name)
        private val STATUS_BAR_TRANSPARENT =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.statusBarTransparent.name)
        private val STATUS_BAR_HIDDEN =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.statusBarHidden.name)
        private val CHAT_HEADER_TRANSPARENT =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.chatHeaderTransparent.name)
        private val CHAT_INPUT_TRANSPARENT =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.chatInputTransparent.name)
        private val CHAT_INPUT_FLOATING =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.chatInputFloating.name)
        private val CHAT_INPUT_LIQUID_GLASS =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.chatInputLiquidGlass.name)
        private val CHAT_INPUT_WATER_GLASS =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.chatInputWaterGlass.name)

        // AppBar 内容颜色设置
        private val FORCE_APP_BAR_CONTENT_COLOR_ENABLED =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.forceAppBarContentColorEnabled.name)
        private val APP_BAR_CONTENT_COLOR_MODE =
            stringPreferencesKey(NativeThemePreferenceSchemaV1.appBarContentColorMode.name)

        // ChatHeader 图标颜色设置
        private val CHAT_HEADER_HISTORY_ICON_COLOR =
            intPreferencesKey(NativeThemePreferenceSchemaV1.chatHeaderHistoryIconColor.name)
        private val CHAT_HEADER_PIP_ICON_COLOR =
            intPreferencesKey(NativeThemePreferenceSchemaV1.chatHeaderPipIconColor.name)
        private val CHAT_HEADER_OVERLAY_MODE =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.chatHeaderOverlayMode.name)

        // 背景模糊设置
        private val USE_BACKGROUND_BLUR =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.useBackgroundBlur.name)
        private val BACKGROUND_BLUR_RADIUS =
            floatPreferencesKey(NativeThemePreferenceSchemaV1.backgroundBlurRadius.name)

        // 字体设置
        private val USE_CUSTOM_FONT =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.useCustomFont.name)
        private val FONT_TYPE = stringPreferencesKey(NativeThemePreferenceSchemaV1.fontType.name)
        private val SYSTEM_FONT_NAME =
            stringPreferencesKey(NativeThemePreferenceSchemaV1.systemFontName.name)
        private val CUSTOM_FONT_PATH =
            stringPreferencesKey(NativeThemePreferenceSchemaV1.customFontPath.name)
        private val FONT_SCALE = floatPreferencesKey(NativeThemePreferenceSchemaV1.fontScale.name)

        // Chat style preference
        private val CHAT_STYLE = stringPreferencesKey(NativeThemePreferenceSchemaV1.chatStyle.name)
        private val INPUT_STYLE = stringPreferencesKey(NativeThemePreferenceSchemaV1.inputStyle.name)

        private val BUBBLE_SHOW_AVATAR =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.bubbleShowAvatar.name)
        private val BUBBLE_WIDE_LAYOUT_ENABLED =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.bubbleWideLayoutEnabled.name)
        private val CURSOR_USER_BUBBLE_FOLLOW_THEME =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.cursorUserBubbleFollowTheme.name)
        private val CURSOR_USER_BUBBLE_LIQUID_GLASS =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.cursorUserBubbleLiquidGlass.name)
        private val CURSOR_USER_BUBBLE_WATER_GLASS =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.cursorUserBubbleWaterGlass.name)
        private val CURSOR_USER_BUBBLE_COLOR =
            intPreferencesKey(NativeThemePreferenceSchemaV1.cursorUserBubbleColor.name)
        private val BUBBLE_USER_BUBBLE_LIQUID_GLASS =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.bubbleUserBubbleLiquidGlass.name)
        private val BUBBLE_USER_BUBBLE_WATER_GLASS =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.bubbleUserBubbleWaterGlass.name)
        private val BUBBLE_AI_BUBBLE_LIQUID_GLASS =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.bubbleAiBubbleLiquidGlass.name)
        private val BUBBLE_AI_BUBBLE_WATER_GLASS =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.bubbleAiBubbleWaterGlass.name)
        private val BUBBLE_USER_BUBBLE_COLOR =
            intPreferencesKey(NativeThemePreferenceSchemaV1.bubbleUserBubbleColor.name)
        private val BUBBLE_AI_BUBBLE_COLOR =
            intPreferencesKey(NativeThemePreferenceSchemaV1.bubbleAiBubbleColor.name)
        private val BUBBLE_USER_TEXT_COLOR =
            intPreferencesKey(NativeThemePreferenceSchemaV1.bubbleUserTextColor.name)
        private val BUBBLE_AI_TEXT_COLOR =
            intPreferencesKey(NativeThemePreferenceSchemaV1.bubbleAiTextColor.name)
        private val BUBBLE_USER_USE_CUSTOM_FONT =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.bubbleUserUseCustomFont.name)
        private val BUBBLE_USER_FONT_TYPE =
            stringPreferencesKey(NativeThemePreferenceSchemaV1.bubbleUserFontType.name)
        private val BUBBLE_USER_SYSTEM_FONT_NAME =
            stringPreferencesKey(NativeThemePreferenceSchemaV1.bubbleUserSystemFontName.name)
        private val BUBBLE_USER_CUSTOM_FONT_PATH =
            stringPreferencesKey(NativeThemePreferenceSchemaV1.bubbleUserCustomFontPath.name)
        private val BUBBLE_AI_USE_CUSTOM_FONT =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.bubbleAiUseCustomFont.name)
        private val BUBBLE_AI_FONT_TYPE =
            stringPreferencesKey(NativeThemePreferenceSchemaV1.bubbleAiFontType.name)
        private val BUBBLE_AI_SYSTEM_FONT_NAME =
            stringPreferencesKey(NativeThemePreferenceSchemaV1.bubbleAiSystemFontName.name)
        private val BUBBLE_AI_CUSTOM_FONT_PATH =
            stringPreferencesKey(NativeThemePreferenceSchemaV1.bubbleAiCustomFontPath.name)
        private val BUBBLE_USER_USE_IMAGE =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.bubbleUserUseImage.name)
        private val BUBBLE_AI_USE_IMAGE =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.bubbleAiUseImage.name)
        private val BUBBLE_USER_IMAGE_URI =
            stringPreferencesKey(NativeThemePreferenceSchemaV1.bubbleUserImageUri.name)
        private val BUBBLE_AI_IMAGE_URI =
            stringPreferencesKey(NativeThemePreferenceSchemaV1.bubbleAiImageUri.name)
        private val BUBBLE_USER_IMAGE_CROP_LEFT =
            floatPreferencesKey(NativeThemePreferenceSchemaV1.bubbleUserImageCropLeft.name)
        private val BUBBLE_USER_IMAGE_CROP_TOP =
            floatPreferencesKey(NativeThemePreferenceSchemaV1.bubbleUserImageCropTop.name)
        private val BUBBLE_USER_IMAGE_CROP_RIGHT =
            floatPreferencesKey(NativeThemePreferenceSchemaV1.bubbleUserImageCropRight.name)
        private val BUBBLE_USER_IMAGE_CROP_BOTTOM =
            floatPreferencesKey(NativeThemePreferenceSchemaV1.bubbleUserImageCropBottom.name)
        private val BUBBLE_USER_IMAGE_REPEAT_START =
            floatPreferencesKey(NativeThemePreferenceSchemaV1.bubbleUserImageRepeatStart.name)
        private val BUBBLE_USER_IMAGE_REPEAT_END =
            floatPreferencesKey(NativeThemePreferenceSchemaV1.bubbleUserImageRepeatEnd.name)
        private val BUBBLE_USER_IMAGE_REPEAT_Y_START =
            floatPreferencesKey(NativeThemePreferenceSchemaV1.bubbleUserImageRepeatYStart.name)
        private val BUBBLE_USER_IMAGE_REPEAT_Y_END =
            floatPreferencesKey(NativeThemePreferenceSchemaV1.bubbleUserImageRepeatYEnd.name)
        private val BUBBLE_USER_IMAGE_SCALE =
            floatPreferencesKey(NativeThemePreferenceSchemaV1.bubbleUserImageScale.name)
        private val BUBBLE_AI_IMAGE_CROP_LEFT =
            floatPreferencesKey(NativeThemePreferenceSchemaV1.bubbleAiImageCropLeft.name)
        private val BUBBLE_AI_IMAGE_CROP_TOP =
            floatPreferencesKey(NativeThemePreferenceSchemaV1.bubbleAiImageCropTop.name)
        private val BUBBLE_AI_IMAGE_CROP_RIGHT =
            floatPreferencesKey(NativeThemePreferenceSchemaV1.bubbleAiImageCropRight.name)
        private val BUBBLE_AI_IMAGE_CROP_BOTTOM =
            floatPreferencesKey(NativeThemePreferenceSchemaV1.bubbleAiImageCropBottom.name)
        private val BUBBLE_AI_IMAGE_REPEAT_START =
            floatPreferencesKey(NativeThemePreferenceSchemaV1.bubbleAiImageRepeatStart.name)
        private val BUBBLE_AI_IMAGE_REPEAT_END =
            floatPreferencesKey(NativeThemePreferenceSchemaV1.bubbleAiImageRepeatEnd.name)
        private val BUBBLE_AI_IMAGE_REPEAT_Y_START =
            floatPreferencesKey(NativeThemePreferenceSchemaV1.bubbleAiImageRepeatYStart.name)
        private val BUBBLE_AI_IMAGE_REPEAT_Y_END =
            floatPreferencesKey(NativeThemePreferenceSchemaV1.bubbleAiImageRepeatYEnd.name)
        private val BUBBLE_AI_IMAGE_SCALE =
            floatPreferencesKey(NativeThemePreferenceSchemaV1.bubbleAiImageScale.name)
        private val BUBBLE_IMAGE_RENDER_MODE =
            stringPreferencesKey(NativeThemePreferenceSchemaV1.bubbleImageRenderMode.name)
        private val BUBBLE_USER_ROUNDED_CORNERS_ENABLED =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.bubbleUserRoundedCornersEnabled.name)
        private val BUBBLE_AI_ROUNDED_CORNERS_ENABLED =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.bubbleAiRoundedCornersEnabled.name)
        private val BUBBLE_USER_CONTENT_PADDING_LEFT =
            floatPreferencesKey(NativeThemePreferenceSchemaV1.bubbleUserContentPaddingLeft.name)
        private val BUBBLE_USER_CONTENT_PADDING_RIGHT =
            floatPreferencesKey(NativeThemePreferenceSchemaV1.bubbleUserContentPaddingRight.name)
        private val BUBBLE_AI_CONTENT_PADDING_LEFT =
            floatPreferencesKey(NativeThemePreferenceSchemaV1.bubbleAiContentPaddingLeft.name)
        private val BUBBLE_AI_CONTENT_PADDING_RIGHT =
            floatPreferencesKey(NativeThemePreferenceSchemaV1.bubbleAiContentPaddingRight.name)

        // 默认配置文件ID
        private const val DEFAULT_PROFILE_ID = "default"

        // 主题模式常量
        const val THEME_MODE_LIGHT = NativeThemePreferenceOptionsV1.THEME_MODE_LIGHT
        const val THEME_MODE_DARK = NativeThemePreferenceOptionsV1.THEME_MODE_DARK

        // AppBar 内容颜色模式常量
        const val APP_BAR_CONTENT_COLOR_MODE_LIGHT =
            NativeThemePreferenceOptionsV1.APP_BAR_CONTENT_COLOR_MODE_LIGHT
        const val APP_BAR_CONTENT_COLOR_MODE_DARK =
            NativeThemePreferenceOptionsV1.APP_BAR_CONTENT_COLOR_MODE_DARK

        // 背景媒体类型常量
        const val MEDIA_TYPE_IMAGE = NativeThemePreferenceOptionsV1.MEDIA_TYPE_IMAGE
        const val MEDIA_TYPE_VIDEO = NativeThemePreferenceOptionsV1.MEDIA_TYPE_VIDEO
        
        // 默认语言
        const val DEFAULT_LANGUAGE = LanguageCodes.AUTO

        // Sidebar software identity (drawer header brand text)
        const val SOFTWARE_IDENTITY_OPERIT = "operit_ai"
        const val SOFTWARE_IDENTITY_LINGSHU = "lingshu_ai"

        const val CHAT_STYLE_CURSOR = NativeThemePreferenceOptionsV1.CHAT_STYLE_CURSOR
        const val CHAT_STYLE_BUBBLE = NativeThemePreferenceOptionsV1.CHAT_STYLE_BUBBLE

        const val INPUT_STYLE_CLASSIC = NativeThemePreferenceOptionsV1.INPUT_STYLE_CLASSIC
        const val INPUT_STYLE_AGENT = NativeThemePreferenceOptionsV1.INPUT_STYLE_AGENT
        const val BUBBLE_IMAGE_RENDER_MODE_TILED_NINE_SLICE =
            NativeThemePreferenceOptionsV1.BUBBLE_IMAGE_RENDER_MODE_TILED_NINE_SLICE
        const val BUBBLE_IMAGE_RENDER_MODE_NINE_PATCH =
            NativeThemePreferenceOptionsV1.BUBBLE_IMAGE_RENDER_MODE_NINE_PATCH

        private val KEY_SHOW_THINKING_PROCESS =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.showThinkingProcess.name)
        private val KEY_SHOW_STATUS_TAGS =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.showStatusTags.name)
        private val KEY_SHOW_MODEL_PROVIDER =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.showModelProvider.name)
        private val KEY_SHOW_MODEL_NAME =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.showModelName.name)
        private val KEY_SHOW_ROLE_NAME =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.showRoleName.name)
        private val KEY_SHOW_USER_NAME =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.showUserName.name)
        private val KEY_SHOW_MESSAGE_TOKEN_STATS =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.showMessageTokenStats.name)
        private val KEY_SHOW_MESSAGE_TIMING_STATS =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.showMessageTimingStats.name)
        private val KEY_SHOW_MESSAGE_TIMESTAMP =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.showMessageTimestamp.name)
        private val KEY_CUSTOM_USER_AVATAR_URI =
            stringPreferencesKey(NativeThemePreferenceSchemaV1.customUserAvatarUri.name)
        private val KEY_CUSTOM_AI_AVATAR_URI =
            stringPreferencesKey(NativeThemePreferenceSchemaV1.customAiAvatarUri.name)
        private val KEY_AVATAR_SHAPE =
            stringPreferencesKey(NativeThemePreferenceSchemaV1.avatarShape.name)
        private val KEY_AVATAR_CORNER_RADIUS =
            floatPreferencesKey(NativeThemePreferenceSchemaV1.avatarCornerRadius.name)
        private val KEY_ON_COLOR_MODE =
            stringPreferencesKey(NativeThemePreferenceSchemaV1.onColorMode.name)
        private val KEY_CUSTOM_CHAT_TITLE =
            stringPreferencesKey(NativeThemePreferenceSchemaV1.customChatTitle.name)
        private val KEY_SHOW_INPUT_PROCESSING_STATUS =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.showInputProcessingStatus.name)
        private val KEY_SHOW_CHAT_FLOATING_DOTS_ANIMATION =
            booleanPreferencesKey(NativeThemePreferenceSchemaV1.showChatFloatingDotsAnimation.name)
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


        const val AVATAR_SHAPE_CIRCLE = NativeThemePreferenceOptionsV1.AVATAR_SHAPE_CIRCLE
        const val AVATAR_SHAPE_SQUARE = NativeThemePreferenceOptionsV1.AVATAR_SHAPE_SQUARE

        const val ON_COLOR_MODE_AUTO = NativeThemePreferenceOptionsV1.ON_COLOR_MODE_AUTO
        const val ON_COLOR_MODE_LIGHT = NativeThemePreferenceOptionsV1.ON_COLOR_MODE_LIGHT
        const val ON_COLOR_MODE_DARK = NativeThemePreferenceOptionsV1.ON_COLOR_MODE_DARK

        // 字体类型常量
        const val FONT_TYPE_SYSTEM = NativeThemePreferenceOptionsV1.FONT_TYPE_SYSTEM
        const val FONT_TYPE_FILE = NativeThemePreferenceOptionsV1.FONT_TYPE_FILE
        
        // 系统字体名称常量
        const val SYSTEM_FONT_DEFAULT = NativeThemePreferenceOptionsV1.SYSTEM_FONT_DEFAULT
        const val SYSTEM_FONT_SERIF = NativeThemePreferenceOptionsV1.SYSTEM_FONT_SERIF
        const val SYSTEM_FONT_SANS_SERIF = NativeThemePreferenceOptionsV1.SYSTEM_FONT_SANS_SERIF
        const val SYSTEM_FONT_MONOSPACE = NativeThemePreferenceOptionsV1.SYSTEM_FONT_MONOSPACE
        const val SYSTEM_FONT_CURSIVE = NativeThemePreferenceOptionsV1.SYSTEM_FONT_CURSIVE

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
        return NativeThemePreferenceSchemaV1.stringFields.map { field ->
            stringPreferencesKey(field.name)
        }
    }

    private fun getVisualStringThemeKeys(): List<Preferences.Key<String>> {
        return NativeThemePreferenceSchemaV1.visualStringFields.map { field ->
            stringPreferencesKey(field.name)
        }
    }

    private fun getTargetMetadataStringThemeKeys(): List<Preferences.Key<String>> {
        return NativeThemePreferenceSchemaV1.targetMetadataStringFields.map { field ->
            stringPreferencesKey(field.name)
        }
    }

    private fun getAllBooleanThemeKeys(): List<Preferences.Key<Boolean>> {
        return NativeThemePreferenceSchemaV1.booleanFields.map { field ->
            booleanPreferencesKey(field.name)
        }
    }

    private fun getAllIntThemeKeys(): List<Preferences.Key<Int>> {
        return NativeThemePreferenceSchemaV1.intFields.map { field ->
            intPreferencesKey(field.name)
        }
    }

    private fun getAllFloatThemeKeys(): List<Preferences.Key<Float>> {
        return NativeThemePreferenceSchemaV1.floatFields.map { field ->
            floatPreferencesKey(field.name)
        }
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

    private fun clearVisualThemeValues(preferences: MutablePreferences, prefix: String) {
        getVisualStringThemeKeys().forEach { key ->
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

        NativeThemePreferenceSchemaV1.floatFields.forEach { field ->
            val releasedSource = field.releasedSource ?: return@forEach
            val verticalSourceKey = floatPreferencesKey("${prefix}${field.name}")
            if (!preferences.contains(verticalSourceKey)) {
                val releasedSourceKey = floatPreferencesKey("${prefix}${releasedSource.name}")
                preferences[releasedSourceKey]?.let { floats[field.name] = it }
            }
        }

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
        getVisualStringThemeKeys().forEach { key ->
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
        getTargetMetadataStringThemeKeys().forEach { key ->
            val targetKey = stringPreferencesKey("${prefix}${key.name}")
            val value = values.string(key.name)
            if (value == null) {
                preferences.remove(targetKey)
            } else {
                preferences[targetKey] = value
            }
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
        return getVisualStringThemeKeys()
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
        val targetMetadataSuffixes =
            NativeThemePreferenceSchemaV1.targetMetadataStringFields.map { field -> "_${field.name}" }
        return preferences.asMap().keys.any { key ->
            val isScopedThemeKey =
                key.name.startsWith("character_card_theme_") ||
                    key.name.startsWith("character_group_theme_")
            isScopedThemeKey && targetMetadataSuffixes.none(key.name::endsWith)
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
