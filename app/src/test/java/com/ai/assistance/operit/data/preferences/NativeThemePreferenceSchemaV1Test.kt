package com.ai.assistance.operit.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeThemePreferenceSchemaV1Test {
    @Test
    fun releasedOptionValuesRemainFrozen() {
        assertEquals(
            mapOf(
                "theme.light" to "light",
                "theme.dark" to "dark",
                "app_bar_content.light" to "light",
                "app_bar_content.dark" to "dark",
                "media.image" to "image",
                "media.video" to "video",
                "chat.cursor" to "cursor",
                "chat.bubble" to "bubble",
                "input.classic" to "classic",
                "input.agent" to "agent",
                "bubble_image.tiled_nine_slice" to "tiled_nine_slice",
                "bubble_image.nine_patch" to "nine_patch",
                "avatar.circle" to "circle",
                "avatar.square" to "square",
                "on_color.auto" to "auto",
                "on_color.light" to "light",
                "on_color.dark" to "dark",
                "font.system" to "system",
                "font.file" to "file",
                "system_font.default" to "default",
                "system_font.serif" to "serif",
                "system_font.sans_serif" to "sans-serif",
                "system_font.monospace" to "monospace",
                "system_font.cursive" to "cursive",
            ),
            mapOf(
                "theme.light" to NativeThemePreferenceOptionsV1.THEME_MODE_LIGHT,
                "theme.dark" to NativeThemePreferenceOptionsV1.THEME_MODE_DARK,
                "app_bar_content.light" to
                    NativeThemePreferenceOptionsV1.APP_BAR_CONTENT_COLOR_MODE_LIGHT,
                "app_bar_content.dark" to
                    NativeThemePreferenceOptionsV1.APP_BAR_CONTENT_COLOR_MODE_DARK,
                "media.image" to NativeThemePreferenceOptionsV1.MEDIA_TYPE_IMAGE,
                "media.video" to NativeThemePreferenceOptionsV1.MEDIA_TYPE_VIDEO,
                "chat.cursor" to NativeThemePreferenceOptionsV1.CHAT_STYLE_CURSOR,
                "chat.bubble" to NativeThemePreferenceOptionsV1.CHAT_STYLE_BUBBLE,
                "input.classic" to NativeThemePreferenceOptionsV1.INPUT_STYLE_CLASSIC,
                "input.agent" to NativeThemePreferenceOptionsV1.INPUT_STYLE_AGENT,
                "bubble_image.tiled_nine_slice" to
                    NativeThemePreferenceOptionsV1.BUBBLE_IMAGE_RENDER_MODE_TILED_NINE_SLICE,
                "bubble_image.nine_patch" to
                    NativeThemePreferenceOptionsV1.BUBBLE_IMAGE_RENDER_MODE_NINE_PATCH,
                "avatar.circle" to NativeThemePreferenceOptionsV1.AVATAR_SHAPE_CIRCLE,
                "avatar.square" to NativeThemePreferenceOptionsV1.AVATAR_SHAPE_SQUARE,
                "on_color.auto" to NativeThemePreferenceOptionsV1.ON_COLOR_MODE_AUTO,
                "on_color.light" to NativeThemePreferenceOptionsV1.ON_COLOR_MODE_LIGHT,
                "on_color.dark" to NativeThemePreferenceOptionsV1.ON_COLOR_MODE_DARK,
                "font.system" to NativeThemePreferenceOptionsV1.FONT_TYPE_SYSTEM,
                "font.file" to NativeThemePreferenceOptionsV1.FONT_TYPE_FILE,
                "system_font.default" to NativeThemePreferenceOptionsV1.SYSTEM_FONT_DEFAULT,
                "system_font.serif" to NativeThemePreferenceOptionsV1.SYSTEM_FONT_SERIF,
                "system_font.sans_serif" to
                    NativeThemePreferenceOptionsV1.SYSTEM_FONT_SANS_SERIF,
                "system_font.monospace" to
                    NativeThemePreferenceOptionsV1.SYSTEM_FONT_MONOSPACE,
                "system_font.cursive" to NativeThemePreferenceOptionsV1.SYSTEM_FONT_CURSIVE,
            ),
        )
        assertEquals(NativeThemePreferenceOptionsV1.THEME_MODE_LIGHT, UserPreferencesManager.THEME_MODE_LIGHT)
        assertEquals(NativeThemePreferenceOptionsV1.THEME_MODE_DARK, UserPreferencesManager.THEME_MODE_DARK)
        assertEquals(
            NativeThemePreferenceOptionsV1.APP_BAR_CONTENT_COLOR_MODE_LIGHT,
            UserPreferencesManager.APP_BAR_CONTENT_COLOR_MODE_LIGHT,
        )
        assertEquals(
            NativeThemePreferenceOptionsV1.APP_BAR_CONTENT_COLOR_MODE_DARK,
            UserPreferencesManager.APP_BAR_CONTENT_COLOR_MODE_DARK,
        )
        assertEquals(NativeThemePreferenceOptionsV1.MEDIA_TYPE_IMAGE, UserPreferencesManager.MEDIA_TYPE_IMAGE)
        assertEquals(NativeThemePreferenceOptionsV1.MEDIA_TYPE_VIDEO, UserPreferencesManager.MEDIA_TYPE_VIDEO)
        assertEquals(NativeThemePreferenceOptionsV1.CHAT_STYLE_CURSOR, UserPreferencesManager.CHAT_STYLE_CURSOR)
        assertEquals(NativeThemePreferenceOptionsV1.CHAT_STYLE_BUBBLE, UserPreferencesManager.CHAT_STYLE_BUBBLE)
        assertEquals(NativeThemePreferenceOptionsV1.INPUT_STYLE_CLASSIC, UserPreferencesManager.INPUT_STYLE_CLASSIC)
        assertEquals(NativeThemePreferenceOptionsV1.INPUT_STYLE_AGENT, UserPreferencesManager.INPUT_STYLE_AGENT)
        assertEquals(
            NativeThemePreferenceOptionsV1.BUBBLE_IMAGE_RENDER_MODE_TILED_NINE_SLICE,
            UserPreferencesManager.BUBBLE_IMAGE_RENDER_MODE_TILED_NINE_SLICE,
        )
        assertEquals(
            NativeThemePreferenceOptionsV1.BUBBLE_IMAGE_RENDER_MODE_NINE_PATCH,
            UserPreferencesManager.BUBBLE_IMAGE_RENDER_MODE_NINE_PATCH,
        )
        assertEquals(NativeThemePreferenceOptionsV1.AVATAR_SHAPE_CIRCLE, UserPreferencesManager.AVATAR_SHAPE_CIRCLE)
        assertEquals(NativeThemePreferenceOptionsV1.AVATAR_SHAPE_SQUARE, UserPreferencesManager.AVATAR_SHAPE_SQUARE)
        assertEquals(NativeThemePreferenceOptionsV1.ON_COLOR_MODE_AUTO, UserPreferencesManager.ON_COLOR_MODE_AUTO)
        assertEquals(NativeThemePreferenceOptionsV1.ON_COLOR_MODE_LIGHT, UserPreferencesManager.ON_COLOR_MODE_LIGHT)
        assertEquals(NativeThemePreferenceOptionsV1.ON_COLOR_MODE_DARK, UserPreferencesManager.ON_COLOR_MODE_DARK)
        assertEquals(NativeThemePreferenceOptionsV1.FONT_TYPE_SYSTEM, UserPreferencesManager.FONT_TYPE_SYSTEM)
        assertEquals(NativeThemePreferenceOptionsV1.FONT_TYPE_FILE, UserPreferencesManager.FONT_TYPE_FILE)
        assertEquals(NativeThemePreferenceOptionsV1.SYSTEM_FONT_DEFAULT, UserPreferencesManager.SYSTEM_FONT_DEFAULT)
        assertEquals(NativeThemePreferenceOptionsV1.SYSTEM_FONT_SERIF, UserPreferencesManager.SYSTEM_FONT_SERIF)
        assertEquals(
            NativeThemePreferenceOptionsV1.SYSTEM_FONT_SANS_SERIF,
            UserPreferencesManager.SYSTEM_FONT_SANS_SERIF,
        )
        assertEquals(
            NativeThemePreferenceOptionsV1.SYSTEM_FONT_MONOSPACE,
            UserPreferencesManager.SYSTEM_FONT_MONOSPACE,
        )
        assertEquals(NativeThemePreferenceOptionsV1.SYSTEM_FONT_CURSIVE, UserPreferencesManager.SYSTEM_FONT_CURSIVE)
    }

    @Test
    fun releasedFieldNamesAndTypesRemainFrozen() {
        assertEquals(
            setOf(
                "theme_mode",
                "background_image_uri",
                "background_media_type",
                "app_bar_content_color_mode",
                "chat_style",
                "custom_user_avatar_uri",
                "custom_ai_avatar_uri",
                "avatar_shape",
                "on_color_mode",
                "custom_chat_title",
                "input_style",
                "font_type",
                "system_font_name",
                "custom_font_path",
                "bubble_user_font_type",
                "bubble_user_system_font_name",
                "bubble_user_custom_font_path",
                "bubble_ai_font_type",
                "bubble_ai_system_font_name",
                "bubble_ai_custom_font_path",
                "bubble_user_image_uri",
                "bubble_ai_image_uri",
                "bubble_image_render_mode",
            ),
            NativeThemePreferenceSchemaV1.stringFields.map { it.name }.toSet(),
        )
        assertEquals(
            setOf(
                "use_system_theme",
                "use_custom_colors",
                "use_background_image",
                "video_background_muted",
                "video_background_loop",
                "toolbar_transparent",
                "navigation_drawer_water_glass",
                "navigation_drawer_button_liquid_glass",
                "use_custom_navigation_drawer_background_color",
                "use_custom_navigation_drawer_accent_color",
                "use_custom_app_bar_color",
                "use_custom_status_bar_color",
                "status_bar_transparent",
                "status_bar_hidden",
                "chat_header_transparent",
                "chat_input_transparent",
                "chat_input_floating",
                "chat_input_liquid_glass",
                "chat_input_water_glass",
                "force_app_bar_content_color_enabled",
                "chat_header_overlay_mode",
                "use_background_blur",
                "bubble_show_avatar",
                "bubble_wide_layout_enabled",
                "cursor_user_bubble_follow_theme",
                "cursor_user_bubble_liquid_glass",
                "cursor_user_bubble_water_glass",
                "bubble_user_bubble_liquid_glass",
                "bubble_user_bubble_water_glass",
                "bubble_ai_bubble_liquid_glass",
                "bubble_ai_bubble_water_glass",
                "bubble_user_use_image",
                "bubble_ai_use_image",
                "bubble_rounded_corners_enabled",
                "bubble_ai_rounded_corners_enabled",
                "show_thinking_process",
                "show_status_tags",
                "show_input_processing_status",
                "show_chat_floating_dots_animation",
                "use_custom_font",
                "bubble_user_use_custom_font",
                "bubble_ai_use_custom_font",
                "show_model_provider",
                "show_model_name",
                "show_role_name",
                "show_user_name",
                "show_message_token_stats",
                "show_message_timing_stats",
                "show_message_timestamp",
            ),
            NativeThemePreferenceSchemaV1.booleanFields.map { it.name }.toSet(),
        )
        assertEquals(
            setOf(
                "custom_primary_color",
                "custom_secondary_color",
                "custom_navigation_drawer_background_color",
                "custom_navigation_drawer_accent_color",
                "custom_app_bar_color",
                "custom_status_bar_color",
                "chat_header_history_icon_color",
                "chat_header_pip_icon_color",
                "cursor_user_bubble_color",
                "bubble_user_bubble_color",
                "bubble_ai_bubble_color",
                "bubble_user_text_color",
                "bubble_ai_text_color",
            ),
            NativeThemePreferenceSchemaV1.intFields.map { it.name }.toSet(),
        )
        assertEquals(
            setOf(
                "background_image_opacity",
                "background_blur_radius",
                "avatar_corner_radius",
                "font_scale",
                "bubble_user_image_crop_left",
                "bubble_user_image_crop_top",
                "bubble_user_image_crop_right",
                "bubble_user_image_crop_bottom",
                "bubble_user_image_repeat_start",
                "bubble_user_image_repeat_end",
                "bubble_user_image_repeat_y_start",
                "bubble_user_image_repeat_y_end",
                "bubble_user_image_scale",
                "bubble_ai_image_crop_left",
                "bubble_ai_image_crop_top",
                "bubble_ai_image_crop_right",
                "bubble_ai_image_crop_bottom",
                "bubble_ai_image_repeat_start",
                "bubble_ai_image_repeat_end",
                "bubble_ai_image_repeat_y_start",
                "bubble_ai_image_repeat_y_end",
                "bubble_ai_image_scale",
                "bubble_content_padding_left",
                "bubble_content_padding_right",
                "bubble_ai_content_padding_left",
                "bubble_ai_content_padding_right",
            ),
            NativeThemePreferenceSchemaV1.floatFields.map { it.name }.toSet(),
        )
        assertEquals(111, NativeThemePreferenceSchemaV1.fields.size)
        assertEquals(111, NativeThemePreferenceSchemaV1.fields.map { it.name }.toSet().size)
    }

    @Test
    fun releasedDefaultsRemainFrozen() {
        val defaults = ThemePreferenceValues.defaultVisual()

        assertEquals(
            mapOf(
                "theme_mode" to "light",
                "background_media_type" to "image",
                "app_bar_content_color_mode" to "light",
                "chat_style" to "cursor",
                "avatar_shape" to "circle",
                "on_color_mode" to "auto",
                "input_style" to "agent",
                "font_type" to "system",
                "system_font_name" to "default",
                "bubble_user_font_type" to "system",
                "bubble_user_system_font_name" to "default",
                "bubble_ai_font_type" to "system",
                "bubble_ai_system_font_name" to "default",
                "bubble_image_render_mode" to "tiled_nine_slice",
            ),
            defaults.strings,
        )
        assertEquals(
            NativeThemePreferenceSchemaV1.booleanFields.map { it.name }.toSet(),
            defaults.booleans.keys,
        )
        assertEquals(
            setOf(
                "use_system_theme",
                "video_background_muted",
                "video_background_loop",
                "bubble_show_avatar",
                "cursor_user_bubble_follow_theme",
                "bubble_rounded_corners_enabled",
                "bubble_ai_rounded_corners_enabled",
                "show_thinking_process",
                "show_status_tags",
                "show_input_processing_status",
                "show_chat_floating_dots_animation",
                "show_role_name",
                "show_user_name",
            ),
            defaults.booleans.filterValues { it }.keys,
        )
        assertTrue(defaults.ints.isEmpty())
        assertEquals(
            mapOf(
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
            defaults.floats,
        )
    }

    @Test
    fun metadataAndReleasedSourcesRemainExplicit() {
        assertEquals(
            setOf("custom_ai_avatar_uri", "custom_chat_title"),
            NativeThemePreferenceSchemaV1.targetMetadataStringFields.map { it.name }.toSet(),
        )
        assertEquals(
            mapOf(
                "bubble_user_image_repeat_y_start" to "bubble_user_image_repeat_start",
                "bubble_user_image_repeat_y_end" to "bubble_user_image_repeat_end",
                "bubble_ai_image_repeat_y_start" to "bubble_ai_image_repeat_start",
                "bubble_ai_image_repeat_y_end" to "bubble_ai_image_repeat_end",
            ),
            NativeThemePreferenceSchemaV1.floatFields
                .mapNotNull { field -> field.releasedSource?.let { field.name to it.name } }
                .toMap(),
        )
    }

    @Test
    fun defaultSnapshotsOwnTheirPopulatedMaps() {
        val first = ThemePreferenceValues.defaultVisual()
        val second = ThemePreferenceValues.defaultVisual()

        assertNotSame(first.strings, second.strings)
        assertNotSame(first.booleans, second.booleans)
        assertNotSame(first.floats, second.floats)
    }
}
