package com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments

import com.ai.assistance.operit.data.model.AttachmentInfo

/** Utility functions for working with attachments in the chat feature */
object AttachmentUtils {
    /** Format file size in a human-readable way */
    fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }

    /** Get a short display name for an attachment (truncated if too long) */
    fun getDisplayName(attachment: AttachmentInfo, maxLength: Int = 20): String {
        return if (attachment.fileName.length <= maxLength) {
            attachment.fileName
        } else {
            val extension = attachment.fileName.substringAfterLast('.', "")
            val nameWithoutExtension = attachment.fileName.substringBeforeLast('.')
            val truncatedName = nameWithoutExtension.take(maxLength - extension.length - 3)
            "$truncatedName...${if (extension.isNotEmpty()) ".$extension" else ""}"
        }
    }

    /** Get icon resource ID based on MIME type */
    fun getIconForMimeType(mimeType: String): Int {
        // Implementation would return appropriate drawable resource IDs
        // This would map to drawable resources in your app
        // For now just returning 0 since we don't have access to the real drawable resources
        return 0
    }
}
