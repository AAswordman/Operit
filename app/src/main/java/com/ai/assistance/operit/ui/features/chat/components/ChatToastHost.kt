package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatToastEvent
import kotlin.math.max

@Composable
fun ChatToastHost(
    event: ChatToastEvent?,
    onDismiss: (Long) -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 720.dp,
    maxHeight: Dp = 240.dp,
) {
    val message = event?.message
    val scrollState = rememberScrollState()

    LaunchedEffect(event?.id) {
        val activeEvent = event ?: return@LaunchedEffect
        scrollState.scrollTo(0)
        kotlinx.coroutines.delay(
            activeEvent.durationMs?.coerceAtLeast(1L)
                ?: estimateToastDurationMs(activeEvent.message)
        )
        onDismiss(activeEvent.id)
    }

    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = !message.isNullOrBlank(),
            enter = fadeIn(animationSpec = tween(durationMillis = 180)) +
                slideInVertically(
                    animationSpec = tween(durationMillis = 220),
                    initialOffsetY = { -it / 2 }
                ),
            exit = fadeOut(animationSpec = tween(durationMillis = 140)) +
                slideOutVertically(
                    animationSpec = tween(durationMillis = 180),
                    targetOffsetY = { -it / 2 }
                )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = maxWidth),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                )
            ) {
                val estimatedLines = message
                    ?.lineSequence()
                    ?.sumOf { line -> max(1, (line.length + 23) / 24) }
                    ?: 1
                val isCompactMessage = estimatedLines <= 2

                Row(
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_simple_foreground),
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 28.dp)
                            .then(
                                if (isCompactMessage) {
                                    Modifier
                                } else {
                                    Modifier
                                        .heightIn(max = maxHeight)
                                        .verticalScroll(scrollState)
                                }
                            ),
                        contentAlignment =
                            if (isCompactMessage) Alignment.CenterStart else Alignment.TopStart
                    ) {
                        Text(
                            text = message.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            overflow = TextOverflow.Clip
                        )
                    }
                    IconButton(
                        onClick = { event?.let { onDismiss(it.id) } },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun estimateToastDurationMs(message: String): Long {
    val estimatedLines = message
        .lineSequence()
        .sumOf { line -> max(1, (line.length + 23) / 24) }

    return (2500L + estimatedLines * 850L).coerceIn(3500L, 12000L)
}
