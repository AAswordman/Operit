package com.ai.assistance.operit.data.model

/** 确定展示页边界所需的轻量字段，查询时不读取正文和候选。 */
data class ChatMessagePageMetadata(
    val sender: String,
    val timestamp: Long,
)
