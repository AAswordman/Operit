package com.ai.assistance.operit.integrations.http

import com.ai.assistance.operit.util.logging.AppLogger
import java.io.BufferedWriter
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * An SSE client owns the pipe's read side and may disconnect with output still buffered.
 * Guard that close-phase IO failure while use preserves body failures and cancellation.
 */
internal inline fun <T> withSseWriter(
    output: OutputStream,
    logContext: String,
    block: (BufferedWriter) -> T,
): T {
    val writer = object : BufferedWriter(OutputStreamWriter(output, StandardCharsets.UTF_8)) {
        override fun close() {
            try {
                super.close()
            } catch (error: IOException) {
                AppLogger.i("SseWriter", "SSE writer closed after client disconnect ($logContext): ${error.message}")
            }
        }
    }
    return writer.use(block)
}
