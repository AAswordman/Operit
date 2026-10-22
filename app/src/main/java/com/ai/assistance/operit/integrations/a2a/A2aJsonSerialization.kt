package com.ai.assistance.operit.integrations.a2a

import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes the A2A 1.0 payloads of the JSON-RPC surface.
 *
 * Members follow the A2A 1.0 protobuf definitions for `lf.a2a.v1`. `TaskStatusUpdateEvent` only
 * has `taskId`, `contextId`, `status` and optional `metadata`, so a stream ends at the status whose
 * `status.state` is terminal. Strict clients, such as the official SDK parsing with
 * `json_format.ParseDict`, reject any member outside that field set.
 */
internal object A2aJsonSerialization {

    private const val JSON_RPC_VERSION = "2.0"
    private const val ROLE_AGENT = "ROLE_AGENT"

    /** One SSE `data` frame plus whether the stream ends after it. */
    data class StreamFrame(
        val payload: JSONObject,
        val endsStream: Boolean
    )

    /** JSON-RPC 2.0 response envelope shared by the unary and the streaming surface. */
    fun jsonRpcResult(id: Any, result: JSONObject): JSONObject {
        return JSONObject().apply {
            put("jsonrpc", JSON_RPC_VERSION)
            put("id", id)
            put("result", result)
        }
    }

    /** Payload of the single `Task` returned by `SendMessage`, `GetTask` and `CancelTask`. */
    fun task(task: A2aTaskSnapshot): JSONObject {
        return JSONObject().apply {
            put("id", task.id)
            put("contextId", task.contextId)
            put("status", status(task))
            if (task.output.isNotBlank()) {
                put("artifacts", JSONArray().put(artifact(task.id, task.output)))
            }
        }
    }

    /** Payload of the `Task` stream member sent before the task starts reporting changes. */
    fun taskStreamFrame(requestId: Any, task: A2aTaskSnapshot): StreamFrame {
        return StreamFrame(
            payload = jsonRpcResult(requestId, JSONObject().put("task", task(task))),
            endsStream = A2aTaskManager.isTerminalState(task.state)
        )
    }

    /** Stream member for a task change, using the schema member of its event type. */
    fun eventStreamFrame(requestId: Any, event: A2aTaskEvent): StreamFrame {
        val (result, endsStream) = when (event) {
            is A2aTaskEvent.Status ->
                statusUpdate(event.task) to A2aTaskManager.isTerminalState(event.task.state)

            is A2aTaskEvent.Artifact -> artifactUpdate(event.task, event.text) to false
        }
        return StreamFrame(payload = jsonRpcResult(requestId, result), endsStream = endsStream)
    }

    private fun statusUpdate(task: A2aTaskSnapshot): JSONObject {
        return JSONObject().put(
            "statusUpdate",
            JSONObject().apply {
                put("taskId", task.id)
                put("contextId", task.contextId)
                put("status", status(task))
            }
        )
    }

    private fun artifactUpdate(task: A2aTaskSnapshot, text: String): JSONObject {
        return JSONObject().put(
            "artifactUpdate",
            JSONObject().apply {
                put("taskId", task.id)
                put("contextId", task.contextId)
                put("artifact", artifact(task.id, text))
                put("append", true)
                put("lastChunk", false)
            }
        )
    }

    private fun status(task: A2aTaskSnapshot): JSONObject {
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

    private fun artifact(taskId: String, text: String): JSONObject {
        return JSONObject().apply {
            put("artifactId", "$taskId-result")
            put("parts", JSONArray().put(JSONObject().put("text", text)))
        }
    }
}
