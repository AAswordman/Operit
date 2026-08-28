package com.ai.assistance.operit.ui.features.settings.theme.editor.contract

import com.ai.assistance.operit.data.preferences.NativeThemePreferenceOptionsV1
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceSchemaV1

internal object NativeThemeEditorBackgroundDefinitionV1 {
    private val mediaEnabled =
        NativeThemeEditorPredicateV1.BooleanEquals(
            field = NativeThemePreferenceSchemaV1.useBackgroundImage,
            expected = true,
        )
    private val videoMediaSelected =
        NativeThemeEditorPredicateV1.AllOf(
            listOf(
                mediaEnabled,
                NativeThemeEditorPredicateV1.StringEquals(
                    field = NativeThemePreferenceSchemaV1.backgroundMediaType,
                    expected = NativeThemePreferenceOptionsV1.MEDIA_TYPE_VIDEO,
                ),
                NativeThemeEditorPredicateV1.StringPresent(
                    field = NativeThemePreferenceSchemaV1.backgroundImageUri,
                ),
            )
            )
    private val imageMediaSelected =
        NativeThemeEditorPredicateV1.AllOf(
            listOf(
                mediaEnabled,
                NativeThemeEditorPredicateV1.StringEquals(
                    field = NativeThemePreferenceSchemaV1.backgroundMediaType,
                    expected = NativeThemePreferenceOptionsV1.MEDIA_TYPE_IMAGE,
                ),
            )
        )
    private val blurredMedia =
        NativeThemeEditorPredicateV1.AllOf(
            listOf(
                imageMediaSelected,
                NativeThemeEditorPredicateV1.BooleanEquals(
                    field = NativeThemePreferenceSchemaV1.useBackgroundBlur,
                    expected = true,
                ),
            )
        )

    val media =
        NativeThemeEditorGroupDefinitionV1(
            id = NativeThemeEditorGroupId("background.media"),
            title = NativeThemeEditorTextKey.BACKGROUND_MEDIA,
            items =
                listOf(
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("background.media.use"),
                        title = NativeThemeEditorTextKey.USE_BACKGROUND_MEDIA,
                        description = NativeThemeEditorTextKey.USE_BACKGROUND_MEDIA_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.useBackgroundImage,
                    ),
                    NativeThemeStringChoiceDefinitionV1(
                        id = NativeThemeEditorItemId("background.media.type"),
                        title = NativeThemeEditorTextKey.MEDIA_TYPE,
                        description = null,
                        field = NativeThemePreferenceSchemaV1.backgroundMediaType,
                        options =
                            listOf(
                                NativeThemeStringOptionDefinitionV1(
                                    NativeThemePreferenceOptionsV1.MEDIA_TYPE_IMAGE,
                                    NativeThemeEditorTextKey.MEDIA_TYPE_IMAGE,
                                ),
                                NativeThemeStringOptionDefinitionV1(
                                    NativeThemePreferenceOptionsV1.MEDIA_TYPE_VIDEO,
                                    NativeThemeEditorTextKey.MEDIA_TYPE_VIDEO,
                                ),
                            ),
                        presentation = NativeThemeChoicePresentation.SEGMENTED,
                        visibleWhen = mediaEnabled,
                    ),
                    NativeThemeAssetControlDefinitionV1(
                        id = NativeThemeEditorItemId("background.media.asset"),
                        title = NativeThemeEditorTextKey.BACKGROUND_ASSET,
                        description = NativeThemeEditorTextKey.BACKGROUND_ASSET_DESCRIPTION,
                        action = NativeThemeAssetActionV1.BACKGROUND_MEDIA,
                        selectLabel = NativeThemeEditorTextKey.SELECT_BACKGROUND_IMAGE,
                        selectionField = NativeThemePreferenceSchemaV1.backgroundMediaType,
                        selectLabelsByStringValue =
                            mapOf(
                                NativeThemePreferenceOptionsV1.MEDIA_TYPE_IMAGE to
                                    NativeThemeEditorTextKey.SELECT_BACKGROUND_IMAGE,
                                NativeThemePreferenceOptionsV1.MEDIA_TYPE_VIDEO to
                                    NativeThemeEditorTextKey.SELECT_BACKGROUND_VIDEO,
                            ),
                        visibleWhen = mediaEnabled,
                    ),
                    NativeThemeFloatControlDefinitionV1(
                        id = NativeThemeEditorItemId("background.opacity"),
                        title = NativeThemeEditorTextKey.BACKGROUND_OPACITY,
                        description = null,
                        field = NativeThemePreferenceSchemaV1.backgroundImageOpacity,
                        minimum = 0.1f,
                        maximum = 1f,
                        steps = 0,
                        format = NativeThemeFloatFormatV1.PERCENT_INTEGER,
                        commitPolicy = NativeThemeFloatCommitPolicyV1.ON_VALUE_CHANGE_FINISHED,
                        visibleWhen = mediaEnabled,
                    ),
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("background.blur"),
                        title = NativeThemeEditorTextKey.BACKGROUND_BLUR,
                        description = NativeThemeEditorTextKey.BACKGROUND_BLUR_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.useBackgroundBlur,
                        visibleWhen = imageMediaSelected,
                    ),
                    NativeThemeFloatControlDefinitionV1(
                        id = NativeThemeEditorItemId("background.blur.radius"),
                        title = NativeThemeEditorTextKey.BACKGROUND_BLUR_RADIUS,
                        description = null,
                        field = NativeThemePreferenceSchemaV1.backgroundBlurRadius,
                        minimum = 1f,
                        maximum = 30f,
                        steps = 0,
                        format = NativeThemeFloatFormatV1.INTEGER,
                        commitPolicy = NativeThemeFloatCommitPolicyV1.ON_VALUE_CHANGE_FINISHED,
                        visibleWhen = blurredMedia,
                    ),
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("background.video.muted"),
                        title = NativeThemeEditorTextKey.VIDEO_MUTED,
                        description = null,
                        field = NativeThemePreferenceSchemaV1.videoBackgroundMuted,
                        visibleWhen = videoMediaSelected,
                    ),
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("background.video.loop"),
                        title = NativeThemeEditorTextKey.VIDEO_LOOP,
                        description = null,
                        field = NativeThemePreferenceSchemaV1.videoBackgroundLoop,
                        visibleWhen = videoMediaSelected,
                    ),
                ),
        )

    val section =
        NativeThemeEditorSectionDefinitionV1(
            id = NativeThemeEditorSectionId("background"),
            title = NativeThemeEditorTextKey.BACKGROUND,
            groups = listOf(media),
        )
}
