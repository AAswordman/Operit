package com.ai.assistance.operit.ui.theme.script

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.UiComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** 输入区的自绘按钮保留原点击回调，统一消费独立外观与原生交互状态。 */
@Composable
@UiComposable
fun ThemeScriptActionButton(
    actionId: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    color: Color = Color.Transparent,
    shape: Shape = CircleShape,
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable @UiComposable () -> Unit,
) {
    require(actionId in com.ai.assistance.operit.data.theme.script.ThemeScriptContract.actionButtonIds)
    ThemeInteractionScope(actionId, interactionSource, enabled, selected) { source ->
        val style = themeSurface(actionId)
        Surface(
            onClick = onClick,
            modifier = modifier.alpha(style?.opacity ?: 1f),
            enabled = enabled,
            shape = themeScriptShape(actionId, shape),
            color = themeScriptFill(actionId, color),
            contentColor = themeScriptContentColor(actionId, LocalContentColor.current),
            border = themeScriptBorder(actionId, border),
            shadowElevation = (style?.elevation ?: 0f).dp,
            interactionSource = source,
        ) {
            Box(
                Modifier.themeScriptDecoration(actionId, enabled = enabled, selected = selected,
                    drawFill = false, drawStroke = false, originalShape = shape, drawElevation = false),
                contentAlignment = Alignment.Center,
                propagateMinConstraints = true,
            ) { content() }
        }
    }
}
