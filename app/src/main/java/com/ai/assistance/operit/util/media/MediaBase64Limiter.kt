package com.ai.assistance.operit.util.media

import android.util.Base64

object MediaBase64Limiter {
    private const val DEFAULT_MAX_DECODED_BYTES = 20 * 1024 * 1024

    data class LimitedMedia(
        val base64: String,
        val mimeType: String
    )

    fun estimateDecodedSizeBytes(base64: String): Int? {
        var count = 0
        for (c in base64) {
            if (!c.isWhitespace()) count += 1
        }
        if (count <= 0) return 0

        var padding = 0
        var i = base64.length - 1
        while (i >= 0) {
            val c = base64[i]
            if (c.isWhitespace()) {
                i -= 1
                continue
            }
            if (c == '=') {
                padding += 1
                i -= 1
                if (padding >= 2) break
                continue
            }
            break
        }

        val decoded = (count * 3) / 4 - padding
        return decoded.coerceAtLeast(0)
    }

    fun limitBase64ForAi(base64: String, mimeType: String, maxDecodedBytes: Int = DEFAULT_MAX_DECODED_BYTES): LimitedMedia? {
        val estimated = estimateDecodedSizeBytes(base64) ?: return null
        if (estimated > maxDecodedBytes) {
            return null
        }

        val bytes = try {
            Base64.decode(base64, Base64.DEFAULT)
        } catch (_: Throwable) {
            return null
        }

        if (bytes.size > maxDecodedBytes) {
            return null
        }

        return LimitedMedia(
            base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
            mimeType = mimeType
        )
    }
}
