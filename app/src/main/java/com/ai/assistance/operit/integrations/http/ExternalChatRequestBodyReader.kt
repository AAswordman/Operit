package com.ai.assistance.operit.integrations.http

import java.io.InputStream
import java.nio.charset.Charset

/** 按声明的字节数循环读取请求体，读取完成后统一按指定字符集解码。 */
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
