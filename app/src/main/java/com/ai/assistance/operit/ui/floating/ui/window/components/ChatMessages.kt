package com.ai.assistance.operit.ui.floating.ui.window.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.features.chat.components.style.cursor.CursorStyleChatMessage

/** 单个消息项组件 将消息渲染逻辑提取到单独的组件，减少重组范围 */
@Composable
fun MessageItem(
        index: Int,
        message: ChatMessage,
        isLastAiMessage: Boolean,
        onSelectMessageToEdit: ((Int, ChatMessage) -> Unit)?,
) {
        // 编辑模式下为消息添加点击功能
        val messageModifier = Modifier

        Box(modifier = messageModifier) {
                val streamToRender = if (isLastAiMessage) message.contentStream else null

                CursorStyleChatMessage(
                        message = message,
                        supportToolMarkup = true,
                        initialThinkingExpanded = true,
                        overrideStream = streamToRender,
                        enableDialogs = false  // 在悬浮窗中禁用弹窗，避免闪退
                )
        }
}
