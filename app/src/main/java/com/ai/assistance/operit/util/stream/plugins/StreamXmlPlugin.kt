package com.ai.assistance.operit.util.stream.plugins

import com.ai.assistance.operit.util.stream.*

private const val GROUP_TAG_NAME = 1
private const val GROUP_CONTENT = 2

/**
 * A stream processing plugin to identify and process XML-formatted data streams. This
 * implementation uses the group capturing mechanism of the StreamKmpGraph. This version has a known
 * limitation: it does not handle nested tags of the same name.
 *
 * @param includeTagsInOutput If true, the XML tags themselves (`<tag>`, `</tag>`) will be included
 * in the output stream. If false, they will be filtered out, leaving only the content between the
 * tags.
 */
class StreamXmlPlugin(private val includeTagsInOutput: Boolean = true) : StreamPlugin {

    override var state: PluginState = PluginState.IDLE
        private set

    private var startTagMatcher: StreamKmpGraph
    private var endTagMatcher: StreamKmpGraph? = null
    // Allow matching a new start tag immediately after we just closed an end tag, even if not at start of line
    private var allowStartAfterEndTag: Boolean = false
    private var allowStartAfterPunctuation: Boolean = false
    private var lastChar: Char = '\u0000'

    private val punctuationTriggers =
            setOf('，', '。', '？', '！', '：', '（', '）', '【', '】', '《', '》', ':', ',', '.', '?', '!', '~', '～')
    private val emojiContinuationChars = setOf('\u200D', '\uFE0E', '\uFE0F', '\u20E3')

    init {
        startTagMatcher =
                StreamKmpGraphBuilder()
                        .build(
                                kmpPattern {
                                    char('<')
                                    // Group 1: Capture the tag name. A valid tag name must start
                                    // with a letter.
                                    // This prevents matching comments (<!--) or closing tags (</).
                                    group(GROUP_TAG_NAME) {
                                        predicate("asciiXmlTagFirstChar") {
                                            it in 'A'..'Z' || it in 'a'..'z'
                                        }
                                        greedyStar {
                                            predicate("xmlTagNameContinuation") {
                                                (it in 'A'..'Z') || (it in 'a'..'z') || (it in '0'..'9') || it == '_'
                                            }
                                        }
                                    }
                                    // Optional: Match attributes until the tag closes
                                    greedyStar { notChar('>') }
                                    char('>')
                                }
                        )

        reset()
    }

    /**
     * Processes a single character for XML stream parsing and decides if it should be emitted. The
     * return value is used by `splitBy` as a filter.
     */
    override fun processChar(c: Char, atStartOfLine: Boolean): Boolean {
        fun finish(result: Boolean): Boolean {
            lastChar = c
            return result
        }

        if (state == PluginState.PROCESSING) {
            // We are inside a tag, looking for the end tag.
            val matcher = endTagMatcher!!
            val result = matcher.processChar(c)

            return when (result) {
                is StreamKmpMatchResult.Match -> {
                    // End tag fully matched. Reset state and filter this last character if needed.
                    StreamLogger.i("StreamXmlPlugin", "Found end tag. Switching to IDLE.")
                    // Enable one-time allowance for starting a new tag right after this end tag
                    allowStartAfterEndTag = true
                    allowStartAfterPunctuation = false
                    reset()
                    finish(includeTagsInOutput)
                }
                is StreamKmpMatchResult.InProgress -> {
                    // We are in the middle of matching the end tag (e.g., '</', '</t', etc.).
                    // The emission of these characters depends on the flag.
                    finish(includeTagsInOutput)
                }
                is StreamKmpMatchResult.NoMatch -> {
                    // The character `c` did not match the next char of the end tag.
                    // This means it's regular content between tags.
                    finish(true)
                }
            }
        } else {
            // We are in IDLE or TRYING state, looking for a start tag.
            val previousState = state
            when (val result = startTagMatcher.processChar(c)) {
                is StreamKmpMatchResult.Match -> {
                    val tagName = result.groups[GROUP_TAG_NAME]
                    if (tagName != null) {
                        if (lastChar == '/') {
                            // Treat self-closing tags like <br/> as plain text to avoid entering XML mode.
                            reset()
                            return finish(true)
                        }
                        StreamLogger.i(
                                "StreamXmlPlugin",
                                "Found start tag '$tagName'. Switching to PROCESSING."
                        )
                        state = PluginState.PROCESSING
                        // Consuming this as a new start clears the post-end allowance
                        allowStartAfterEndTag = false
                        allowStartAfterPunctuation = false
                        // We have a full start tag. Configure the end tag matcher.
                        endTagMatcher =
                                StreamKmpGraphBuilder()
                                        .build(
                                                kmpPattern {
                                                    literal("</")
                                                    literal(tagName)
                                                    char('>')
                                                }
                                        )
                        startTagMatcher.reset()
                    } else {
                        // Should not happen, but as a safeguard:
                        reset()
                    }
                    return finish(includeTagsInOutput)
                }
                is StreamKmpMatchResult.InProgress -> {
                    state = PluginState.TRYING
                    updatePunctuationAllowance(c)
                    return finish(includeTagsInOutput)
                }
                is StreamKmpMatchResult.NoMatch -> {
                    // If we were trying and the match failed, we must reset to idle.
                    if (previousState == PluginState.TRYING) {
                        reset()
                    }
                    // Clear the allowance if we failed to start a new tag
                    allowStartAfterEndTag = false
                    allowStartAfterPunctuation = false
                    // This is a default character, not part of a tag managed by this plugin.
                    return finish(handleDefaultCharacter(c))
                }
            }
        }
    }

    /** Initializes the plugin to its default state. */
    override fun initPlugin(): Boolean {
        reset()
        return true
    }

    /** Destroys the plugin. No-op as listener is removed. */
    override fun destroy() {}

    /** Resets the plugin state. */
    override fun reset() {
        endTagMatcher = null
        startTagMatcher.reset()
        state = PluginState.IDLE
        lastChar = '\u0000'
    }

    private fun handleDefaultCharacter(c: Char): Boolean {
        updatePunctuationAllowance(c)
        return true
    }

    private fun updatePunctuationAllowance(c: Char) {
        when {
            punctuationTriggers.contains(c) || isEmojiTrigger(c) -> {
                allowStartAfterPunctuation = true
            }
            c == ' ' || c == '\t' || isEmojiContinuationChar(c) -> {
                // Keep current state so `<` after spaces/tabs or emoji joiners/selectors still benefits.
            }
            else -> {
                allowStartAfterPunctuation = false
            }
        }
    }

    private fun isEmojiTrigger(c: Char): Boolean {
        // Most modern emojis are surrogate pairs in UTF-16. Treat either half as a trigger.
        if (Character.isSurrogate(c)) {
            return true
        }
        // BMP emoji/symbols (e.g. ☀, ❤) are usually "OTHER_SYMBOL".
        return Character.getType(c) == Character.OTHER_SYMBOL.toInt()
    }

    private fun isEmojiContinuationChar(c: Char): Boolean = emojiContinuationChars.contains(c)
}
