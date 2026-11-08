package com.ai.assistance.operit.ui.features.conversation.chat.foundation.model

/** 聊天界面与状态管理共用的声明，独立归位以保持引用方向清晰。 */
enum class ChatBatchAction { SUMMARIZE_TITLES, ARCHIVE, UNARCHIVE, LOCK, UNLOCK, DELETE }

enum class ChatBatchItemResult { SUCCEEDED, SKIPPED, FAILED }

data class ChatBatchProgress(val action: ChatBatchAction, val completed: Int, val total: Int)

data class ChatBatchSummary(val succeeded: Int, val skipped: Int, val failed: Int)
