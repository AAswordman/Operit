package com.ai.assistance.operit.ui.features.conversation.chat.foundation.model

/** 聊天界面与状态管理共用的声明，独立归位以保持引用方向清晰。 */
data class TranslationUiState(
    val isLoading: Boolean = false,
    val translatedText: String? = null,
    val errorMessage: String? = null,
    val isFromCache: Boolean = false,
)
