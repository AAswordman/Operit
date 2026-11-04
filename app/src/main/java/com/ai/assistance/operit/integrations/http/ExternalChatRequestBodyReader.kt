package com.ai.assistance.operit.integrations.http

import java.io.InputStream
import java.nio.charset.Charset

/**
 * Reads exactly [contentLength] bytes of an external-chat request body.
 *
 * NanoHTTPD keeps the request body on the same buffered stream as the headers and only forwards
 * what is left after the header terminator. Reading a fixed byte count is essential: a
 * character-based reader would stop early on multi-byte payloads and a single `read` call would
 * return whatever happened to arrive first. HTTP clients choose the TCP segmentation, so the loop
 * must keep reading until the declared byte count is satisfied.
 */
internal object ExternalChatRequestBodyReader {

    fun read(inputStream: InputStream, contentLength: Long, charset: Charset): String {
        require(contentLength in 0L..Int.MAX_VALUE.toLong()) {
            "Unsupported Content-Length: $contentLength"
        }
        if (contentLength == 0L) {
            return ""
        }

        val bodyBytes = ByteArray(contentLength.toInt())
        var offset = 0
        while (offset < bodyBytes.size) {
            val read = inputStream.read(bodyBytes, offset, bodyBytes.size - offset)
            if (read < 0) {
                error("Unexpected end of stream while reading request body")
            }
            offset += read
        }
        return String(bodyBytes, charset)
    }
}
