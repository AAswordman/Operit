package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.util.ImagePoolManager
import com.ai.assistance.operit.util.MediaBase64Limiter
import com.ai.assistance.operit.util.MediaPoolManager

data class MediaLink(
    val type: String,
    val id: String,
    val base64Data: String,
    val mimeType: String
)

data class ImageLink(
    val type: String,
    val id: String,
    val base64Data: String,
    val mimeType: String
)

data class MediaLinkTag(
    val type: String,
    val id: String
)

object MediaLinkParser {
    private val IMAGE_LINK_PATTERN_PLAIN = Regex(
        """<link\s+type\s*=\s*\\?["']?image\\?["']?\s+id\s*=\s*\\?["']?([^"'\s>]+)\\?["']?\s*>.*?</link>""",
        RegexOption.DOT_MATCHES_ALL
    )

    private val IMAGE_LINK_PATTERN_ESCAPED = Regex(
        """<link\s+type\s*=\s*\\?["']?image\\?["']?\s+id\s*=\s*\\?["']?([^"'\s>]+)\\?["']?\s*>.*?</link>""",
        RegexOption.DOT_MATCHES_ALL
    )

    private val LINK_PATTERN_PLAIN = Regex(
        """<link\s+type=\"?(audio|video)\"?\s+id=\"?([^\"\s>]+)\"?\s*>.*?</link>""",
        RegexOption.DOT_MATCHES_ALL
    )

    private val LINK_PATTERN_ESCAPED = Regex(
        """<link\s+type=\\\"?(audio|video)\\\"?\s+id=\\\"?([^\"\s>]+)\\\"?\s*>.*?</link>""",
        RegexOption.DOT_MATCHES_ALL
    )

    fun extractImageLinks(message: String): List<ImageLink> {
        val imageLinks = mutableListOf<ImageLink>()
        val seenIds = mutableSetOf<String>()

        fun collectFromPattern(pattern: Regex) {
            pattern.findAll(message).forEach { match ->
                val id = match.groupValues[1]
                if (id == "error") {
                    return@forEach
                }
                if (!seenIds.add(id)) {
                    return@forEach
                }
                val imageData = ImagePoolManager.getImage(id) ?: return@forEach
                imageLinks.add(
                    ImageLink(
                        type = "image",
                        id = id,
                        base64Data = imageData.base64,
                        mimeType = imageData.mimeType
                    )
                )
            }
        }

        collectFromPattern(IMAGE_LINK_PATTERN_PLAIN)
        collectFromPattern(IMAGE_LINK_PATTERN_ESCAPED)

        return imageLinks
    }

    fun extractImageLinkIds(message: String): List<String> {
        val ids = mutableListOf<String>()
        val seenIds = mutableSetOf<String>()

        fun collectFromPattern(pattern: Regex) {
            pattern.findAll(message).forEach { match ->
                val id = match.groupValues[1]
                if (id == "error") {
                    return@forEach
                }
                if (seenIds.add(id)) {
                    ids.add(id)
                }
            }
        }

        collectFromPattern(IMAGE_LINK_PATTERN_PLAIN)
        collectFromPattern(IMAGE_LINK_PATTERN_ESCAPED)

        return ids
    }

    fun removeImageLinks(message: String): String {
        return message
            .replace(IMAGE_LINK_PATTERN_PLAIN, "")
            .replace(IMAGE_LINK_PATTERN_ESCAPED, "")
    }

    fun replaceImageLinks(message: String, replacer: (id: String) -> String): String {
        var result = message
        val patterns = listOf(IMAGE_LINK_PATTERN_PLAIN, IMAGE_LINK_PATTERN_ESCAPED)
        patterns.forEach { pattern ->
            result = pattern.replace(result) { match ->
                val id = match.groupValues.getOrNull(1) ?: return@replace ""
                if (id == "error") "" else replacer(id)
            }
        }
        return result
    }

    fun hasImageLinks(message: String): Boolean {
        return IMAGE_LINK_PATTERN_PLAIN.containsMatchIn(message) ||
            IMAGE_LINK_PATTERN_ESCAPED.containsMatchIn(message)
    }

    fun extractMediaLinks(message: String): List<MediaLink> {
        val links = mutableListOf<MediaLink>()
        val seenIds = mutableSetOf<String>()

        fun collectFromPattern(pattern: Regex) {
            pattern.findAll(message).forEach { match ->
                val type = match.groupValues[1]
                val id = match.groupValues[2]

                if (id == "error") {
                    return@forEach
                }

                if (!seenIds.add("$type:$id")) {
                    return@forEach
                }

                val mediaData = MediaPoolManager.getMedia(id) ?: return@forEach

                val limited = MediaBase64Limiter.limitBase64ForAi(mediaData.base64, mediaData.mimeType)
                    ?: return@forEach
                links.add(
                    MediaLink(
                        type = type,
                        id = id,
                        base64Data = limited.base64,
                        mimeType = limited.mimeType
                    )
                )
            }
        }

        collectFromPattern(LINK_PATTERN_PLAIN)
        collectFromPattern(LINK_PATTERN_ESCAPED)

        return links
    }

    fun extractMediaLinkTags(message: String): List<MediaLinkTag> {
        val tags = mutableListOf<MediaLinkTag>()
        val seenIds = mutableSetOf<String>()

        fun collectFromPattern(pattern: Regex) {
            pattern.findAll(message).forEach { match ->
                val type = match.groupValues[1]
                val id = match.groupValues[2]
                if (id == "error") {
                    return@forEach
                }
                if (!seenIds.add("$type:$id")) {
                    return@forEach
                }
                tags.add(MediaLinkTag(type = type, id = id))
            }
        }

        collectFromPattern(LINK_PATTERN_PLAIN)
        collectFromPattern(LINK_PATTERN_ESCAPED)

        return tags
    }

    fun replaceMediaLinks(message: String, replacer: (type: String, id: String) -> String): String {
        var result = message
        val patterns = listOf(LINK_PATTERN_PLAIN, LINK_PATTERN_ESCAPED)
        patterns.forEach { pattern ->
            result = pattern.replace(result) { match ->
                val type = match.groupValues.getOrNull(1) ?: return@replace ""
                val id = match.groupValues.getOrNull(2) ?: return@replace ""
                if (id == "error") "" else replacer(type, id)
            }
        }
        return result
    }

    fun removeMediaLinks(message: String): String {
        return message
            .replace(LINK_PATTERN_PLAIN, "")
            .replace(LINK_PATTERN_ESCAPED, "")
    }

    fun hasMediaLinks(message: String): Boolean {
        return LINK_PATTERN_PLAIN.containsMatchIn(message) || LINK_PATTERN_ESCAPED.containsMatchIn(message)
    }
}
