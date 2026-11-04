package com.ai.assistance.operit.util.logging

import java.security.MessageDigest

/** 提供长度、摘要和受限首尾预览，便于核对消息经过各处理阶段时是否被截断。 */
object ChatTrace {

    private const val DEFAULT_PREVIEW_CHARS = 24

    fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }

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
