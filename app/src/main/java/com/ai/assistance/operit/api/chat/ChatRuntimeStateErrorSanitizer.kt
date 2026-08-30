package com.ai.assistance.operit.api.chat

internal object ChatRuntimeStateErrorSanitizer {
    internal const val MAX_PUBLIC_MESSAGE_LENGTH = 300

    private const val REDACTED = "[redacted]"

    private val thinkingBlockPattern =
        Regex("""(?is)<(?:think|thinking)>.*?</(?:think|thinking)>""")
    private val bearerPattern =
        Regex("""(?i)\bBearer\s+[A-Za-z0-9._~+/=-]{6,}""")
    private val namedCredentialPattern =
        Regex(
            """(?i)\b(authorization|api[_ -]?key|access[_ -]?token)\b\s*[:=]\s*(?:Bearer\s+)?["']?[A-Za-z0-9._~+/=-]{6,}["']?"""
        )
    private val commonTokenPattern =
        Regex("""(?i)\b(?:sk-|xai-|hf_)[A-Za-z0-9_-]{8,}\b|\bAIza[0-9A-Za-z_-]{20,}\b""")
    private val sensitiveJsonStringPattern =
        Regex(
            """(?is)(["'](?:api[_-]?key|access[_-]?token|authorization|prompt|input|messages|reasoning(?:_content)?|chain_of_thought)["']\s*:\s*)(["'])(.*?)(["'])"""
        )
    private val sensitiveJsonContainerPattern =
        Regex(
            "(?is)([\"'](?:prompt|input|messages|reasoning(?:_content)?|chain_of_thought)[\"']\\s*:\\s*)(\\[[^\\]]*\\]|\\{[^}]*\\})"
        )

    fun sanitize(message: String?): String? {
        val source = message?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        var sanitized = source
            .replace(thinkingBlockPattern, REDACTED)
            .replace(namedCredentialPattern) { match ->
                "${match.groupValues[1]}=$REDACTED"
            }
            .replace(bearerPattern, "Bearer $REDACTED")
            .replace(commonTokenPattern, REDACTED)
            .replace(sensitiveJsonStringPattern) { match ->
                val quote = match.groupValues[2]
                "${match.groupValues[1]}$quote$REDACTED$quote"
            }
            .replace(sensitiveJsonContainerPattern) { match ->
                "${match.groupValues[1]}\"$REDACTED\""
            }
            .filter { character ->
                character == '\n' || character == '\t' || !character.isISOControl()
            }
            .trim()

        if (sanitized.length > MAX_PUBLIC_MESSAGE_LENGTH) {
            sanitized = sanitized.take(MAX_PUBLIC_MESSAGE_LENGTH - 3).trimEnd() + "..."
        }
        return sanitized.takeIf { it.isNotEmpty() }
    }
}
