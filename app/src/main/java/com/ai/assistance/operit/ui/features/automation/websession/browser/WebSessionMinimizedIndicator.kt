package com.ai.assistance.operit.ui.features.automation.websession.browser

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import kotlin.math.roundToInt

@Composable
internal fun WebSessionMinimizedIndicator(
    contentDescription: String,
    activeDownloadCount: Int,
    hasFailedDownloads: Boolean,
    externalOpenPrompt: com.ai.assistance.operit.core.tools.builtin.web.session.browser.ExternalOpenPromptState?,
    onToggleFullscreen: () -> Unit,
    onDragBy: (dx: Int, dy: Int) -> Unit,
    onConfirmExternalOpen: (String) -> Unit,
    onCancelExternalOpen: (String) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val dragModifier =
        Modifier.pointerInput(Unit) {
            detectDragGestures { _, dragAmount ->
                onDragBy(dragAmount.x.roundToInt(), dragAmount.y.roundToInt())
            }
        }

    if (externalOpenPrompt != null) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            modifier =
                Modifier
                    .fillMaxSize()
                    .semantics { this.contentDescription = contentDescription }
                    .then(dragModifier)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Box(
                            modifier = Modifier.size(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = externalOpenPrompt.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = externalOpenPrompt.target,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { onCancelExternalOpen(externalOpenPrompt.requestId) }) {
                        Text(stringResource(R.string.web_session_external_open_cancel))
                    }
                    TextButton(onClick = { onConfirmExternalOpen(externalOpenPrompt.requestId) }) {
                        Text(stringResource(R.string.web_session_external_open_allow_once))
                    }
                }
            }
        }
        return
    }

    val transition = rememberInfiniteTransition(label = "web-session-indicator")
    val primaryColor = MaterialTheme.colorScheme.primary

    val bobbingDp by
        transition.animateFloat(
            initialValue = -2f,
            targetValue = 2f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 900, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
            label = "bobbing"
        )

    val wiggleDeg by
        transition.animateFloat(
            initialValue = -8f,
            targetValue = 8f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
            label = "wiggle"
        )

    val pulse by
        transition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1100, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
            label = "pulse"
        )

    Surface(
        shape = CircleShape,
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier =
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .then(dragModifier)
                .semantics {
                    this.contentDescription = contentDescription
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) {
                    onToggleFullscreen()
                }
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .scale(pulse)
                    .drawBehind {
                        val radius = size.minDimension / 2f
                        drawCircle(
                            brush =
                                Brush.radialGradient(
                                    colors =
                                        listOf(
                                            Color.White.copy(alpha = 0.40f),
                                            primaryColor.copy(alpha = 0.16f),
                                            Color.Transparent
                                        ),
                                    center = Offset(size.width * 0.30f, size.height * 0.28f),
                                    radius = radius * 1.15f
                                ),
                            radius = radius
                        )

                        drawCircle(
                            color = primaryColor.copy(alpha = 0.12f),
                            radius = radius * 0.76f,
                            center = Offset(size.width * 0.5f, size.height * 0.54f)
                        )
                    }
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.35f),
                        shape = CircleShape
                    ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (activeDownloadCount > 0) Icons.Filled.Download else Icons.Filled.Language,
                contentDescription = null,
                tint = primaryColor.copy(alpha = 0.76f),
                modifier =
                    Modifier
                        .offset(y = bobbingDp.dp)
                        .rotate(wiggleDeg)
            )
            if (activeDownloadCount > 0 || hasFailedDownloads) {
                Surface(
                    shape = CircleShape,
                    color = if (hasFailedDownloads) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(18.dp)
                                .background(Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text =
                                if (activeDownloadCount > 0) {
                                    activeDownloadCount.coerceAtMost(9).toString()
                                } else {
                                    "!"
                                },
                            style = MaterialTheme.typography.labelSmall,
                            color =
                                if (hasFailedDownloads) {
                                    MaterialTheme.colorScheme.onError
                                } else {
                                    MaterialTheme.colorScheme.onPrimary
                                },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
