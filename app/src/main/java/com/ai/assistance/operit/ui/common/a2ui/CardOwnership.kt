package com.ai.assistance.operit.ui.common.a2ui

import com.ai.assistance.operit.data.model.ChatMessage

/** 使用完整消息身份校验交互，分片渲染不改变身份。 */
data class A2uiOwner(val chatId: String, val timestamp: Long, val variant: Int, val content: String)

internal fun matchesA2uiOwner(chatId: String?, message: ChatMessage?, owner: A2uiOwner): Boolean {
    if (chatId != owner.chatId || message == null) return false
    return message.timestamp == owner.timestamp && message.sender == "ai" && !message.isVariantPreview &&
        message.contentStream == null && message.selectedVariantIndex == owner.variant && message.content == owner.content
}
