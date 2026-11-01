package com.ai.assistance.operit.ui.features.conversation.workspace.hub

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/** 隐藏后保留工作区组合状态，仅在滑入和滑出期间测量并放置覆盖层。 */
@Composable
internal fun WorkspaceHubOverlay(
    visible: Boolean,
    content: @Composable (Boolean) -> Unit,
) {
    val transition = updateTransition(targetState = visible, label = "workspaceHub")
    val offsetFraction by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 240, easing = FastOutSlowInEasing) },
        label = "workspaceHubOffset",
    ) { shown -> if (shown) 0f else 1f }
    val placed = transition.currentState || transition.targetState || transition.isRunning

    Layout(
        modifier = if (placed) {
            Modifier.fillMaxSize().clipToBounds()
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
        } else {
            Modifier.size(0.dp).clearAndSetSemantics {}
        },
        content = {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { translationX = size.width * offsetFraction }
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 1f)),
            ) {
                content(placed)
            }
        },
    ) { measurables, constraints ->
        if (!placed) {
            layout(0, 0) {}
        } else {
            val placeable = measurables.single().measure(constraints)
            layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
        }
    }
}
