package com.ai.assistance.operit.ui.common.displays

import android.view.Choreographer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * FPS计数器组件，用于测量和显示当前应用的帧率
 */
@Composable
fun FpsCounter(
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (!enabled) return
    
    // FPS状态
    var fps by remember { mutableStateOf(0) }
    var frameCount by remember { mutableStateOf(0) }
    var lastFrameTimeNanos by remember { mutableStateOf(0L) }
    
    // 帧率颜色，根据帧率值动态变化
    val fpsColor by remember(fps) {
        derivedStateOf {
            when {
                fps >= 55 -> Color(0xFF4CAF50) // 绿色 - 好
                fps >= 30 -> Color(0xFFFF9800) // 橙色 - 一般
                else -> Color(0xFFE53935)      // 红色 - 差
            }
        }
    }
    
    // 使用Choreographer监听帧绘制
    DisposableEffect(Unit) {
        val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (lastFrameTimeNanos == 0L) {
                    lastFrameTimeNanos = frameTimeNanos
                } else {
                    // 计算自上次回调以来的时间差（纳秒）
                    val timeDiffNanos = frameTimeNanos - lastFrameTimeNanos
                    frameCount++
                    
                    // 每秒更新一次FPS值
                    if (timeDiffNanos >= TimeUnit.SECONDS.toNanos(1)) {
                        // 计算FPS = 帧数 / 时间（秒）
                        fps = (frameCount * TimeUnit.SECONDS.toNanos(1) / timeDiffNanos).toInt()
                        frameCount = 0
                        lastFrameTimeNanos = frameTimeNanos
                    }
                }
                
                // 继续监听下一帧
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        
        // 注册帧回调
        Choreographer.getInstance().postFrameCallback(frameCallback)
        
        // 清理
        onDispose {
            // 取消帧回调
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        }
    }
    
    // 为了在低帧率情况下也能更新显示，添加一个定时器保持UI刷新
    LaunchedEffect(Unit) {
        while (true) {
            delay(500) // 每500毫秒强制刷新一次
            // 空操作，只是为了触发重组
        }
    }
    
    // FPS显示UI
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0x88000000))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .alpha(0.8f)
    ) {
        Text(
            text = "$fps FPS",
            color = fpsColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
} 