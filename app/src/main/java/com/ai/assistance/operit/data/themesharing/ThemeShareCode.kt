package com.ai.assistance.operit.data.themesharing

import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater

/** 分享码只使用标准 zlib 包装；编码标识明确，避免猜测或尝试多种压缩格式。 */
internal object ThemeShareCode {
    const val PREFIX = "oktheme://theme/import?schema=1&encoding=zlib-base64url&data="
    private const val MAX_COMPRESSED_BYTES = 32768

    fun encode(document: ThemeTransferDocument): String {
        if (document.full) throw ThemeTransferException(ThemeTransferError.INVALID_CONFIG)
        val compressed = ByteArrayOutputStream()
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        try {
            DeflaterOutputStream(compressed, deflater).use {
                it.write(ThemeTransferSchema.encode(document))
            }
        } finally {
            deflater.end()
        }
        return PREFIX +
            Base64.getUrlEncoder().withoutPadding().encodeToString(compressed.toByteArray())
    }

    fun decode(input: String): ThemeTransferDocument {
        try {
            val text = input.trim()
            if (text.length > MAX_COMPRESSED_BYTES * 2)
                throw ThemeTransferException(ThemeTransferError.TOO_LARGE)
            val data =
                if (text.startsWith("oktheme:") || text.startsWith("operit:")) {
                    val uri = URI(text)
                    if (
                        uri.scheme !in setOf("oktheme", "operit") ||
                            uri.host != "theme" ||
                            uri.path != "/import" ||
                            uri.fragment != null ||
                            uri.port != -1 ||
                            uri.userInfo != null
                    )
                        invalid()
                    val versionKey = if (uri.scheme == "oktheme") "schema" else "v"
                    val pairs =
                        uri.rawQuery?.split('&')?.map { it.split('=', limit = 2) } ?: invalid()
                    if (
                        pairs.size != 3 ||
                            pairs.any { it.size != 2 } ||
                            pairs.map { it[0] }.toSet() != setOf(versionKey, "encoding", "data")
                    )
                        invalid()
                    val query = pairs.associate { it[0] to it[1] }
                    if (query[versionKey] != "1")
                        throw ThemeTransferException(ThemeTransferError.UNSUPPORTED_VERSION)
                    if (query["encoding"] != "zlib-base64url") invalid()
                    query.getValue("data")
                } else text
            if (!data.matches(Regex("[A-Za-z0-9_-]+"))) invalid()
            val compressed = Base64.getUrlDecoder().decode(data)
            if (compressed.size > MAX_COMPRESSED_BYTES)
                throw ThemeTransferException(ThemeTransferError.TOO_LARGE)
            if (Base64.getUrlEncoder().withoutPadding().encodeToString(compressed) != data)
                invalid()
            val inflater = Inflater()
            val output = ByteArrayOutputStream()
            try {
                inflater.setInput(compressed)
                val buffer = ByteArray(4096)
                while (!inflater.finished()) {
                    val count = inflater.inflate(buffer)
                    if (count == 0 && !inflater.finished()) invalid()
                    if (output.size() + count > ThemeTransferSchema.MAX_JSON_BYTES)
                        throw ThemeTransferException(ThemeTransferError.TOO_LARGE)
                    output.write(buffer, 0, count)
                }
                if (inflater.remaining != 0) invalid()
            } finally {
                inflater.end()
            }
            return ThemeTransferSchema.decode(output.toByteArray(), full = false)
        } catch (e: ThemeTransferException) {
            throw e
        } catch (e: Exception) {
            throw ThemeTransferException(ThemeTransferError.INVALID_CODE, e)
        }
    }

    private fun invalid(): Nothing = throw ThemeTransferException(ThemeTransferError.INVALID_CODE)
}
