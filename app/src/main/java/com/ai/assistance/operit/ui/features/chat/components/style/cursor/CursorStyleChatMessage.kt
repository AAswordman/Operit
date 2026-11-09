package com.ai.assistance.operit.ui.features.chat.components.style.cursor

import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
        userMessageColor: Color,
        userMessageLiquidGlassEnabled: Boolean = false,
        userMessageWaterGlassEnabled: Boolean = false,
        aiMessageColor: Color,
        userTextColor: Color,
        aiTextColor: Color,
        systemMessageColor: Color,
        systemTextColor: Color,
        thinkingBackgroundColor: Color,
        thinkingTextColor: Color,
        supportToolMarkup: Boolean = true,
        initialThinkingExpanded: Boolean = false,
        allowExpandedThinkingFullHeight: Boolean = false,
        expandThinkToolsGroups: Boolean = false,
        forceShowThinkingProcess: Boolean = false,
        overrideStream: Stream<String>? = null,
        heightMemory: ChatMessageHeightMemory? = null,
        onDeleteMessage: ((Int) -> Unit)? = null,
        index: Int = -1,
        enableDialogs: Boolean = true,

        enableToolDetailDialogs: Boolean? = null,
        onEditSummary: ((ChatMessage) -> Unit)? = null,
        showAssistantHeader: Boolean = true,
) {
    when (if (message.displayMode.isCollaborationEvent) "ai" else message.sender) {
        "user" -> {
            UserMessageComposable(
                    message = message,
                    backgroundColor = userMessageColor,
                    enableLiquidGlass = userMessageLiquidGlassEnabled,
                    enableWaterGlass = userMessageWaterGlassEnabled,
                    textColor = userTextColor,
                    enableDialogs = enableDialogs,
            )
        }
        "ai" -> {
            AiMessageComposable(
                    message = message,
                    showHeader = showAssistantHeader && !message.displayMode.isCollaborationEvent,
                inlineContent = if (message.displayMode.isCollaborationEvent) { { com.ai.assistance.operit.ui.features.chat.components.CollaborationMessageCard(message, horizontalPadding = 0.dp) } } else null,
                    backgroundColor = aiMessageColor,
                    textColor = aiTextColor,
                    initialThinkingExpanded = initialThinkingExpanded,
                    allowExpandedThinkingFullHeight = allowExpandedThinkingFullHeight,
                    expandThinkToolsGroups = expandThinkToolsGroups,
                    forceShowThinkingProcess = forceShowThinkingProcess,
                    overrideStream = overrideStream,
                    heightMemory = heightMemory,
                    enableDialogs = enableDialogs,  // 传递弹窗启用状态
                    enableToolDetailDialogs = enableToolDetailDialogs,
            )
        }
        "summary" -> {
            SummaryMessageComposable(
                    message = message,
                    backgroundColor = systemMessageColor.copy(alpha = 0.7f),
                    textColor = systemTextColor,
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
