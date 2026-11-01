package com.ai.assistance.operit.ui.features.conversation.chat.foundation.model

/** 聊天界面与状态管理共用的声明，独立归位以保持引用方向清晰。 */
data class PendingQueueMessageItem(
    val id: Long,
    val text: String
)
