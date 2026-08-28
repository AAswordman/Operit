package com.ai.assistance.operit.data.preferences

internal enum class NativeThemePreferenceSection {
    BASIC,
    BACKGROUND,
    CHAT,
    INPUT,
    INTERFACE,
    TARGET_METADATA,
}

internal enum class NativeThemePreferenceStorageRole {
    VISUAL,
    TARGET_METADATA,
}

internal sealed class NativeThemePreferenceField<T : Any>(
    val name: String,
    val defaultValue: T?,
    val section: NativeThemePreferenceSection,
    val storageRole: NativeThemePreferenceStorageRole,
)

internal class NativeThemeStringField(
    name: String,
    defaultValue: String?,
    section: NativeThemePreferenceSection,
    storageRole: NativeThemePreferenceStorageRole = NativeThemePreferenceStorageRole.VISUAL,
) : NativeThemePreferenceField<String>(name, defaultValue, section, storageRole)

internal class NativeThemeBooleanField(
    name: String,
    defaultValue: Boolean,
    section: NativeThemePreferenceSection,
) : NativeThemePreferenceField<Boolean>(
    name,
    defaultValue,
    section,
    NativeThemePreferenceStorageRole.VISUAL,
)

internal class NativeThemeIntField(
    name: String,
    section: NativeThemePreferenceSection,
) : NativeThemePreferenceField<Int>(
    name,
    null,
    section,
    NativeThemePreferenceStorageRole.VISUAL,
)

internal class NativeThemeFloatField(
    name: String,
    defaultValue: Float,
    section: NativeThemePreferenceSection,
    val releasedSource: NativeThemeFloatField? = null,
) : NativeThemePreferenceField<Float>(
    name,
    defaultValue,
    section,
    NativeThemePreferenceStorageRole.VISUAL,
)

/**
 * Frozen contract for the released role-scoped native theme preferences.
 *
 * Names, types, defaults, metadata classification, and released compatibility relationships in
 * this object are persisted behavior. New renderer contracts must adapt these fields rather than
 * reinterpret their storage representation.
 */
internal object NativeThemePreferenceSchemaV1 {
    const val ID = "operit.native_v1.preferences"
    const val VERSION = 1

    val themeMode = string(
        "theme_mode",
        UserPreferencesManager.THEME_MODE_LIGHT,
        NativeThemePreferenceSection.BASIC,
    )
    val backgroundImageUri = string("background_image_uri", section = NativeThemePreferenceSection.BACKGROUND)
    val backgroundMediaType = string(
        "background_media_type",
        UserPreferencesManager.MEDIA_TYPE_IMAGE,
        NativeThemePreferenceSection.BACKGROUND,
    )
    val appBarContentColorMode = string(
        "app_bar_content_color_mode",
        UserPreferencesManager.APP_BAR_CONTENT_COLOR_MODE_LIGHT,
        NativeThemePreferenceSection.INTERFACE,
    )
    val chatStyle = string(
        "chat_style",
        UserPreferencesManager.CHAT_STYLE_CURSOR,
        NativeThemePreferenceSection.CHAT,
    )
    val customUserAvatarUri = string("custom_user_avatar_uri", section = NativeThemePreferenceSection.CHAT)
    val customAiAvatarUri = string(
        "custom_ai_avatar_uri",
        section = NativeThemePreferenceSection.TARGET_METADATA,
        storageRole = NativeThemePreferenceStorageRole.TARGET_METADATA,
    )
    val avatarShape = string(
        "avatar_shape",
        UserPreferencesManager.AVATAR_SHAPE_CIRCLE,
        NativeThemePreferenceSection.CHAT,
    )
    val onColorMode = string(
        "on_color_mode",
        UserPreferencesManager.ON_COLOR_MODE_AUTO,
        NativeThemePreferenceSection.BASIC,
    )
    val customChatTitle = string(
        "custom_chat_title",
        section = NativeThemePreferenceSection.TARGET_METADATA,
        storageRole = NativeThemePreferenceStorageRole.TARGET_METADATA,
    )
    val inputStyle = string(
        "input_style",
        UserPreferencesManager.INPUT_STYLE_AGENT,
        NativeThemePreferenceSection.INPUT,
    )
    val fontType = string(
        "font_type",
        UserPreferencesManager.FONT_TYPE_SYSTEM,
        NativeThemePreferenceSection.BASIC,
    )
    val systemFontName = string(
        "system_font_name",
        UserPreferencesManager.SYSTEM_FONT_DEFAULT,
        NativeThemePreferenceSection.BASIC,
    )
    val customFontPath = string("custom_font_path", section = NativeThemePreferenceSection.BASIC)
    val bubbleUserFontType = string(
        "bubble_user_font_type",
        UserPreferencesManager.FONT_TYPE_SYSTEM,
        NativeThemePreferenceSection.CHAT,
    )
    val bubbleUserSystemFontName = string(
        "bubble_user_system_font_name",
        UserPreferencesManager.SYSTEM_FONT_DEFAULT,
        NativeThemePreferenceSection.CHAT,
    )
    val bubbleUserCustomFontPath = string(
        "bubble_user_custom_font_path",
        section = NativeThemePreferenceSection.CHAT,
    )
    val bubbleAiFontType = string(
        "bubble_ai_font_type",
        UserPreferencesManager.FONT_TYPE_SYSTEM,
        NativeThemePreferenceSection.CHAT,
    )
    val bubbleAiSystemFontName = string(
        "bubble_ai_system_font_name",
        UserPreferencesManager.SYSTEM_FONT_DEFAULT,
        NativeThemePreferenceSection.CHAT,
    )
    val bubbleAiCustomFontPath = string(
        "bubble_ai_custom_font_path",
        section = NativeThemePreferenceSection.CHAT,
    )
    val bubbleUserImageUri = string("bubble_user_image_uri", section = NativeThemePreferenceSection.CHAT)
    val bubbleAiImageUri = string("bubble_ai_image_uri", section = NativeThemePreferenceSection.CHAT)
    val bubbleImageRenderMode = string(
        "bubble_image_render_mode",
        UserPreferencesManager.BUBBLE_IMAGE_RENDER_MODE_TILED_NINE_SLICE,
        NativeThemePreferenceSection.CHAT,
    )

    val useSystemTheme = boolean("use_system_theme", true, NativeThemePreferenceSection.BASIC)
    val useCustomColors = boolean("use_custom_colors", false, NativeThemePreferenceSection.BASIC)
    val useBackgroundImage = boolean("use_background_image", false, NativeThemePreferenceSection.BACKGROUND)
    val videoBackgroundMuted = boolean("video_background_muted", true, NativeThemePreferenceSection.BACKGROUND)
    val videoBackgroundLoop = boolean("video_background_loop", true, NativeThemePreferenceSection.BACKGROUND)
    val toolbarTransparent = boolean("toolbar_transparent", false, NativeThemePreferenceSection.INTERFACE)
    val navigationDrawerWaterGlass = boolean(
        "navigation_drawer_water_glass",
        false,
        NativeThemePreferenceSection.INTERFACE,
    )
    val navigationDrawerButtonLiquidGlass = boolean(
        "navigation_drawer_button_liquid_glass",
        false,
        NativeThemePreferenceSection.INTERFACE,
    )
    val useCustomNavigationDrawerBackgroundColor = boolean(
        "use_custom_navigation_drawer_background_color",
        false,
        NativeThemePreferenceSection.INTERFACE,
    )
    val useCustomNavigationDrawerAccentColor = boolean(
        "use_custom_navigation_drawer_accent_color",
        false,
        NativeThemePreferenceSection.INTERFACE,
    )
    val useCustomAppBarColor = boolean(
        "use_custom_app_bar_color",
        false,
        NativeThemePreferenceSection.INTERFACE,
    )
    val useCustomStatusBarColor = boolean(
        "use_custom_status_bar_color",
        false,
        NativeThemePreferenceSection.INTERFACE,
    )
    val statusBarTransparent = boolean("status_bar_transparent", false, NativeThemePreferenceSection.INTERFACE)
    val statusBarHidden = boolean("status_bar_hidden", false, NativeThemePreferenceSection.INTERFACE)
    val chatHeaderTransparent = boolean(
        "chat_header_transparent",
        false,
        NativeThemePreferenceSection.INTERFACE,
    )
    val chatInputTransparent = boolean("chat_input_transparent", false, NativeThemePreferenceSection.INPUT)
    val chatInputFloating = boolean("chat_input_floating", false, NativeThemePreferenceSection.INPUT)
    val chatInputLiquidGlass = boolean("chat_input_liquid_glass", false, NativeThemePreferenceSection.INPUT)
    val chatInputWaterGlass = boolean("chat_input_water_glass", false, NativeThemePreferenceSection.INPUT)
    val forceAppBarContentColorEnabled = boolean(
        "force_app_bar_content_color_enabled",
        false,
        NativeThemePreferenceSection.INTERFACE,
    )
    val chatHeaderOverlayMode = boolean(
        "chat_header_overlay_mode",
        false,
        NativeThemePreferenceSection.INTERFACE,
    )
    val useBackgroundBlur = boolean("use_background_blur", false, NativeThemePreferenceSection.BACKGROUND)
    val bubbleShowAvatar = boolean("bubble_show_avatar", true, NativeThemePreferenceSection.CHAT)
    val bubbleWideLayoutEnabled = boolean(
        "bubble_wide_layout_enabled",
        false,
        NativeThemePreferenceSection.CHAT,
    )
    val cursorUserBubbleFollowTheme = boolean(
        "cursor_user_bubble_follow_theme",
        true,
        NativeThemePreferenceSection.CHAT,
    )
    val cursorUserBubbleLiquidGlass = boolean(
        "cursor_user_bubble_liquid_glass",
        false,
        NativeThemePreferenceSection.CHAT,
    )
    val cursorUserBubbleWaterGlass = boolean(
        "cursor_user_bubble_water_glass",
        false,
        NativeThemePreferenceSection.CHAT,
    )
    val bubbleUserBubbleLiquidGlass = boolean(
        "bubble_user_bubble_liquid_glass",
        false,
        NativeThemePreferenceSection.CHAT,
    )
    val bubbleUserBubbleWaterGlass = boolean(
        "bubble_user_bubble_water_glass",
        false,
        NativeThemePreferenceSection.CHAT,
    )
    val bubbleAiBubbleLiquidGlass = boolean(
        "bubble_ai_bubble_liquid_glass",
        false,
        NativeThemePreferenceSection.CHAT,
    )
    val bubbleAiBubbleWaterGlass = boolean(
        "bubble_ai_bubble_water_glass",
        false,
        NativeThemePreferenceSection.CHAT,
    )
    val bubbleUserUseImage = boolean("bubble_user_use_image", false, NativeThemePreferenceSection.CHAT)
    val bubbleAiUseImage = boolean("bubble_ai_use_image", false, NativeThemePreferenceSection.CHAT)
    val bubbleUserRoundedCornersEnabled = boolean(
        "bubble_rounded_corners_enabled",
        true,
        NativeThemePreferenceSection.CHAT,
    )
    val bubbleAiRoundedCornersEnabled = boolean(
        "bubble_ai_rounded_corners_enabled",
        true,
        NativeThemePreferenceSection.CHAT,
    )
    val showThinkingProcess = boolean("show_thinking_process", true, NativeThemePreferenceSection.CHAT)
    val showStatusTags = boolean("show_status_tags", true, NativeThemePreferenceSection.CHAT)
    val showInputProcessingStatus = boolean(
        "show_input_processing_status",
        true,
        NativeThemePreferenceSection.CHAT,
    )
    val showChatFloatingDotsAnimation = boolean(
        "show_chat_floating_dots_animation",
        true,
        NativeThemePreferenceSection.CHAT,
    )
    val useCustomFont = boolean("use_custom_font", false, NativeThemePreferenceSection.BASIC)
    val bubbleUserUseCustomFont = boolean(
        "bubble_user_use_custom_font",
        false,
        NativeThemePreferenceSection.CHAT,
    )
    val bubbleAiUseCustomFont = boolean(
        "bubble_ai_use_custom_font",
        false,
        NativeThemePreferenceSection.CHAT,
    )
    val showModelProvider = boolean("show_model_provider", false, NativeThemePreferenceSection.CHAT)
    val showModelName = boolean("show_model_name", false, NativeThemePreferenceSection.CHAT)
    val showRoleName = boolean("show_role_name", true, NativeThemePreferenceSection.CHAT)
    val showUserName = boolean("show_user_name", true, NativeThemePreferenceSection.CHAT)
    val showMessageTokenStats = boolean(
        "show_message_token_stats",
        false,
        NativeThemePreferenceSection.CHAT,
    )
    val showMessageTimingStats = boolean(
        "show_message_timing_stats",
        false,
        NativeThemePreferenceSection.CHAT,
    )
    val showMessageTimestamp = boolean(
        "show_message_timestamp",
        false,
        NativeThemePreferenceSection.CHAT,
    )

    val customPrimaryColor = int("custom_primary_color", NativeThemePreferenceSection.BASIC)
    val customSecondaryColor = int("custom_secondary_color", NativeThemePreferenceSection.BASIC)
    val customNavigationDrawerBackgroundColor = int(
        "custom_navigation_drawer_background_color",
        NativeThemePreferenceSection.INTERFACE,
    )
    val customNavigationDrawerAccentColor = int(
        "custom_navigation_drawer_accent_color",
        NativeThemePreferenceSection.INTERFACE,
    )
    val customAppBarColor = int("custom_app_bar_color", NativeThemePreferenceSection.INTERFACE)
    val customStatusBarColor = int("custom_status_bar_color", NativeThemePreferenceSection.INTERFACE)
    val chatHeaderHistoryIconColor = int(
        "chat_header_history_icon_color",
        NativeThemePreferenceSection.INTERFACE,
    )
    val chatHeaderPipIconColor = int(
        "chat_header_pip_icon_color",
        NativeThemePreferenceSection.INTERFACE,
    )
    val cursorUserBubbleColor = int("cursor_user_bubble_color", NativeThemePreferenceSection.CHAT)
    val bubbleUserBubbleColor = int("bubble_user_bubble_color", NativeThemePreferenceSection.CHAT)
    val bubbleAiBubbleColor = int("bubble_ai_bubble_color", NativeThemePreferenceSection.CHAT)
    val bubbleUserTextColor = int("bubble_user_text_color", NativeThemePreferenceSection.CHAT)
    val bubbleAiTextColor = int("bubble_ai_text_color", NativeThemePreferenceSection.CHAT)

    val backgroundImageOpacity = float("background_image_opacity", 0.3f, NativeThemePreferenceSection.BACKGROUND)
    val backgroundBlurRadius = float("background_blur_radius", 10f, NativeThemePreferenceSection.BACKGROUND)
    val avatarCornerRadius = float("avatar_corner_radius", 8f, NativeThemePreferenceSection.CHAT)
    val fontScale = float("font_scale", 1f, NativeThemePreferenceSection.BASIC)
    val bubbleUserImageCropLeft = float("bubble_user_image_crop_left", 0f, NativeThemePreferenceSection.CHAT)
    val bubbleUserImageCropTop = float("bubble_user_image_crop_top", 0f, NativeThemePreferenceSection.CHAT)
    val bubbleUserImageCropRight = float("bubble_user_image_crop_right", 0f, NativeThemePreferenceSection.CHAT)
    val bubbleUserImageCropBottom = float("bubble_user_image_crop_bottom", 0f, NativeThemePreferenceSection.CHAT)
    val bubbleUserImageRepeatStart = float(
        "bubble_user_image_repeat_start",
        0.35f,
        NativeThemePreferenceSection.CHAT,
    )
    val bubbleUserImageRepeatEnd = float(
        "bubble_user_image_repeat_end",
        0.65f,
        NativeThemePreferenceSection.CHAT,
    )
    val bubbleUserImageRepeatYStart = float(
        "bubble_user_image_repeat_y_start",
        0.35f,
        NativeThemePreferenceSection.CHAT,
        releasedSource = bubbleUserImageRepeatStart,
    )
    val bubbleUserImageRepeatYEnd = float(
        "bubble_user_image_repeat_y_end",
        0.65f,
        NativeThemePreferenceSection.CHAT,
        releasedSource = bubbleUserImageRepeatEnd,
    )
    val bubbleUserImageScale = float("bubble_user_image_scale", 1f, NativeThemePreferenceSection.CHAT)
    val bubbleAiImageCropLeft = float("bubble_ai_image_crop_left", 0f, NativeThemePreferenceSection.CHAT)
    val bubbleAiImageCropTop = float("bubble_ai_image_crop_top", 0f, NativeThemePreferenceSection.CHAT)
    val bubbleAiImageCropRight = float("bubble_ai_image_crop_right", 0f, NativeThemePreferenceSection.CHAT)
    val bubbleAiImageCropBottom = float("bubble_ai_image_crop_bottom", 0f, NativeThemePreferenceSection.CHAT)
    val bubbleAiImageRepeatStart = float(
        "bubble_ai_image_repeat_start",
        0.35f,
        NativeThemePreferenceSection.CHAT,
    )
    val bubbleAiImageRepeatEnd = float(
        "bubble_ai_image_repeat_end",
        0.65f,
        NativeThemePreferenceSection.CHAT,
    )
    val bubbleAiImageRepeatYStart = float(
        "bubble_ai_image_repeat_y_start",
        0.35f,
        NativeThemePreferenceSection.CHAT,
        releasedSource = bubbleAiImageRepeatStart,
    )
    val bubbleAiImageRepeatYEnd = float(
        "bubble_ai_image_repeat_y_end",
        0.65f,
        NativeThemePreferenceSection.CHAT,
        releasedSource = bubbleAiImageRepeatEnd,
    )
    val bubbleAiImageScale = float("bubble_ai_image_scale", 1f, NativeThemePreferenceSection.CHAT)
    val bubbleUserContentPaddingLeft = float(
        "bubble_content_padding_left",
        12f,
        NativeThemePreferenceSection.CHAT,
    )
    val bubbleUserContentPaddingRight = float(
        "bubble_content_padding_right",
        12f,
        NativeThemePreferenceSection.CHAT,
    )
    val bubbleAiContentPaddingLeft = float(
        "bubble_ai_content_padding_left",
        12f,
        NativeThemePreferenceSection.CHAT,
    )
    val bubbleAiContentPaddingRight = float(
        "bubble_ai_content_padding_right",
        12f,
        NativeThemePreferenceSection.CHAT,
    )

    val stringFields = listOf(
        themeMode,
        backgroundImageUri,
        backgroundMediaType,
        appBarContentColorMode,
        chatStyle,
        customUserAvatarUri,
        customAiAvatarUri,
        avatarShape,
        onColorMode,
        customChatTitle,
        inputStyle,
        fontType,
        systemFontName,
        customFontPath,
        bubbleUserFontType,
        bubbleUserSystemFontName,
        bubbleUserCustomFontPath,
        bubbleAiFontType,
        bubbleAiSystemFontName,
        bubbleAiCustomFontPath,
        bubbleUserImageUri,
        bubbleAiImageUri,
        bubbleImageRenderMode,
    )

    val booleanFields = listOf(
        useSystemTheme,
        useCustomColors,
        useBackgroundImage,
        videoBackgroundMuted,
        videoBackgroundLoop,
        toolbarTransparent,
        navigationDrawerWaterGlass,
        navigationDrawerButtonLiquidGlass,
        useCustomNavigationDrawerBackgroundColor,
        useCustomNavigationDrawerAccentColor,
        useCustomAppBarColor,
        useCustomStatusBarColor,
        statusBarTransparent,
        statusBarHidden,
        chatHeaderTransparent,
        chatInputTransparent,
        chatInputFloating,
        chatInputLiquidGlass,
        chatInputWaterGlass,
        forceAppBarContentColorEnabled,
        chatHeaderOverlayMode,
        useBackgroundBlur,
        bubbleShowAvatar,
        bubbleWideLayoutEnabled,
        cursorUserBubbleFollowTheme,
        cursorUserBubbleLiquidGlass,
        cursorUserBubbleWaterGlass,
        bubbleUserBubbleLiquidGlass,
        bubbleUserBubbleWaterGlass,
        bubbleAiBubbleLiquidGlass,
        bubbleAiBubbleWaterGlass,
        bubbleUserUseImage,
        bubbleAiUseImage,
        bubbleUserRoundedCornersEnabled,
        bubbleAiRoundedCornersEnabled,
        showThinkingProcess,
        showStatusTags,
        showInputProcessingStatus,
        showChatFloatingDotsAnimation,
        useCustomFont,
        bubbleUserUseCustomFont,
        bubbleAiUseCustomFont,
        showModelProvider,
        showModelName,
        showRoleName,
        showUserName,
        showMessageTokenStats,
        showMessageTimingStats,
        showMessageTimestamp,
    )

    val intFields = listOf(
        customPrimaryColor,
        customSecondaryColor,
        customNavigationDrawerBackgroundColor,
        customNavigationDrawerAccentColor,
        customAppBarColor,
        customStatusBarColor,
        chatHeaderHistoryIconColor,
        chatHeaderPipIconColor,
        cursorUserBubbleColor,
        bubbleUserBubbleColor,
        bubbleAiBubbleColor,
        bubbleUserTextColor,
        bubbleAiTextColor,
    )

    val floatFields = listOf(
        backgroundImageOpacity,
        backgroundBlurRadius,
        avatarCornerRadius,
        fontScale,
        bubbleUserImageCropLeft,
        bubbleUserImageCropTop,
        bubbleUserImageCropRight,
        bubbleUserImageCropBottom,
        bubbleUserImageRepeatStart,
        bubbleUserImageRepeatEnd,
        bubbleUserImageRepeatYStart,
        bubbleUserImageRepeatYEnd,
        bubbleUserImageScale,
        bubbleAiImageCropLeft,
        bubbleAiImageCropTop,
        bubbleAiImageCropRight,
        bubbleAiImageCropBottom,
        bubbleAiImageRepeatStart,
        bubbleAiImageRepeatEnd,
        bubbleAiImageRepeatYStart,
        bubbleAiImageRepeatYEnd,
        bubbleAiImageScale,
        bubbleUserContentPaddingLeft,
        bubbleUserContentPaddingRight,
        bubbleAiContentPaddingLeft,
        bubbleAiContentPaddingRight,
    )

    val fields: List<NativeThemePreferenceField<*>> =
        stringFields + booleanFields + intFields + floatFields

    val visualStringFields = stringFields.filter {
        it.storageRole == NativeThemePreferenceStorageRole.VISUAL
    }
    val targetMetadataStringFields = stringFields.filter {
        it.storageRole == NativeThemePreferenceStorageRole.TARGET_METADATA
    }

    val defaultStrings: Map<String, String> = stringFields.defaults()
    val defaultBooleans: Map<String, Boolean> = booleanFields.defaults()
    val defaultInts: Map<String, Int> = intFields.defaults()
    val defaultFloats: Map<String, Float> = floatFields.defaults()

    private val booleanFieldsByName = booleanFields.associateBy { field -> field.name }

    init {
        require(fields.map { it.name }.distinct().size == fields.size) {
            "Native theme preference names must be unique."
        }
        require(floatFields.filter { it.releasedSource != null }.all { it.releasedSource in floatFields }) {
            "Released native theme sources must belong to the same schema."
        }
    }

    fun requireBooleanField(name: String): NativeThemeBooleanField =
        requireNotNull(booleanFieldsByName[name]) {
            "Unknown native theme boolean field: $name"
        }

    private fun string(
        name: String,
        defaultValue: String? = null,
        section: NativeThemePreferenceSection,
        storageRole: NativeThemePreferenceStorageRole = NativeThemePreferenceStorageRole.VISUAL,
    ) = NativeThemeStringField(name, defaultValue, section, storageRole)

    private fun boolean(
        name: String,
        defaultValue: Boolean,
        section: NativeThemePreferenceSection,
    ) = NativeThemeBooleanField(name, defaultValue, section)

    private fun int(
        name: String,
        section: NativeThemePreferenceSection,
    ) = NativeThemeIntField(name, section)

    private fun float(
        name: String,
        defaultValue: Float,
        section: NativeThemePreferenceSection,
        releasedSource: NativeThemeFloatField? = null,
    ) = NativeThemeFloatField(name, defaultValue, section, releasedSource)

    private fun <T : Any> List<NativeThemePreferenceField<T>>.defaults(): Map<String, T> =
        mapNotNull { field -> field.defaultValue?.let { field.name to it } }.toMap()
}
