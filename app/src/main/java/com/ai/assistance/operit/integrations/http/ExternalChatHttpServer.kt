package com.ai.assistance.operit.integrations.http

import android.content.Context
import com.ai.assistance.operit.BuildConfig
import com.ai.assistance.operit.data.preferences.ExternalHttpApiPreferences
import com.ai.assistance.operit.integrations.a2a.A2aHttpHandler
import com.ai.assistance.operit.integrations.externalchat.ExternalChatAcceptedResponse
import com.ai.assistance.operit.integrations.externalchat.ExternalChatHealthResponse
import com.ai.assistance.operit.integrations.externalchat.ExternalChatHttpRequest
import com.ai.assistance.operit.integrations.externalchat.ExternalChatRequestExecutor
import com.ai.assistance.operit.integrations.externalchat.ExternalChatResponseMode
import com.ai.assistance.operit.integrations.externalchat.ExternalChatResponseSanitizer
import com.ai.assistance.operit.integrations.externalchat.ExternalChatResult
import com.ai.assistance.operit.integrations.externalchat.ExternalChatStreamEnvelope
import com.ai.assistance.operit.integrations.externalchat.ExternalChatStreamingStartResult
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.util.AppLogger
import fi.iki.elonen.NanoHTTPD
import java.io.BufferedWriter
import java.io.FilterInputStream
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ExternalChatHttpServer(
    context: Context,
    private val preferences: ExternalHttpApiPreferences,
    private val serviceScope: CoroutineScope
) : NanoHTTPD(LISTEN_HOST, preferences.getPort()) {

    private val appContext = context.applicationContext
    private val executor = ExternalChatRequestExecutor(appContext)
    private val a2aHandler = A2aHttpHandler(appContext, serviceScope, ::requireBearerToken)
    private val webChatBridge = WebChatHttpBridge(appContext, preferences, serviceScope)
    private val callbackClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
        .build()
    private val running = AtomicBoolean(false)

    fun startServer() {
        if (running.get()) {
            return
        }
        start(SOCKET_READ_TIMEOUT, false)
        running.set(true)
        AppLogger.i(TAG, "External HTTP chat server started on port $listeningPort")
    }

    fun stopServer() {
        if (!running.get()) {
            return
        }
        a2aHandler.close()
        stop()
        running.set(false)
        AppLogger.i(TAG, "External HTTP chat server stopped")
    }

    override fun serve(session: IHTTPSession): Response {
        return when {
            session.method == Method.OPTIONS -> handleOptions(session)
            session.uri == A2aHttpHandler.AGENT_CARD_PATH -> a2aHandler.handleAgentCard(session).withCors()
            session.uri == A2aHttpHandler.A2A_PATH -> a2aHandler.handleJsonRpc(session).withCors()
            session.uri == HEALTH_PATH && session.method == Method.GET -> handleHealth(session)
            session.uri == CHAT_PATH && session.method == Method.POST -> handleChat(session)
            session.uri.startsWith(WEB_API_PREFIX) -> webChatBridge.handleApi(session)
            !session.uri.startsWith(API_PREFIX) -> webChatBridge.serveStatic(session)
            else -> jsonResponse(
                Response.Status.NOT_FOUND,
                ExternalChatResult(
                    success = false,
                    error = "API endpoint not found"
                )
            ).withCors()
        }
    }

    override fun useGzipWhenAccepted(response: Response): Boolean {
        val mimeType = response.mimeType?.lowercase()
        if (mimeType?.startsWith("text/event-stream") == true) {
            return false
        }
        return super.useGzipWhenAccepted(response)
    }

    private fun handleOptions(session: IHTTPSession): Response {
        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "").withCors()
    }

    private fun handleHealth(session: IHTTPSession): Response {
        val unauthorized = requireBearerToken(session)
        if (unauthorized != null) {
            return unauthorized
        }

        val config = preferences.getConfigSync()
        return jsonResponse(
            Response.Status.OK,
            ExternalChatHealthResponse(
                enabled = config.enabled,
                serviceRunning = true,
                port = listeningPort,
                versionName = BuildConfig.VERSION_NAME
            )
        ).withCors()
    }

    private fun handleChat(session: IHTTPSession): Response {
        val unauthorized = requireBearerToken(session)
        if (unauthorized != null) {
            return unauthorized
        }

        val requestBodyResult = readRequestBody(session)
        if (requestBodyResult.error != null) {
            return jsonResponse(
                Response.Status.BAD_REQUEST,
                ExternalChatResult(
                    success = false,
                    error = requestBodyResult.error
                )
            ).withCors()
        }

        val rawBody = requestBodyResult.body.orEmpty()
        if (rawBody.isBlank()) {
            return jsonResponse(
                Response.Status.BAD_REQUEST,
                ExternalChatResult(
                    success = false,
                    error = "Request body is empty"
                )
            ).withCors()
        }

        val request = try {
            json.decodeFromString<ExternalChatHttpRequest>(rawBody)
        } catch (e: Exception) {
            return jsonResponse(
                Response.Status.BAD_REQUEST,
                ExternalChatResult(
                    success = false,
                    error = "Invalid JSON: ${e.message}"
                )
            ).withCors()
        }

        val resolvedRequestId = request.resolvedRequestId()
        val responseMode = request.normalizedResponseMode()
            ?: return jsonResponse(
                Response.Status.BAD_REQUEST,
                ExternalChatResult(
                    requestId = resolvedRequestId,
                    success = false,
                    error = "Invalid parameter: response_mode must be sync/async_callback"
                )
            ).withCors()

        if (request.message.isNullOrBlank()) {
            return jsonResponse(
                Response.Status.BAD_REQUEST,
                ExternalChatResult(
                    requestId = resolvedRequestId,
                    success = false,
                    error = "Missing extra: message"
                )
            ).withCors()
        }

        if (request.stream && responseMode == ExternalChatResponseMode.ASYNC_CALLBACK) {
            return jsonResponse(
                Response.Status.BAD_REQUEST,
                ExternalChatResult(
                    requestId = resolvedRequestId,
                    success = false,
                    error = "Invalid parameter: stream=true is not compatible with async_callback"
                )
            ).withCors()
        }

        if (request.stream) {
            return sseResponse(request, resolvedRequestId).withCors()
        }

        val callbackUrl = request.callbackUrl?.trim()
        if (responseMode == ExternalChatResponseMode.ASYNC_CALLBACK) {
            if (callbackUrl.isNullOrBlank()) {
                return jsonResponse(
                    Response.Status.BAD_REQUEST,
                    ExternalChatResult(
                        requestId = resolvedRequestId,
                        success = false,
                        error = "Invalid parameter: callback_url is required for async_callback"
                    )
                ).withCors()
            }
            val callbackUri = kotlin.runCatching { java.net.URI(callbackUrl) }.getOrNull()
            if (callbackUri == null || (callbackUri.scheme != "http" && callbackUri.scheme != "https")) {
                return jsonResponse(
                    Response.Status.BAD_REQUEST,
                    ExternalChatResult(
                        requestId = resolvedRequestId,
                        success = false,
                        error = "Invalid parameter: callback_url must be http/https"
                    )
                ).withCors()
            }

            val executionRequest = request.toExecutionRequest(resolvedRequestId)
            serviceScope.launch {
                val result = executor.execute(executionRequest)
                postCallback(callbackUrl, result)
            }

            return jsonResponse(
                Response.Status.ACCEPTED,
                ExternalChatAcceptedResponse(requestId = resolvedRequestId)
            ).withCors()
        }

        val result = runBlocking {
            executor.execute(request.toExecutionRequest(resolvedRequestId))
        }
        return jsonResponse(Response.Status.OK, result).withCors()
    }

    private fun sseResponse(request: ExternalChatHttpRequest, resolvedRequestId: String): Response {
        val pipeInput = PipedInputStream(SSE_PIPE_BUFFER_SIZE)
        val pipeOutput = PipedOutputStream(pipeInput)
        val streamingSessionRef =
            AtomicReference<com.ai.assistance.operit.integrations.externalchat.ExternalChatStreamingSession?>(
                null
            )

        val streamJob: Job =
            serviceScope.launch(Dispatchers.IO) {
                val writer = pipeOutput.bufferedWriter(StandardCharsets.UTF_8)
                try {
                    when (
                        val startResult =
                            executor.startStreaming(request.toExecutionRequest(resolvedRequestId))
                    ) {
                        is ExternalChatStreamingStartResult.Failed -> {
                            writeSseEvent(
                                writer,
                                startResult.result.toStreamEnvelope(
                                    event = STREAM_EVENT_ERROR,
                                    fallbackRequestId = resolvedRequestId
                                )
                            )
                        }

                        is ExternalChatStreamingStartResult.Started -> {
                            val streamSession = startResult.session
                            streamingSessionRef.set(streamSession)
                            writeSseEvent(
                                writer,
                                ExternalChatStreamEnvelope(
                                    event = STREAM_EVENT_START,
                                    requestId = streamSession.requestId,
                                    chatId = streamSession.chatId
                                )
                            )

                            val filteredResponseStream =
                                ExternalChatResponseSanitizer.sanitizeStream(
                                    streamSession.responseStreamSession.responseStream,
                                    request.returnToolStatus
                                )
                            val finalResponse = StringBuilder()
                            filteredResponseStream.collect { chunk ->
                                if (chunk.isEmpty()) {
                                    return@collect
                                }
                                finalResponse.append(chunk)
                                writeSseEvent(
                                    writer,
                                    ExternalChatStreamEnvelope(
                                        event = STREAM_EVENT_DELTA,
                                        requestId = streamSession.requestId,
                                        chatId = streamSession.chatId,
                                        delta = chunk
                                    )
                                )
                            }

                            val finalState = streamSession.responseStreamSession.currentState()
                            val finalResponseText = finalResponse.toString().takeIf { it.isNotBlank() }
                            if (finalState is InputProcessingState.Error) {
                                writeSseEvent(
                                    writer,
                                    ExternalChatStreamEnvelope(
                                        event = STREAM_EVENT_ERROR,
                                        requestId = streamSession.requestId,
                                        chatId = streamSession.chatId,
                                        aiResponse = finalResponseText,
                                        success = false,
                                        error = finalState.message
                                    )
                                )
                            } else {
                                writeSseEvent(
                                    writer,
                                    ExternalChatStreamEnvelope(
                                        event = STREAM_EVENT_DONE,
                                        requestId = streamSession.requestId,
                                        chatId = streamSession.chatId,
                                        aiResponse = finalResponseText,
                                        success = true
                                    )
                                )
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    streamingSessionRef.get()?.responseStreamSession?.cancel()
                    throw e
                } catch (e: IOException) {
                    AppLogger.i(TAG, "SSE client disconnected: requestId=$resolvedRequestId")
                    streamingSessionRef.get()?.responseStreamSession?.cancel()
                } catch (e: Exception) {
                    AppLogger.e(TAG, "SSE stream failed: requestId=$resolvedRequestId", e)
                    val streamSession = streamingSessionRef.get()
                    runCatching {
                        writeSseEvent(
                            writer,
                            ExternalChatStreamEnvelope(
                                event = STREAM_EVENT_ERROR,
                                requestId = streamSession?.requestId ?: resolvedRequestId,
                                chatId = streamSession?.chatId,
                                success = false,
                                error = e.message ?: "Unknown error"
                            )
                        )
                    }
                    streamSession?.responseStreamSession?.cancel()
                } finally {
                    streamingSessionRef.get()?.cleanup()
                    closeSseWriter(writer, "requestId=$resolvedRequestId")
                }
            }

        val responseInput =
            object : FilterInputStream(pipeInput) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        streamJob.cancel()
                        streamingSessionRef.get()?.responseStreamSession?.cancel()
                        streamingSessionRef.get()?.cleanup()
                    }
                }
            }

        return newChunkedResponse(Response.Status.OK, SSE_MIME_TYPE, responseInput).apply {
            addHeader("Cache-Control", "no-cache")
            addHeader("Connection", "keep-alive")
            addHeader("X-Accel-Buffering", "no")
        }
    }

    private fun requireBearerToken(session: IHTTPSession): Response? {
        val expectedToken = preferences.getBearerToken().trim()
        if (expectedToken.isBlank()) {
            return jsonResponse(
                Response.Status.UNAUTHORIZED,
                ExternalChatResult(
                    success = false,
                    error = "Bearer token not configured"
                )
            ).withCors()
        }

        val authorization = session.headers.entries.firstOrNull {
            it.key.equals("authorization", ignoreCase = true)
        }?.value?.trim().orEmpty()

        val actualToken = if (authorization.startsWith("Bearer ", ignoreCase = true)) {
            authorization.substringAfter(' ').trim()
        } else {
            ""
        }

        return if (actualToken == expectedToken) {
            null
        } else {
            jsonResponse(
                Response.Status.UNAUTHORIZED,
                ExternalChatResult(
                    success = false,
                    error = "Unauthorized"
                )
            ).withCors()
        }
    }

    private fun postCallback(callbackUrl: String, result: ExternalChatResult) {
        try {
            val requestBody = json.encodeToString(result).toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(callbackUrl)
                .post(requestBody)
                .build()

            callbackClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w(
                        TAG,
                        "Async callback failed: url=$callbackUrl code=${response.code}"
                    )
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Async callback failed: url=$callbackUrl", e)
        }
    }

    private fun readRequestBody(session: IHTTPSession): RequestBodyResult {
        return try {
            val contentLength = session.headers.entries.firstOrNull {
                it.key.equals("content-length", ignoreCase = true)
            }?.value?.trim()?.toLongOrNull()
                ?: return RequestBodyResult(error = "Missing or invalid Content-Length")
            if (contentLength < 0L || contentLength > Int.MAX_VALUE.toLong()) {
                return RequestBodyResult(error = "Unsupported Content-Length: $contentLength")
            }
            if (contentLength == 0L) {
                return RequestBodyResult(body = "")
            }

            val bodyBytes = ByteArray(contentLength.toInt())
            var offset = 0
            val inputStream = session.inputStream
            while (offset < bodyBytes.size) {
                val read = inputStream.read(bodyBytes, offset, bodyBytes.size - offset)
                if (read < 0) {
                    return RequestBodyResult(error = "Unexpected end of stream while reading request body")
                }
                offset += read
            }

            val charset = resolveRequestCharset(
                session.headers.entries.firstOrNull {
                    it.key.equals("content-type", ignoreCase = true)
                }?.value
            )
            RequestBodyResult(body = String(bodyBytes, charset))
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to read HTTP request body", e)
            RequestBodyResult(error = "Failed to read request body: ${e.message ?: "Unknown error"}")
        }
    }

    private fun resolveRequestCharset(contentTypeHeader: String?): Charset {
        val charsetName = contentTypeHeader
            ?.split(';')
            ?.asSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("charset=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.trim()
            ?.removeSurrounding("\"")
            ?.takeIf { it.isNotBlank() }

        if (charsetName != null) {
            return kotlin.runCatching { Charset.forName(charsetName) }
                .getOrElse {
                    AppLogger.w(
                        TAG,
                        "Unsupported request charset '$charsetName', falling back to UTF-8"
                    )
                    StandardCharsets.UTF_8
                }
        }

        // application/json is defined as UTF-8 by default; keep requests usable
        // even when the caller omits `charset=utf-8`.
        return StandardCharsets.UTF_8
    }

    private fun jsonResponse(status: Response.Status, body: ExternalChatResult): Response {
        return newFixedLengthResponse(status, JSON_MIME_TYPE, json.encodeToString(body))
    }

    private fun jsonResponse(status: Response.Status, body: ExternalChatAcceptedResponse): Response {
        return newFixedLengthResponse(status, JSON_MIME_TYPE, json.encodeToString(body))
    }

    private fun jsonResponse(status: Response.Status, body: ExternalChatHealthResponse): Response {
        return newFixedLengthResponse(status, JSON_MIME_TYPE, json.encodeToString(body))
    }

    private fun writeSseEvent(writer: BufferedWriter, payload: ExternalChatStreamEnvelope) {
        val serialized = json.encodeToString(payload)
        writer.write("event: ")
        writer.write(payload.event)
        writer.newLine()
        serialized.lineSequence().forEach { line ->
            writer.write("data: ")
            writer.write(line)
            writer.newLine()
        }
        writer.newLine()
        writer.flush()
    }

    private fun ExternalChatResult.toStreamEnvelope(
        event: String,
        fallbackRequestId: String
    ): ExternalChatStreamEnvelope {
        return ExternalChatStreamEnvelope(
            event = event,
            requestId = requestId ?: fallbackRequestId,
            chatId = chatId,
            aiResponse = aiResponse,
            success = success,
            error = error
        )
    }

    private fun Response.withCors(): Response {
        addHeader("Access-Control-Allow-Origin", "*")
        addHeader("Access-Control-Allow-Methods", "GET, POST, PATCH, DELETE, OPTIONS")
        addHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept")
        addHeader("Access-Control-Max-Age", "3600")
        return this
    }

    companion object {
        private const val TAG = "ExternalChatHttpServer"
        private const val LISTEN_HOST = "0.0.0.0"
        private const val API_PREFIX = "/api"
        private const val CHAT_PATH = "/api/external-chat"
        private const val HEALTH_PATH = "/api/health"
        private const val WEB_API_PREFIX = "/api/web/"
        private const val JSON_MIME_TYPE = "application/json; charset=utf-8"
        private const val SSE_MIME_TYPE = "text/event-stream; charset=utf-8"
        private const val SSE_PIPE_BUFFER_SIZE = 64 * 1024
        private const val STREAM_EVENT_START = "start"
        private const val STREAM_EVENT_DELTA = "delta"
        private const val STREAM_EVENT_DONE = "done"
        private const val STREAM_EVENT_ERROR = "error"
        private val JSON_MEDIA_TYPE = JSON_MIME_TYPE.toMediaType()
        private val json = Json {
            ignoreUnknownKeys = true
        }
    }
}

private data class RequestBodyResult(
    val body: String? = null,
    val error: String? = null
)
