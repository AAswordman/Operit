package com.ai.assistance.operit.core.chat.attachment

/**
 * Data class to store information about an attachment This represents a file or content that can be
 * attached to a chat message
 */
data class AttachmentInfo(
        val filePath: String,
        val fileName: String,
        val mimeType: String,
        val fileSize: Long,
        val content: String = "" // Field to store inline content
)
