package com.ai.assistance.operit.core.chat.media

import com.ai.assistance.operit.util.media.ImagePoolManager
import com.ai.assistance.operit.core.chat.attachment.AttachmentInfo
import com.ai.assistance.operit.util.media.MediaBase64Limiter
import com.ai.assistance.operit.util.media.MediaPoolManager

data class MediaLink(
    val type: String,
    val id: String,
    val base64Data: String,
    val mimeType: String,
    val fileName: String? = null,
)

data class ImageLink(
    val type: String,
    val id: String,
    val base64Data: String,
    val mimeType: String
)

data class MediaLinkTag(
    val type: String,
    val id: String,
    val fileName: String? = null,
)

object MediaLinkParser {
    // Match the whole link block while allowing arbitrary attribute order and escaping.
    private val LINK_PATTERN = Regex(
        """<link\b(?=[^>]*\btype\s*=\s*\\*["']?(image|audio|video|file)\\*["']?)(?=[^>]*\bid\s*=\s*\\*["']?([^"'\\\s>]+)\\*["']?)[^>]*(?:/>|>.*?</link>)""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )

    private fun isMediaType(type: String): Boolean =
        type.equals("audio", ignoreCase = true) ||
            type.equals("video", ignoreCase = true) ||
            type.equals("file", ignoreCase = true)

    private fun matchesForType(message: String, type: String): Sequence<MatchResult> =
        LINK_PATTERN.findAll(message).filter {
            it.groupValues[1].equals(type, ignoreCase = true)
        }

    private fun matchesForMediaType(message: String): Sequence<MatchResult> =
        LINK_PATTERN.findAll(message).filter { isMediaType(it.groupValues[1]) }

    private val FILE_LINK_PATTERN_PLAIN = Regex(
        """<link\s+type\s*=\s*\"file\"\s+id\s*=\s*\"([^\"]+)\"\s+filename\s*=\s*\"([^\"]+)\"\s*>.*?</link>""",
        RegexOption.DOT_MATCHES_ALL,
    )

    private val FILE_LINK_PATTERN_ESCAPED = Regex(
        """<link\s+type=\\\"file\\\"\s+id=\\\"([^\\\"]+)\\\"\s+filename=\\\"([^\\\"]+)\\\"\s*>.*?</link>""",
        RegexOption.DOT_MATCHES_ALL,
    )

    private val FILE_NAME_ATTRIBUTE_PATTERN = Regex(
        """filename\s*=\s*\\*["']?([^"'\\>]+)\\*["']?""",
        RegexOption.IGNORE_CASE,
    )

    private fun extractFileName(match: MatchResult): String? =
        FILE_NAME_ATTRIBUTE_PATTERN.find(match.value)
            ?.groupValues
            ?.get(1)
            ?.let(::unescapeXml)
            ?.takeIf { it.isNotBlank() }

    fun extractImageLinks(message: String): List<ImageLink> {
        val imageLinks = mutableListOf<ImageLink>()
        val seenIds = mutableSetOf<String>()
        for (match in matchesForType(message, "image")) {
            val id = match.groupValues[2]
            if (id == "error" || !seenIds.add(id)) continue
            val imageData = ImagePoolManager.getImage(id) ?: continue
            imageLinks.add(
                ImageLink(
                    type = "image",
                    id = id,
                    base64Data = imageData.base64,
                    mimeType = imageData.mimeType
                )
            )
        }
        return imageLinks
    }

    fun extractImageLinkIds(message: String): List<String> {
        val ids = mutableListOf<String>()
        val seenIds = mutableSetOf<String>()
        for (match in matchesForType(message, "image")) {
            val id = match.groupValues[2]
            if (id != "error" && seenIds.add(id)) {
                ids.add(id)
            }
        }
        return ids
    }

    /** 关联模型图片链接后紧邻的附件说明，展示时只保留同一张图的一份缩略图。 */
    fun extractImageAttachmentBindings(message: String): Map<String, String> = buildMap {
        for (link in matchesForType(message, "image")) {
            val id = link.groupValues[2]
            if (id == "error") continue
            val remaining = message.substring(link.range.last + 1).trimStart()
            val attachment = com.ai.assistance.operit.core.chat.protocol.ChatMarkupRegex.attachmentDataTag.find(remaining)
                ?.takeIf { it.range.first == 0 }
                ?: com.ai.assistance.operit.core.chat.protocol.ChatMarkupRegex.attachmentDataSelfClosingTag.find(remaining)
                    ?.takeIf { it.range.first == 0 }
            if (attachment != null && attachment.groupValues[3].startsWith("image/", ignoreCase = true)) {
                put(unescapeXml(attachment.groupValues[1]), id)
            }
        }
    }

    /** 仅关联紧邻且类型一致的原文件说明，旧的纯池链接仍按原格式处理。 */
    fun extractMediaAttachmentBindings(message: String): Map<String, AttachmentInfo> = buildMap {
        for (link in matchesForMediaType(message)) {
            val type = link.groupValues[1].lowercase()
            if (type != "audio" && type != "video") continue
            val id = link.groupValues[2]
            if (id == "error") continue
            val remaining = message.substring(link.range.last + 1).trimStart()
            val attachment = com.ai.assistance.operit.core.chat.protocol.ChatMarkupRegex.attachmentDataTag.find(remaining)
                ?.takeIf { it.range.first == 0 }
                ?: com.ai.assistance.operit.core.chat.protocol.ChatMarkupRegex.attachmentDataSelfClosingTag.find(remaining)
                    ?.takeIf { it.range.first == 0 }
            if (attachment != null && attachment.groupValues[3].startsWith("$type/", ignoreCase = true)) {
                put(id, AttachmentInfo(
                    filePath = unescapeXml(attachment.groupValues[1]),
                    fileName = unescapeXml(attachment.groupValues[2]),
                    mimeType = attachment.groupValues[3],
                    fileSize = attachment.groupValues[4].toLongOrNull() ?: 0L,
                    content = attachment.groupValues[5],
                ))
            }
        }
    }

    fun removeImageLinks(message: String): String =
        LINK_PATTERN.replace(message) { match ->
            if (match.groupValues[1].equals("image", ignoreCase = true)) "" else match.value
        }

    /** 只移除已确认可用的图片引用，源码中的示例标签继续作为工具文本保留。 */
    fun removeImageLinks(message: String, imageIds: Set<String>): String =
        LINK_PATTERN.replace(message) { match ->
            if (match.groupValues[1].equals("image", ignoreCase = true) && match.groupValues[2] in imageIds) ""
            else match.value
        }

    fun replaceImageLinks(message: String, replacer: (id: String) -> String): String =
        LINK_PATTERN.replace(message) { match ->
            if (!match.groupValues[1].equals("image", ignoreCase = true)) {
                match.value
            } else {
                val id = match.groupValues[2]
                if (id == "error") "" else replacer(id)
            }
        }

    fun hasImageLinks(message: String): Boolean = matchesForType(message, "image").any()

    fun extractMediaLinks(message: String): List<MediaLink> {
        val links = mutableListOf<MediaLink>()
        val seenIds = mutableSetOf<String>()
        for (match in matchesForMediaType(message)) {
            val type = match.groupValues[1].lowercase()
            val id = match.groupValues[2]
            if (id == "error") continue
            val key = "$type:$id"
            if (key in seenIds) continue
            val fileName = if (type == "file") extractFileName(match) else null
            if (type == "file" && fileName == null) continue
            seenIds.add(key)
            val mediaData = MediaPoolManager.getMedia(id) ?: continue
            val limited = MediaBase64Limiter.limitBase64ForAi(mediaData.base64, mediaData.mimeType) ?: continue
            links.add(
                MediaLink(
                    type = type,
                    id = id,
                    base64Data = limited.base64,
                    mimeType = limited.mimeType,
                    fileName = fileName,
                )
            )
        }
        return links
    }

    fun extractMediaLinkTags(message: String): List<MediaLinkTag> {
        val tags = mutableListOf<MediaLinkTag>()
        val seenIds = mutableSetOf<String>()
        for (match in matchesForMediaType(message)) {
            val type = match.groupValues[1].lowercase()
            val id = match.groupValues[2]
            if (id == "error") continue
            val key = "$type:$id"
            if (key in seenIds) continue
            val fileName = if (type == "file") extractFileName(match) else null
            if (type == "file" && fileName == null) continue
            seenIds.add(key)
            tags.add(MediaLinkTag(type = type, id = id, fileName = fileName))
        }

        return tags
    }

    fun replaceMediaLinks(message: String, replacer: (type: String, id: String) -> String): String =
        LINK_PATTERN.replace(message) { match ->
            val type = match.groupValues[1].lowercase()
            val id = match.groupValues[2]
            if (!isMediaType(type)) {
                match.value
            } else if (id == "error") {
                ""
            } else {
                replacer(type, id)
            }
        }

    fun removeMediaLinks(message: String): String =
        LINK_PATTERN.replace(message) { match ->
            if (isMediaType(match.groupValues[1])) "" else match.value
        }

    fun hasMediaLinks(message: String): Boolean =
        matchesForMediaType(message).any()

    private fun unescapeXml(value: String): String {
        return value
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    }
}
