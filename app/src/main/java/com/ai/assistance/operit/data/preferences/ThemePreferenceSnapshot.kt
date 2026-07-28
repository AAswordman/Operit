package com.ai.assistance.operit.data.preferences

data class ThemePreferenceValues(
    val strings: Map<String, String> = emptyMap(),
    val booleans: Map<String, Boolean> = emptyMap(),
    val ints: Map<String, Int> = emptyMap(),
    val floats: Map<String, Float> = emptyMap(),
) {
    fun string(name: String): String? = strings[name]

    fun boolean(name: String): Boolean? = booleans[name]

    fun int(name: String): Int? = ints[name]

    fun float(name: String): Float? = floats[name]

    fun requiredString(name: String): String = requireNotNull(string(name))

    fun requiredBoolean(name: String): Boolean = requireNotNull(boolean(name))

    fun requiredFloat(name: String): Float = requireNotNull(float(name))

    fun withString(name: String, value: String?): ThemePreferenceValues {
        val updated = strings.toMutableMap()
        if (value == null) {
            updated.remove(name)
        } else {
            updated[name] = value
        }
        return copy(strings = updated)
    }

    fun withBoolean(name: String, value: Boolean): ThemePreferenceValues =
        copy(booleans = booleans + (name to value))

    fun withInt(name: String, value: Int?): ThemePreferenceValues {
        val updated = ints.toMutableMap()
        if (value == null) {
            updated.remove(name)
        } else {
            updated[name] = value
        }
        return copy(ints = updated)
    }

    fun withFloat(name: String, value: Float): ThemePreferenceValues =
        copy(floats = floats + (name to value))

    companion object {
        fun defaultVisual(): ThemePreferenceValues =
            ThemePreferenceValues(
                strings = mapOf(
                    "theme_mode" to UserPreferencesManager.THEME_MODE_LIGHT,
                    "background_media_type" to UserPreferencesManager.MEDIA_TYPE_IMAGE,
                    "app_bar_content_color_mode" to
                        UserPreferencesManager.APP_BAR_CONTENT_COLOR_MODE_LIGHT,
                    "chat_style" to UserPreferencesManager.CHAT_STYLE_CURSOR,
                    "avatar_shape" to UserPreferencesManager.AVATAR_SHAPE_CIRCLE,
                    "on_color_mode" to UserPreferencesManager.ON_COLOR_MODE_AUTO,
                    "input_style" to UserPreferencesManager.INPUT_STYLE_AGENT,
                    "font_type" to UserPreferencesManager.FONT_TYPE_SYSTEM,
                    "system_font_name" to UserPreferencesManager.SYSTEM_FONT_DEFAULT,
                    "bubble_user_font_type" to UserPreferencesManager.FONT_TYPE_SYSTEM,
                    "bubble_user_system_font_name" to UserPreferencesManager.SYSTEM_FONT_DEFAULT,
                    "bubble_ai_font_type" to UserPreferencesManager.FONT_TYPE_SYSTEM,
                    "bubble_ai_system_font_name" to UserPreferencesManager.SYSTEM_FONT_DEFAULT,
                    "bubble_image_render_mode" to
                        UserPreferencesManager.BUBBLE_IMAGE_RENDER_MODE_TILED_NINE_SLICE,
                ),
                booleans = mapOf(
                    "use_system_theme" to true,
                    "use_custom_colors" to false,
                    "use_background_image" to false,
                    "video_background_muted" to true,
                    "video_background_loop" to true,
                    "toolbar_transparent" to false,
                    "navigation_drawer_water_glass" to false,
                    "navigation_drawer_button_liquid_glass" to false,
                    "use_custom_navigation_drawer_background_color" to false,
                    "use_custom_navigation_drawer_accent_color" to false,
                    "use_custom_app_bar_color" to false,
                    "use_custom_status_bar_color" to false,
                    "status_bar_transparent" to false,
                    "status_bar_hidden" to false,
                    "chat_header_transparent" to false,
                    "chat_input_transparent" to false,
                    "chat_input_floating" to false,
                    "chat_input_liquid_glass" to false,
                    "chat_input_water_glass" to false,
                    "force_app_bar_content_color_enabled" to false,
                    "chat_header_overlay_mode" to false,
                    "use_background_blur" to false,
                    "bubble_show_avatar" to true,
                    "bubble_wide_layout_enabled" to false,
                    "cursor_user_bubble_follow_theme" to true,
                    "cursor_user_bubble_liquid_glass" to false,
                    "cursor_user_bubble_water_glass" to false,
                    "bubble_user_bubble_liquid_glass" to false,
                    "bubble_user_bubble_water_glass" to false,
                    "bubble_ai_bubble_liquid_glass" to false,
                    "bubble_ai_bubble_water_glass" to false,
                    "bubble_user_use_image" to false,
                    "bubble_ai_use_image" to false,
                    "bubble_rounded_corners_enabled" to true,
                    "bubble_ai_rounded_corners_enabled" to true,
                    "show_thinking_process" to true,
                    "show_status_tags" to true,
                    "show_model_provider" to false,
                    "show_model_name" to false,
                    "show_role_name" to true,
                    "show_user_name" to true,
                    "show_message_token_stats" to false,
                    "show_message_timing_stats" to false,
                    "show_message_timestamp" to false,
                    "show_input_processing_status" to true,
                    "show_chat_floating_dots_animation" to true,
                    "use_custom_font" to false,
                    "bubble_user_use_custom_font" to false,
                    "bubble_ai_use_custom_font" to false,
                ),
                floats = mapOf(
                    "background_image_opacity" to 0.3f,
                    "background_blur_radius" to 10f,
                    "avatar_corner_radius" to 8f,
                    "font_scale" to 1f,
                    "bubble_user_image_crop_left" to 0f,
                    "bubble_user_image_crop_top" to 0f,
                    "bubble_user_image_crop_right" to 0f,
                    "bubble_user_image_crop_bottom" to 0f,
                    "bubble_user_image_repeat_start" to 0.35f,
                    "bubble_user_image_repeat_end" to 0.65f,
                    "bubble_user_image_repeat_y_start" to 0.35f,
                    "bubble_user_image_repeat_y_end" to 0.65f,
                    "bubble_user_image_scale" to 1f,
                    "bubble_ai_image_crop_left" to 0f,
                    "bubble_ai_image_crop_top" to 0f,
                    "bubble_ai_image_crop_right" to 0f,
                    "bubble_ai_image_crop_bottom" to 0f,
                    "bubble_ai_image_repeat_start" to 0.35f,
                    "bubble_ai_image_repeat_end" to 0.65f,
                    "bubble_ai_image_repeat_y_start" to 0.35f,
                    "bubble_ai_image_repeat_y_end" to 0.65f,
                    "bubble_ai_image_scale" to 1f,
                    "bubble_content_padding_left" to 12f,
                    "bubble_content_padding_right" to 12f,
                    "bubble_ai_content_padding_left" to 12f,
                    "bubble_ai_content_padding_right" to 12f,
                ),
            )
    }
}

data class ThemePreferenceSnapshot(
    val source: String,
    val sourceId: String? = null,
    val values: ThemePreferenceValues,
) {
    val themeMode: String get() = values.requiredString("theme_mode")
    val useSystemTheme: Boolean get() = values.requiredBoolean("use_system_theme")
    val useCustomColors: Boolean get() = values.requiredBoolean("use_custom_colors")
    val customPrimaryColor: Int? get() = values.int("custom_primary_color")
    val customSecondaryColor: Int? get() = values.int("custom_secondary_color")
    val onColorMode: String get() = values.requiredString("on_color_mode")
    val useBackgroundImage: Boolean get() = values.requiredBoolean("use_background_image")
    val backgroundImageUri: String? get() = values.string("background_image_uri")
    val backgroundMediaType: String get() = values.requiredString("background_media_type")
    val backgroundImageOpacity: Float get() = values.requiredFloat("background_image_opacity")
    val videoBackgroundMuted: Boolean get() = values.requiredBoolean("video_background_muted")
    val videoBackgroundLoop: Boolean get() = values.requiredBoolean("video_background_loop")
    val toolbarTransparent: Boolean get() = values.requiredBoolean("toolbar_transparent")
    val navigationDrawerWaterGlass: Boolean
        get() = values.requiredBoolean("navigation_drawer_water_glass")
    val navigationDrawerButtonLiquidGlass: Boolean
        get() = values.requiredBoolean("navigation_drawer_button_liquid_glass")
    val useCustomNavigationDrawerBackgroundColor: Boolean
        get() = values.requiredBoolean("use_custom_navigation_drawer_background_color")
    val customNavigationDrawerBackgroundColor: Int?
        get() = values.int("custom_navigation_drawer_background_color")
    val useCustomNavigationDrawerAccentColor: Boolean
        get() = values.requiredBoolean("use_custom_navigation_drawer_accent_color")
    val customNavigationDrawerAccentColor: Int?
        get() = values.int("custom_navigation_drawer_accent_color")
    val useCustomAppBarColor: Boolean get() = values.requiredBoolean("use_custom_app_bar_color")
    val customAppBarColor: Int? get() = values.int("custom_app_bar_color")
    val useCustomStatusBarColor: Boolean
        get() = values.requiredBoolean("use_custom_status_bar_color")
    val customStatusBarColor: Int? get() = values.int("custom_status_bar_color")
    val statusBarTransparent: Boolean get() = values.requiredBoolean("status_bar_transparent")
    val statusBarHidden: Boolean get() = values.requiredBoolean("status_bar_hidden")
    val chatHeaderTransparent: Boolean get() = values.requiredBoolean("chat_header_transparent")
    val chatHeaderOverlayMode: Boolean get() = values.requiredBoolean("chat_header_overlay_mode")
    val chatHeaderHistoryIconColor: Int? get() = values.int("chat_header_history_icon_color")
    val chatHeaderPipIconColor: Int? get() = values.int("chat_header_pip_icon_color")
    val chatInputTransparent: Boolean get() = values.requiredBoolean("chat_input_transparent")
    val chatInputFloating: Boolean get() = values.requiredBoolean("chat_input_floating")
    val chatInputLiquidGlass: Boolean get() = values.requiredBoolean("chat_input_liquid_glass")
    val chatInputWaterGlass: Boolean get() = values.requiredBoolean("chat_input_water_glass")
    val forceAppBarContentColor: Boolean
        get() = values.requiredBoolean("force_app_bar_content_color_enabled")
    val appBarContentColorMode: String get() = values.requiredString("app_bar_content_color_mode")
    val useBackgroundBlur: Boolean get() = values.requiredBoolean("use_background_blur")
    val backgroundBlurRadius: Float get() = values.requiredFloat("background_blur_radius")
    val chatStyle: String get() = values.requiredString("chat_style")
    val inputStyle: String get() = values.requiredString("input_style")
    val bubbleShowAvatar: Boolean get() = values.requiredBoolean("bubble_show_avatar")
    val bubbleWideLayoutEnabled: Boolean get() = values.requiredBoolean("bubble_wide_layout_enabled")
    val cursorUserBubbleFollowTheme: Boolean
        get() = values.requiredBoolean("cursor_user_bubble_follow_theme")
    val cursorUserBubbleColor: Int? get() = values.int("cursor_user_bubble_color")
    val bubbleUserBubbleColor: Int? get() = values.int("bubble_user_bubble_color")
    val bubbleAiBubbleColor: Int? get() = values.int("bubble_ai_bubble_color")
    val bubbleUserTextColor: Int? get() = values.int("bubble_user_text_color")
    val bubbleAiTextColor: Int? get() = values.int("bubble_ai_text_color")
    val bubbleUserUseImage: Boolean get() = values.requiredBoolean("bubble_user_use_image")
    val bubbleAiUseImage: Boolean get() = values.requiredBoolean("bubble_ai_use_image")
    val bubbleUserImageUri: String? get() = values.string("bubble_user_image_uri")
    val bubbleAiImageUri: String? get() = values.string("bubble_ai_image_uri")
    val bubbleUserImageCropLeft: Float get() = values.requiredFloat("bubble_user_image_crop_left")
    val bubbleUserImageCropTop: Float get() = values.requiredFloat("bubble_user_image_crop_top")
    val bubbleUserImageCropRight: Float get() = values.requiredFloat("bubble_user_image_crop_right")
    val bubbleUserImageCropBottom: Float get() = values.requiredFloat("bubble_user_image_crop_bottom")
    val bubbleUserImageRepeatStart: Float
        get() = values.requiredFloat("bubble_user_image_repeat_start")
    val bubbleUserImageRepeatEnd: Float get() = values.requiredFloat("bubble_user_image_repeat_end")
    val bubbleUserImageRepeatYStart: Float
        get() = values.requiredFloat("bubble_user_image_repeat_y_start")
    val bubbleUserImageRepeatYEnd: Float
        get() = values.requiredFloat("bubble_user_image_repeat_y_end")
    val bubbleUserImageScale: Float get() = values.requiredFloat("bubble_user_image_scale")
    val bubbleAiImageCropLeft: Float get() = values.requiredFloat("bubble_ai_image_crop_left")
    val bubbleAiImageCropTop: Float get() = values.requiredFloat("bubble_ai_image_crop_top")
    val bubbleAiImageCropRight: Float get() = values.requiredFloat("bubble_ai_image_crop_right")
    val bubbleAiImageCropBottom: Float get() = values.requiredFloat("bubble_ai_image_crop_bottom")
    val bubbleAiImageRepeatStart: Float get() = values.requiredFloat("bubble_ai_image_repeat_start")
    val bubbleAiImageRepeatEnd: Float get() = values.requiredFloat("bubble_ai_image_repeat_end")
    val bubbleAiImageRepeatYStart: Float
        get() = values.requiredFloat("bubble_ai_image_repeat_y_start")
    val bubbleAiImageRepeatYEnd: Float get() = values.requiredFloat("bubble_ai_image_repeat_y_end")
    val bubbleAiImageScale: Float get() = values.requiredFloat("bubble_ai_image_scale")
    val bubbleImageRenderMode: String get() = values.requiredString("bubble_image_render_mode")
    val bubbleUserRoundedCornersEnabled: Boolean
        get() = values.requiredBoolean("bubble_rounded_corners_enabled")
    val bubbleAiRoundedCornersEnabled: Boolean
        get() = values.requiredBoolean("bubble_ai_rounded_corners_enabled")
    val bubbleUserContentPaddingLeft: Float
        get() = values.requiredFloat("bubble_content_padding_left")
    val bubbleUserContentPaddingRight: Float
        get() = values.requiredFloat("bubble_content_padding_right")
    val bubbleAiContentPaddingLeft: Float
        get() = values.requiredFloat("bubble_ai_content_padding_left")
    val bubbleAiContentPaddingRight: Float
        get() = values.requiredFloat("bubble_ai_content_padding_right")
    val customUserAvatarUri: String? get() = values.string("custom_user_avatar_uri")
    val customAiAvatarUri: String? get() = values.string("custom_ai_avatar_uri")
    val avatarShape: String get() = values.requiredString("avatar_shape")
    val avatarCornerRadius: Float get() = values.requiredFloat("avatar_corner_radius")
    val fontType: String get() = values.requiredString("font_type")
    val systemFontName: String get() = values.requiredString("system_font_name")
    val customFontPath: String? get() = values.string("custom_font_path")
    val fontScale: Float get() = values.requiredFloat("font_scale")
    val showThinkingProcess: Boolean get() = values.requiredBoolean("show_thinking_process")
    val showStatusTags: Boolean get() = values.requiredBoolean("show_status_tags")
    val showModelProvider: Boolean get() = values.requiredBoolean("show_model_provider")
    val showModelName: Boolean get() = values.requiredBoolean("show_model_name")
    val showRoleName: Boolean get() = values.requiredBoolean("show_role_name")
    val showUserName: Boolean get() = values.requiredBoolean("show_user_name")
    val showMessageTokenStats: Boolean get() = values.requiredBoolean("show_message_token_stats")
    val showMessageTimingStats: Boolean get() = values.requiredBoolean("show_message_timing_stats")
    val showMessageTimestamp: Boolean get() = values.requiredBoolean("show_message_timestamp")
    val showInputProcessingStatus: Boolean
        get() = values.requiredBoolean("show_input_processing_status")
    val useCustomFont: Boolean get() = values.requiredBoolean("use_custom_font")
    val bubbleUserUseCustomFont: Boolean
        get() = values.requiredBoolean("bubble_user_use_custom_font")
    val bubbleUserFontType: String get() = values.requiredString("bubble_user_font_type")
    val bubbleUserSystemFontName: String
        get() = values.requiredString("bubble_user_system_font_name")
    val bubbleUserCustomFontPath: String? get() = values.string("bubble_user_custom_font_path")
    val bubbleAiUseCustomFont: Boolean
        get() = values.requiredBoolean("bubble_ai_use_custom_font")
    val bubbleAiFontType: String get() = values.requiredString("bubble_ai_font_type")
    val bubbleAiSystemFontName: String
        get() = values.requiredString("bubble_ai_system_font_name")
    val bubbleAiCustomFontPath: String? get() = values.string("bubble_ai_custom_font_path")
    val cursorUserBubbleLiquidGlass: Boolean
        get() = values.requiredBoolean("cursor_user_bubble_liquid_glass")
    val cursorUserBubbleWaterGlass: Boolean
        get() = values.requiredBoolean("cursor_user_bubble_water_glass")
    val bubbleUserBubbleLiquidGlass: Boolean
        get() = values.requiredBoolean("bubble_user_bubble_liquid_glass")
    val bubbleUserBubbleWaterGlass: Boolean
        get() = values.requiredBoolean("bubble_user_bubble_water_glass")
    val bubbleAiBubbleLiquidGlass: Boolean
        get() = values.requiredBoolean("bubble_ai_bubble_liquid_glass")
    val bubbleAiBubbleWaterGlass: Boolean
        get() = values.requiredBoolean("bubble_ai_bubble_water_glass")
    val customChatTitle: String? get() = values.string("custom_chat_title")
    val showChatFloatingDotsAnimation: Boolean
        get() = values.requiredBoolean("show_chat_floating_dots_animation")
}
