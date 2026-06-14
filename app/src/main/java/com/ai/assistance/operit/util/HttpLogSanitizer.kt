package com.ai.assistance.operit.util

import okhttp3.Headers
import okhttp3.HttpUrl

object HttpLogSanitizer {
    fun urlForLog(url: HttpUrl): String {
        return buildString {
            append(url.scheme)
            append("://")
            append(url.host)

            if (url.port != HttpUrl.defaultPort(url.scheme)) {
                append(':')
                append(url.port)
            }

            if (url.pathSegments.isNotEmpty() && url.encodedPath != "/") {
                append("/")
                append("${url.pathSegments.size} path segments")
            }

            if (url.querySize > 0) {
                append("?")
                append(
                    (0 until url.querySize).joinToString("&") { index ->
                        "${url.queryParameterName(index)}=[omitted]"
                    }
                )
            }

            if (url.encodedFragment != null) {
                append("#[omitted]")
            }
        }
    }

    fun headersForLog(headers: Headers): String {
        if (headers.size == 0) {
            return "[empty]"
        }

        return headers.names()
            .joinToString(separator = "\n") { name -> "$name: [omitted]" }
    }
}
