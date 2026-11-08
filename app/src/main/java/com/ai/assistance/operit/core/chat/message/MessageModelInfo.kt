package com.ai.assistance.operit.core.chat.message

/** Display metadata is captured for each response, never rebuilt from today's model settings. */
fun modelInfoWithThinking(model: String, optionId: String?): String =
    if (model.isNotBlank() && !optionId.isNullOrBlank()) "$model($optionId)" else model
