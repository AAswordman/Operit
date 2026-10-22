package com.ai.assistance.operit.integrations.a2a

import android.content.Context
import com.ai.assistance.operit.BuildConfig
import com.ai.assistance.operit.util.AppLogger
import fi.iki.elonen.NanoHTTPD
import java.io.BufferedWriter
import java.io.FilterInputStream
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/** A2A 1.0 JSON-RPC surface hosted by the existing external HTTP service. */
class A2aHttpHandler(
    context: Context,
    private val serviceScope: CoroutineScope,
    private val requireBearerToken: (NanoHTTPD.IHTTPSession) -> NanoHTTPD.Response?
) {

    private val taskManager = A2aTaskManager(context.applicationContext, serviceScope)

    fun handleAgentCard(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        if (session.method != NanoHTTPD.Method.GET) {
            return plainResponse(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED, "Method not allowed")
        }

        val endpoint = try {
            buildJsonRpcEndpoint(session)
        } catch (error: A2aProtocolException) {
            return plainResponse(NanoHTTPD.Response.Status.BAD_REQUEST, error.message ?: "Invalid Host header")
        }
        val card = JSONObject().apply {
            put("protocolVersion", A2A_PROTOCOL_VERSION)
            put("name", "Operit")
            put("description", "Operit assistant with text chat and tool-use capabilities")
            put(
                "supportedInterfaces",
                JSONArray().put(
                    JSONObject().apply {
                        put("url", endpoint)
                        put("protocolBinding", JSON_RPC_PROTOCOL_BINDING)
                        put("protocolVersion", A2A_PROTOCOL_VERSION)
                    }
                )
            )
            put(
                "capabilities",
                JSONObject().apply {
                    put("streaming", true)
                    put("pushNotifications", false)
                }
            )
            put("defaultInputModes", JSONArray().put(TEXT_MEDIA_TYPE))
            put("defaultOutputModes", JSONArray().put(TEXT_MEDIA_TYPE))
            put(
                "skills",
                JSONArray().put(
                    JSONObject().apply {
                        put("id", "operit-chat")
                        put("name", "Operit Chat")
                        put("description", "Send a text task to Operit and receive a text response")
                        put("tags", JSONArray().put("chat").put("assistant").put("tools"))
                    }
                )
            )
            put(
                "securitySchemes",
                JSONObject().put(
                    "bearerAuth",
                    JSONObject().put(
                        "httpAuthSecurityScheme",
                        JSONObject().apply {
                            put("scheme", "Bearer")
                            put("bearerFormat", "Operit Bearer Token")
                        }
                    )
                )
            )
            put("securityRequirements", JSONArray().put(JSONObject().put("bearerAuth", JSONArray())))
            put("version", BuildConfig.VERSION_NAME)
        }
        return jsonResponse(NanoHTTPD.Response.Status.OK, card)
    }

    fun handleJsonRpc(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        if (session.method != NanoHTTPD.Method.POST) {
            return jsonRpcErrorResponse(
                status = NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
                requestId = null,
                code = JSON_RPC_INVALID_REQUEST,
                message = "A2A JSON-RPC requires POST"
            )
        }
        val unauthorized = requireBearerToken(session)
        if (unauthorized != null) {
            return unauthorized
        }

        val body = try {
            readRequestBody(session)
        } catch (error: A2aProtocolException) {
            return jsonRpcErrorResponse(
                status = NanoHTTPD.Response.Status.BAD_REQUEST,
                requestId = null,
                code = JSON_RPC_PARSE_ERROR,
                message = error.message ?: "Invalid request body"
            )
        }
        val request = try {
            parseJsonRpcRequest(body)
        } catch (error: A2aProtocolException) {
            return jsonRpcErrorResponse(
                status = NanoHTTPD.Response.Status.BAD_REQUEST,
                requestId = null,
                code = JSON_RPC_INVALID_REQUEST,
                message = error.message ?: "Invalid JSON-RPC request"
            )
        }

        return try {
            validateRequestedProtocolVersion(session)
            when (request.method) {
                METHOD_SEND_MESSAGE -> {
                    val sendRequest = parseSendMessageRequest(request.params)
                    val submittedTask = taskManager.submit(sendRequest.message)
                    val resultTask = if (sendRequest.returnImmediately) {
                        submittedTask
                    } else {
                        runBlocking { taskManager.awaitTerminalTask(submittedTask.id) }
                    }
                    jsonRpcResultResponse(
                        request.id,
                        JSONObject().put("task", taskToJson(resultTask))
                    )
                }

                METHOD_SEND_STREAMING_MESSAGE -> {
                    val sendRequest = parseSendMessageRequest(request.params)
                    val task = taskManager.submit(sendRequest.message)
                    streamingResponse(task.id, request.id, allowTerminalTask = true)
                }

                METHOD_GET_TASK -> {
                    val task = taskManager.getTask(request.params.requiredString("id"))
                    jsonRpcResultResponse(request.id, taskToJson(task))
                }

                METHOD_LIST_TASKS -> {
                    val query = parseTaskListRequest(request.params)
                    jsonRpcResultResponse(request.id, listTasksToJson(query))
                }

                METHOD_CANCEL_TASK -> {
                    val task = taskManager.cancelTask(request.params.requiredString("id"))
                    jsonRpcResultResponse(request.id, taskToJson(task))
                }

                METHOD_SUBSCRIBE_TO_TASK -> {
                    val taskId = request.params.requiredString("id")
                    streamingResponse(taskId, request.id, allowTerminalTask = false)
                }

                else -> jsonRpcErrorResponse(
                    status = NanoHTTPD.Response.Status.OK,
                    requestId = request.id,
                    code = JSON_RPC_METHOD_NOT_FOUND,
                    message = "Unsupported A2A method: ${request.method}"
                )
            }
        } catch (error: A2aProtocolException) {
            jsonRpcErrorResponse(
                status = NanoHTTPD.Response.Status.OK,
                requestId = request.id,
                code = error.errorCode,
                message = error.message ?: "Invalid A2A request parameters"
            )
        } catch (error: Exception) {
            AppLogger.e(TAG, "A2A JSON-RPC request failed: ${request.method}", error)
            jsonRpcErrorResponse(
                status = NanoHTTPD.Response.Status.OK,
                requestId = request.id,
                code = JSON_RPC_INTERNAL_ERROR,
                message = "A2A request failed"
            )
        }
    }

    fun close() {
        taskManager.close()
    }

    private fun streamingResponse(
        taskId: String,
        requestId: Any,
        allowTerminalTask: Boolean
    ): NanoHTTPD.Response {
        val input = PipedInputStream(SSE_PIPE_BUFFER_SIZE)
        val output = PipedOutputStream(input)
        val eventChannel = Channel<A2aTaskEvent>(Channel.UNLIMITED)
        val subscription = try {
            taskManager.subscribe(
                taskId,
                requireActive = !allowTerminalTask
            ) { event -> eventChannel.trySend(event) }
        } catch (error: Exception) {
            eventChannel.close()
            output.close()
            input.close()
            throw error
        }
        val streamJob: Job = serviceScope.launch(Dispatchers.IO) {
            try {
                output.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                    val initialTask = subscription.snapshot
                    writeSseEvent(
                        writer,
                        jsonRpcStreamResponse(
                            requestId,
                            JSONObject().put("task", taskToJson(initialTask))
                        )
                    )
                    if (!A2aTaskManager.isTerminalState(initialTask.state)) {
                        while (true) {
                            val event = eventChannel.receive()
                            writeSseEvent(writer, jsonRpcStreamResponse(requestId, eventToJson(event)))
                            if (event is A2aTaskEvent.Status && event.final) {
                                break
                            }
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                AppLogger.i(TAG, "A2A streaming client disconnected: taskId=$taskId")
            } catch (error: Exception) {
                AppLogger.e(TAG, "A2A stream failed: taskId=$taskId", error)
            } finally {
                subscription.close()
                eventChannel.close()
            }
        }
        val responseInput = object : FilterInputStream(input) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    streamJob.cancel()
                }
            }
        }

        return NanoHTTPD.newChunkedResponse(
            NanoHTTPD.Response.Status.OK,
            SSE_MIME_TYPE,
            responseInput
        ).apply {
            addHeader("A2A-Version", A2A_PROTOCOL_VERSION)
            addHeader("Cache-Control", "no-cache")
            addHeader("Connection", "keep-alive")
            addHeader("X-Accel-Buffering", "no")
        }
    }

    private fun eventToJson(event: A2aTaskEvent): JSONObject {
        return when (event) {
            is A2aTaskEvent.Status -> statusUpdateEvent(event.task, event.final)
            is A2aTaskEvent.Artifact -> JSONObject().put(
                "artifactUpdate",
                JSONObject().apply {
                    put("taskId", event.task.id)
                    put("contextId", event.task.contextId)
                    put("artifact", artifactToJson(event.task.id, event.text))
                    put("append", true)
                    put("lastChunk", false)
                }
            )
        }
    }

    private fun statusUpdateEvent(task: A2aTaskSnapshot, final: Boolean): JSONObject {
        return JSONObject().put(
            "statusUpdate",
            JSONObject().apply {
                put("taskId", task.id)
                put("contextId", task.contextId)
                put("status", statusToJson(task))
                put("final", final)
            }
        )
    }

    private fun taskToJson(task: A2aTaskSnapshot): JSONObject {
        return JSONObject().apply {
            put("id", task.id)
            put("contextId", task.contextId)
            put("status", statusToJson(task))
            if (task.output.isNotBlank()) {
                put("artifacts", JSONArray().put(artifactToJson(task.id, task.output)))
            }
        }
    }

    private fun statusToJson(task: A2aTaskSnapshot): JSONObject {
        return JSONObject().apply {
            put("state", task.state)
            task.error?.takeIf { it.isNotBlank() }?.let { message ->
                put(
                    "message",
                    JSONObject().apply {
                        put("messageId", "${task.id}-status")
                        put("contextId", task.contextId)
                        put("taskId", task.id)
                        put("role", ROLE_AGENT)
                        put("parts", JSONArray().put(JSONObject().put("text", message)))
                    }
                )
            }
        }
    }

    private fun artifactToJson(taskId: String, text: String): JSONObject {
        return JSONObject().apply {
            put("artifactId", "$taskId-result")
            put("parts", JSONArray().put(JSONObject().put("text", text)))
        }
    }

    private fun parseSendMessageRequest(params: JSONObject): A2aSendMessageRequest {
        val configuration = params.optionalObject("configuration")
        val returnImmediately = configuration?.optionalBoolean("returnImmediately") ?: false
        if (configuration?.hasNonNullValue("taskPushNotificationConfig") == true) {
            throw A2aPushNotificationNotSupportedException()
        }
        configuration?.validateAcceptedOutputModes()
        configuration?.optionalNonNegativeInt("historyLength")
        return A2aSendMessageRequest(
            message = parseIncomingMessage(params),
            returnImmediately = returnImmediately
        )
    }

    private fun parseIncomingMessage(params: JSONObject): A2aIncomingMessage {
        val message = params.optJSONObject("message")
            ?: throw A2aProtocolException("A2A SendMessage params must contain message")
        if (message.requiredString("role") != ROLE_USER) {
            throw A2aProtocolException("A2A server accepts ROLE_USER messages only")
        }
        message.requiredString("messageId")
        if (message.optionalString("taskId") != null) {
            throw A2aUnsupportedOperationException(
                "Operit does not accept messages for an existing A2A task"
            )
        }
        val parts = message.optJSONArray("parts")
            ?: throw A2aProtocolException("A2A message must contain parts")
        if (parts.length() == 0) {
            throw A2aProtocolException("A2A message parts must not be empty")
        }
        val text = parseA2aTextParts(parts)
        if (text.isBlank()) {
            throw A2aProtocolException("A2A message text must not be blank")
        }
        return A2aIncomingMessage(
            text = text,
            contextId = message.optionalString("contextId")
        )
    }

    private fun parseTaskListRequest(params: JSONObject): A2aTaskListRequest {
        val contextId = params.optionalString("contextId")
        val state = params.optionalString("status")
        if (state != null && state !in VALID_TASK_STATES) {
            throw A2aProtocolException("A2A task status is invalid: $state")
        }
        val pageSize = params.optionalPositiveInt("pageSize") ?: DEFAULT_TASK_LIST_PAGE_SIZE
        if (pageSize > MAX_TASK_LIST_PAGE_SIZE) {
            throw A2aProtocolException(
                "A2A task pageSize must not exceed $MAX_TASK_LIST_PAGE_SIZE"
            )
        }
        return A2aTaskListRequest(
            contextId = contextId,
            state = state,
            pageSize = pageSize,
            pageToken = params.optionalString("pageToken")
        )
    }

    private fun listTasksToJson(query: A2aTaskListRequest): JSONObject {
        val tasks = taskManager.listTasks(query.contextId, query.state)
        val startIndex = query.pageToken?.let { token ->
            val lastTaskIndex = tasks.indexOfFirst { task -> task.id == token }
            if (lastTaskIndex < 0) {
                throw A2aProtocolException("A2A task pageToken is invalid")
            }
            lastTaskIndex + 1
        } ?: 0
        val page = tasks.drop(startIndex).take(query.pageSize)
        return JSONObject().apply {
            put("tasks", JSONArray().apply {
                page.forEach { task -> put(taskToJson(task)) }
            })
            if (startIndex + page.size < tasks.size) {
                put("nextPageToken", page.last().id)
            }
        }
    }

    private fun parseJsonRpcRequest(body: String): A2aJsonRpcRequest {
        val json = try {
            JSONObject(body)
        } catch (error: Exception) {
            throw A2aProtocolException("A2A JSON-RPC request body is not valid JSON")
        }
        if (json.requiredString("jsonrpc") != JSON_RPC_VERSION) {
            throw A2aProtocolException("A2A JSON-RPC version must be $JSON_RPC_VERSION")
        }
        val id = json.opt("id")
        if (id !is String && id !is Number) {
            throw A2aProtocolException("A2A JSON-RPC request id must be a string or number")
        }
        return A2aJsonRpcRequest(
            id = id,
            method = json.requiredString("method"),
            params = json.optJSONObject("params")
                ?: throw A2aProtocolException("A2A JSON-RPC params must be an object")
        )
    }

    private fun readRequestBody(session: NanoHTTPD.IHTTPSession): String {
        val contentLength = session.headers.entries.firstOrNull {
            it.key.equals("content-length", ignoreCase = true)
        }?.value?.trim()?.toLongOrNull()
            ?: throw A2aProtocolException("A2A JSON-RPC requires Content-Length")
        if (contentLength !in 1..MAX_REQUEST_BYTES.toLong()) {
            throw A2aProtocolException("A2A JSON-RPC Content-Length must be between 1 and $MAX_REQUEST_BYTES")
        }

        val bodyBytes = ByteArray(contentLength.toInt())
        var offset = 0
        while (offset < bodyBytes.size) {
            val count = session.inputStream.read(bodyBytes, offset, bodyBytes.size - offset)
            if (count < 0) {
                throw A2aProtocolException("A2A JSON-RPC request ended before Content-Length bytes were received")
            }
            offset += count
        }
        return String(bodyBytes, StandardCharsets.UTF_8)
    }

    private fun validateRequestedProtocolVersion(session: NanoHTTPD.IHTTPSession) {
        val requestedVersion = session.headers.entries.firstOrNull {
            it.key.equals("a2a-version", ignoreCase = true)
        }?.value?.trim()?.takeIf { it.isNotBlank() }
        if (requestedVersion != null && requestedVersion != A2A_PROTOCOL_VERSION) {
            throw A2aProtocolException(
                "A2A protocol version is not supported: $requestedVersion",
                errorCode = A2A_VERSION_NOT_SUPPORTED
            )
        }
    }

    private fun buildJsonRpcEndpoint(session: NanoHTTPD.IHTTPSession): String {
        val host = session.headers.entries.firstOrNull {
            it.key.equals("host", ignoreCase = true)
        }?.value?.trim()?.takeIf { it.isNotBlank() }
            ?: throw A2aProtocolException("A2A Agent Card request must include Host")
        if (host.contains('/') || host.contains('?') || host.contains('#')) {
            throw A2aProtocolException("A2A Agent Card Host is invalid")
        }
        return "http://$host$A2A_PATH"
    }

    private fun jsonRpcResultResponse(id: Any, result: JSONObject): NanoHTTPD.Response {
        return jsonResponse(
            NanoHTTPD.Response.Status.OK,
            JSONObject().apply {
                put("jsonrpc", JSON_RPC_VERSION)
                put("id", id)
                put("result", result)
            }
        )
    }

    private fun jsonRpcStreamResponse(id: Any, result: JSONObject): JSONObject {
        return JSONObject().apply {
            put("jsonrpc", JSON_RPC_VERSION)
            put("id", id)
            put("result", result)
        }
    }

    private fun jsonRpcErrorResponse(
        status: NanoHTTPD.Response.Status,
        requestId: Any?,
        code: Int,
        message: String
    ): NanoHTTPD.Response {
        return jsonResponse(
            status,
            JSONObject().apply {
                put("jsonrpc", JSON_RPC_VERSION)
                put("id", requestId ?: JSONObject.NULL)
                put("error", JSONObject().apply {
                    put("code", code)
                    put("message", message)
                })
            }
        )
    }

    private fun jsonResponse(status: NanoHTTPD.Response.Status, body: JSONObject): NanoHTTPD.Response {
        return NanoHTTPD.newFixedLengthResponse(status, JSON_MIME_TYPE, body.toString()).apply {
            addHeader("A2A-Version", A2A_PROTOCOL_VERSION)
        }
    }

    private fun plainResponse(status: NanoHTTPD.Response.Status, body: String): NanoHTTPD.Response {
        return NanoHTTPD.newFixedLengthResponse(status, NanoHTTPD.MIME_PLAINTEXT, body)
    }

    private fun writeSseEvent(writer: BufferedWriter, payload: JSONObject) {
        val serialized = payload.toString()
        serialized.lineSequence().forEach { line ->
            writer.write("data: ")
            writer.write(line)
            writer.newLine()
        }
        writer.newLine()
        writer.flush()
    }

    private data class A2aJsonRpcRequest(
        val id: Any,
        val method: String,
        val params: JSONObject
    )

    private data class A2aSendMessageRequest(
        val message: A2aIncomingMessage,
        val returnImmediately: Boolean
    )

    private data class A2aTaskListRequest(
        val contextId: String?,
        val state: String?,
        val pageSize: Int,
        val pageToken: String?
    )

    companion object {
        private const val TAG = "A2aHttpHandler"
        const val AGENT_CARD_PATH = "/.well-known/agent-card.json"
        const val A2A_PATH = "/a2a"
        private const val A2A_PROTOCOL_VERSION = "1.0"
        private const val JSON_RPC_PROTOCOL_BINDING = "JSONRPC"
        private const val JSON_RPC_VERSION = "2.0"
        private const val ROLE_USER = "ROLE_USER"
        private const val ROLE_AGENT = "ROLE_AGENT"
        private const val METHOD_SEND_MESSAGE = "SendMessage"
        private const val METHOD_SEND_STREAMING_MESSAGE = "SendStreamingMessage"
        private const val METHOD_GET_TASK = "GetTask"
        private const val METHOD_LIST_TASKS = "ListTasks"
        private const val METHOD_CANCEL_TASK = "CancelTask"
        private const val METHOD_SUBSCRIBE_TO_TASK = "SubscribeToTask"
        private const val JSON_RPC_PARSE_ERROR = -32700
        private const val JSON_RPC_INVALID_REQUEST = -32600
        private const val JSON_RPC_METHOD_NOT_FOUND = -32601
        private const val JSON_RPC_INTERNAL_ERROR = -32603
        private const val A2A_VERSION_NOT_SUPPORTED = -32009
        private const val JSON_MIME_TYPE = "application/json; charset=utf-8"
        private const val SSE_MIME_TYPE = "text/event-stream; charset=utf-8"
        private const val TEXT_MEDIA_TYPE = "text/plain"
        private const val SSE_PIPE_BUFFER_SIZE = 64 * 1024
        private const val MAX_REQUEST_BYTES = 1024 * 1024
        private const val DEFAULT_TASK_LIST_PAGE_SIZE = 50
        private const val MAX_TASK_LIST_PAGE_SIZE = 100
        private val VALID_TASK_STATES = setOf(
            A2aTaskManager.TASK_STATE_SUBMITTED,
            A2aTaskManager.TASK_STATE_WORKING,
            A2aTaskManager.TASK_STATE_INPUT_REQUIRED,
            A2aTaskManager.TASK_STATE_AUTH_REQUIRED,
            A2aTaskManager.TASK_STATE_COMPLETED,
            A2aTaskManager.TASK_STATE_CANCELED,
            A2aTaskManager.TASK_STATE_FAILED,
            A2aTaskManager.TASK_STATE_REJECTED
        )
    }
}

/**
 * A2A 1.0 `Part` carries its payload in a `content` oneof (`text`, `raw`, `url`, `data`) while
 * `metadata`, `filename`, and `mediaType` are sibling fields. The part kind therefore follows
 * which content member is present; companions such as `mediaType: "text/plain"` leave a text
 * part a text part.
 */
internal fun parseA2aTextParts(parts: JSONArray): String {
    val texts = mutableListOf<String>()
    for (index in 0 until parts.length()) {
        val part = parts.optJSONObject(index)
            ?: throw A2aProtocolException("A2A message part[$index] must be an object")
        val contentMembers = A2A_PART_CONTENT_MEMBERS.filter { part.hasNonNullValue(it) }
        if (contentMembers.size != 1) {
            throw A2aProtocolException(
                "A2A message part[$index] must contain exactly one of " +
                    A2A_PART_CONTENT_MEMBERS.joinToString(separator = ", ")
            )
        }
        val contentMember = contentMembers.single()
        if (contentMember != A2A_TEXT_MEMBER) {
            throw A2aProtocolException(
                "A2A server accepts text parts only, part[$index] contains $contentMember"
            )
        }
        texts += part.requiredString(A2A_TEXT_MEMBER)
    }
    return texts.joinToString(separator = "\n")
}

private const val A2A_TEXT_MEMBER = "text"

private val A2A_PART_CONTENT_MEMBERS = listOf(A2A_TEXT_MEMBER, "raw", "url", "data")

private fun JSONObject.requiredString(name: String): String {
    return optionalString(name) ?: throw A2aProtocolException("A2A field $name is required")
}

private fun JSONObject.optionalString(name: String): String? {
    if (!has(name) || isNull(name)) {
        return null
    }
    val value = opt(name)
    if (value !is String || value.isBlank()) {
        throw A2aProtocolException("A2A field $name must be a non-blank string")
    }
    return value
}

private fun JSONObject.optionalObject(name: String): JSONObject? {
    if (!has(name) || isNull(name)) {
        return null
    }
    return optJSONObject(name) ?: throw A2aProtocolException("A2A field $name must be an object")
}

private fun JSONObject.optionalBoolean(name: String): Boolean? {
    if (!has(name) || isNull(name)) {
        return null
    }
    return opt(name) as? Boolean ?: throw A2aProtocolException("A2A field $name must be a boolean")
}

private fun JSONObject.optionalNonNegativeInt(name: String): Int? {
    if (!has(name) || isNull(name)) {
        return null
    }
    val value = opt(name)
    if (value !is Number) {
        throw A2aProtocolException("A2A field $name must be an integer")
    }
    val longValue = value.toLong()
    if (value.toDouble() != longValue.toDouble() || longValue !in 0..Int.MAX_VALUE.toLong()) {
        throw A2aProtocolException("A2A field $name must be a non-negative integer")
    }
    return longValue.toInt()
}

private fun JSONObject.optionalPositiveInt(name: String): Int? {
    val value = optionalNonNegativeInt(name) ?: return null
    if (value == 0) {
        throw A2aProtocolException("A2A field $name must be greater than zero")
    }
    return value
}

private fun JSONObject.hasNonNullValue(name: String): Boolean {
    return has(name) && !isNull(name)
}

private fun JSONObject.validateAcceptedOutputModes() {
    if (!has("acceptedOutputModes") || isNull("acceptedOutputModes")) {
        return
    }
    val modes = optJSONArray("acceptedOutputModes")
        ?: throw A2aProtocolException("A2A field acceptedOutputModes must be an array")
    val acceptsText = (0 until modes.length()).any { index ->
        modes.opt(index) == "text/plain"
    }
    if (!acceptsText) {
        throw A2aProtocolException(
            "Operit supports text/plain A2A output only",
            errorCode = -32005
        )
    }
}
