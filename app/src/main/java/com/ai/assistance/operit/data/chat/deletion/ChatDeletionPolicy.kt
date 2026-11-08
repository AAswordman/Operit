package com.ai.assistance.operit.data.chat.deletion

import com.ai.assistance.operit.data.model.ChatEntity

/** 删除失败的原因独立传递，界面不能把所有失败都解释为锁定。 */
enum class ChatDeletionResult {
    DELETED,
    LOCKED,
    NOT_FOUND,
    CURRENT_CHAT,
    FAILED,
}

internal object ChatDeletionPolicy {
    /** 准备阶段允许当前会话进入切换流程，真正删除时仍拒绝当前会话。 */
    fun blockReason(
        target: ChatEntity?,
        currentChatId: String?,
        allowCurrentChat: Boolean = false,
    ): ChatDeletionResult? = when {
        target == null -> ChatDeletionResult.NOT_FOUND
        target.locked -> ChatDeletionResult.LOCKED
        !allowCurrentChat && target.id == currentChatId -> ChatDeletionResult.CURRENT_CHAT
        else -> null
    }
}