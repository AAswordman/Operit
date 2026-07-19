package com.ai.assistance.operit.util

import java.security.SecureRandom

object ChatMarkupRegex {
    private const val TOOL_TAG_SUFFIX_REGEX_SOURCE = "[A-Za-z0-9_]+"
    private const val GEMINI_THOUGHT_SIGNATURE_PROVIDER = "gemini:thought_signature"
    private const val OPENAI_RESPONSES_REASONING_PROVIDER = "openai:responses_reasoning"
    const val TOOL_TAG_NAME_REGEX_SOURCE =
        "tool(?:_(?!result(?:_|\\b))$TOOL_TAG_SUFFIX_REGEX_SOURCE)?"
    const val TOOL_RESULT_TAG_NAME_REGEX_SOURCE = "tool_result(?:_${TOOL_TAG_SUFFIX_REGEX_SOURCE})?"

    private val toolTagNameRegex = Regex("^$TOOL_TAG_NAME_REGEX_SOURCE$", RegexOption.IGNORE_CASE)
    private val toolResultTagNameRegex = Regex("^$TOOL_RESULT_TAG_NAME_REGEX_SOURCE$", RegexOption.IGNORE_CASE)
    private val openingTagNameRegex = Regex("<([A-Za-z][A-Za-z0-9_]*)")
    private val toolStartTagRegex = Regex("<(?:$TOOL_TAG_NAME_REGEX_SOURCE)\\b", RegexOption.IGNORE_CASE)
    private val toolResultStartTagRegex = Regex("<(?:$TOOL_RESULT_TAG_NAME_REGEX_SOURCE)\\b", RegexOption.IGNORE_CASE)
    private val metaProviderAttrRegex = Regex("""\bprovider\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val metaBodyRegex = Regex(
        """<meta\b[^>]*>([\s\S]*?)</meta>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val randomTagCodeChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private val randomTagCodeSource = SecureRandom()

    val toolCallPattern = Regex(
        """<($TOOL_TAG_NAME_REGEX_SOURCE)\b[^>]*name="([^"]+)"[^>]*>([\s\S]*?)</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    val toolTag = Regex(
        """<($TOOL_TAG_NAME_REGEX_SOURCE)\b[\s\S]*?</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    val toolSelfClosingTag = Regex(
        """<${TOOL_TAG_NAME_REGEX_SOURCE}\b[^>]*/>""",
        RegexOption.IGNORE_CASE
    )

    val toolResultTag = Regex(
        """<($TOOL_RESULT_TAG_NAME_REGEX_SOURCE)\b[\s\S]*?</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    val toolResultSelfClosingTag = Regex(
        """<${TOOL_RESULT_TAG_NAME_REGEX_SOURCE}\b[^>]*/>""",
        RegexOption.IGNORE_CASE
    )

    val toolResultTagWithAttrs = Regex(
        """<($TOOL_RESULT_TAG_NAME_REGEX_SOURCE)\b([^>]*)>([\s\S]*?)</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    val toolResultAnyPattern = Regex(
        """<($TOOL_RESULT_TAG_NAME_REGEX_SOURCE)\b[^>]*>([\s\S]*?)</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    val toolResultWithNameAnyPattern = Regex(
        """<($TOOL_RESULT_TAG_NAME_REGEX_SOURCE)\b[^>]*name="([^"]+)"[^>]*>([\s\S]*?)</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    val toolOrToolResultBlock = Regex(
        """<($TOOL_RESULT_TAG_NAME_REGEX_SOURCE|$TOOL_TAG_NAME_REGEX_SOURCE)\b[\s\S]*?</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    val xmlStatusPattern = Regex(
        "<status\\s+type=\"([^\"]+)\"(?:\\s+uuid=\"([^\"]+)\")?(?:\\s+title=\"([^\"]+)\")?(?:\\s+subtitle=\"([^\"]+)\")?>([\\s\\S]*?)</status>"
    )

    val xmlToolResultPattern = Regex(
        """<($TOOL_RESULT_TAG_NAME_REGEX_SOURCE)\b[^>]*name="([^"]+)"[^>]*status="([^"]+)"[^>]*>\s*<content>([\s\S]*?)</content>\s*</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    val xmlToolRequestPattern = Regex(
        """<($TOOL_TAG_NAME_REGEX_SOURCE)\b[^>]*name="([^"]+)"(?:\s+description="([^"]+)")?[^>]*>([\s\S]*?)</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    val namePattern = Regex(
        "<(?:$TOOL_TAG_NAME_REGEX_SOURCE)\\b[^>]*name=\"([^\"]+)\"",
        RegexOption.IGNORE_CASE
    )

    val toolParamPattern = Regex("<param\\s+name=\"([^\"]+)\">([\\s\\S]*?)</param>")

    val nameAttr = Regex("name\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)

    val statusAttr = Regex("status\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)

    val typeAttr = Regex("type\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)

    val titleAttr = Regex("title\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)

    val subtitleAttr = Regex("subtitle\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)

    val toolAttr = Regex("tool\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)

    val uuidAttr = Regex("uuid\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)

    val contentTag = Regex(
        "<content>([\\s\\S]*?)</content>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    val errorTag = Regex(
        "<error>([\\s\\S]*?)</error>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    val statusTag = Regex(
        "<status\\b[\\s\\S]*?</status>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    val statusSelfClosingTag = Regex(
        "<status\\b[^>]*/>",
        RegexOption.IGNORE_CASE
    )

    val thinkTag = Regex(
        "<think(?:ing)?\\b[\\s\\S]*?</think(?:ing)?>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    val thinkSelfClosingTag = Regex(
        "<think(?:ing)?\\b[^>]*/>",
        RegexOption.IGNORE_CASE
    )

    val searchTag = Regex(
        "<search\\b[\\s\\S]*?</search>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    val searchSelfClosingTag = Regex(
        "<search\\b[^>]*/>",
        RegexOption.IGNORE_CASE
    )

    val metaTag = Regex(
        """<meta\b[^>]*>(?:(?!<meta\b)[\s\S])*?</meta>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    val emotionTag = Regex(
        "<emotion\\b[\\s\\S]*?</emotion>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    val memoryTag = Regex(
        "<memory>.*?</memory>",
        RegexOption.DOT_MATCHES_ALL
    )

    val replyToTag = Regex(
        "<reply_to\\s+sender=\"([^\"]+)\"\\s+timestamp=\"([^\"]+)\">([^<]*)</reply_to>"
    )

    val attachmentDataTag = Regex(
        "<attachment\\s+id=\"([^\"]+)\"\\s+filename=\"([^\"]+)\"\\s+type=\"([^\"]+)\"(?:\\s+size=\"([^\"]+)\")?\\s*>([\\s\\S]*?)</attachment>"
    )

    val attachmentDataSelfClosingTag = Regex(
        "<attachment\\s+id=\"([^\"]+)\"\\s+filename=\"([^\"]+)\"\\s+type=\"([^\"]+)\"(?:\\s+size=\"([^\"]+)\")?(?:\\s+content=\"(.*?)\")?\\s*/>",
        RegexOption.DOT_MATCHES_ALL
    )

    val attachmentTag = Regex(
        "<attachment\\b[\\s\\S]*?</attachment>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    val attachmentSelfClosingTag = Regex(
        "<attachment\\b[\\s\\S]*?/>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    val workspaceAttachmentTag = Regex(
        "<workspace_attachment\\b[\\s\\S]*?</workspace_attachment>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    val proxySenderTag = Regex(
        "<proxy_sender\\s+name=\"([^\"]+)\"\\s*/>",
        RegexOption.IGNORE_CASE
    )

    val anyXmlTag = Regex("<[^>]*>")

    val pruneToolResultContentPattern = Regex(
        """<($TOOL_RESULT_TAG_NAME_REGEX_SOURCE)\b(.*? status=["'](.*?)["'].*?)>(.*?)</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    fun isToolTagName(tagName: String?): Boolean {
        return tagName?.let { toolTagNameRegex.matches(it) } == true
    }

    fun isToolResultTagName(tagName: String?): Boolean {
        return tagName?.let { toolResultTagNameRegex.matches(it) } == true
    }

    fun normalizeToolLikeTagName(tagName: String?): String? {
        return when {
            isToolTagName(tagName) -> "tool"
            isToolResultTagName(tagName) -> "tool_result"
            else -> tagName
        }
    }

    fun containsToolTag(content: String): Boolean {
        return toolStartTagRegex.containsMatchIn(content)
    }

    fun containsToolResultTag(content: String): Boolean {
        return toolResultStartTagRegex.containsMatchIn(content)
    }

    fun containsAnyToolLikeTag(content: String): Boolean {
        return containsToolTag(content) || containsToolResultTag(content)
    }

    fun extractOpeningTagName(xml: String): String? {
        return openingTagNameRegex.find(xml.trim())?.groupValues?.getOrNull(1)
    }

    fun generateRandomToolTagName(): String = "tool_${generateRandomTagCode()}"

    fun generateRandomToolResultTagName(): String = "tool_result_${generateRandomTagCode()}"

    fun geminiThoughtSignatureMetaTag(signatureBase64: String): String {
        return """<meta provider="$GEMINI_THOUGHT_SIGNATURE_PROVIDER">$signatureBase64</meta>"""
    }

    fun openAiResponsesReasoningMetaTag(payloadBase64: String): String {
        return """<meta provider="$OPENAI_RESPONSES_REASONING_PROVIDER">$payloadBase64</meta>"""
    }

    fun extractGeminiThoughtSignature(content: String): String? {
        return metaTag.findAll(content)
            .mapNotNull { match ->
                val tagContent = match.value
                val provider = extractMetaProvider(tagContent)
                if (!provider.equals(GEMINI_THOUGHT_SIGNATURE_PROVIDER, ignoreCase = true)) {
                    return@mapNotNull null
                }
                metaBodyRegex.find(tagContent)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }
            .lastOrNull()
    }

    fun extractOpenAiResponsesReasoningPayloads(content: String): List<String> {
        return metaTag.findAll(content)
            .mapNotNull { match ->
                val tagContent = match.value
                val provider = extractMetaProvider(tagContent)
                if (!provider.equals(OPENAI_RESPONSES_REASONING_PROVIDER, ignoreCase = true)) {
                    return@mapNotNull null
                }
                metaBodyRegex.find(tagContent)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }
            .toList()
    }

    fun removeGeminiThoughtSignatureMeta(content: String): String {
        var removed = false
        val result = metaTag.replace(content) { match ->
            val provider = extractMetaProvider(match.value)
            if (provider.equals(GEMINI_THOUGHT_SIGNATURE_PROVIDER, ignoreCase = true)) {
                removed = true
                ""
            } else {
                match.value
            }
        }
        return if (removed) result.trimEnd() else result
    }

    fun removeOpenAiResponsesReasoningMeta(content: String): String {
        var removed = false
        val result = metaTag.replace(content) { match ->
            val provider = extractMetaProvider(match.value)
            if (provider.equals(OPENAI_RESPONSES_REASONING_PROVIDER, ignoreCase = true)) {
                removed = true
                ""
            } else {
                match.value
            }
        }
        return if (removed) result.trimEnd() else result
    }

    private fun extractMetaProvider(metaTagContent: String): String? {
        return metaProviderAttrRegex
            .find(metaTagContent.substringBefore('>'))
            ?.groupValues
            ?.getOrNull(1)
    }

    private fun generateRandomTagCode(length: Int = 4): String {
        return buildString(length) {
            repeat(length) {
                append(randomTagCodeChars[randomTagCodeSource.nextInt(randomTagCodeChars.length)])
            }
        }
    }
}
