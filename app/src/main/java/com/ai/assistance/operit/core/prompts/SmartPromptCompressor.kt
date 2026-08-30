package com.ai.assistance.operit.core.prompts

import com.ai.assistance.operit.util.AppLogger

/**
 * SmartPromptCompressor — compresses prompts to save tokens.
 *
 * Per PROJECT_PLAN.md §5.3:
 * - Removes repeated boilerplate
 * - Converts long logs into summaries
 * - Uses references instead of repeating content
 * - Has toggle to disable compression
 *
 * Usage:
 * ```kotlin
 * val compressor = SmartPromptCompressor(enabled = true)
 * val compressed = compressor.compress(originalPrompt)
 * // compressed has fewer tokens but same semantic meaning
 * ```
 */
class SmartPromptCompressor(
    private val enabled: Boolean = true
) {
    companion object {
        private const val TAG = "SmartPromptCompressor"

        // Patterns that indicate repeated/boilerplate content
        private val BOILERPLATE_PATTERNS = listOf(
            Regex("""(?m)^\s*---+\s*$"""),  // Horizontal rules
            Regex("""(?m)^\s*===+\s*$"""),  // Double horizontal rules
            Regex("""(?m)^\s*#+\s*$"""),    // Empty headers
        )

        // Patterns for long log-like content
        private val LOG_PATTERNS = listOf(
            Regex("""(?m)^[\d\-T:.Z]+\s+\w+\s+"""),
            Regex("""(?m)^\[\d{4}-\d{2}-\d{2}"""),
            Regex("""(?m)^\d{4}/\d{2}/\d{2}"""),
        )

        // Common repeated phrases that can be abbreviated
        private val REDUNDANT_PHRASES = mapOf(
            "Please note that" to "Note:",
            "It is important to" to "Important:",
            "In order to" to "To",
            "Due to the fact that" to "Because",
            "At this point in time" to "Now",
            "For the purpose of" to "For",
            "In the event that" to "If",
            "With regard to" to "Regarding",
            "In addition to" to "Also",
            "As a result of" to "Due to",
        )
    }

    /**
     * Compress a prompt to reduce token count.
     *
     * @param prompt The original prompt text
     * @return Compressed prompt with same semantic meaning
     */
    fun compress(prompt: String): String {
        if (!enabled) return prompt

        var result = prompt

        // Step 1: Remove redundant phrases
        result = removeRedundantPhrases(result)

        // Step 2: Remove empty lines and excessive whitespace
        result = removeExcessiveWhitespace(result)

        // Step 3: Compress repeated sections
        result = compressRepeatedSections(result)

        // Step 4: Summarize long log-like content
        result = summarizeLogs(result)

        // Step 5: Remove boilerplate formatting
        result = removeBoilerplate(result)

        val originalTokens = estimateTokens(prompt)
        val compressedTokens = estimateTokens(result)
        val savings = if (originalTokens > 0) {
            ((originalTokens - compressedTokens).toFloat() / originalTokens * 100).toInt()
        } else 0

        AppLogger.d(TAG, "Compressed prompt: $originalTokens → $compressedTokens tokens ($savings% savings)")

        return result.trim()
    }

    /**
     * Estimate token count (rough approximation: 1 token ≈ 4 characters).
     */
    fun estimateTokens(text: String): Int {
        return text.length / 4
    }

    /**
     * Check if compression is enabled.
     */
    fun isEnabled(): Boolean = enabled

    /**
     * Remove redundant phrases and replace with shorter alternatives.
     */
    private fun removeRedundantPhrases(text: String): String {
        var result = text
        for ((long, short) in REDUNDANT_PHRASES) {
            result = result.replace(long, short, ignoreCase = true)
        }
        return result
    }

    /**
     * Remove excessive whitespace and empty lines.
     */
    private fun removeExcessiveWhitespace(text: String): String {
        // Collapse multiple blank lines into single blank line
        return text.replace(Regex("""\n{3,}"""), "\n\n")
            .replace(Regex("""[ \t]+\n"""), "\n")
            .replace(Regex("""\n[ \t]+"""), "\n")
    }

    /**
     * Compress repeated sections (e.g., repeated instructions).
     */
    private fun compressRepeatedSections(text: String): String {
        val lines = text.split("\n")
        val compressed = mutableListOf<String>()
        var lastLine = ""
        var repeatCount = 0

        for (line in lines) {
            if (line == lastLine && line.isNotBlank()) {
                repeatCount++
            } else {
                if (repeatCount > 1) {
                    compressed.add("$lastLine (repeated $repeatCount times)")
                } else if (repeatCount == 1) {
                    compressed.add(lastLine)
                }
                if (line.isNotBlank()) {
                    compressed.add(line)
                }
                lastLine = line
                repeatCount = 0
            }
        }

        // Handle last repeated line
        if (repeatCount > 1) {
            compressed.add("$lastLine (repeated $repeatCount times)")
        } else if (repeatCount == 1 || lastLine.isNotBlank()) {
            compressed.add(lastLine)
        }

        return compressed.joinToString("\n")
    }

    /**
     * Summarize long log-like content.
     */
    private fun summarizeLogs(text: String): String {
        val lines = text.split("\n")
        val result = mutableListOf<String>()
        var logLines = mutableListOf<String>()

        for (line in lines) {
            val isLogLine = LOG_PATTERNS.any { it.containsMatchIn(line) }
            if (isLogLine) {
                logLines.add(line)
            } else {
                if (logLines.size > 3) {
                    // Summarize log block
                    result.add("[${logLines.size} log entries — ${logLines.first().take(50)}...]")
                    logLines.clear()
                } else {
                    result.addAll(logLines)
                    logLines.clear()
                }
                result.add(line)
            }
        }

        // Handle remaining log lines
        if (logLines.size > 3) {
            result.add("[${logLines.size} log entries — ${logLines.first().take(50)}...]")
        } else {
            result.addAll(logLines)
        }

        return result.joinToString("\n")
    }

    /**
     * Remove boilerplate formatting (horizontal rules, empty headers).
     */
    private fun removeBoilerplate(text: String): String {
        var result = text
        for (pattern in BOILERPLATE_PATTERNS) {
            result = pattern.replace(result, "")
        }
        return result
    }
}
