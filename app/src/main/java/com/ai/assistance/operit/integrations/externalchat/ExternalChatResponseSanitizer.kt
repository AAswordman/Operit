package com.ai.assistance.operit.integrations.externalchat

import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.StreamGroup
import com.ai.assistance.operit.util.stream.splitBy
import com.ai.assistance.operit.util.stream.stream
import com.ai.assistance.operit.util.stream.plugins.StreamPlugin
import com.ai.assistance.operit.util.stream.plugins.StreamXmlPlugin
import com.ai.assistance.operit.util.stream.stream as charStream

object ExternalChatResponseSanitizer {

    private const val TEXT_BATCH_FLUSH_SIZE = 32

    suspend fun sanitize(content: String?, returnToolStatus: Boolean): String? {
        val raw = content?.takeIf { it.isNotBlank() } ?: return null
        if (returnToolStatus) {
            return raw
        }

        val sanitized = StringBuilder()
        collectSanitizedGroups(raw.charStream().splitBy(listOf(StreamXmlPlugin()))) { chunk ->
            sanitized.append(chunk)
        }
        return sanitizeResidualWhitespace(sanitized.toString())
    }

    fun sanitizeStream(rawStream: Stream<String>, returnToolStatus: Boolean): Stream<String> {
        if (returnToolStatus) {
            return rawStream
        }

        return stream {
            collectSanitizedGroups(rawStream.splitBy(listOf(StreamXmlPlugin()))) { chunk ->
                emit(chunk)
            }
        }
    }

    private suspend fun collectSanitizedGroups(
        groups: Stream<StreamGroup<StreamPlugin?>>,
        emitChunk: suspend (String) -> Unit
    ) {
        groups.collect { group ->
            when (val tag = group.tag) {
                is StreamXmlPlugin -> {
                    val xmlContent = StringBuilder()
                    group.stream.collect { piece -> xmlContent.append(piece) }
                    val xml = xmlContent.toString()
                    if (!shouldStripXmlGroup(xml)) {
                        emitChunk(xml)
                    }
                }

                null -> {
                    val textBuffer = StringBuilder()

                    suspend fun flushTextBuffer() {
                        if (textBuffer.isNotEmpty()) {
                            emitChunk(textBuffer.toString())
                            textBuffer.clear()
                        }
                    }

                    group.stream.collect { piece ->
                        textBuffer.append(piece)
                        val lastChar = piece.lastOrNull()
                        if (
                            textBuffer.length >= TEXT_BATCH_FLUSH_SIZE ||
                                lastChar == '\n' ||
                                lastChar == '\r' ||
                                isNaturalFlushBoundary(lastChar)
                        ) {
                            flushTextBuffer()
                        }
                    }
                    flushTextBuffer()
                }

                else -> {
                    group.stream.collect { piece -> emitChunk(piece) }
                }
            }
        }
    }

    private fun shouldStripXmlGroup(xml: String): Boolean {
        val normalizedTagName =
            ChatMarkupRegex.normalizeToolLikeTagName(ChatMarkupRegex.extractOpeningTagName(xml))
        return normalizedTagName == "status" ||
            normalizedTagName == "tool" ||
            normalizedTagName == "tool_result" ||
            normalizedTagName == "meta"
    }

    private fun sanitizeResidualWhitespace(content: String): String? {
        val normalized =
            content
                .replace("\r\n", "\n")
                .replace(Regex("[ \t]+\n"), "\n")
                .replace(Regex("\n[ \t]+"), "\n")
                .replace(Regex("\n{3,}"), "\n\n")
                .trim()
        return normalized.takeIf { it.isNotBlank() }
    }

    private fun isNaturalFlushBoundary(lastChar: Char?): Boolean {
        return lastChar in
            setOf('。', '，', '！', '？', '；', '：', '.', ',', '!', '?', ';', ':')
    }
}
