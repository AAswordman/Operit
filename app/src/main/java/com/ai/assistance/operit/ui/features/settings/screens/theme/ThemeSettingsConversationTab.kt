package com.ai.assistance.operit.ui.features.settings.screens.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceOptionsV1
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceSchemaV1
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.ui.features.chat.components.style.bubble.BubbleImageStyleConfig
import com.ai.assistance.operit.ui.features.chat.components.style.bubble.BubbleStyleChatMessage
import com.ai.assistance.operit.ui.features.chat.components.style.cursor.CursorStyleChatMessage
import com.ai.assistance.operit.ui.features.settings.components.ColorPickerDialog
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeAssetActionV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeColorControlDefinitionV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeColorTargetV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeEditorDefinitionV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeEditorValueChangeV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeFloatCommitPolicyV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.applyNativeThemeBooleanControlV1
import com.ai.assistance.operit.ui.theme.getTextColorForBackground
import kotlinx.coroutines.launch

private data class NativeThemeConversationColorPickerRequestV1(
    val definition: NativeThemeColorControlDefinitionV1,
    val initialColor: Int,
)

@Composable
internal fun ThemeSettingsConversationTab(
    shared: ThemeSettingsShared,
) {
    val editorSession = shared.editorSession
    val editorDocument by editorSession.document.collectAsState()
    val values = editorDocument.draft
    val recentColors by editorSession.recentColorsFlow.collectAsState(initial = emptyList())
    var colorPickerRequest by remember {
        mutableStateOf<NativeThemeConversationColorPickerRequestV1?>(null)
    }
    val runtime =
        rememberThemeSettingsConversationRuntime(
            state =
                ThemeSettingsConversationRuntimeState(
                    context = shared.context,
                    scope = shared.scope,
                    editorSession = editorSession,
                ),
        )
    val bubbleFontPicker = rememberBubbleFontPicker(shared)

    fun onValueChanged(change: NativeThemeEditorValueChangeV1) {
        when (change) {
            is NativeThemeEditorValueChangeV1.BooleanChanged ->
                editorSession.update { current ->
                    applyNativeThemeBooleanControlV1(current, change.definition, change.value)
                }

            is NativeThemeEditorValueChangeV1.StringChanged ->
                editorSession.setString(change.definition.field, change.value)

            is NativeThemeEditorValueChangeV1.TextChanged ->
                editorSession.setOptionalString(change.definition.field, change.value)

            is NativeThemeEditorValueChangeV1.FloatChanged -> {
                if (
                    change.definition.commitPolicy == NativeThemeFloatCommitPolicyV1.ON_VALUE_CHANGE_FINISHED &&
                        !change.finished
                ) {
                    return
                }
                editorSession.update { current ->
                    current.withFloat(
                        change.definition.field,
                        clampConversationFloat(current, change.definition.field.name, change.value),
                    )
                }
            }
        }
    }

    fun onAssetRequested(action: NativeThemeAssetActionV1) {
        when (action) {
            NativeThemeAssetActionV1.BUBBLE_USER_FONT -> bubbleFontPicker.onPickBubbleUserFont()
            NativeThemeAssetActionV1.BUBBLE_AI_FONT -> bubbleFontPicker.onPickBubbleAiFont()
            NativeThemeAssetActionV1.BUBBLE_USER_IMAGE -> runtime.onPickBubbleUserImage()
            NativeThemeAssetActionV1.BUBBLE_AI_IMAGE -> runtime.onPickBubbleAiImage()
            NativeThemeAssetActionV1.USER_AVATAR -> {
                runtime.onAvatarPickerModeChange("user")
                runtime.avatarImagePicker.launch("image/*")
            }
            NativeThemeAssetActionV1.AI_AVATAR -> {
                runtime.onAvatarPickerModeChange("ai")
                runtime.avatarImagePicker.launch("image/*")
            }
            NativeThemeAssetActionV1.APP_FONT,
            NativeThemeAssetActionV1.BACKGROUND_MEDIA ->
                error("Unsupported Conversation asset action: $action")
        }
    }

    fun onAssetCleared(action: NativeThemeAssetActionV1) {
        when (action) {
            NativeThemeAssetActionV1.BUBBLE_USER_IMAGE -> runtime.onClearBubbleUserImage()
            NativeThemeAssetActionV1.BUBBLE_AI_IMAGE -> runtime.onClearBubbleAiImage()
            NativeThemeAssetActionV1.BUBBLE_USER_FONT,
            NativeThemeAssetActionV1.BUBBLE_AI_FONT,
            NativeThemeAssetActionV1.USER_AVATAR,
            NativeThemeAssetActionV1.AI_AVATAR -> editorSession.setOptionalString(action.field, null)
            NativeThemeAssetActionV1.APP_FONT,
            NativeThemeAssetActionV1.BACKGROUND_MEDIA ->
                error("Unsupported Conversation asset action: $action")
        }
    }

    val visibleGroups =
        NativeThemeEditorDefinitionV1.conversation.groups.filter { group -> group.isVisible(values) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        visibleGroups.forEachIndexed { index, group ->
            if (index > 0) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
            NativeThemeEditorGroupV1(
                definition = group,
                values = values,
                editorSession = editorSession,
                onColorRequested = { definition, color ->
                    colorPickerRequest =
                        NativeThemeConversationColorPickerRequestV1(
                            definition = definition,
                            initialColor = color,
                        )
                },
                onAssetRequested = { definition -> onAssetRequested(definition.action) },
                onAssetCleared = { definition -> onAssetCleared(definition.action) },
                onValueChanged = ::onValueChanged,
            )
        }

        ThemeSettingsConversationPreview(values = values)
    }

    colorPickerRequest?.let { request ->
        key(request.definition.target) {
            ColorPickerDialog(
                initialColor = request.initialColor,
                title = request.definition.target.pickerTitle.localizedText(),
                recentColors = recentColors,
                onColorSelected = { color ->
                    editorSession.setInt(request.definition.target.field, color)
                    shared.scope.launch { editorSession.addRecentColor(color) }
                },
                onDismiss = { colorPickerRequest = null },
            )
        }
    }
}

private fun clampConversationFloat(
    values: ThemePreferenceValues,
    fieldName: String,
    value: Float,
): Float =
    when (fieldName) {
        NativeThemePreferenceSchemaV1.bubbleUserImageCropLeft.name,
        NativeThemePreferenceSchemaV1.bubbleUserImageCropTop.name,
        NativeThemePreferenceSchemaV1.bubbleUserImageCropRight.name,
        NativeThemePreferenceSchemaV1.bubbleUserImageCropBottom.name,
        NativeThemePreferenceSchemaV1.bubbleAiImageCropLeft.name,
        NativeThemePreferenceSchemaV1.bubbleAiImageCropTop.name,
        NativeThemePreferenceSchemaV1.bubbleAiImageCropRight.name,
        NativeThemePreferenceSchemaV1.bubbleAiImageCropBottom.name -> value.coerceIn(0f, 0.45f)

        NativeThemePreferenceSchemaV1.bubbleUserImageRepeatStart.name ->
            value.coerceIn(
                0.05f,
                (values.requiredFloat(NativeThemePreferenceSchemaV1.bubbleUserImageRepeatEnd) - 0.01f)
                    .coerceAtLeast(0.05f),
            )
        NativeThemePreferenceSchemaV1.bubbleUserImageRepeatEnd.name ->
            value.coerceIn(
                (values.requiredFloat(NativeThemePreferenceSchemaV1.bubbleUserImageRepeatStart) + 0.01f)
                    .coerceAtMost(0.95f),
                0.95f,
            )
        NativeThemePreferenceSchemaV1.bubbleUserImageRepeatYStart.name ->
            value.coerceIn(
                0.05f,
                (values.requiredFloat(NativeThemePreferenceSchemaV1.bubbleUserImageRepeatYEnd) - 0.01f)
                    .coerceAtLeast(0.05f),
            )
        NativeThemePreferenceSchemaV1.bubbleUserImageRepeatYEnd.name ->
            value.coerceIn(
                (values.requiredFloat(NativeThemePreferenceSchemaV1.bubbleUserImageRepeatYStart) + 0.01f)
                    .coerceAtMost(0.95f),
                0.95f,
            )
        NativeThemePreferenceSchemaV1.bubbleAiImageRepeatStart.name ->
            value.coerceIn(
                0.05f,
                (values.requiredFloat(NativeThemePreferenceSchemaV1.bubbleAiImageRepeatEnd) - 0.01f)
                    .coerceAtLeast(0.05f),
            )
        NativeThemePreferenceSchemaV1.bubbleAiImageRepeatEnd.name ->
            value.coerceIn(
                (values.requiredFloat(NativeThemePreferenceSchemaV1.bubbleAiImageRepeatStart) + 0.01f)
                    .coerceAtMost(0.95f),
                0.95f,
            )
        NativeThemePreferenceSchemaV1.bubbleAiImageRepeatYStart.name ->
            value.coerceIn(
                0.05f,
                (values.requiredFloat(NativeThemePreferenceSchemaV1.bubbleAiImageRepeatYEnd) - 0.01f)
                    .coerceAtLeast(0.05f),
            )
        NativeThemePreferenceSchemaV1.bubbleAiImageRepeatYEnd.name ->
            value.coerceIn(
                (values.requiredFloat(NativeThemePreferenceSchemaV1.bubbleAiImageRepeatYStart) + 0.01f)
                    .coerceAtMost(0.95f),
                0.95f,
            )
        NativeThemePreferenceSchemaV1.bubbleUserImageScale.name,
        NativeThemePreferenceSchemaV1.bubbleAiImageScale.name -> value.coerceIn(0.2f, 3f)
        NativeThemePreferenceSchemaV1.bubbleUserContentPaddingLeft.name,
        NativeThemePreferenceSchemaV1.bubbleUserContentPaddingRight.name,
        NativeThemePreferenceSchemaV1.bubbleAiContentPaddingLeft.name,
        NativeThemePreferenceSchemaV1.bubbleAiContentPaddingRight.name -> value.coerceIn(0f, 32f)
        NativeThemePreferenceSchemaV1.avatarCornerRadius.name -> value.coerceIn(0f, 16f)
        else -> error("Unsupported Conversation float field: $fieldName")
    }

@Composable
private fun ThemeSettingsConversationPreview(
    values: ThemePreferenceValues,
) {
    NativeThemeEditorPreviewTheme(values = values) {
        val userBubbleColor =
            Color(
                NativeThemeColorTargetV1.BUBBLE_USER_BUBBLE.displayColor(values),
            )
        val aiBubbleColor =
            Color(
                NativeThemeColorTargetV1.BUBBLE_AI_BUBBLE.displayColor(values),
            )
        val cursorUserBubbleColor =
            if (values.requiredBoolean(NativeThemePreferenceSchemaV1.cursorUserBubbleFollowTheme)) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                Color(NativeThemeColorTargetV1.CURSOR_USER_BUBBLE.displayColor(values))
            }
        val userTextColor =
            Color(
                NativeThemeColorTargetV1.BUBBLE_USER_TEXT.displayColor(values),
            )
        val aiTextColor =
            Color(
                NativeThemeColorTargetV1.BUBBLE_AI_TEXT.displayColor(values),
            )
        val userPreviewText = stringResource(R.string.chat_style_preview_user_message)
        val aiPreviewText = stringResource(R.string.chat_style_preview_ai_message)
        val userMessage =
            remember(userPreviewText) {
                ChatMessage(
                    sender = "user",
                    content = userPreviewText,
                )
            }
        val aiMessage =
            remember(aiPreviewText) {
                ChatMessage(
                    sender = "ai",
                    content = aiPreviewText,
                )
            }
        val chatStyle = values.requiredString(NativeThemePreferenceSchemaV1.chatStyle)

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.chat_style_preview_title),
                style = MaterialTheme.typography.titleMedium,
            )
            when (chatStyle) {
                NativeThemePreferenceOptionsV1.CHAT_STYLE_CURSOR -> {
                    CursorStyleChatMessage(
                        message = userMessage,
                        userMessageColor = cursorUserBubbleColor,
                        userMessageLiquidGlassEnabled =
                            values.requiredBoolean(NativeThemePreferenceSchemaV1.cursorUserBubbleLiquidGlass),
                        userMessageWaterGlassEnabled =
                            values.requiredBoolean(NativeThemePreferenceSchemaV1.cursorUserBubbleWaterGlass),
                        aiMessageColor = MaterialTheme.colorScheme.surface,
                        userTextColor = getTextColorForBackground(cursorUserBubbleColor),
                        aiTextColor = MaterialTheme.colorScheme.onSurface,
                        systemMessageColor = MaterialTheme.colorScheme.surfaceVariant,
                        systemTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        thinkingBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                        thinkingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        enableDialogs = false,
                    )
                    CursorStyleChatMessage(
                        message = aiMessage,
                        userMessageColor = cursorUserBubbleColor,
                        userMessageLiquidGlassEnabled =
                            values.requiredBoolean(NativeThemePreferenceSchemaV1.cursorUserBubbleLiquidGlass),
                        userMessageWaterGlassEnabled =
                            values.requiredBoolean(NativeThemePreferenceSchemaV1.cursorUserBubbleWaterGlass),
                        aiMessageColor = MaterialTheme.colorScheme.surface,
                        userTextColor = getTextColorForBackground(cursorUserBubbleColor),
                        aiTextColor = MaterialTheme.colorScheme.onSurface,
                        systemMessageColor = MaterialTheme.colorScheme.surfaceVariant,
                        systemTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        thinkingBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                        thinkingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        enableDialogs = false,
                    )
                }
                NativeThemePreferenceOptionsV1.CHAT_STYLE_BUBBLE -> {
                    BubbleStyleChatMessage(
                        message = userMessage,
                        userMessageColor = userBubbleColor,
                        aiMessageColor = aiBubbleColor,
                        userTextColor = userTextColor,
                        aiTextColor = aiTextColor,
                        systemMessageColor = MaterialTheme.colorScheme.surfaceVariant,
                        systemTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        userMessageLiquidGlassEnabled =
                            values.requiredBoolean(NativeThemePreferenceSchemaV1.bubbleUserBubbleLiquidGlass),
                        userMessageWaterGlassEnabled =
                            values.requiredBoolean(NativeThemePreferenceSchemaV1.bubbleUserBubbleWaterGlass),
                        aiMessageLiquidGlassEnabled =
                            values.requiredBoolean(NativeThemePreferenceSchemaV1.bubbleAiBubbleLiquidGlass),
                        aiMessageWaterGlassEnabled =
                            values.requiredBoolean(NativeThemePreferenceSchemaV1.bubbleAiBubbleWaterGlass),
                        userBubbleImageStyle = values.bubbleImageStyle(user = true),
                        aiBubbleImageStyle = values.bubbleImageStyle(user = false),
                        bubbleUserRoundedCornersEnabled =
                            values.requiredBoolean(NativeThemePreferenceSchemaV1.bubbleUserRoundedCornersEnabled),
                        bubbleAiRoundedCornersEnabled =
                            values.requiredBoolean(NativeThemePreferenceSchemaV1.bubbleAiRoundedCornersEnabled),
                        bubbleUserContentPaddingLeft =
                            values.requiredFloat(NativeThemePreferenceSchemaV1.bubbleUserContentPaddingLeft),
                        bubbleUserContentPaddingRight =
                            values.requiredFloat(NativeThemePreferenceSchemaV1.bubbleUserContentPaddingRight),
                        bubbleAiContentPaddingLeft =
                            values.requiredFloat(NativeThemePreferenceSchemaV1.bubbleAiContentPaddingLeft),
                        bubbleAiContentPaddingRight =
                            values.requiredFloat(NativeThemePreferenceSchemaV1.bubbleAiContentPaddingRight),
                        enableDialogs = false,
                    )
                    BubbleStyleChatMessage(
                        message = aiMessage,
                        userMessageColor = userBubbleColor,
                        aiMessageColor = aiBubbleColor,
                        userTextColor = userTextColor,
                        aiTextColor = aiTextColor,
                        systemMessageColor = MaterialTheme.colorScheme.surfaceVariant,
                        systemTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        userMessageLiquidGlassEnabled =
                            values.requiredBoolean(NativeThemePreferenceSchemaV1.bubbleUserBubbleLiquidGlass),
                        userMessageWaterGlassEnabled =
                            values.requiredBoolean(NativeThemePreferenceSchemaV1.bubbleUserBubbleWaterGlass),
                        aiMessageLiquidGlassEnabled =
                            values.requiredBoolean(NativeThemePreferenceSchemaV1.bubbleAiBubbleLiquidGlass),
                        aiMessageWaterGlassEnabled =
                            values.requiredBoolean(NativeThemePreferenceSchemaV1.bubbleAiBubbleWaterGlass),
                        userBubbleImageStyle = values.bubbleImageStyle(user = true),
                        aiBubbleImageStyle = values.bubbleImageStyle(user = false),
                        bubbleUserRoundedCornersEnabled =
                            values.requiredBoolean(NativeThemePreferenceSchemaV1.bubbleUserRoundedCornersEnabled),
                        bubbleAiRoundedCornersEnabled =
                            values.requiredBoolean(NativeThemePreferenceSchemaV1.bubbleAiRoundedCornersEnabled),
                        bubbleUserContentPaddingLeft =
                            values.requiredFloat(NativeThemePreferenceSchemaV1.bubbleUserContentPaddingLeft),
                        bubbleUserContentPaddingRight =
                            values.requiredFloat(NativeThemePreferenceSchemaV1.bubbleUserContentPaddingRight),
                        bubbleAiContentPaddingLeft =
                            values.requiredFloat(NativeThemePreferenceSchemaV1.bubbleAiContentPaddingLeft),
                        bubbleAiContentPaddingRight =
                            values.requiredFloat(NativeThemePreferenceSchemaV1.bubbleAiContentPaddingRight),
                        enableDialogs = false,
                    )
                }
                else -> error("Unsupported Conversation chat style: $chatStyle")
            }
        }
    }
}

private fun ThemePreferenceValues.bubbleImageStyle(user: Boolean): BubbleImageStyleConfig? {
    val useImage =
        requiredBoolean(
            if (user) {
                NativeThemePreferenceSchemaV1.bubbleUserUseImage
            } else {
                NativeThemePreferenceSchemaV1.bubbleAiUseImage
            },
        )
    val liquidGlass =
        requiredBoolean(
            if (user) {
                NativeThemePreferenceSchemaV1.bubbleUserBubbleLiquidGlass
            } else {
                NativeThemePreferenceSchemaV1.bubbleAiBubbleLiquidGlass
            },
        )
    val waterGlass =
        requiredBoolean(
            if (user) {
                NativeThemePreferenceSchemaV1.bubbleUserBubbleWaterGlass
            } else {
                NativeThemePreferenceSchemaV1.bubbleAiBubbleWaterGlass
            },
        )
    val imageUri =
        string(
            if (user) {
                NativeThemePreferenceSchemaV1.bubbleUserImageUri
            } else {
                NativeThemePreferenceSchemaV1.bubbleAiImageUri
            },
        ) ?: return null
    if (!useImage || liquidGlass || waterGlass || imageUri.isBlank()) return null

    return BubbleImageStyleConfig(
        imageUri = imageUri,
        cropLeftRatio =
            requiredFloat(
                if (user) {
                    NativeThemePreferenceSchemaV1.bubbleUserImageCropLeft
                } else {
                    NativeThemePreferenceSchemaV1.bubbleAiImageCropLeft
                },
            ),
        cropTopRatio =
            requiredFloat(
                if (user) {
                    NativeThemePreferenceSchemaV1.bubbleUserImageCropTop
                } else {
                    NativeThemePreferenceSchemaV1.bubbleAiImageCropTop
                },
            ),
        cropRightRatio =
            requiredFloat(
                if (user) {
                    NativeThemePreferenceSchemaV1.bubbleUserImageCropRight
                } else {
                    NativeThemePreferenceSchemaV1.bubbleAiImageCropRight
                },
            ),
        cropBottomRatio =
            requiredFloat(
                if (user) {
                    NativeThemePreferenceSchemaV1.bubbleUserImageCropBottom
                } else {
                    NativeThemePreferenceSchemaV1.bubbleAiImageCropBottom
                },
            ),
        repeatXStartRatio =
            requiredFloat(
                if (user) {
                    NativeThemePreferenceSchemaV1.bubbleUserImageRepeatStart
                } else {
                    NativeThemePreferenceSchemaV1.bubbleAiImageRepeatStart
                },
            ),
        repeatXEndRatio =
            requiredFloat(
                if (user) {
                    NativeThemePreferenceSchemaV1.bubbleUserImageRepeatEnd
                } else {
                    NativeThemePreferenceSchemaV1.bubbleAiImageRepeatEnd
                },
            ),
        repeatYStartRatio =
            requiredFloat(
                if (user) {
                    NativeThemePreferenceSchemaV1.bubbleUserImageRepeatYStart
                } else {
                    NativeThemePreferenceSchemaV1.bubbleAiImageRepeatYStart
                },
            ),
        repeatYEndRatio =
            requiredFloat(
                if (user) {
                    NativeThemePreferenceSchemaV1.bubbleUserImageRepeatYEnd
                } else {
                    NativeThemePreferenceSchemaV1.bubbleAiImageRepeatYEnd
                },
            ),
        imageScale =
            requiredFloat(
                if (user) {
                    NativeThemePreferenceSchemaV1.bubbleUserImageScale
                } else {
                    NativeThemePreferenceSchemaV1.bubbleAiImageScale
                },
            ),
        renderMode = requiredString(NativeThemePreferenceSchemaV1.bubbleImageRenderMode),
    )
}
