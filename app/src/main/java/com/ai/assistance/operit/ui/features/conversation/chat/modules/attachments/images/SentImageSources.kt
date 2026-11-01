package com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.images

import com.ai.assistance.operit.core.chat.media.MediaLinkParser
import com.ai.assistance.operit.core.chat.protocol.ChatMarkupRegex
import com.ai.assistance.operit.core.chat.media.unescapeMediaAttribute

/** 同一图片的池链接和附件描述合为一项；不修改已保存消息的 XML。 */
internal fun sentImageSources(content: String): List<SentMessageImage> {
    val matches = (ChatMarkupRegex.attachmentDataTag.findAll(content) +
        ChatMarkupRegex.attachmentDataSelfClosingTag.findAll(content)).sortedBy { it.range.first }
    var previousEnd = -1
    val attachments = matches.filter { match ->
        if (match.range.first <= previousEnd) false else {
            previousEnd = match.range.last
            match.groupValues[3].startsWith("image/", ignoreCase = true)
        }
    }.associate { unescapeMediaAttribute(it.groupValues[1]) to unescapeMediaAttribute(it.groupValues[2]) }
    val bindings = MediaLinkParser.extractImageAttachmentBindings(content)
    val images = MediaLinkParser.extractImageLinkIds(content).map { poolId ->
        val attachmentId = bindings.entries.firstOrNull { it.value == poolId }?.key
        // 已发送附件直接绑定原始文件，模型图片池的淘汰不影响聊天浏览。
        if (attachmentId != null) SentMessageImage(id = attachmentId, title = attachments[attachmentId])
        else SentMessageImage(id = poolId, poolId = poolId)
    }
    return images + attachments.filterKeys { it !in bindings }.map { (id, name) -> SentMessageImage(id, name) }
}
