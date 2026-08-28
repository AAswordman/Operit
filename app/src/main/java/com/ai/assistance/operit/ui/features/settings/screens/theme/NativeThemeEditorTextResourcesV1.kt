package com.ai.assistance.operit.ui.features.settings.screens.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceSchemaV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeColorTargetV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeEditorColorsModeDefinitionV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeEditorTextKey
import com.ai.assistance.operit.ui.main.components.rememberNavigationDrawerAppearance
import com.ai.assistance.operit.ui.theme.getTextColorForBackground

@StringRes
internal fun NativeThemeEditorTextKey.stringResourceId(): Int =
    when (this) {
        NativeThemeEditorTextKey.COMPOSER -> R.string.theme_tab_input
        NativeThemeEditorTextKey.COMPOSER_STYLE -> R.string.input_style_title
        NativeThemeEditorTextKey.COMPOSER_STYLE_DESCRIPTION -> R.string.input_style_desc
        NativeThemeEditorTextKey.COMPOSER_STYLE_CLASSIC -> R.string.input_style_classic
        NativeThemeEditorTextKey.COMPOSER_STYLE_AGENT -> R.string.input_style_agent
        NativeThemeEditorTextKey.COMPOSER_APPEARANCE ->
            R.string.theme_chat_input_transparent_title
        NativeThemeEditorTextKey.COMPOSER_TRANSPARENT -> R.string.theme_chat_input_transparent
        NativeThemeEditorTextKey.COMPOSER_TRANSPARENT_DESCRIPTION ->
            R.string.theme_chat_input_transparent_desc
        NativeThemeEditorTextKey.COMPOSER_FLOATING -> R.string.theme_chat_input_floating
        NativeThemeEditorTextKey.COMPOSER_FLOATING_DESCRIPTION ->
            R.string.theme_chat_input_floating_desc
        NativeThemeEditorTextKey.COMPOSER_LIQUID_GLASS ->
            R.string.theme_chat_input_liquid_glass
        NativeThemeEditorTextKey.COMPOSER_LIQUID_GLASS_DESCRIPTION ->
            R.string.theme_chat_input_liquid_glass_desc
        NativeThemeEditorTextKey.COMPOSER_WATER_GLASS ->
            R.string.theme_chat_input_water_glass
        NativeThemeEditorTextKey.COMPOSER_WATER_GLASS_DESCRIPTION ->
            R.string.theme_chat_input_water_glass_desc
        NativeThemeEditorTextKey.ADVANCED_SETTINGS -> R.string.advanced_settings
        NativeThemeEditorTextKey.EXPANDED -> R.string.expanded
        NativeThemeEditorTextKey.COLLAPSED -> R.string.collapsed
        NativeThemeEditorTextKey.COLORS_AND_MODE -> R.string.theme_title_color
        NativeThemeEditorTextKey.APPEARANCE_MODE -> R.string.theme_title_mode
        NativeThemeEditorTextKey.FOLLOW_SYSTEM -> R.string.theme_follow_system
        NativeThemeEditorTextKey.FOLLOW_SYSTEM_DESCRIPTION -> R.string.theme_follow_system_desc
        NativeThemeEditorTextKey.APPEARANCE_MODE_SELECT -> R.string.theme_select
        NativeThemeEditorTextKey.APPEARANCE_MODE_LIGHT -> R.string.theme_light
        NativeThemeEditorTextKey.APPEARANCE_MODE_DARK -> R.string.theme_dark
        NativeThemeEditorTextKey.PALETTE -> R.string.theme_custom_color
        NativeThemeEditorTextKey.USE_CUSTOM_COLORS -> R.string.theme_use_custom_color
        NativeThemeEditorTextKey.USE_CUSTOM_COLORS_DESCRIPTION -> R.string.theme_custom_color_desc
        NativeThemeEditorTextKey.PRIMARY_COLOR -> R.string.theme_primary_color
        NativeThemeEditorTextKey.SECONDARY_COLOR -> R.string.theme_secondary_color
        NativeThemeEditorTextKey.PICK_PRIMARY_COLOR -> R.string.colorpicker_select_primary
        NativeThemeEditorTextKey.PICK_SECONDARY_COLOR -> R.string.colorpicker_select_secondary
        NativeThemeEditorTextKey.ON_COLOR_MODE -> R.string.theme_on_color_mode
        NativeThemeEditorTextKey.ON_COLOR_AUTO -> R.string.theme_on_color_auto
        NativeThemeEditorTextKey.ON_COLOR_LIGHT -> R.string.theme_on_color_light
        NativeThemeEditorTextKey.ON_COLOR_DARK -> R.string.theme_on_color_dark
        NativeThemeEditorTextKey.COLOR_STATUS_BAR -> R.string.colorpicker_select_statusbar
        NativeThemeEditorTextKey.COLOR_NAVIGATION_DRAWER_BACKGROUND ->
            R.string.colorpicker_select_navigation_drawer_background
        NativeThemeEditorTextKey.COLOR_NAVIGATION_DRAWER_ACCENT ->
            R.string.colorpicker_select_navigation_drawer_accent
        NativeThemeEditorTextKey.COLOR_HISTORY_ICON -> R.string.colorpicker_select_history_icon
        NativeThemeEditorTextKey.COLOR_PIP_ICON -> R.string.colorpicker_select_pip_icon
        NativeThemeEditorTextKey.COLOR_CURSOR_USER_BUBBLE ->
            R.string.colorpicker_select_cursor_user_bubble
        NativeThemeEditorTextKey.COLOR_BUBBLE_USER_BUBBLE ->
            R.string.colorpicker_select_bubble_user_bubble
        NativeThemeEditorTextKey.COLOR_BUBBLE_AI_BUBBLE ->
            R.string.colorpicker_select_bubble_ai_bubble
        NativeThemeEditorTextKey.COLOR_BUBBLE_USER_TEXT ->
            R.string.colorpicker_select_bubble_user_text
        NativeThemeEditorTextKey.COLOR_BUBBLE_AI_TEXT ->
            R.string.colorpicker_select_bubble_ai_text
        NativeThemeEditorTextKey.PICK_COLOR -> R.string.colorpicker_select_color
        NativeThemeEditorTextKey.APP_CHROME -> R.string.theme_tab_interface
        NativeThemeEditorTextKey.APP_CHROME_STATUS_BAR -> R.string.theme_statusbar_color
        NativeThemeEditorTextKey.APP_CHROME_STATUS_BAR_HIDDEN -> R.string.theme_statusbar_hidden
        NativeThemeEditorTextKey.APP_CHROME_STATUS_BAR_HIDDEN_DESCRIPTION ->
            R.string.theme_statusbar_hidden_desc
        NativeThemeEditorTextKey.APP_CHROME_STATUS_BAR_TRANSPARENT ->
            R.string.theme_statusbar_transparent
        NativeThemeEditorTextKey.APP_CHROME_STATUS_BAR_TRANSPARENT_DESCRIPTION ->
            R.string.theme_statusbar_transparent_desc
        NativeThemeEditorTextKey.APP_CHROME_USE_CUSTOM_STATUS_BAR_COLOR ->
            R.string.theme_use_custom_statusbar_color
        NativeThemeEditorTextKey.APP_CHROME_USE_CUSTOM_STATUS_BAR_COLOR_DESCRIPTION ->
            R.string.theme_use_custom_statusbar_color_desc
        NativeThemeEditorTextKey.APP_CHROME_STATUS_BAR_COLOR -> R.string.theme_statusbar_color
        NativeThemeEditorTextKey.APP_CHROME_TOOLBAR -> R.string.theme_toolbar_transparent
        NativeThemeEditorTextKey.APP_CHROME_TOOLBAR_TRANSPARENT ->
            R.string.theme_toolbar_transparent_desc
        NativeThemeEditorTextKey.APP_CHROME_TOOLBAR_TRANSPARENT_DESCRIPTION ->
            R.string.theme_toolbar_transparent_desc_desc
        NativeThemeEditorTextKey.APP_CHROME_APP_BAR_COLOR -> R.string.theme_appbar_color
        NativeThemeEditorTextKey.APP_CHROME_USE_CUSTOM_APP_BAR_COLOR ->
            R.string.theme_use_custom_appbar_color
        NativeThemeEditorTextKey.APP_CHROME_USE_CUSTOM_APP_BAR_COLOR_DESCRIPTION ->
            R.string.theme_use_custom_appbar_color_desc
        NativeThemeEditorTextKey.APP_CHROME_NAVIGATION_DRAWER ->
            R.string.theme_navigation_drawer_title
        NativeThemeEditorTextKey.APP_CHROME_NAVIGATION_DRAWER_WATER_GLASS ->
            R.string.theme_navigation_drawer_water_glass
        NativeThemeEditorTextKey.APP_CHROME_NAVIGATION_DRAWER_WATER_GLASS_DESCRIPTION ->
            R.string.theme_navigation_drawer_water_glass_desc
        NativeThemeEditorTextKey.APP_CHROME_NAVIGATION_DRAWER_BUTTON_LIQUID_GLASS ->
            R.string.theme_navigation_drawer_button_liquid_glass
        NativeThemeEditorTextKey.APP_CHROME_NAVIGATION_DRAWER_BUTTON_LIQUID_GLASS_DESCRIPTION ->
            R.string.theme_navigation_drawer_button_liquid_glass_desc
        NativeThemeEditorTextKey.APP_CHROME_USE_CUSTOM_NAVIGATION_DRAWER_BACKGROUND_COLOR ->
            R.string.theme_use_custom_navigation_drawer_background_color
        NativeThemeEditorTextKey.APP_CHROME_USE_CUSTOM_NAVIGATION_DRAWER_BACKGROUND_COLOR_DESCRIPTION ->
            R.string.theme_use_custom_navigation_drawer_background_color_desc
        NativeThemeEditorTextKey.APP_CHROME_NAVIGATION_DRAWER_BACKGROUND_COLOR ->
            R.string.theme_navigation_drawer_background_color
        NativeThemeEditorTextKey.APP_CHROME_USE_CUSTOM_NAVIGATION_DRAWER_ACCENT_COLOR ->
            R.string.theme_use_custom_navigation_drawer_accent_color
        NativeThemeEditorTextKey.APP_CHROME_USE_CUSTOM_NAVIGATION_DRAWER_ACCENT_COLOR_DESCRIPTION ->
            R.string.theme_use_custom_navigation_drawer_accent_color_desc
        NativeThemeEditorTextKey.APP_CHROME_NAVIGATION_DRAWER_ACCENT_COLOR ->
            R.string.theme_navigation_drawer_accent_color
        NativeThemeEditorTextKey.APP_CHROME_CHAT_HEADER -> R.string.theme_chat_header_transparent_title
        NativeThemeEditorTextKey.APP_CHROME_CHAT_HEADER_TRANSPARENT ->
            R.string.theme_chat_header_transparent
        NativeThemeEditorTextKey.APP_CHROME_CHAT_HEADER_TRANSPARENT_DESCRIPTION ->
            R.string.theme_chat_header_transparent_desc
        NativeThemeEditorTextKey.APP_CHROME_CHAT_HEADER_OVERLAY_MODE ->
            R.string.theme_chat_header_overlay_mode
        NativeThemeEditorTextKey.APP_CHROME_CHAT_HEADER_OVERLAY_MODE_DESCRIPTION ->
            R.string.theme_chat_header_overlay_mode_desc
        NativeThemeEditorTextKey.APP_CHROME_APP_BAR_CONTENT_COLOR ->
            R.string.theme_appbar_content_color_title
        NativeThemeEditorTextKey.APP_CHROME_FORCE_APP_BAR_CONTENT_COLOR ->
            R.string.theme_force_appbar_content_color
        NativeThemeEditorTextKey.APP_CHROME_FORCE_APP_BAR_CONTENT_COLOR_DESCRIPTION ->
            R.string.theme_force_appbar_content_color_desc
        NativeThemeEditorTextKey.APP_CHROME_APP_BAR_CONTENT_COLOR_MODE ->
            R.string.theme_appbar_content_color_mode
        NativeThemeEditorTextKey.APP_CHROME_APP_BAR_CONTENT_COLOR_LIGHT ->
            R.string.theme_appbar_content_color_light
        NativeThemeEditorTextKey.APP_CHROME_APP_BAR_CONTENT_COLOR_DARK ->
            R.string.theme_appbar_content_color_dark
        NativeThemeEditorTextKey.APP_CHROME_CHAT_HEADER_ICONS ->
            R.string.theme_chat_header_icons_color_title
        NativeThemeEditorTextKey.APP_CHROME_CHAT_HEADER_HISTORY_ICON_COLOR ->
            R.string.theme_chat_header_history_icon_color
        NativeThemeEditorTextKey.APP_CHROME_CHAT_HEADER_PIP_ICON_COLOR ->
            R.string.theme_chat_header_pip_icon_color
        NativeThemeEditorTextKey.TYPOGRAPHY -> R.string.theme_font_settings
        NativeThemeEditorTextKey.FONT_FAMILY -> R.string.theme_font_settings
        NativeThemeEditorTextKey.USE_CUSTOM_FONT -> R.string.enable_custom_font
        NativeThemeEditorTextKey.USE_CUSTOM_FONT_DESCRIPTION -> R.string.use_system_or_custom_font
        NativeThemeEditorTextKey.FONT_SOURCE -> R.string.font_type_label
        NativeThemeEditorTextKey.FONT_SOURCE_SYSTEM -> R.string.system_font
        NativeThemeEditorTextKey.FONT_SOURCE_FILE -> R.string.custom_font_file
        NativeThemeEditorTextKey.SYSTEM_FONT_NAME -> R.string.select_system_font
        NativeThemeEditorTextKey.SYSTEM_FONT_DEFAULT -> R.string.theme_font_default
        NativeThemeEditorTextKey.SYSTEM_FONT_SERIF -> R.string.theme_font_serif
        NativeThemeEditorTextKey.SYSTEM_FONT_SANS_SERIF -> R.string.theme_font_sans_serif
        NativeThemeEditorTextKey.SYSTEM_FONT_MONOSPACE -> R.string.theme_font_monospace
        NativeThemeEditorTextKey.SYSTEM_FONT_CURSIVE -> R.string.theme_font_cursive
        NativeThemeEditorTextKey.FONT_FILE -> R.string.custom_font_file_title
        NativeThemeEditorTextKey.FONT_FILE_DESCRIPTION -> R.string.font_file_support_desc
        NativeThemeEditorTextKey.SELECT_FONT_FILE -> R.string.select_font_file
        NativeThemeEditorTextKey.CLEAR_FONT_FILE -> R.string.clear_font
        NativeThemeEditorTextKey.CURRENT_FONT_FILE -> R.string.current_font_file_path
        NativeThemeEditorTextKey.FONT_SCALE -> R.string.font_size_scale_label
        NativeThemeEditorTextKey.BACKGROUND -> R.string.theme_title_background
        NativeThemeEditorTextKey.BACKGROUND_MEDIA -> R.string.theme_bg_media
        NativeThemeEditorTextKey.USE_BACKGROUND_MEDIA -> R.string.theme_use_custom_bg
        NativeThemeEditorTextKey.USE_BACKGROUND_MEDIA_DESCRIPTION -> R.string.theme_custom_bg_desc
        NativeThemeEditorTextKey.MEDIA_TYPE -> R.string.theme_media_type
        NativeThemeEditorTextKey.MEDIA_TYPE_IMAGE -> R.string.theme_media_image
        NativeThemeEditorTextKey.MEDIA_TYPE_VIDEO -> R.string.theme_media_video
        NativeThemeEditorTextKey.BACKGROUND_ASSET -> R.string.theme_bg_media
        NativeThemeEditorTextKey.BACKGROUND_ASSET_DESCRIPTION -> R.string.theme_custom_bg_desc
        NativeThemeEditorTextKey.SELECT_BACKGROUND_IMAGE -> R.string.theme_select_image
        NativeThemeEditorTextKey.SELECT_BACKGROUND_VIDEO -> R.string.theme_select_video
        NativeThemeEditorTextKey.BACKGROUND_OPACITY -> R.string.theme_bg_opacity
        NativeThemeEditorTextKey.BACKGROUND_BLUR -> R.string.theme_background_blur
        NativeThemeEditorTextKey.BACKGROUND_BLUR_DESCRIPTION -> R.string.theme_background_blur_desc
        NativeThemeEditorTextKey.BACKGROUND_BLUR_RADIUS -> R.string.theme_background_blur_radius
        NativeThemeEditorTextKey.VIDEO_MUTED -> R.string.theme_mute
        NativeThemeEditorTextKey.VIDEO_LOOP -> R.string.theme_loop_on
        NativeThemeEditorTextKey.RECROP_BACKGROUND -> R.string.theme_recrop
        NativeThemeEditorTextKey.MESSAGE_DETAILS_AND_MOTION -> R.string.display_options_title
        NativeThemeEditorTextKey.MESSAGE_REASONING -> R.string.theme_message_reasoning_title
        NativeThemeEditorTextKey.SHOW_THINKING_PROCESS -> R.string.show_thinking_process
        NativeThemeEditorTextKey.SHOW_THINKING_PROCESS_DESCRIPTION -> R.string.show_thinking_process_desc
        NativeThemeEditorTextKey.SHOW_STATUS_TAGS -> R.string.show_status_tags
        NativeThemeEditorTextKey.SHOW_STATUS_TAGS_DESCRIPTION -> R.string.show_status_tags_desc
        NativeThemeEditorTextKey.MESSAGE_IDENTITY -> R.string.theme_message_identity_title
        NativeThemeEditorTextKey.SHOW_ROLE_NAME -> R.string.show_role_name
        NativeThemeEditorTextKey.SHOW_ROLE_NAME_DESCRIPTION -> R.string.show_role_name_description
        NativeThemeEditorTextKey.SHOW_USER_NAME -> R.string.show_user_name
        NativeThemeEditorTextKey.SHOW_USER_NAME_DESCRIPTION -> R.string.show_user_name_description
        NativeThemeEditorTextKey.SHOW_MESSAGE_TIMESTAMP -> R.string.show_message_timestamp
        NativeThemeEditorTextKey.SHOW_MESSAGE_TIMESTAMP_DESCRIPTION -> R.string.show_message_timestamp_description
        NativeThemeEditorTextKey.MESSAGE_DIAGNOSTICS -> R.string.theme_message_diagnostics_title
        NativeThemeEditorTextKey.SHOW_MODEL_PROVIDER -> R.string.show_model_provider
        NativeThemeEditorTextKey.SHOW_MODEL_PROVIDER_DESCRIPTION -> R.string.show_model_provider_description
        NativeThemeEditorTextKey.SHOW_MODEL_NAME -> R.string.show_model_name
        NativeThemeEditorTextKey.SHOW_MODEL_NAME_DESCRIPTION -> R.string.show_model_name_description
        NativeThemeEditorTextKey.SHOW_MESSAGE_TOKEN_STATS -> R.string.show_message_token_stats
        NativeThemeEditorTextKey.SHOW_MESSAGE_TOKEN_STATS_DESCRIPTION ->
            R.string.show_message_token_stats_description
        NativeThemeEditorTextKey.SHOW_MESSAGE_TIMING_STATS -> R.string.show_message_timing_stats
        NativeThemeEditorTextKey.SHOW_MESSAGE_TIMING_STATS_DESCRIPTION ->
            R.string.show_message_timing_stats_description
        NativeThemeEditorTextKey.MESSAGE_ACTIVITY -> R.string.theme_message_activity_title
        NativeThemeEditorTextKey.SHOW_INPUT_PROCESSING_STATUS -> R.string.show_input_processing_status
        NativeThemeEditorTextKey.SHOW_INPUT_PROCESSING_STATUS_DESCRIPTION ->
            R.string.show_input_processing_status_desc
        NativeThemeEditorTextKey.SHOW_CHAT_FLOATING_DOTS -> R.string.show_chat_floating_dots_animation
        NativeThemeEditorTextKey.SHOW_CHAT_FLOATING_DOTS_DESCRIPTION ->
            R.string.show_chat_floating_dots_animation_desc
    }

@Composable
internal fun NativeThemeEditorTextKey.localizedText(vararg formatArgs: Any): String =
    stringResource(stringResourceId(), *formatArgs)

@Composable
internal fun NativeThemeColorTargetV1.displayColor(
    values: ThemePreferenceValues,
    displayDefault: Int? = null,
): Int {
    values.int(field)?.let { return it }
    displayDefault?.let { return it }
    val colorScheme = MaterialTheme.colorScheme
    val navigationDrawerAppearance = rememberNavigationDrawerAppearance()
    return when (this) {
        NativeThemeColorTargetV1.PRIMARY ->
            NativeThemeEditorColorsModeDefinitionV1.defaultPrimaryColor
        NativeThemeColorTargetV1.SECONDARY ->
            NativeThemeEditorColorsModeDefinitionV1.defaultSecondaryColor
        NativeThemeColorTargetV1.STATUS_BAR,
        NativeThemeColorTargetV1.APP_BAR,
        NativeThemeColorTargetV1.NAVIGATION_DRAWER_BACKGROUND,
        NativeThemeColorTargetV1.BUBBLE_AI_BUBBLE -> colorScheme.surface.toArgb()
        NativeThemeColorTargetV1.NAVIGATION_DRAWER_ACCENT ->
            navigationDrawerAppearance.titleColor.toArgb()
        NativeThemeColorTargetV1.HISTORY_ICON,
        NativeThemeColorTargetV1.PIP_ICON -> Color.Gray.toArgb()
        NativeThemeColorTargetV1.CURSOR_USER_BUBBLE,
        NativeThemeColorTargetV1.BUBBLE_USER_BUBBLE -> colorScheme.primaryContainer.toArgb()
        NativeThemeColorTargetV1.BUBBLE_USER_TEXT -> {
            val bubbleColor =
                values.int(NativeThemePreferenceSchemaV1.bubbleUserBubbleColor)
                    ?: colorScheme.primaryContainer.toArgb()
            getTextColorForBackground(Color(bubbleColor)).toArgb()
        }
        NativeThemeColorTargetV1.BUBBLE_AI_TEXT -> {
            val bubbleColor =
                values.int(NativeThemePreferenceSchemaV1.bubbleAiBubbleColor)
                    ?: colorScheme.surface.toArgb()
            getTextColorForBackground(Color(bubbleColor)).toArgb()
        }
    }
}
