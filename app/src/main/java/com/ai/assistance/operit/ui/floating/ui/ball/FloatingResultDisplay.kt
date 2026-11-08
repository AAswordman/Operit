package com.ai.assistance.operit.ui.floating.ui.ball

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.floating.FloatContext
import com.ai.assistance.operit.ui.floating.FloatingMode

/**
 * 显示结果的悬浮组件
 * 目前只显示一个简单的"你好"气泡
 */
@Composable
fun FloatingResultDisplay(floatContext: FloatContext) {
    // 获取最后一条消息的内容
    val lastMessage = floatContext.messages.lastOrNull()
    val displayText = if (lastMessage != null && lastMessage.sender == "ai") {
        lastMessage.content
    } else {
        stringResource(R.string.floating_hello)
    }

    Box(
        modifier = Modifier
            .background(Color.Transparent) // 背景透明
            .clickable { 
                // 点击切回球模式
                floatContext.onModeChange(FloatingMode.BALL) 
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .background(Color.White, RoundedCornerShape(12.dp))
                .clickable { floatContext.onModeChange(FloatingMode.BALL) }
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = displayText,
                color = Color.Black,
                fontSize = 16.sp
            )
        }
    }
}
