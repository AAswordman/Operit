package com.ai.assistance.operit.ui.features.chat.components.style.cursor

import androidx.compose.runtime.Composable
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.features.chat.components.ChatMessageHeightMemory
import com.ai.assistance.operit.util.stream.Stream

/**
 * A composable function that renders chat messages in a Cursor IDE style. Delegates to specialized
 * composables based on message type.
 */
@Composable
fun CursorStyleChatMessage(
        message: ChatMessage,
        supportToolMarkup: Boolean = true,
        initialThinkingExpanded: Boolean = false,
        allowExpandedThinkingFullHeight: Boolean = false,
        expandThinkToolsGroups: Boolean = false,
        forceShowThinkingProcess: Boolean = false,
        overrideStream: Stream<String>? = null,
        heightMemory: ChatMessageHeightMemory? = null,
        onDeleteMessage: ((Int) -> Unit)? = null,
        index: Int = -1,
        enableDialogs: Boolean = true,  // 新增参数：是否启用弹窗功能，默认启用
        onEditSummary: ((ChatMessage) -> Unit)? = null,
) {
    when (message.sender) {
        "user" -> {
            UserMessageComposable(
                    message = message,
                    enableDialogs = enableDialogs,
            )
        }
        "ai" -> {
            AiMessageComposable(
                    message = message,
                    initialThinkingExpanded = initialThinkingExpanded,
                    allowExpandedThinkingFullHeight = allowExpandedThinkingFullHeight,
                    expandThinkToolsGroups = expandThinkToolsGroups,
                    forceShowThinkingProcess = forceShowThinkingProcess,
                    overrideStream = overrideStream,
                    heightMemory = heightMemory,
                    enableDialogs = enableDialogs,  // 传递弹窗启用状态
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
