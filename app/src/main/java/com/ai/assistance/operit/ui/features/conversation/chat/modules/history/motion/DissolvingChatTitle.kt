package com.ai.assistance.operit.ui.features.conversation.chat.modules.history.motion

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.sin

/** 分块重绘真实字形，省去逐帧截图和位图分配，也保留标题的无障碍语义。 */
@Composable
internal fun DissolvingChatTitle(title: String, progress: Float, color: Color, modifier: Modifier = Modifier) {
    val layer = rememberGraphicsLayer()
    Text(
        title,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Normal,
        color = color,
        modifier = modifier.graphicsLayer { alpha = (1f - ((progress - 0.65f) / 0.35f).coerceIn(0f, 1f)) }
            .drawWithContent {
                if (progress == 0f) {
                    drawContent()
                } else {
                    layer.record { this@drawWithContent.drawContent() }
                    val cell = 4.dp.toPx()
                    val columns = ceil(size.width / cell).toInt()
                    val rows = ceil(size.height / cell).toInt()
                    for (y in 0 until rows) for (x in 0 until columns) {
                        val seed = ((x * 31 + y * 17) % 29) / 29f
                        val phase = ((progress - seed * 0.24f) / 0.76f).coerceIn(0f, 1f)
                        val dx = (seed - 0.35f) * 44.dp.toPx() * phase * phase
                        val dy = (-12f - seed * 24f) * density * phase + sin(x.toFloat()) * phase * cell
                        translate(dx, dy) {
                            clipRect(x * cell, y * cell, (x + 1) * cell, (y + 1) * cell) { drawLayer(layer) }
                        }
                    }
                }
            },
    )
}