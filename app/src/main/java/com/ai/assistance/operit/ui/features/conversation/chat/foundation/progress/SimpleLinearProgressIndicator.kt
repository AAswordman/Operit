package com.ai.assistance.operit.ui.features.conversation.chat.foundation.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A simple custom linear progress indicator that avoids using the problematic Material3 animation features.
 * This is implemented using Box composables instead of LinearProgressIndicator to avoid version compatibility issues.
 */
@Composable
fun SimpleLinearProgressIndicator(
    progress: Float = -1f,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
) {
    Box(
        modifier = modifier
            .height(4.dp)
            .fillMaxWidth()
            .background(
                color = trackColor,
                shape = RoundedCornerShape(2.dp)
            )
    ) {
        if (progress in 0.0f..1.0f) {
            // 确定性进度条 - 有特定进度值
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(
                        color = color,
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        } else {
            // 不确定性进度条 - 静态填充30%作为替代
            // 注意：这里不再有动画效果，如果需要动画可以使用其他方式实现
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.3f)
                    .fillMaxHeight()
                    .background(
                        color = color,
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
} 