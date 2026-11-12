package com.ai.assistance.operit.services

import com.ai.assistance.operit.data.model.ChatMessage

interface ChatServiceUiBridge {
    fun setActiveDraftChat(chatId: String?) = Unit
    fun updateWebServerForCurrentChat(chatId: String)
    fun resetAttachmentPanelState()
    fun clearReplyToMessage()
    fun getReplyToMessage(): ChatMessage?
}

object EmptyChatServiceUiBridge : ChatServiceUiBridge {
    override fun updateWebServerForCurrentChat(chatId: String) = Unit

    override fun resetAttachmentPanelState() = Unit

    override fun clearReplyToMessage() = Unit

    override fun getReplyToMessage(): ChatMessage? = null
}
