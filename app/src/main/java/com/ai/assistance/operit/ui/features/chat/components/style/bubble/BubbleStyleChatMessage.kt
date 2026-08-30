
package com.ai.assistance.operit.ui.features.chat.components.style.bubble

import androidx.compose.runtime.Composable
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.features.chat.components.ChatMessageHeightMemory
import com.ai.assistance.operit.ui.features.chat.components.style.cursor.SummaryMessageComposable
import com.ai.assistance.operit.util.stream.Stream

/**
 * A composable function that renders chat messages in a bubble chat style.
 * Delegates to specialized composables based on message type.
 */
@Composable
fun BubbleStyleChatMessage(
    message: ChatMessage,
    initialThinkingExpanded: Boolean = false,
    allowExpandedThinkingFullHeight: Boolean = false,
    expandThinkToolsGroups: Boolean = false,
    forceShowThinkingProcess: Boolean = false,
    isHidden: Boolean = false,
    heightMemory: ChatMessageHeightMemory? = null,
    onDeleteMessage: ((Int) -> Unit)? = null,
    index: Int = -1,
    enableDialogs: Boolean = true,  // 新增参数：是否启用弹窗功能，默认启用
    onRoleAvatarLongPress: ((String) -> Unit)? = null,
    onEditSummary: ((ChatMessage) -> Unit)? = null,
) {
    when (message.sender) {
        "user" -> {
            BubbleUserMessageComposable(
                message = message,
                enableDialogs = enableDialogs,
            )
        }
        "ai" -> {
            BubbleAiMessageComposable(
                message = message,
                initialThinkingExpanded = initialThinkingExpanded,
                allowExpandedThinkingFullHeight = allowExpandedThinkingFullHeight,
                expandThinkToolsGroups = expandThinkToolsGroups,
                forceShowThinkingProcess = forceShowThinkingProcess,
                isHidden = isHidden,
                heightMemory = heightMemory,
                enableDialogs = enableDialogs,
                onAvatarLongPressMention = onRoleAvatarLongPress,
            )
        }
        "summary" -> {
            SummaryMessageComposable(
                message = message,
                onDelete = {
                    if (index != -1) {
                        onDeleteMessage?.invoke(index)
                    }
                },
                enableDialog = enableDialogs,  // 传递弹窗启用状态
                onEdit = { editedMessage ->
                    onEditSummary?.invoke(editedMessage)
                }
            )
        }
    }
}
