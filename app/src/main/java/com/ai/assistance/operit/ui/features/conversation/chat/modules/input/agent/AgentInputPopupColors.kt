package com.ai.assistance.operit.ui.features.conversation.chat.modules.input.agent

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.ai.assistance.operit.ui.theme.script.themeScriptFill

/** 正式菜单与完整聊天预览使用相同的输入主题底色和背景透明度。 */
@Composable
internal fun agentInputPopupContainerColor(
    hasBackgroundImage: Boolean,
    chatInputTransparent: Boolean,
    chatInputOpacity: Float?,
): Color {
    val colors = MaterialTheme.colorScheme
    val dark = colors.onSurface.luminance() > 0.5f
    val darkInput = lerp(colors.surface, colors.onSurface, 0.08f)
    val transparent = chatInputOpacity?.let { it < 1f } ?: chatInputTransparent
    val opacity = chatInputOpacity?.coerceIn(0f, 1f) ?: when {
        chatInputTransparent -> 0f
        dark && hasBackgroundImage -> 0.82f
        hasBackgroundImage -> 0.85f
        else -> 1f
    }
    val inputColor = themeScriptFill("composer", if (dark) darkInput else colors.surface, opacity)
    return when {
        dark && transparent -> darkInput
        dark -> inputColor
        else -> colors.surface.copy(alpha = 0.95f)
    }
}
