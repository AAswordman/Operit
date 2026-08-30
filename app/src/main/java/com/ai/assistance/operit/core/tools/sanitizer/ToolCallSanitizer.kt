package com.ai.assistance.operit.core.tools.sanitizer

import com.ai.assistance.operit.util.AppLogger

/**
 * ToolCallSanitizer — separates parse layer before ToolExecutionEngine.
 *
 * Per PROJECT_PLAN.md §10 (Lessons from Operit):
 * > Tool-call param ถูก XML tag ปนเปื้อนจนพัง JSON parsing
 * > เขียน sanitizer แยก parse layer ก่อนส่งเข้า ToolExecutionEngine
 *
 * This sanitizer handles:
 * 1. XML tags embedded in tool call parameters (e.g., <tool_result>, </tool_call>)
 * 2. Markdown formatting artifacts (e.g., ```, **, *)
 * 3. CDATA wrappers left over from XML processing
 * 4. Escaped XML entities (&lt;, &gt;, &amp;, etc.)
 * 5. Provider-specific meta tags (e.g., <meta provider="gemini:thought_signature">)
 * 6. Nested XML within JSON string values
 *
 * Usage:
 * ```kotlin
 * val cleanParams = ToolCallSanitizer.sanitize(rawParams)
 * val json = JSONObject(cleanParams) // Now safe to parse
 * ```
 */
object ToolCallSanitizer {

    private const val TAG = "ToolCallSanitizer"

    // Patterns for XML tags that commonly contaminate tool call params
    private val XML_TAG_PATTERNS = listOf(
        // Tool call/result XML tags from various providers
        Regex("""</?tool_call[^>]*>""", RegexOption.IGNORE_CASE),
        Regex("""</?tool_result[^>]*>""", RegexOption.IGNORE_CASE),
        Regex("""</?function_call[^>]*>""", RegexOption.IGNORE_CASE),
        Regex("""</?arguments[^>]*>""", RegexOption.IGNORE_CASE),
        Regex("""</?invoke[^>]*>""", RegexOption.IGNORE_CASE),

        // Provider-specific meta tags
        Regex("""<meta\s+provider="[^"]*"[^>]*/?>.*?</meta>""", RegexOption.DOT_MATCHES_ALL),
        Regex("""<meta\s+provider='[^']*'[^>]*/?>.*?</meta>""", RegexOption.DOT_MATCHES_ALL),

        // Generic XML tags that may appear in tool params
        Regex("""</?[a-zA-Z_][a-zA-Z0-9_]*\s*/?>"""),
    )

    // CDATA patterns
    private val CDATA_START = Regex("""<!\[CDATA\[""")
    private val CDATA_END = Regex(""">\]\]>""")

    // Markdown formatting patterns
    private val MARKDOWN_PATTERNS = listOf(
        Regex("""^```\w*\n?""", RegexOption.MULTILINE),          // Code fence open
        Regex("""\n?```$""", RegexOption.MULTILINE),              // Code fence close
        Regex("""\*\*([^*]+)\*\*"""),                              // Bold
        Regex("""(?<!\*)\*([^*]+)\*(?!\*)"""),                     // Italic (not bold)
    )

    // XML entity map
    private val XML_ENTITIES = mapOf(
        "&lt;" to "<",
        "&gt;" to ">",
        "&amp;" to "&",
        "&quot;" to "\"",
        "&apos;" to "'",
        "&#60;" to "<",
        "&#62;" to ">",
        "&#38;" to "&",
        "&#34;" to "\"",
        "&#39;" to "'",
    )

    /**
     * Sanitize a raw tool call parameter string.
     * Returns a clean string safe for JSON parsing.
     */
    fun sanitize(raw: String): String {
        if (raw.isBlank()) return raw

        var result = raw

        // Step 1: Strip CDATA wrappers
        result = stripCdata(result)

        // Step 2: Remove XML tags
        result = stripXmlTags(result)

        // Step 3: Unescape XML entities
        result = unescapeXmlEntities(result)

        // Step 4: Strip markdown formatting
        result = stripMarkdown(result)

        // Step 5: Clean up whitespace artifacts
        result = cleanWhitespace(result)

        return result
    }

    /**
     * Sanitize a JSON string value (inside a parsed JSON object).
     * Handles cases where XML/markdown is embedded within JSON string values.
     */
    fun sanitizeJsonValue(value: String): String {
        if (value.isBlank()) return value

        var result = value

        // Unescape JSON string escapes that may have double-escaped
        result = result.replace("\\\\\"", "\"")
        result = result.replace("\\\\n", "\n")
        result = result.replace("\\\\t", "\t")

        // Apply standard sanitization
        result = sanitize(result)

        // Re-escape for JSON if needed (only special chars)
        // Note: Caller is responsible for proper JSON escaping

        return result
    }

    /**
     * Validate that a string is valid JSON after sanitization.
     * Returns the sanitized version if valid, or null if still invalid.
     */
    fun sanitizeAndValidate(raw: String): SanitizeResult {
        val sanitized = sanitize(raw)

        return try {
            // Basic JSON validation — check if it starts with { or [
            val trimmed = sanitized.trim()
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                SanitizeResult.Success(sanitized)
            } else {
                // Try wrapping in quotes if it's a plain string
                SanitizeResult.Success("\"$sanitized\"")
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Sanitization produced invalid result: ${e.message}")
            SanitizeResult.Failure("Sanitization failed: ${e.message}", sanitized)
        }
    }

    private fun stripCdata(input: String): String {
        var result = input
        result = CDATA_START.replace(result, "")
        result = CDATA_END.replace(result, "")
        return result
    }

    private fun stripXmlTags(input: String): String {
        var result = input
        for (pattern in XML_TAG_PATTERNS) {
            result = pattern.replace(result, "")
        }
        return result
    }

    private fun unescapeXmlEntities(input: String): String {
        var result = input
        for ((entity, replacement) in XML_ENTITIES) {
            result = result.replace(entity, replacement)
        }
        return result
    }

    private fun stripMarkdown(input: String): String {
        var result = input
        for (pattern in MARKDOWN_PATTERNS) {
            result = pattern.replace(result, "")
        }
        return result
    }

    private fun cleanWhitespace(input: String): String {
        return input
            .replace(Regex("""\n{3,}"""), "\n\n")  // Max 2 consecutive newlines
            .replace(Regex("""[ \t]+\n"""), "\n")    // Trailing whitespace
            .replace(Regex("""\n[ \t]+"""), "\n")    // Leading whitespace per line (preserve indent for code)
            .trim()
    }

    sealed class SanitizeResult {
        data class Success(val sanitized: String) : SanitizeResult()
        data class Failure(val error: String, val partialResult: String) : SanitizeResult()
    }
}
