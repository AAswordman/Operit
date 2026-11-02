package com.ai.assistance.operit.core.chat.media

import com.ai.assistance.operit.core.chat.attachment.AttachmentInfo

/** 媒体链接用于模型输入，原文件描述独立保存在消息中供聊天预览使用。 */
internal fun mediaAttachmentMarkup(attachment: AttachmentInfo, content: String = attachment.content): String {
    val size = if (attachment.fileSize > 0) " size=\"${attachment.fileSize}\"" else ""
    return "<attachment id=\"${escapeMediaAttribute(attachment.filePath)}\" " +
        "filename=\"${escapeMediaAttribute(attachment.fileName)}\" " +
        "type=\"${escapeMediaAttribute(attachment.mimeType)}\"$size>$content</attachment>"
}

private fun escapeMediaAttribute(value: String): String = value
    .replace("&", "&" + "amp;")
    .replace("\"", "&" + "quot;")
    .replace("<", "&" + "lt;")
    .replace(">", "&" + "gt;")
    .replace("'", "&" + "apos;")

internal fun unescapeMediaAttribute(value: String): String = value
    .replace("&" + "quot;", "\"")
    .replace("&" + "apos;", "'")
    .replace("&" + "lt;", "<")
    .replace("&" + "gt;", ">")
    .replace("&" + "amp;", "&")