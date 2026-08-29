package com.ai.assistance.operit.ui.features.settings.theme.editor.contract

import com.ai.assistance.operit.data.preferences.NativeThemeBooleanField
import com.ai.assistance.operit.data.preferences.NativeThemeFloatField
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceOptionsV1
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceSchemaV1

internal object NativeThemeEditorConversationDefinitionV1 {
    private val bubbleStyle =
        NativeThemeEditorPredicateV1.StringEquals(
            field = NativeThemePreferenceSchemaV1.chatStyle,
            expected = NativeThemePreferenceOptionsV1.CHAT_STYLE_BUBBLE,
        )
    private val cursorStyle =
        NativeThemeEditorPredicateV1.StringEquals(
            field = NativeThemePreferenceSchemaV1.chatStyle,
            expected = NativeThemePreferenceOptionsV1.CHAT_STYLE_CURSOR,
        )
    private val cursorUserBubbleColorVisible =
        NativeThemeEditorPredicateV1.AllOf(
            listOf(
                cursorStyle,
                NativeThemeEditorPredicateV1.Not(
                    NativeThemeEditorPredicateV1.BooleanEquals(
                        field = NativeThemePreferenceSchemaV1.cursorUserBubbleFollowTheme,
                        expected = true,
                    ),
                ),
            ),
        )
    private val userBubbleImageEnabled =
        NativeThemeEditorPredicateV1.AllOf(
            listOf(
                bubbleStyle,
                NativeThemeEditorPredicateV1.BooleanEquals(
                    field = NativeThemePreferenceSchemaV1.bubbleUserUseImage,
                    expected = true,
                ),
                NativeThemeEditorPredicateV1.Not(
                    NativeThemeEditorPredicateV1.BooleanEquals(
                        field = NativeThemePreferenceSchemaV1.bubbleUserBubbleLiquidGlass,
                        expected = true,
                    ),
                ),
                NativeThemeEditorPredicateV1.Not(
                    NativeThemeEditorPredicateV1.BooleanEquals(
                        field = NativeThemePreferenceSchemaV1.bubbleUserBubbleWaterGlass,
                        expected = true,
                    ),
                ),
            ),
        )
    private val aiBubbleImageEnabled =
        NativeThemeEditorPredicateV1.AllOf(
            listOf(
                bubbleStyle,
                NativeThemeEditorPredicateV1.BooleanEquals(
                    field = NativeThemePreferenceSchemaV1.bubbleAiUseImage,
                    expected = true,
                ),
                NativeThemeEditorPredicateV1.Not(
                    NativeThemeEditorPredicateV1.BooleanEquals(
                        field = NativeThemePreferenceSchemaV1.bubbleAiBubbleLiquidGlass,
                        expected = true,
                    ),
                ),
                NativeThemeEditorPredicateV1.Not(
                    NativeThemeEditorPredicateV1.BooleanEquals(
                        field = NativeThemePreferenceSchemaV1.bubbleAiBubbleWaterGlass,
                        expected = true,
                    ),
                ),
            ),
        )
    private val userBubbleGlassDisabled =
        NativeThemeEditorPredicateV1.AllOf(
            listOf(
                bubbleStyle,
                NativeThemeEditorPredicateV1.Not(
                    NativeThemeEditorPredicateV1.BooleanEquals(
                        field = NativeThemePreferenceSchemaV1.bubbleUserBubbleLiquidGlass,
                        expected = true,
                    ),
                ),
                NativeThemeEditorPredicateV1.Not(
                    NativeThemeEditorPredicateV1.BooleanEquals(
                        field = NativeThemePreferenceSchemaV1.bubbleUserBubbleWaterGlass,
                        expected = true,
                    ),
                ),
            ),
        )
    private val aiBubbleGlassDisabled =
        NativeThemeEditorPredicateV1.AllOf(
            listOf(
                bubbleStyle,
                NativeThemeEditorPredicateV1.Not(
                    NativeThemeEditorPredicateV1.BooleanEquals(
                        field = NativeThemePreferenceSchemaV1.bubbleAiBubbleLiquidGlass,
                        expected = true,
                    ),
                ),
                NativeThemeEditorPredicateV1.Not(
                    NativeThemeEditorPredicateV1.BooleanEquals(
                        field = NativeThemePreferenceSchemaV1.bubbleAiBubbleWaterGlass,
                        expected = true,
                    ),
                ),
            ),
        )
    private val userCustomFont =
        NativeThemeEditorPredicateV1.AllOf(
            listOf(
                bubbleStyle,
                NativeThemeEditorPredicateV1.BooleanEquals(
                    field = NativeThemePreferenceSchemaV1.bubbleUserUseCustomFont,
                    expected = true,
                ),
            ),
        )
    private val aiCustomFont =
        NativeThemeEditorPredicateV1.AllOf(
            listOf(
                bubbleStyle,
                NativeThemeEditorPredicateV1.BooleanEquals(
                    field = NativeThemePreferenceSchemaV1.bubbleAiUseCustomFont,
                    expected = true,
                ),
            ),
        )
    private val userSystemFont =
        NativeThemeEditorPredicateV1.AllOf(
            listOf(
                userCustomFont,
                NativeThemeEditorPredicateV1.StringEquals(
                    field = NativeThemePreferenceSchemaV1.bubbleUserFontType,
                    expected = NativeThemePreferenceOptionsV1.FONT_TYPE_SYSTEM,
                ),
            ),
        )
    private val userFileFont =
        NativeThemeEditorPredicateV1.AllOf(
            listOf(
                userCustomFont,
                NativeThemeEditorPredicateV1.StringEquals(
                    field = NativeThemePreferenceSchemaV1.bubbleUserFontType,
                    expected = NativeThemePreferenceOptionsV1.FONT_TYPE_FILE,
                ),
            ),
        )
    private val aiSystemFont =
        NativeThemeEditorPredicateV1.AllOf(
            listOf(
                aiCustomFont,
                NativeThemeEditorPredicateV1.StringEquals(
                    field = NativeThemePreferenceSchemaV1.bubbleAiFontType,
                    expected = NativeThemePreferenceOptionsV1.FONT_TYPE_SYSTEM,
                ),
            ),
        )
    private val aiFileFont =
        NativeThemeEditorPredicateV1.AllOf(
            listOf(
                aiCustomFont,
                NativeThemeEditorPredicateV1.StringEquals(
                    field = NativeThemePreferenceSchemaV1.bubbleAiFontType,
                    expected = NativeThemePreferenceOptionsV1.FONT_TYPE_FILE,
                ),
            ),
        )
    private val squareAvatar =
        NativeThemeEditorPredicateV1.StringEquals(
            field = NativeThemePreferenceSchemaV1.avatarShape,
            expected = NativeThemePreferenceOptionsV1.AVATAR_SHAPE_SQUARE,
        )

    val style =
        NativeThemeEditorGroupDefinitionV1(
            id = NativeThemeEditorGroupId("conversation.style"),
            title = NativeThemeEditorTextKey.CONVERSATION_STYLE,
            description = NativeThemeEditorTextKey.CONVERSATION_STYLE_DESCRIPTION,
            items =
                listOf(
                    NativeThemeStringChoiceDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.style.mode"),
                        title = NativeThemeEditorTextKey.CONVERSATION_STYLE,
                        description = null,
                        field = NativeThemePreferenceSchemaV1.chatStyle,
                        options =
                            listOf(
                                NativeThemeStringOptionDefinitionV1(
                                    value = NativeThemePreferenceOptionsV1.CHAT_STYLE_CURSOR,
                                    title = NativeThemeEditorTextKey.CONVERSATION_STYLE_CURSOR,
                                ),
                                NativeThemeStringOptionDefinitionV1(
                                    value = NativeThemePreferenceOptionsV1.CHAT_STYLE_BUBBLE,
                                    title = NativeThemeEditorTextKey.CONVERSATION_STYLE_BUBBLE,
                                ),
                            ),
                        presentation = NativeThemeChoicePresentation.SEGMENTED,
                    ),
                ),
        )

    val cursorAppearance =
        NativeThemeEditorGroupDefinitionV1(
            id = NativeThemeEditorGroupId("conversation.cursor.appearance"),
            title = NativeThemeEditorTextKey.CONVERSATION_CURSOR_APPEARANCE,
            visibleWhen = cursorStyle,
            items =
                listOf(
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.cursor.follow_theme"),
                        title = NativeThemeEditorTextKey.CONVERSATION_CURSOR_FOLLOW_THEME,
                        description = NativeThemeEditorTextKey.CONVERSATION_CURSOR_FOLLOW_THEME_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.cursorUserBubbleFollowTheme,
                    ),
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.cursor.liquid_glass"),
                        title = NativeThemeEditorTextKey.CONVERSATION_CURSOR_LIQUID_GLASS,
                        description = NativeThemeEditorTextKey.CONVERSATION_CURSOR_LIQUID_GLASS_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.cursorUserBubbleLiquidGlass,
                        advanced = true,
                    ),
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.cursor.water_glass"),
                        title = NativeThemeEditorTextKey.CONVERSATION_CURSOR_WATER_GLASS,
                        description = NativeThemeEditorTextKey.CONVERSATION_CURSOR_WATER_GLASS_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.cursorUserBubbleWaterGlass,
                        advanced = true,
                    ),
                    NativeThemeColorControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.cursor.user_bubble_color"),
                        title = NativeThemeEditorTextKey.COLOR_CURSOR_USER_BUBBLE,
                        description = null,
                        target = NativeThemeColorTargetV1.CURSOR_USER_BUBBLE,
                        visibleWhen = cursorUserBubbleColorVisible,
                    ),
                ),
        )

    val bubbleAppearance =
        NativeThemeEditorGroupDefinitionV1(
            id = NativeThemeEditorGroupId("conversation.bubble.appearance"),
            title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_APPEARANCE,
            visibleWhen = bubbleStyle,
            items =
                listOf(
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.bubble.show_avatar"),
                        title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_SHOW_AVATAR,
                        description = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_SHOW_AVATAR_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.bubbleShowAvatar,
                    ),
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.bubble.wide_layout"),
                        title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_WIDE_LAYOUT,
                        description = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_WIDE_LAYOUT_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.bubbleWideLayoutEnabled,
                    ),
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.bubble.user.liquid_glass"),
                        title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_USER_LIQUID_GLASS,
                        description = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_USER_LIQUID_GLASS_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.bubbleUserBubbleLiquidGlass,
                        advanced = true,
                    ),
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.bubble.user.water_glass"),
                        title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_USER_WATER_GLASS,
                        description = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_USER_WATER_GLASS_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.bubbleUserBubbleWaterGlass,
                        advanced = true,
                    ),
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.bubble.ai.liquid_glass"),
                        title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_AI_LIQUID_GLASS,
                        description = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_AI_LIQUID_GLASS_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.bubbleAiBubbleLiquidGlass,
                        advanced = true,
                    ),
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.bubble.ai.water_glass"),
                        title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_AI_WATER_GLASS,
                        description = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_AI_WATER_GLASS_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.bubbleAiBubbleWaterGlass,
                        advanced = true,
                    ),
                    NativeThemeStringChoiceDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.bubble.image.render_mode"),
                        title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_IMAGE_RENDER_MODE,
                        description = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_IMAGE_RENDER_MODE_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.bubbleImageRenderMode,
                        options =
                            listOf(
                                NativeThemeStringOptionDefinitionV1(
                                    value = NativeThemePreferenceOptionsV1.BUBBLE_IMAGE_RENDER_MODE_NINE_PATCH,
                                    title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_IMAGE_RENDER_MODE_NINE_PATCH,
                                ),
                                NativeThemeStringOptionDefinitionV1(
                                    value = NativeThemePreferenceOptionsV1.BUBBLE_IMAGE_RENDER_MODE_TILED_NINE_SLICE,
                                    title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_IMAGE_RENDER_MODE_TILED,
                                ),
                            ),
                        presentation = NativeThemeChoicePresentation.SEGMENTED,
                    ),
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.bubble.rounded_corners.user"),
                        title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_ROUNDED_CORNERS_USER,
                        description = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_ROUNDED_CORNERS_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.bubbleUserRoundedCornersEnabled,
                    ),
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.bubble.rounded_corners.ai"),
                        title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_ROUNDED_CORNERS_AI,
                        description = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_ROUNDED_CORNERS_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.bubbleAiRoundedCornersEnabled,
                    ),
                ),
        )

    val bubbleColors =
        NativeThemeEditorGroupDefinitionV1(
            id = NativeThemeEditorGroupId("conversation.bubble.colors"),
            title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_COLORS,
            visibleWhen = bubbleStyle,
            items =
                listOf(
                    NativeThemeColorControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.bubble.colors.user"),
                        title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_USER_COLOR,
                        description = null,
                        target = NativeThemeColorTargetV1.BUBBLE_USER_BUBBLE,
                    ),
                    NativeThemeColorControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.bubble.colors.ai"),
                        title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_AI_COLOR,
                        description = null,
                        target = NativeThemeColorTargetV1.BUBBLE_AI_BUBBLE,
                    ),
                    NativeThemeColorControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.bubble.text_colors.user"),
                        title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_USER_TEXT_COLOR,
                        description = null,
                        target = NativeThemeColorTargetV1.BUBBLE_USER_TEXT,
                        advanced = true,
                    ),
                    NativeThemeColorControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.bubble.text_colors.ai"),
                        title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_AI_TEXT_COLOR,
                        description = null,
                        target = NativeThemeColorTargetV1.BUBBLE_AI_TEXT,
                        advanced = true,
                    ),
                ),
        )

    private fun fontItems(
        prefix: String,
        title: NativeThemeEditorTextKey,
        useCustomFont: com.ai.assistance.operit.data.preferences.NativeThemeBooleanField,
        fontType: com.ai.assistance.operit.data.preferences.NativeThemeStringField,
        systemFontName: com.ai.assistance.operit.data.preferences.NativeThemeStringField,
        customFontPredicate: NativeThemeEditorPredicateV1,
        systemFontPredicate: NativeThemeEditorPredicateV1,
        fileFontPredicate: NativeThemeEditorPredicateV1,
        assetAction: NativeThemeAssetActionV1,
    ): NativeThemeEditorGroupDefinitionV1 =
        NativeThemeEditorGroupDefinitionV1(
            id = NativeThemeEditorGroupId("conversation.bubble.$prefix.font"),
            title = title,
            visibleWhen = bubbleStyle,
            items =
                listOf(
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.bubble.$prefix.font.custom"),
                        title = NativeThemeEditorTextKey.CONVERSATION_FONT_USE_CUSTOM,
                        description = NativeThemeEditorTextKey.CONVERSATION_FONT_USE_CUSTOM_DESCRIPTION,
                        field = useCustomFont,
                    ),
                    NativeThemeStringChoiceDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.bubble.$prefix.font.source"),
                        title = NativeThemeEditorTextKey.CONVERSATION_FONT_SOURCE,
                        description = null,
                        field = fontType,
                        options =
                            listOf(
                                NativeThemeStringOptionDefinitionV1(
                                    value = NativeThemePreferenceOptionsV1.FONT_TYPE_SYSTEM,
                                    title = NativeThemeEditorTextKey.CONVERSATION_FONT_SOURCE_SYSTEM,
                                ),
                                NativeThemeStringOptionDefinitionV1(
                                    value = NativeThemePreferenceOptionsV1.FONT_TYPE_FILE,
                                    title = NativeThemeEditorTextKey.CONVERSATION_FONT_SOURCE_FILE,
                                ),
                            ),
                        presentation = NativeThemeChoicePresentation.SEGMENTED,
                        visibleWhen = customFontPredicate,
                    ),
                    NativeThemeStringChoiceDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.bubble.$prefix.font.system"),
                        title = NativeThemeEditorTextKey.CONVERSATION_FONT_SYSTEM_NAME,
                        description = null,
                        field = systemFontName,
                        options =
                            listOf(
                                NativeThemeStringOptionDefinitionV1(
                                    value = NativeThemePreferenceOptionsV1.SYSTEM_FONT_DEFAULT,
                                    title = NativeThemeEditorTextKey.CONVERSATION_FONT_SYSTEM_DEFAULT,
                                ),
                                NativeThemeStringOptionDefinitionV1(
                                    value = NativeThemePreferenceOptionsV1.SYSTEM_FONT_SERIF,
                                    title = NativeThemeEditorTextKey.CONVERSATION_FONT_SYSTEM_SERIF,
                                ),
                                NativeThemeStringOptionDefinitionV1(
                                    value = NativeThemePreferenceOptionsV1.SYSTEM_FONT_SANS_SERIF,
                                    title = NativeThemeEditorTextKey.CONVERSATION_FONT_SYSTEM_SANS_SERIF,
                                ),
                                NativeThemeStringOptionDefinitionV1(
                                    value = NativeThemePreferenceOptionsV1.SYSTEM_FONT_MONOSPACE,
                                    title = NativeThemeEditorTextKey.CONVERSATION_FONT_SYSTEM_MONOSPACE,
                                ),
                                NativeThemeStringOptionDefinitionV1(
                                    value = NativeThemePreferenceOptionsV1.SYSTEM_FONT_CURSIVE,
                                    title = NativeThemeEditorTextKey.CONVERSATION_FONT_SYSTEM_CURSIVE,
                                ),
                            ),
                        presentation = NativeThemeChoicePresentation.RADIO,
                        visibleWhen = systemFontPredicate,
                    ),
                    NativeThemeAssetControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.bubble.$prefix.font.file"),
                        title = NativeThemeEditorTextKey.CONVERSATION_FONT_FILE,
                        description = NativeThemeEditorTextKey.CONVERSATION_FONT_FILE_DESCRIPTION,
                        action = assetAction,
                        selectLabel = NativeThemeEditorTextKey.CONVERSATION_FONT_SELECT_FILE,
                        clearLabel = NativeThemeEditorTextKey.CONVERSATION_FONT_CLEAR_FILE,
                        currentValueLabel = NativeThemeEditorTextKey.CONVERSATION_FONT_CURRENT_FILE,
                        visibleWhen = fileFontPredicate,
                    ),
                ),
        )

    val userFont =
        fontItems(
            prefix = "user",
            title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_USER_FONT,
            useCustomFont = NativeThemePreferenceSchemaV1.bubbleUserUseCustomFont,
            fontType = NativeThemePreferenceSchemaV1.bubbleUserFontType,
            systemFontName = NativeThemePreferenceSchemaV1.bubbleUserSystemFontName,
            customFontPredicate = userCustomFont,
            systemFontPredicate = userSystemFont,
            fileFontPredicate = userFileFont,
            assetAction = NativeThemeAssetActionV1.BUBBLE_USER_FONT,
        )

    val aiFont =
        fontItems(
            prefix = "ai",
            title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_AI_FONT,
            useCustomFont = NativeThemePreferenceSchemaV1.bubbleAiUseCustomFont,
            fontType = NativeThemePreferenceSchemaV1.bubbleAiFontType,
            systemFontName = NativeThemePreferenceSchemaV1.bubbleAiSystemFontName,
            customFontPredicate = aiCustomFont,
            systemFontPredicate = aiSystemFont,
            fileFontPredicate = aiFileFont,
            assetAction = NativeThemeAssetActionV1.BUBBLE_AI_FONT,
        )

    private fun imageItems(
        prefix: String,
        title: NativeThemeEditorTextKey,
        useImage: NativeThemeBooleanField = NativeThemePreferenceSchemaV1.bubbleUserUseImage,
        imageEnabled: NativeThemeEditorPredicateV1,
        imageControls: NativeThemeEditorPredicateV1,
        glassDisabled: NativeThemeEditorPredicateV1,
        assetAction: NativeThemeAssetActionV1,
        cropLeft: NativeThemeFloatField = NativeThemePreferenceSchemaV1.bubbleUserImageCropLeft,
        cropTop: NativeThemeFloatField = NativeThemePreferenceSchemaV1.bubbleUserImageCropTop,
        cropRight: NativeThemeFloatField = NativeThemePreferenceSchemaV1.bubbleUserImageCropRight,
        cropBottom: NativeThemeFloatField = NativeThemePreferenceSchemaV1.bubbleUserImageCropBottom,
        repeatXStart: NativeThemeFloatField = NativeThemePreferenceSchemaV1.bubbleUserImageRepeatStart,
        repeatXEnd: NativeThemeFloatField = NativeThemePreferenceSchemaV1.bubbleUserImageRepeatEnd,
        repeatYStart: NativeThemeFloatField = NativeThemePreferenceSchemaV1.bubbleUserImageRepeatYStart,
        repeatYEnd: NativeThemeFloatField = NativeThemePreferenceSchemaV1.bubbleUserImageRepeatYEnd,
        imageScale: NativeThemeFloatField = NativeThemePreferenceSchemaV1.bubbleUserImageScale,
        contentPaddingLeft: NativeThemeFloatField = NativeThemePreferenceSchemaV1.bubbleUserContentPaddingLeft,
        contentPaddingRight: NativeThemeFloatField = NativeThemePreferenceSchemaV1.bubbleUserContentPaddingRight,
    ): NativeThemeEditorGroupDefinitionV1 =
        NativeThemeEditorGroupDefinitionV1(
            id = NativeThemeEditorGroupId("conversation.bubble.$prefix.image"),
            title = title,
            visibleWhen = bubbleStyle,
            items =
                listOf(
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.bubble.$prefix.image.enabled"),
                        title = title,
                        description = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_IMAGE_DESCRIPTION,
                        field = useImage,
                        enabledWhen = glassDisabled,
                    ),
                    NativeThemeAssetControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.bubble.$prefix.image.asset"),
                        title = title,
                        description = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_IMAGE_DESCRIPTION,
                        action = assetAction,
                        selectLabel = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_PICK_IMAGE,
                        clearLabel = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_CLEAR_IMAGE,
                        valueStatusLabel = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_IMAGE_SELECTED,
                        visibleWhen = imageEnabled,
                    ),
                    NativeThemeFloatControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.bubble.$prefix.image.crop_left"),
                        title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_CROP_LEFT,
                        description = null,
                        field = cropLeft,
                        minimum = 0f,
                        maximum = 0.45f,
                        steps = 0,
                        format = NativeThemeFloatFormatV1.PERCENT_INTEGER,
                        visibleWhen = imageControls,
                        advanced = true,
                    ),
                    NativeThemeFloatControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.bubble.$prefix.image.crop_top"),
                        title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_CROP_TOP,
                        description = null,
                        field = cropTop,
                        minimum = 0f,
                        maximum = 0.45f,
                        steps = 0,
                        format = NativeThemeFloatFormatV1.PERCENT_INTEGER,
                        visibleWhen = imageControls,
                        advanced = true,
                    ),
                    NativeThemeFloatControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.bubble.$prefix.image.crop_right"),
                        title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_CROP_RIGHT,
                        description = null,
                        field = cropRight,
                        minimum = 0f,
                        maximum = 0.45f,
                        steps = 0,
                        format = NativeThemeFloatFormatV1.PERCENT_INTEGER,
                        visibleWhen = imageControls,
                        advanced = true,
                    ),
                    NativeThemeFloatControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.bubble.$prefix.image.crop_bottom"),
                        title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_CROP_BOTTOM,
                        description = null,
                        field = cropBottom,
                        minimum = 0f,
                        maximum = 0.45f,
                        steps = 0,
                        format = NativeThemeFloatFormatV1.PERCENT_INTEGER,
                        visibleWhen = imageControls,
                        advanced = true,
                    ),
                    NativeThemeFloatControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.bubble.$prefix.image.repeat_x_start"),
                        title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_REPEAT_X_START,
                        description = null,
                        field = repeatXStart,
                        minimum = 0.05f,
                        maximum = 0.9f,
                        steps = 0,
                        format = NativeThemeFloatFormatV1.PERCENT_INTEGER,
                        visibleWhen = imageControls,
                        advanced = true,
                    ),
                    NativeThemeFloatControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.bubble.$prefix.image.repeat_x_end"),
                        title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_REPEAT_X_END,
                        description = null,
                        field = repeatXEnd,
                        minimum = 0.05f,
                        maximum = 0.95f,
                        steps = 0,
                        format = NativeThemeFloatFormatV1.PERCENT_INTEGER,
                        visibleWhen = imageControls,
                        advanced = true,
                    ),
                    NativeThemeFloatControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.bubble.$prefix.image.repeat_y_start"),
                        title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_REPEAT_Y_START,
                        description = null,
                        field = repeatYStart,
                        minimum = 0.05f,
                        maximum = 0.9f,
                        steps = 0,
                        format = NativeThemeFloatFormatV1.PERCENT_INTEGER,
                        visibleWhen = imageControls,
                        advanced = true,
                    ),
                    NativeThemeFloatControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.bubble.$prefix.image.repeat_y_end"),
                        title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_REPEAT_Y_END,
                        description = null,
                        field = repeatYEnd,
                        minimum = 0.05f,
                        maximum = 0.95f,
                        steps = 0,
                        format = NativeThemeFloatFormatV1.PERCENT_INTEGER,
                        visibleWhen = imageControls,
                        advanced = true,
                    ),
                    NativeThemeFloatControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.bubble.$prefix.image.scale"),
                        title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_IMAGE_SCALE,
                        description = null,
                        field = imageScale,
                        minimum = 0.2f,
                        maximum = 3f,
                        steps = 0,
                        format = NativeThemeFloatFormatV1.PERCENT_INTEGER,
                        visibleWhen = imageControls,
                        advanced = true,
                    ),
                    NativeThemeFloatControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.bubble.$prefix.padding.left"),
                        title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_PADDING_LEFT,
                        description = null,
                        field = contentPaddingLeft,
                        minimum = 0f,
                        maximum = 32f,
                        steps = 32,
                        format = NativeThemeFloatFormatV1.INTEGER,
                        visibleWhen = imageControls,
                        advanced = true,
                    ),
                    NativeThemeFloatControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.bubble.$prefix.padding.right"),
                        title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_PADDING_RIGHT,
                        description = null,
                        field = contentPaddingRight,
                        minimum = 0f,
                        maximum = 32f,
                        steps = 32,
                        format = NativeThemeFloatFormatV1.INTEGER,
                        visibleWhen = imageControls,
                        advanced = true,
                    ),
                ),
        )

    val userImage =
        imageItems(
            prefix = "user",
            title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_USER_IMAGE,
            imageEnabled = userBubbleImageEnabled,
            imageControls = userBubbleImageEnabled,
            glassDisabled = userBubbleGlassDisabled,
            assetAction = NativeThemeAssetActionV1.BUBBLE_USER_IMAGE,
        )

    val aiImage =
        imageItems(
            prefix = "ai",
            title = NativeThemeEditorTextKey.CONVERSATION_BUBBLE_AI_IMAGE,
            useImage = NativeThemePreferenceSchemaV1.bubbleAiUseImage,
            imageEnabled = aiBubbleImageEnabled,
            imageControls = aiBubbleImageEnabled,
            glassDisabled = aiBubbleGlassDisabled,
            assetAction = NativeThemeAssetActionV1.BUBBLE_AI_IMAGE,
            cropLeft = NativeThemePreferenceSchemaV1.bubbleAiImageCropLeft,
            cropTop = NativeThemePreferenceSchemaV1.bubbleAiImageCropTop,
            cropRight = NativeThemePreferenceSchemaV1.bubbleAiImageCropRight,
            cropBottom = NativeThemePreferenceSchemaV1.bubbleAiImageCropBottom,
            repeatXStart = NativeThemePreferenceSchemaV1.bubbleAiImageRepeatStart,
            repeatXEnd = NativeThemePreferenceSchemaV1.bubbleAiImageRepeatEnd,
            repeatYStart = NativeThemePreferenceSchemaV1.bubbleAiImageRepeatYStart,
            repeatYEnd = NativeThemePreferenceSchemaV1.bubbleAiImageRepeatYEnd,
            imageScale = NativeThemePreferenceSchemaV1.bubbleAiImageScale,
            contentPaddingLeft = NativeThemePreferenceSchemaV1.bubbleAiContentPaddingLeft,
            contentPaddingRight = NativeThemePreferenceSchemaV1.bubbleAiContentPaddingRight,
        )

    val avatars =
        NativeThemeEditorGroupDefinitionV1(
            id = NativeThemeEditorGroupId("conversation.avatars"),
            title = NativeThemeEditorTextKey.CONVERSATION_AVATAR,
            description = NativeThemeEditorTextKey.CONVERSATION_AVATAR_DESCRIPTION,
            items =
                listOf(
                    NativeThemeAssetControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.avatar.user"),
                        title = NativeThemeEditorTextKey.CONVERSATION_USER_AVATAR,
                        description = null,
                        action = NativeThemeAssetActionV1.USER_AVATAR,
                        selectLabel = NativeThemeEditorTextKey.CONVERSATION_USER_AVATAR,
                        clearLabel = NativeThemeEditorTextKey.CONVERSATION_AVATAR_RESET,
                        valueStatusLabel = NativeThemeEditorTextKey.CONVERSATION_AVATAR_SELECTED,
                    ),
                    NativeThemeAssetControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.avatar.ai"),
                        title = NativeThemeEditorTextKey.CONVERSATION_AI_AVATAR,
                        description = null,
                        action = NativeThemeAssetActionV1.AI_AVATAR,
                        selectLabel = NativeThemeEditorTextKey.CONVERSATION_AI_AVATAR,
                        clearLabel = NativeThemeEditorTextKey.CONVERSATION_AVATAR_RESET,
                        valueStatusLabel = NativeThemeEditorTextKey.CONVERSATION_AVATAR_SELECTED,
                    ),
                    NativeThemeStringChoiceDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.avatar.shape"),
                        title = NativeThemeEditorTextKey.CONVERSATION_AVATAR_SHAPE,
                        description = null,
                        field = NativeThemePreferenceSchemaV1.avatarShape,
                        options =
                            listOf(
                                NativeThemeStringOptionDefinitionV1(
                                    value = NativeThemePreferenceOptionsV1.AVATAR_SHAPE_CIRCLE,
                                    title = NativeThemeEditorTextKey.CONVERSATION_AVATAR_SHAPE_CIRCLE,
                                ),
                                NativeThemeStringOptionDefinitionV1(
                                    value = NativeThemePreferenceOptionsV1.AVATAR_SHAPE_SQUARE,
                                    title = NativeThemeEditorTextKey.CONVERSATION_AVATAR_SHAPE_SQUARE,
                                ),
                            ),
                        presentation = NativeThemeChoicePresentation.SEGMENTED,
                    ),
                    NativeThemeFloatControlDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.avatar.corner_radius"),
                        title = NativeThemeEditorTextKey.CONVERSATION_AVATAR_CORNER_RADIUS,
                        description = null,
                        field = NativeThemePreferenceSchemaV1.avatarCornerRadius,
                        minimum = 0f,
                        maximum = 16f,
                        steps = 15,
                        format = NativeThemeFloatFormatV1.INTEGER,
                        visibleWhen = squareAvatar,
                    ),
                ),
        )

    val targetMetadata =
        NativeThemeEditorGroupDefinitionV1(
            id = NativeThemeEditorGroupId("conversation.target_metadata"),
            title = NativeThemeEditorTextKey.CONVERSATION_TARGET_METADATA,
            items =
                listOf(
                    NativeThemeTextInputDefinitionV1(
                        id = NativeThemeEditorItemId("conversation.target_metadata.chat_title"),
                        title = NativeThemeEditorTextKey.CONVERSATION_CHAT_TITLE,
                        description = null,
                        field = NativeThemePreferenceSchemaV1.customChatTitle,
                        placeholder = NativeThemeEditorTextKey.CONVERSATION_CHAT_TITLE_PLACEHOLDER,
                    ),
                ),
        )

    val section =
        NativeThemeEditorSectionDefinitionV1(
            id = NativeThemeEditorSectionId("conversation"),
            title = NativeThemeEditorTextKey.CONVERSATION,
            groups =
                listOf(
                    style,
                    cursorAppearance,
                    bubbleAppearance,
                    bubbleColors,
                    userFont,
                    aiFont,
                    userImage,
                    aiImage,
                    avatars,
                    targetMetadata,
                ),
        )
}
