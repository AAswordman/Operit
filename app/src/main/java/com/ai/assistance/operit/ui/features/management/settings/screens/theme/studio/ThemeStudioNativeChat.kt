package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.preferences.transparency.ThemeTransparencyPreferences
import com.ai.assistance.operit.ui.features.conversation.chat.modules.input.agent.AgentChatInputContent
import com.ai.assistance.operit.ui.features.conversation.chat.modules.input.classic.ClassicChatInputContent
import com.ai.assistance.operit.ui.features.conversation.chat.modules.input.classic.ClassicChatSettingsContent
import com.ai.assistance.operit.ui.features.conversation.chat.modules.input.common.ChatInputRuntime
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.chatMessageAppearance
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.MessageFooterBar
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.style.bubble.BubbleStyleChatMessage
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.style.cursor.CursorStyleChatMessage
import com.ai.assistance.operit.ui.permissions.PermissionLevel
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot

/** 两种消息风格直接使用正式消息，字体、头像、图片气泡和元信息随草稿变化。 */
@Composable
internal fun StudioNativeMessage(state: ThemeStudioState, user: Boolean, content: String) {
    val snapshot = LocalThemePreferenceSnapshot.current
    val values = snapshot.values
    val sampleTime = remember { System.currentTimeMillis() }
    val message = remember(user, content, state.targetName, state.target.id) {
        ChatMessage(
            sender = if (user) "user" else "ai", content = content, timestamp = if (user) 1L else 2L,
            roleName = if (user) "" else state.targetName,
            variantCount = if (!user && state.target.id == "status") 2 else 1,
            inputTokens = if (user) 0L else 128L, outputTokens = if (user) 0L else 64L,
            sentAt = sampleTime - 2000L, completedAt = if (user) 0L else sampleTime,
            outputDurationMs = 1500L, waitDurationMs = 500L,
        )
    }
    Column {
        with(chatMessageAppearance()) {
            if (values.requiredString(if (user) "user_chat_style" else "ai_chat_style") == "cursor") {
                CursorStyleChatMessage(
                    message = message,
                    userMessageColor = userMessageColor,
                    aiMessageColor = aiMessageColor,
                    userTextColor = userTextColor,
                    aiTextColor = aiTextColor,
                    systemMessageColor = systemMessageColor,
                    systemTextColor = systemTextColor,
                    thinkingBackgroundColor = thinkingBackgroundColor,
                    thinkingTextColor = thinkingTextColor,
                    userMessageLiquidGlassEnabled = values.requiredBoolean("cursor_user_bubble_liquid_glass"),
                    userMessageWaterGlassEnabled = values.requiredBoolean("cursor_user_bubble_water_glass"),
                    enableDialogs = false,
                )
            } else {
                BubbleStyleChatMessage(
                    message = message,
                    userMessageColor = userMessageColor,
                    aiMessageColor = aiMessageColor,
                    userTextColor = userTextColor,
                    aiTextColor = aiTextColor,
                    systemMessageColor = systemMessageColor,
                    systemTextColor = systemTextColor,
                    userMessageLiquidGlassEnabled = values.requiredBoolean("bubble_user_bubble_liquid_glass"),
                    userMessageWaterGlassEnabled = values.requiredBoolean("bubble_user_bubble_water_glass"),
                    aiMessageLiquidGlassEnabled = values.requiredBoolean("bubble_ai_bubble_liquid_glass"),
                    aiMessageWaterGlassEnabled = values.requiredBoolean("bubble_ai_bubble_water_glass"),
                    userBubbleImageStyle = bubbleUserImageStyle,
                    aiBubbleImageStyle = bubbleAiImageStyle,
                    bubbleUserRoundedCornersEnabled = values.requiredBoolean("bubble_rounded_corners_enabled"),
                    bubbleAiRoundedCornersEnabled = values.requiredBoolean("bubble_ai_rounded_corners_enabled"),
                    bubbleUserContentPaddingLeft = values.requiredFloat("bubble_user_content_padding_left"),
                    bubbleUserContentPaddingRight = values.requiredFloat("bubble_user_content_padding_right"),
                    bubbleAiContentPaddingLeft = values.requiredFloat("bubble_ai_content_padding_left"),
                    bubbleAiContentPaddingRight = values.requiredFloat("bubble_ai_content_padding_right"),
                    enableDialogs = false,
                )
            }
        }
        if (!user) MessageFooterBar(
            message, snapshot.showMessageTokenStats, snapshot.showMessageTimingStats,
            snapshot.showMessageTokenSpeed, snapshot.showMessageTimestamp, {},
        )
    }
}

/** 预览使用固定会话状态，点击由外层选择罩接收，不发送请求或修改角色绑定。 */
@Composable
internal fun StudioNativeInput(state: ThemeStudioState) {
    val snapshot = LocalThemePreferenceSnapshot.current
    val values = snapshot.values
    val runtime = remember { ChatInputRuntime(0L, 128f, "Operit", {}, { _ -> }, { _ -> }) }
    val text = TextFieldValue(if (state.target.id in setOf("composer.stop", "composer.microphone")) "" else stringResource(R.string.studio_preview_input))
    val processing = state.target.id in setOf("composer.stop", "composer.queue")
    if (state.previewInputStyle == "agent") {
        AgentChatInputContent(
            runtime = runtime, userMessage = text, onUserMessageChange = {},
            onSendMessage = {}, onRecordVoiceMessage = {}, onContinueGeneration = {}, continueGenerationEnabled = false,
            onQueueMessage = {}, onCancelMessage = {}, isLoading = processing, onTakePhoto = {}, currentChatId = null,
            inputMenuRuntime = "theme-preview",
            hasBackgroundImage = values.requiredBoolean("use_background_image"),
            chatInputTransparent = snapshot.chatInputTransparent,
            chatInputOpacity = values.float(ThemeTransparencyPreferences.INPUT),
            chatInputFloating = snapshot.chatInputFloating,
            chatInputLiquidGlass = snapshot.chatInputLiquidGlass,
            chatInputWaterGlass = snapshot.chatInputWaterGlass,
        )
    } else {
        Column {
            ClassicChatSettingsContent(
                runtime = runtime, currentChatId = null, featureStates = emptyMap(), onToggleFeature = {},
                permissionLevel = PermissionLevel.ASK, onSetPermissionLevel = {}, enableThinkingMode = false,
                onToggleThinkingMode = {}, thinkingOptionId = "", onThinkingOptionIdChange = {},
                maxWindowSizeInK = 128f, onContextLengthChange = {}, enableMemoryAutoUpdate = false,
                onToggleMemoryAutoUpdate = {}, summaryTokenThreshold = .8f, onSummaryTokenThresholdChange = {},
                onNavigateToMemoryBase = {}, onNavigateToModelConfig = {}, onNavigateToModelPrompts = {},
                onNavigateToPackageManager = {}, enableTools = true, onToggleTools = {},
                toolPromptVisibility = emptyMap(), onSaveToolPromptVisibilityMap = {},
                disableStreamOutput = false, onToggleDisableStreamOutput = {},
                disableUserPreferenceDescription = false, onToggleDisableUserPreferenceDescription = {},
                onManualMemoryUpdate = {}, inputMenuRuntime = "theme-preview",
            )
            ClassicChatInputContent(
                runtime = runtime, userMessage = text, onUserMessageChange = {},
                onSendMessage = {}, onRecordVoiceMessage = {}, onContinueGeneration = {}, continueGenerationEnabled = false,
                onQueueMessage = {}, onCancelMessage = {}, isLoading = processing, onTakePhoto = {},
                hasBackgroundImage = values.requiredBoolean("use_background_image"),
                chatInputTransparent = snapshot.chatInputTransparent,
                chatInputOpacity = values.float(ThemeTransparencyPreferences.INPUT),
                chatInputFloating = snapshot.chatInputFloating,
                chatInputLiquidGlass = snapshot.chatInputLiquidGlass,
                chatInputWaterGlass = snapshot.chatInputWaterGlass,
            )
        }
    }
}