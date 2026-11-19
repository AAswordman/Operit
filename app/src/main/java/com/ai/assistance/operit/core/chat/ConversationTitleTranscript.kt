package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.api.chat.llmprovider.MediaLinkParser
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.util.ChatUtils

/**
 * Builds the dialogue transcript that the title model reads when the user asks for an AI summary
 * title from the chat history long-press menu.
 *
 * The transcript keeps the newest messages when the character budget runs out, because the latest
 * turns say what the conversation is currently about. Tool calls, reasoning blocks, memory tags and
 * media links are stripped so the model reads dialogue text instead of protocol markup.
 */
object ConversationTitleTranscript {
    const val MAX_TRANSCRIPT_CHARS = 6_000
    const val MAX_MESSAGES = 40
    const val MAX_MESSAGE_CHARS = 600

    private const val USER_LABEL = "User"
    private const val ASSISTANT_LABEL = "Assistant"
    private const val IMAGE_PLACEHOLDER = "[image]"
    private const val MEDIA_PLACEHOLDER = "[media]"

    private val whitespaceRegex = Regex("\\s+")

    /** Transcript text plus how many messages contributed to it. */
    data class Result(
        val transcript: String,
        val messageCount: Int,
    ) {
        val isUsable: Boolean
            get() = transcript.isNotBlank()
    }

    /**
     * Renders the readable part of [messages] as `label: content` lines, oldest first.
     *
     * Messages whose sender is neither `user` nor `ai` are skipped: summaries and system notices are
     * generated text, not something the user wrote or read.
     */
    fun build(
        messages: List<ChatMessage>,
        maxChars: Int = MAX_TRANSCRIPT_CHARS,
        maxMessages: Int = MAX_MESSAGES,
    ): Result {
        if (messages.isEmpty() || maxChars <= 0 || maxMessages <= 0) {
            return Result(transcript = "", messageCount = 0)
        }

        val selectedLines = ArrayList<String>()
        var usedChars = 0
        for (message in messages.asReversed()) {
            if (selectedLines.size >= maxMessages) break
            val label =
                when (message.sender) {
                    "user" -> USER_LABEL
                    "ai" -> ASSISTANT_LABEL
                    else -> continue
                }
            val content = condense(cleanContent(message.content))
            if (content.isBlank()) continue

            val line = "$label: $content"
            val separatorChars = if (selectedLines.isEmpty()) 0 else 1
            if (usedChars + line.length + separatorChars > maxChars) {
                if (selectedLines.isNotEmpty()) break
                selectedLines.add(line.take(maxChars))
                break
            }
            selectedLines.add(line)
            usedChars += line.length + separatorChars
        }

        return Result(
            transcript = selectedLines.asReversed().joinToString("\n"),
            messageCount = selectedLines.size
        )
    }

    private fun cleanContent(rawContent: String): String {
        if (rawContent.isBlank()) return ""

        val hadImageLink = MediaLinkParser.hasImageLinks(rawContent)
        val hadMediaLink = MediaLinkParser.hasMediaLinks(rawContent)
        var text = rawContent
        if (hadImageLink) {
            text = MediaLinkParser.removeImageLinks(text)
        }
        if (hadMediaLink) {
            text = MediaLinkParser.removeMediaLinks(text)
        }
        text = ChatUtils.removeThinkingContent(text)
        text =
            text
                .replace(ChatMarkupRegex.toolOrToolResultBlock, " ")
                .replace(ChatMarkupRegex.toolSelfClosingTag, " ")
                .replace(ChatMarkupRegex.toolResultSelfClosingTag, " ")
                .replace(ChatMarkupRegex.memoryTag, " ")
                .replace(ChatMarkupRegex.emotionTag, " ")
                .replace(ChatMarkupRegex.attachmentTag, " ")
                .replace(ChatMarkupRegex.attachmentSelfClosingTag, " ")
                .replace(ChatMarkupRegex.workspaceAttachmentTag, " ")
                .replace(ChatMarkupRegex.statusTag, " ")
                .replace(ChatMarkupRegex.statusSelfClosingTag, " ")
        text = text.replace(whitespaceRegex, " ").trim()

        if (text.isNotBlank()) return text
        return when {
            hadImageLink -> IMAGE_PLACEHOLDER
            hadMediaLink -> MEDIA_PLACEHOLDER
            else -> ""
        }
    }

    private fun condense(content: String): String {
        if (content.length <= MAX_MESSAGE_CHARS) return content
        return content.take(MAX_MESSAGE_CHARS).trimEnd() + "…"
    }
}
