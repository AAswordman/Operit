package com.ai.assistance.operit.ui.features.settings.screens.theme

import com.ai.assistance.operit.data.preferences.NativeThemePreferenceSchemaV1
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceSection
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceOptionsV1
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeBooleanControlDefinitionV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeAssetActionV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeAssetControlDefinitionV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeChoicePresentation
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeColorControlDefinitionV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeEditorDefinitionV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeEditorColorsModeDefinitionV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeEditorItemDefinitionV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeEditorTextKey
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeFloatControlDefinitionV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeFloatCommitPolicyV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeFloatFormatV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeStringChoiceDefinitionV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.applyNativeThemeBooleanControlV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeThemeEditorDefinitionV1Test {
    @Test
    fun inputDefinitionCoversEveryInputField() {
        val definitionFields =
            NativeThemeEditorDefinitionV1.composer.groups.flatMap { group ->
                group.items.map { item ->
                    when (item) {
                        is NativeThemeAssetControlDefinitionV1 -> item.field.name
                        is NativeThemeBooleanControlDefinitionV1 -> item.field.name
                        is NativeThemeColorControlDefinitionV1 -> item.target.field.name
                        is NativeThemeFloatControlDefinitionV1 -> item.field.name
                        is NativeThemeStringChoiceDefinitionV1 -> item.field.name
                    }
                }
            }
        val expectedFields =
            listOf(
                "input_style",
                "chat_input_transparent",
                "chat_input_floating",
                "chat_input_liquid_glass",
                "chat_input_water_glass",
            )
        val schemaFields =
            NativeThemePreferenceSchemaV1.fields
                .filter { field -> field.section == NativeThemePreferenceSection.INPUT }
                .map { field -> field.name }
                .toSet()

        assertEquals(expectedFields, definitionFields)
        assertEquals(expectedFields.size, definitionFields.toSet().size)
        assertEquals(schemaFields, definitionFields.toSet())
    }

    @Test
    fun inputStyleOptionsRemainClassicAndAgent() {
        val inputStyle =
            NativeThemeEditorDefinitionV1.composerStyle.items.single()
                as NativeThemeStringChoiceDefinitionV1
        assertEquals(
            listOf(
                NativeThemePreferenceOptionsV1.INPUT_STYLE_CLASSIC,
                NativeThemePreferenceOptionsV1.INPUT_STYLE_AGENT,
            ),
            inputStyle.options.map { option -> option.value },
        )
        assertEquals(NativeThemeChoicePresentation.SEGMENTED, inputStyle.presentation)
    }

    @Test
    fun glassControlsAreVisibleOnlyForTransparentInput() {
        val controls = NativeThemeEditorDefinitionV1.composerAppearance.items
        val defaultValues = ThemePreferenceValues.defaultVisual()
        val transparentValues =
            defaultValues.withBoolean(NativeThemePreferenceSchemaV1.chatInputTransparent, true)

        assertEquals(
            listOf("chat_input_transparent", "chat_input_floating"),
            controls.filter { it.isVisible(defaultValues) }.map { item -> item.booleanFieldName() },
        )
        assertEquals(
            listOf(
                "chat_input_transparent",
                "chat_input_floating",
                "chat_input_liquid_glass",
                "chat_input_water_glass",
            ),
            controls.filter { it.isVisible(transparentValues) }.map { item -> item.booleanFieldName() },
        )
        assertFalse(defaultValues.requiredBoolean(NativeThemePreferenceSchemaV1.chatInputTransparent))
        assertTrue(transparentValues.requiredBoolean(NativeThemePreferenceSchemaV1.chatInputTransparent))
    }

    @Test
    fun composerDefinitionIdsRemainOrderedAndUnique() {
        val groups = NativeThemeEditorDefinitionV1.composer.groups
        val itemIds = groups.flatMap { group -> group.items.map { item -> item.id.value } }

        assertEquals("composer", NativeThemeEditorDefinitionV1.composer.id.value)
        assertEquals(
            listOf("composer.style", "composer.appearance"),
            groups.map { group -> group.id.value },
        )
        assertEquals(
            listOf(
                "composer.style.mode",
                "composer.appearance.transparent",
                "composer.appearance.floating",
                "composer.appearance.liquid_glass",
                "composer.appearance.water_glass",
            ),
            itemIds,
        )
        assertEquals(itemIds.size, itemIds.toSet().size)
    }

    @Test
    fun glassControlsRemainAdvancedAndCustomizedValuesAreDetectable() {
        val advancedControls =
            NativeThemeEditorDefinitionV1.composerAppearance.items.filter { item -> item.advanced }
        val customizedValues =
            ThemePreferenceValues.defaultVisual()
                .withBoolean(NativeThemePreferenceSchemaV1.chatInputLiquidGlass, true)

        assertEquals(
            listOf(
                "composer.appearance.liquid_glass",
                "composer.appearance.water_glass",
            ),
            advancedControls.map { item -> item.id.value },
        )
        assertEquals(
            listOf(true, false),
            advancedControls.map { item -> item.isCustomized(customizedValues) },
        )
    }

    @Test
    fun stableTextKeysRemainUnique() {
        val keys = NativeThemeEditorTextKey.entries.map { key -> key.value }
        assertEquals(
            listOf(
                "composer",
                "composer.style",
                "composer.style.description",
                "composer.style.classic",
                "composer.style.agent",
                "composer.appearance",
                "composer.transparent",
                "composer.transparent.description",
                "composer.floating",
                "composer.floating.description",
                "composer.liquid_glass",
                "composer.liquid_glass.description",
                "composer.water_glass",
                "composer.water_glass.description",
                "advanced_settings",
                "expanded",
                "collapsed",
                "colors_and_mode",
                "appearance_mode",
                "appearance_mode.follow_system",
                "appearance_mode.follow_system.description",
                "appearance_mode.select",
                "appearance_mode.light",
                "appearance_mode.dark",
                "palette",
                "palette.use_custom_colors",
                "palette.use_custom_colors.description",
                "palette.primary",
                "palette.secondary",
                "palette.primary.pick",
                "palette.secondary.pick",
                "palette.on_color_mode",
                "palette.on_color.auto",
                "palette.on_color.light",
                "palette.on_color.dark",
                "color.status_bar",
                "color.navigation_drawer.background",
                "color.navigation_drawer.accent",
                "color.history_icon",
                "color.pip_icon",
                "color.cursor.user_bubble",
                "color.bubble.user_bubble",
                "color.bubble.ai_bubble",
                "color.bubble.user_text",
                "color.bubble.ai_text",
                "color.pick",
                "app_chrome",
                "app_chrome.status_bar",
                "app_chrome.status_bar.hidden",
                "app_chrome.status_bar.hidden.description",
                "app_chrome.status_bar.transparent",
                "app_chrome.status_bar.transparent.description",
                "app_chrome.status_bar.custom",
                "app_chrome.status_bar.custom.description",
                "app_chrome.status_bar.color",
                "app_chrome.toolbar",
                "app_chrome.toolbar.transparent",
                "app_chrome.toolbar.transparent.description",
                "app_chrome.app_bar.color",
                "app_chrome.app_bar.custom",
                "app_chrome.app_bar.custom.description",
                "app_chrome.navigation_drawer",
                "app_chrome.navigation_drawer.water_glass",
                "app_chrome.navigation_drawer.water_glass.description",
                "app_chrome.navigation_drawer.button_liquid_glass",
                "app_chrome.navigation_drawer.button_liquid_glass.description",
                "app_chrome.navigation_drawer.custom_background",
                "app_chrome.navigation_drawer.custom_background.description",
                "app_chrome.navigation_drawer.background_color",
                "app_chrome.navigation_drawer.custom_accent",
                "app_chrome.navigation_drawer.custom_accent.description",
                "app_chrome.navigation_drawer.accent_color",
                "app_chrome.chat_header",
                "app_chrome.chat_header.transparent",
                "app_chrome.chat_header.transparent.description",
                "app_chrome.chat_header.overlay",
                "app_chrome.chat_header.overlay.description",
                "app_chrome.app_bar_content_color",
                "app_chrome.force_app_bar_content_color",
                "app_chrome.force_app_bar_content_color.description",
                "app_chrome.app_bar_content_color.mode",
                "app_chrome.app_bar_content_color.light",
                "app_chrome.app_bar_content_color.dark",
                "app_chrome.chat_header.icons",
                "app_chrome.chat_header.history_icon_color",
                "app_chrome.chat_header.pip_icon_color",
                "typography",
                "typography.family",
                "typography.use_custom_font",
                "typography.use_custom_font.description",
                "typography.source",
                "typography.source.system",
                "typography.source.file",
                "typography.system_font",
                "typography.system_font.default",
                "typography.system_font.serif",
                "typography.system_font.sans_serif",
                "typography.system_font.monospace",
                "typography.system_font.cursive",
                "typography.file",
                "typography.file.description",
                "typography.file.select",
                "typography.file.clear",
                "typography.file.current",
                "typography.scale",
                "background",
                "background.media",
                "background.media.use",
                "background.media.use.description",
                "background.media.type",
                "background.media.type.image",
                "background.media.type.video",
                "background.media.asset",
                "background.media.asset.description",
                "background.media.asset.select_image",
                "background.media.asset.select_video",
                "background.opacity",
                "background.blur",
                "background.blur.description",
                "background.blur.radius",
                "background.video.muted",
                "background.video.loop",
                "background.media.asset.recrop",
                "message_details_and_motion",
                "message.reasoning",
                "message.reasoning.show_thinking",
                "message.reasoning.show_thinking.description",
                "message.reasoning.show_status_tags",
                "message.reasoning.show_status_tags.description",
                "message.identity",
                "message.identity.show_role_name",
                "message.identity.show_role_name.description",
                "message.identity.show_user_name",
                "message.identity.show_user_name.description",
                "message.identity.show_timestamp",
                "message.identity.show_timestamp.description",
                "message.diagnostics",
                "message.diagnostics.show_provider",
                "message.diagnostics.show_provider.description",
                "message.diagnostics.show_name",
                "message.diagnostics.show_name.description",
                "message.diagnostics.show_token_stats",
                "message.diagnostics.show_token_stats.description",
                "message.diagnostics.show_timing_stats",
                "message.diagnostics.show_timing_stats.description",
                "message.activity",
                "message.activity.show_processing",
                "message.activity.show_processing.description",
                "message.activity.show_dots",
                "message.activity.show_dots.description",
            ),
            keys,
        )
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun colorsAndModeDefinitionKeepsFieldOrderAndVisibility() {
        val defaultValues = ThemePreferenceValues.defaultVisual()
        val explicitModeValues =
            defaultValues.withBoolean(NativeThemePreferenceSchemaV1.useSystemTheme, false)
        val customColorValues =
            defaultValues.withBoolean(NativeThemePreferenceSchemaV1.useCustomColors, true)
        val fields =
            NativeThemeEditorDefinitionV1.colorsAndMode.groups.flatMap { group ->
                group.items.map { item -> item.fieldName() }
            }

        assertEquals(
            listOf(
                "use_system_theme",
                "theme_mode",
                "use_custom_colors",
                "custom_primary_color",
                "custom_secondary_color",
                "on_color_mode",
            ),
            fields,
        )
        assertEquals(
            listOf("use_system_theme"),
            NativeThemeEditorDefinitionV1.appearanceMode.visibleItems(defaultValues)
                .map { item -> item.fieldName() },
        )
        assertEquals(
            listOf("use_system_theme", "theme_mode"),
            NativeThemeEditorDefinitionV1.appearanceMode.visibleItems(explicitModeValues)
                .map { item -> item.fieldName() },
        )
        assertEquals(
            listOf("use_custom_colors"),
            NativeThemeEditorDefinitionV1.palette.visibleItems(defaultValues)
                .map { item -> item.fieldName() },
        )
        assertEquals(
            listOf(
                "use_custom_colors",
                "custom_primary_color",
                "custom_secondary_color",
                "on_color_mode",
            ),
            NativeThemeEditorDefinitionV1.palette.visibleItems(customColorValues)
                .map { item -> item.fieldName() },
        )
        assertEquals(
            listOf(
                "colors_mode",
                "typography",
                "background",
                "message_details",
                "composer",
                "app_chrome",
            ),
            NativeThemeEditorDefinitionV1.sections.map { section -> section.id.value },
        )
        assertEquals(
            listOf("colors_mode.appearance", "colors_mode.palette"),
            NativeThemeEditorDefinitionV1.colorsAndMode.groups.map { group -> group.id.value },
        )
        assertEquals(
            listOf(
                "colors_mode.appearance.follow_system",
                "colors_mode.appearance.mode",
                "colors_mode.palette.custom",
                "colors_mode.palette.primary",
                "colors_mode.palette.secondary",
                "colors_mode.palette.foreground",
            ),
            NativeThemeEditorDefinitionV1.colorsAndMode.groups.flatMap { group ->
                group.items.map { item -> item.id.value }
            },
        )
    }

    @Test
    fun enablingCustomColorsMaterializesReleasedEditorDefaults() {
        val definition =
            NativeThemeEditorDefinitionV1.palette.items.first()
                as NativeThemeBooleanControlDefinitionV1
        val enabled =
            applyNativeThemeBooleanControlV1(
                values = ThemePreferenceValues.defaultVisual(),
                definition = definition,
                checked = true,
            )
        val disabled =
            applyNativeThemeBooleanControlV1(
                values = enabled,
                definition = definition,
                checked = false,
            )

        assertTrue(enabled.requiredBoolean(NativeThemePreferenceSchemaV1.useCustomColors))
        assertEquals(
            NativeThemeEditorColorsModeDefinitionV1.defaultPrimaryColor,
            enabled.int(NativeThemePreferenceSchemaV1.customPrimaryColor),
        )
        assertEquals(
            NativeThemeEditorColorsModeDefinitionV1.defaultSecondaryColor,
            enabled.int(NativeThemePreferenceSchemaV1.customSecondaryColor),
        )
        assertFalse(disabled.requiredBoolean(NativeThemePreferenceSchemaV1.useCustomColors))
        assertEquals(
            enabled.int(NativeThemePreferenceSchemaV1.customPrimaryColor),
            disabled.int(NativeThemePreferenceSchemaV1.customPrimaryColor),
        )
        assertEquals(
            enabled.int(NativeThemePreferenceSchemaV1.customSecondaryColor),
            disabled.int(NativeThemePreferenceSchemaV1.customSecondaryColor),
        )

        val customValues =
            disabled
                .withInt(NativeThemePreferenceSchemaV1.customPrimaryColor, 0xFF123456.toInt())
                .withInt(NativeThemePreferenceSchemaV1.customSecondaryColor, 0xFFABCDEF.toInt())
        val reenabled =
            applyNativeThemeBooleanControlV1(
                values = customValues,
                definition = definition,
                checked = true,
            )
        assertEquals(
            0xFF123456.toInt(),
            reenabled.int(NativeThemePreferenceSchemaV1.customPrimaryColor),
        )
        assertEquals(
            0xFFABCDEF.toInt(),
            reenabled.int(NativeThemePreferenceSchemaV1.customSecondaryColor),
        )

        val colorControls =
            NativeThemeEditorDefinitionV1.palette.items
                .filterIsInstance<NativeThemeColorControlDefinitionV1>()
        assertEquals(
            listOf(
                NativeThemeEditorColorsModeDefinitionV1.defaultPrimaryColor,
                NativeThemeEditorColorsModeDefinitionV1.defaultSecondaryColor,
            ),
            colorControls.map { control -> control.displayDefault },
        )
    }

    @Test
    fun typographyDefinitionPreservesFieldsOptionsAndVisibility() {
        val defaultValues = ThemePreferenceValues.defaultVisual()
        val systemValues =
            defaultValues.withBoolean(NativeThemePreferenceSchemaV1.useCustomFont, true)
        val fileValues =
            systemValues.withString(
                NativeThemePreferenceSchemaV1.fontType,
                NativeThemePreferenceOptionsV1.FONT_TYPE_FILE,
            )
        val items = NativeThemeEditorDefinitionV1.typographyFamily.items

        assertEquals(
            listOf(
                "use_custom_font",
                "font_type",
                "system_font_name",
                "custom_font_path",
                "font_scale",
            ),
            items.map { item -> item.fieldName() },
        )
        assertEquals(
            listOf("use_custom_font", "font_scale"),
            NativeThemeEditorDefinitionV1.typographyFamily.visibleItems(defaultValues)
                .map { item -> item.fieldName() },
        )
        assertEquals(
            listOf("use_custom_font", "font_type", "system_font_name", "font_scale"),
            NativeThemeEditorDefinitionV1.typographyFamily.visibleItems(systemValues)
                .map { item -> item.fieldName() },
        )
        assertEquals(
            listOf("use_custom_font", "font_type", "custom_font_path", "font_scale"),
            NativeThemeEditorDefinitionV1.typographyFamily.visibleItems(fileValues)
                .map { item -> item.fieldName() },
        )

        val fontSource = items[1] as NativeThemeStringChoiceDefinitionV1
        val systemFont = items[2] as NativeThemeStringChoiceDefinitionV1
        val fontAsset = items[3] as NativeThemeAssetControlDefinitionV1
        val fontScale = items[4] as NativeThemeFloatControlDefinitionV1
        assertEquals(
            listOf(
                NativeThemePreferenceOptionsV1.FONT_TYPE_SYSTEM,
                NativeThemePreferenceOptionsV1.FONT_TYPE_FILE,
            ),
            fontSource.options.map { option -> option.value },
        )
        assertEquals(
            listOf(
                NativeThemePreferenceOptionsV1.SYSTEM_FONT_DEFAULT,
                NativeThemePreferenceOptionsV1.SYSTEM_FONT_SERIF,
                NativeThemePreferenceOptionsV1.SYSTEM_FONT_SANS_SERIF,
                NativeThemePreferenceOptionsV1.SYSTEM_FONT_MONOSPACE,
                NativeThemePreferenceOptionsV1.SYSTEM_FONT_CURSIVE,
            ),
            systemFont.options.map { option -> option.value },
        )
        assertEquals(NativeThemeAssetActionV1.APP_FONT, fontAsset.action)
        assertEquals(0.8f, fontScale.minimum)
        assertEquals(1.5f, fontScale.maximum)
        assertEquals(6, fontScale.steps)
        assertEquals(
            listOf(
                "typography.family.custom",
                "typography.family.source",
                "typography.family.system_font",
                "typography.family.file",
                "typography.scale",
            ),
            items.map { item -> item.id.value },
        )
    }

    @Test
    fun backgroundDefinitionPreservesVisibilityAndFieldOrder() {
        val defaultValues = ThemePreferenceValues.defaultVisual()
        val enabledValues =
            defaultValues.withBoolean(NativeThemePreferenceSchemaV1.useBackgroundImage, true)
        val videoValues =
            enabledValues
                .withString(
                    NativeThemePreferenceSchemaV1.backgroundMediaType,
                    NativeThemePreferenceOptionsV1.MEDIA_TYPE_VIDEO,
                )
                .withString(
                    NativeThemePreferenceSchemaV1.backgroundImageUri,
                    "file:///background.mp4",
                )
                .withBoolean(NativeThemePreferenceSchemaV1.useBackgroundBlur, true)
        val items = NativeThemeEditorDefinitionV1.backgroundMedia.items

        assertEquals(
            listOf(
                "use_background_image",
                "background_media_type",
                "background_image_uri",
                "background_image_opacity",
                "use_background_blur",
                "background_blur_radius",
                "video_background_muted",
                "video_background_loop",
            ),
            items.map { item -> item.fieldName() },
        )
        assertEquals(
            listOf("use_background_image"),
            NativeThemeEditorDefinitionV1.backgroundMedia.visibleItems(defaultValues)
                .map { item -> item.fieldName() },
        )
        assertEquals(
            listOf(
                "use_background_image",
                "background_media_type",
                "background_image_uri",
                "background_image_opacity",
                "use_background_blur",
            ),
            NativeThemeEditorDefinitionV1.backgroundMedia.visibleItems(enabledValues)
                .map { item -> item.fieldName() },
        )
        assertEquals(
            listOf(
                "use_background_image",
                "background_media_type",
                "background_image_uri",
                "background_image_opacity",
                "video_background_muted",
                "video_background_loop",
            ),
            NativeThemeEditorDefinitionV1.backgroundMedia.visibleItems(videoValues)
                .map { item -> item.fieldName() },
        )

        val mediaChoice = items[1] as NativeThemeStringChoiceDefinitionV1
        val mediaAsset = items[2] as NativeThemeAssetControlDefinitionV1
        val opacity = items[3] as NativeThemeFloatControlDefinitionV1
        val blurRadius = items[5] as NativeThemeFloatControlDefinitionV1
        assertEquals(
            listOf(
                NativeThemePreferenceOptionsV1.MEDIA_TYPE_IMAGE,
                NativeThemePreferenceOptionsV1.MEDIA_TYPE_VIDEO,
            ),
            mediaChoice.options.map { option -> option.value },
        )
        assertEquals(
            NativeThemeEditorTextKey.SELECT_BACKGROUND_VIDEO,
            mediaAsset.selectLabel(videoValues),
        )
        assertEquals(0.1f, opacity.minimum)
        assertEquals(1f, opacity.maximum)
        assertEquals(0, opacity.steps)
        assertEquals(
            NativeThemeFloatFormatV1.INTEGER,
            blurRadius.format,
        )
        assertEquals(
            NativeThemeFloatCommitPolicyV1.ON_VALUE_CHANGE_FINISHED,
            opacity.commitPolicy,
        )
        assertEquals(
            NativeThemeFloatCommitPolicyV1.ON_VALUE_CHANGE_FINISHED,
            blurRadius.commitPolicy,
        )
    }

    @Test
    fun messageDetailsDefinitionCoversAllMessageInformationFields() {
        val items =
            NativeThemeEditorDefinitionV1.messageDetails.groups.flatMap { group -> group.items }
        assertEquals(
            listOf(
                "show_thinking_process",
                "show_status_tags",
                "show_role_name",
                "show_user_name",
                "show_message_timestamp",
                "show_model_provider",
                "show_model_name",
                "show_message_token_stats",
                "show_message_timing_stats",
                "show_input_processing_status",
                "show_chat_floating_dots_animation",
            ),
            items.map { item -> item.fieldName() },
        )
        assertEquals(
            listOf(
                "message_details.reasoning",
                "message_details.identity",
                "message_details.diagnostics",
                "message_details.activity",
            ),
            NativeThemeEditorDefinitionV1.messageDetails.groups.map { group -> group.id.value },
        )
        assertEquals(
            listOf(
                "message_details.diagnostics.provider",
                "message_details.diagnostics.name",
                "message_details.diagnostics.token_stats",
                "message_details.diagnostics.timing_stats",
            ),
            NativeThemeEditorDefinitionV1.messageDetailsDiagnostics.items
                .filter { item -> item.advanced }
                .map { item -> item.id.value },
        )
    }

    @Test
    fun appChromeDefinitionPreservesFieldOrderAndVisibility() {
        val defaultValues = ThemePreferenceValues.defaultVisual()
        val statusBarCustomValues =
            defaultValues.withBoolean(NativeThemePreferenceSchemaV1.useCustomStatusBarColor, true)
        val appBarCustomValues =
            defaultValues.withBoolean(NativeThemePreferenceSchemaV1.useCustomAppBarColor, true)
        val chatHeaderOverlayValues =
            defaultValues.withBoolean(NativeThemePreferenceSchemaV1.chatHeaderTransparent, true)
        val appBarContentValues =
            defaultValues.withBoolean(
                NativeThemePreferenceSchemaV1.forceAppBarContentColorEnabled,
                true,
            )
        val groups = NativeThemeEditorDefinitionV1.appChrome.groups

        assertEquals(
            listOf(
                "status_bar_hidden",
                "status_bar_transparent",
                "use_custom_status_bar_color",
                "custom_status_bar_color",
                "toolbar_transparent",
                "use_custom_app_bar_color",
                "custom_app_bar_color",
                "navigation_drawer_water_glass",
                "navigation_drawer_button_liquid_glass",
                "use_custom_navigation_drawer_background_color",
                "custom_navigation_drawer_background_color",
                "use_custom_navigation_drawer_accent_color",
                "custom_navigation_drawer_accent_color",
                "chat_header_transparent",
                "chat_header_overlay_mode",
                "force_app_bar_content_color_enabled",
                "app_bar_content_color_mode",
                "chat_header_history_icon_color",
                "chat_header_pip_icon_color",
            ),
            groups.flatMap { group -> group.items.map { item -> item.fieldName() } },
        )
        assertEquals(
            listOf("status_bar_hidden", "status_bar_transparent", "use_custom_status_bar_color"),
            groups[0].visibleItems(defaultValues).map { item -> item.fieldName() },
        )
        assertEquals(
            listOf(
                "status_bar_hidden",
                "status_bar_transparent",
                "use_custom_status_bar_color",
                "custom_status_bar_color",
            ),
            groups[0].visibleItems(statusBarCustomValues).map { item -> item.fieldName() },
        )
        assertEquals(
            listOf("toolbar_transparent", "use_custom_app_bar_color"),
            groups[1].visibleItems(defaultValues).map { item -> item.fieldName() },
        )
        assertEquals(
            listOf("toolbar_transparent", "use_custom_app_bar_color", "custom_app_bar_color"),
            groups[1].visibleItems(appBarCustomValues).map { item -> item.fieldName() },
        )
        assertEquals(
            listOf("chat_header_transparent", "chat_header_overlay_mode"),
            groups[3].visibleItems(chatHeaderOverlayValues).map { item -> item.fieldName() },
        )
        assertEquals(
            listOf("force_app_bar_content_color_enabled", "app_bar_content_color_mode"),
            groups[4].visibleItems(appBarContentValues).map { item -> item.fieldName() },
        )
        assertEquals(
            NativeThemePreferenceSchemaV1.fields
                .filter { field -> field.section == NativeThemePreferenceSection.INTERFACE }
                .map { field -> field.name }
                .toSet(),
            groups.flatMap { group -> group.items.map { item -> item.fieldName() } }.toSet(),
        )
    }

    private fun NativeThemeEditorItemDefinitionV1.booleanFieldName(): String =
        (this as NativeThemeBooleanControlDefinitionV1).field.name

    private fun NativeThemeEditorItemDefinitionV1.fieldName(): String =
        when (this) {
            is NativeThemeAssetControlDefinitionV1 -> field.name
            is NativeThemeBooleanControlDefinitionV1 -> field.name
            is NativeThemeColorControlDefinitionV1 -> target.field.name
            is NativeThemeFloatControlDefinitionV1 -> field.name
            is NativeThemeStringChoiceDefinitionV1 -> field.name
        }
}
