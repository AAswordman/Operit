package com.ai.assistance.operit.util

import com.ai.assistance.operit.util.AppLogger

object TtsCleaner {
    private const val TAG = "TtsCleaner"

    /**
     * Cleans a given text by removing all parts that match the provided regex pattern.
     *
     * @param text The input text to clean.
     * @param regexPattern The regex pattern to use for cleaning. If the pattern is blank,
     *                     the original text will be returned.
     * @return The cleaned text.
     */
    fun clean(text: String, regexPattern: String): String {
        if (regexPattern.isBlank()) {
            AppLogger.d(TAG, "clean(single): Empty regex pattern, returning original text")
            return text
        }

        return try {
            val regex = Regex(regexPattern)
            val cleanedText = text.replace(regex, "")
            AppLogger.d(TAG, "clean(single): Original='$text' | Pattern='$regexPattern' | Cleaned='$cleanedText'")
            cleanedText
        } catch (e: Exception) {
            AppLogger.e(TAG, "clean(single): Invalid regex pattern '$regexPattern', returning original text", e)
            text
        }
    }

    /**
     * Cleans a given text by removing all parts that match any of the provided regex patterns.
     *
     * @param text The input text to clean.
     * @param regexPatterns The list of regex patterns to use for cleaning. Empty or blank patterns are skipped.
     * @return The cleaned text.
     */
    fun clean(text: String, regexPatterns: List<String>): String {
        if (regexPatterns.isEmpty()) {
            AppLogger.d(TAG, "clean(list): Empty pattern list, returning original text")
            return text
        }

        AppLogger.d(TAG, "clean(list): Starting with text='$text' | Patterns count=${regexPatterns.size}")
        
        var cleanedText = text
        regexPatterns.forEachIndexed { index, pattern ->
            if (pattern.isNotBlank()) {
                try {
                    val regex = Regex(pattern)
                    val beforeClean = cleanedText
                    cleanedText = cleanedText.replace(regex, "")
                    if (beforeClean != cleanedText) {
                        AppLogger.d(TAG, "clean(list): Pattern[$index]='$pattern' matched | Before='$beforeClean' | After='$cleanedText'")
                    } else {
                        AppLogger.d(TAG, "clean(list): Pattern[$index]='$pattern' no match")
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "clean(list): Invalid regex pattern[$index]='$pattern', skipping", e)
                }
            } else {
                AppLogger.d(TAG, "clean(list): Pattern[$index] is blank, skipping")
            }
        }
        
        AppLogger.d(TAG, "clean(list): Final result='$cleanedText'")
        return cleanedText
    }

    /**
     * Resolves the spoken reply body of a stored message, then applies the user's cleaner patterns.
     *
     * Ordering is what makes this correct. Reasoning, tool and status blocks are only recognizable
     * while their tags are intact, and the built-in "Remove XML tags" template is the pattern
     * `<[^>]+>`. Applying that pattern first deletes the tags but keeps the inner text, so a
     * thought block turns into ordinary unmarked prose that nothing downstream can identify — the
     * whole chain of thought then gets read aloud. Extracting the body first keeps the user
     * patterns matching against reply body only.
     *
     * Markdown is deliberately still intact at this point. Patterns such as `\*\*[^*]+\*\*` rely
     * on the markers, and the Markdown flattening that removes them runs downstream in
     * [WaifuMessageProcessor.cleanContentForWaifu].
     *
     * @param text The raw stored message content.
     * @param regexPatterns The user's TTS cleaner patterns.
     * @return The reply body after block removal and user pattern removal.
     */
    fun cleanForSpeech(text: String, regexPatterns: List<String>): String {
        val replyBody = extractReplyBody(text)
        if (replyBody.length != text.length) {
            AppLogger.d(
                TAG,
                "cleanForSpeech: resolved reply body before user patterns | rawLen=${text.length} bodyLen=${replyBody.length}"
            )
        }
        return clean(replyBody, regexPatterns)
    }

    /**
     * Drops the blocks that are never part of the spoken reply: provider protocol metadata,
     * reasoning, search sources, tool calls, tool results, status lines and emotion markers.
     * Mirrors the block set that [WaifuMessageProcessor.cleanContentForWaifu] strips, applied
     * before user patterns instead of after them so a tag-stripping pattern cannot orphan their
     * contents.
     *
     * Provider metadata matters here as much as reasoning does: those `<meta>` tags carry base64
     * payloads, so letting `<[^>]+>` delete only the tags would leave the payload to be read out
     * loud. The `ChatMarkupRegex` passes run first because they tolerate attributes and letter
     * case (`<think type="...">`, `<THINK>`); [ChatUtils.removeThinkingContent] follows to cover
     * blocks that never close, which a paired-tag regex cannot match.
     */
    private fun extractReplyBody(text: String): String {
        return ChatUtils.removeThinkingContent(
            text
                .let(ChatMarkupRegex::removeGeminiThoughtSignatureMeta)
                .let(ChatMarkupRegex::removeOpenAiResponsesProtocolMeta)
                .replace(ChatMarkupRegex.thinkTag, "")
                .replace(ChatMarkupRegex.thinkSelfClosingTag, "")
                .replace(ChatMarkupRegex.searchTag, "")
                .replace(ChatMarkupRegex.searchSelfClosingTag, "")
                .replace(ChatMarkupRegex.statusTag, "")
                .replace(ChatMarkupRegex.statusSelfClosingTag, "")
                .replace(ChatMarkupRegex.toolTag, "")
                .replace(ChatMarkupRegex.toolSelfClosingTag, "")
                .replace(ChatMarkupRegex.toolResultTag, "")
                .replace(ChatMarkupRegex.toolResultSelfClosingTag, "")
                .replace(ChatMarkupRegex.emotionTag, "")
        )
    }
}
