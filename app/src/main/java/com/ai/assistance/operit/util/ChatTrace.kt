package com.ai.assistance.operit.util

import java.security.MessageDigest

/**
 * Formatting helpers for diagnosing message truncation without writing user content to logs.
 *
 * The HTTP entry, the message conversion layers and the provider request each handle the same
 * text. A length alone cannot tell "the same bytes arrived" apart from "a different string of the
 * same length", and a raw preview leaks private content. A short digest plus the first and last
 * characters is enough to correlate stages while keeping the log safe to share.
 */
object ChatTrace {

    private const val DEFAULT_PREVIEW_CHARS = 24

    /** Returns the lowercase hex SHA-256 of [text] encoded as UTF-8. */
    fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }

    /**
     * Describes [text] for a log line: character length, optional byte length, digest prefix and a
     * bounded head/tail preview with control characters escaped.
     */
    fun describe(
        text: String,
        byteLength: Int? = null,
        previewChars: Int = DEFAULT_PREVIEW_CHARS,
    ): String {
        val digest = sha256(text).take(16)
        val bytePart = byteLength?.let { ", bytes=$it" }.orEmpty()
        return "len=${text.length}$bytePart, sha256=$digest, head=\"${preview(text, 0, previewChars)}\", tail=\"${preview(text, text.length - previewChars, previewChars)}\""
    }

    private fun preview(text: String, from: Int, count: Int): String {
        if (text.isEmpty() || count <= 0) return ""
        val start = from.coerceIn(0, text.length)
        val end = (start + count).coerceAtMost(text.length)
        return text.substring(start, end)
            .replace("\\", "\\\\")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t")
    }
}
