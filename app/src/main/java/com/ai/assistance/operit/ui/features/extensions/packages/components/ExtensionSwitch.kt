package com.ai.assistance.operit.ui.features.extensions.packages.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Shape is a function of travel, so reversal also returns smoothly to a circular endpoint. */
@Composable
internal fun ExtensionSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "extensionSwitch",
    )
    val colors = MaterialTheme.colorScheme
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val track = lerp(colors.surfaceContainerHighest, colors.primary, progress)
    val thumb = lerp(colors.outline, colors.onPrimary, progress)
    val alpha = if (enabled) 1f else 0.38f
    Box(
        modifier = modifier
            // The larger touch target must not paint a rectangular press indication around the track.
            .toggleable(value = checked, enabled = enabled, role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() }, indication = null,
                onValueChange = onCheckedChange)
            .minimumInteractiveComponentSize(),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(width = 52.dp, height = 32.dp)) {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(track.copy(alpha = alpha), cornerRadius = radius)
            drawRoundRect(colors.outline.copy(alpha = (1f - progress) * alpha),
                topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                cornerRadius = radius, style = Stroke(width = 2.dp.toPx()))
            val height = 20.dp.toPx()
            val width = height + 8.dp.toPx() * (4f * progress * (1f - progress))
            val travel = if (rtl) 1f - progress else progress
            val center = 16.dp.toPx() + 20.dp.toPx() * travel
            drawRoundRect(thumb.copy(alpha = alpha),
                topLeft = Offset(center - width / 2, (size.height - height) / 2),
                size = Size(width, height), cornerRadius = CornerRadius(height / 2))
        }
    }
}