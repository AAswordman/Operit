
package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.style.bubble

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.list.ChatMessageHeightMemory
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.style.cursor.SummaryMessageComposable
import com.ai.assistance.operit.util.stream.Stream

/**
 * A composable function that renders chat messages in a bubble chat style.
 * Delegates to specialized composables based on message type.
 */
@Composable
fun BubbleStyleChatMessage(
    message: ChatMessage,
    userMessageColor: Color,
    aiMessageColor: Color,
    userTextColor: Color,
    aiTextColor: Color,
    systemMessageColor: Color,
    systemTextColor: Color,
    userMessageLiquidGlassEnabled: Boolean = false,
    userMessageWaterGlassEnabled: Boolean = false,
    aiMessageLiquidGlassEnabled: Boolean = false,
    aiMessageWaterGlassEnabled: Boolean = false,
    userBubbleImageStyle: BubbleImageStyleConfig? = null,
    aiBubbleImageStyle: BubbleImageStyleConfig? = null,
    bubbleUserRoundedCornersEnabled: Boolean = true,
    bubbleAiRoundedCornersEnabled: Boolean = true,
    bubbleUserContentPaddingLeft: Float = 12f,
    bubbleUserContentPaddingRight: Float = 12f,
    bubbleAiContentPaddingLeft: Float = 12f,
    bubbleAiContentPaddingRight: Float = 12f,
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
                backgroundColor = userMessageColor,
                textColor = userTextColor,
                enableLiquidGlass = userMessageLiquidGlassEnabled,
                enableWaterGlass = userMessageWaterGlassEnabled,
                bubbleImageStyle = userBubbleImageStyle,
                bubbleRoundedCornersEnabled = bubbleUserRoundedCornersEnabled,
                bubbleContentPaddingLeft = bubbleUserContentPaddingLeft,
                bubbleContentPaddingRight = bubbleUserContentPaddingRight,
                enableDialogs = enableDialogs,
            )
        }
        "ai" -> {
            BubbleAiMessageComposable(
                message = message,
                backgroundColor = aiMessageColor,
                textColor = aiTextColor,
                enableLiquidGlass = aiMessageLiquidGlassEnabled,
                enableWaterGlass = aiMessageWaterGlassEnabled,
                bubbleImageStyle = aiBubbleImageStyle,
                bubbleRoundedCornersEnabled = bubbleAiRoundedCornersEnabled,
                bubbleContentPaddingLeft = bubbleAiContentPaddingLeft,
                bubbleContentPaddingRight = bubbleAiContentPaddingRight,
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
