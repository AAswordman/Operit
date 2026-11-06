package com.ai.assistance.operit.integrations.http

import com.ai.assistance.operit.util.AppLogger
import java.io.BufferedWriter
import java.io.IOException

private const val TAG = "SseWriter"

/**
 * Closes an SSE response writer whose read side belongs to the HTTP client. Once the client
 * disconnects the backing pipe is closed, so the flush inside close() raises IOException("Pipe
 * closed"). That is the normal end of a stream job, and the service scope installs no
 * CoroutineExceptionHandler, so letting it escape reaches the process-wide uncaught handler and
 * kills the app.
 */
internal fun closeSseWriter(writer: BufferedWriter, logContext: String) {
    try {
        writer.close()
    } catch (e: IOException) {
        AppLogger.i(TAG, "SSE writer closed after client disconnect ($logContext): ${e.message}")
    }
}
