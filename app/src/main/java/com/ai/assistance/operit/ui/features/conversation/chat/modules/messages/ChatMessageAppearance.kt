package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.transparency.ThemeTransparencyPreferences
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.style.bubble.BubbleImageStyleConfig
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.ui.theme.getTextColorForBackground

/** 草稿名字仅作用于预览子树，正式消息继续读取全局显示名字。 */
internal val LocalThemePreviewUserName = compositionLocalOf<String?> { null }

/** 正式聊天与制作预览共用消息基础配色、透明度和图片气泡参数。 */
internal data class ChatMessageAppearance(
    val userMessageColor: Color,
    val aiMessageColor: Color,
    val userTextColor: Color,
    val aiTextColor: Color,
    val systemMessageColor: Color,
    val systemTextColor: Color,
    val thinkingBackgroundColor: Color,
    val thinkingTextColor: Color,
    val bubbleUserImageStyle: BubbleImageStyleConfig?,
    val bubbleAiImageStyle: BubbleImageStyleConfig?,
)

@Composable
internal fun chatMessageAppearance(): ChatMessageAppearance {
    val values = LocalThemePreferenceSnapshot.current.values
    val colors = MaterialTheme.colorScheme
    val userCursor = values.requiredString("user_chat_style") == "cursor"
    val aiCursor = values.requiredString("ai_chat_style") == "cursor"
    val follow = values.requiredBoolean("cursor_user_bubble_follow_theme")
    val userBase = if (userCursor && follow) colors.primaryContainer else {
        values.int(if (userCursor) "cursor_user_bubble_color" else "bubble_user_bubble_color")?.let(::Color) ?: colors.primaryContainer
    }
    val aiBase = if (aiCursor) colors.surface else values.int("bubble_ai_bubble_color")?.let(::Color) ?: colors.surface
    val userColor = userBase.copy(alpha = ThemeTransparencyPreferences.opacity(values, if (userCursor) ThemeTransparencyPreferences.CURSOR_USER else ThemeTransparencyPreferences.BUBBLE_USER, userBase.alpha))
    val aiColor = aiBase.copy(alpha = ThemeTransparencyPreferences.opacity(values, if (aiCursor) ThemeTransparencyPreferences.CURSOR_AI else ThemeTransparencyPreferences.BUBBLE_AI, aiBase.alpha))
    val userText = when {
        userCursor && follow -> colors.onPrimaryContainer
        !userCursor && values.int("bubble_user_text_color") != null -> Color(requireNotNull(values.int("bubble_user_text_color")))
        else -> getTextColorForBackground(userColor.copy(alpha = 1f))
    }
    val aiText = when {
        aiCursor -> colors.onSurface
        values.int("bubble_ai_text_color") != null -> Color(requireNotNull(values.int("bubble_ai_text_color")))
        else -> getTextColorForBackground(aiColor.copy(alpha = 1f))
    }
    return ChatMessageAppearance(
        userColor, aiColor, userText, aiText, colors.surfaceVariant, colors.onSurfaceVariant,
        colors.surfaceVariant.copy(alpha = .7f), colors.onSurfaceVariant,
        messageImageStyle(values, "bubble_user", userCursor), messageImageStyle(values, "bubble_ai", aiCursor),
    )
}

private fun messageImageStyle(values: ThemePreferenceValues, prefix: String, cursor: Boolean): BubbleImageStyleConfig? {
    val uri = values.string(prefix + "_image_uri")
    if (cursor || values.requiredBoolean(prefix + "_bubble_liquid_glass") || values.requiredBoolean(prefix + "_bubble_water_glass") || !values.requiredBoolean(prefix + "_use_image") || uri.isNullOrBlank()) return null
    return BubbleImageStyleConfig(
        imageUri = uri,
        cropLeftRatio = values.requiredFloat(prefix + "_image_crop_left"),
        cropTopRatio = values.requiredFloat(prefix + "_image_crop_top"),
        cropRightRatio = values.requiredFloat(prefix + "_image_crop_right"),
        cropBottomRatio = values.requiredFloat(prefix + "_image_crop_bottom"),
        repeatXStartRatio = values.requiredFloat(prefix + "_image_repeat_start"),
        repeatXEndRatio = values.requiredFloat(prefix + "_image_repeat_end"),
        repeatYStartRatio = values.requiredFloat(prefix + "_image_repeat_y_start"),
        repeatYEndRatio = values.requiredFloat(prefix + "_image_repeat_y_end"),
        imageScale = values.requiredFloat(prefix + "_image_scale"),
        renderMode = values.requiredString("bubble_image_render_mode"),
    )
}