package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

/** The original navigator's line-and-dot appearance, with progress read only while drawing. */
@Composable
internal fun TranscriptPositionIndicator(progress: () -> Float, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
    val line = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val dot = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
    val border = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)
    val shape = RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp, topEnd = 10.dp, bottomEnd = 10.dp)
    val description = stringResource(R.string.chat_message_locator_title)
    Box(
        Modifier.size(width = 48.dp, height = 64.dp)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(20.dp).height(58.dp).clip(shape)
                    .background(color).border(1.dp, border, shape),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(width = 8.dp, height = 34.dp)) {
                    val x = size.width / 2f
                    val top = 2.dp.toPx()
                    val bottom = size.height - top
                    drawLine(line, Offset(x, top), Offset(x, bottom), strokeWidth = 1.5.dp.toPx())
                    drawCircle(dot, radius = 3.dp.toPx(),
                        center = Offset(x, top + (bottom - top) * progress().coerceIn(0f, 1f)))
                }
            }
            Canvas(Modifier.offset(x = (-1).dp).size(width = 9.dp, height = 18.dp)) {
                drawPath(Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, size.height / 2f)
                    lineTo(0f, size.height)
                    close()
                }, color)
            }
        }
    }
}
