package com.ai.assistance.operit.integrations.a2a

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Field sets of the A2A 1.0 JSON-RPC payloads, checked against the `lf.a2a.v1` definitions.
 *
 * The official SDK parses with `json_format.ParseDict` in strict mode and rejects any member that
 * the message type does not declare. `TaskStatusUpdateEvent` declares only `taskId`, `contextId`,
 * `status` and `metadata`, so a stream must not carry a `final` bit; it ends at the status whose
 * `state` is terminal.
 */
class A2aJsonSerializationTest {

    private val working = snapshot(state = A2aTaskManager.TASK_STATE_WORKING)
    private val completed = snapshot(state = A2aTaskManager.TASK_STATE_COMPLETED)
    private val failed = snapshot(state = A2aTaskManager.TASK_STATE_FAILED, error = "boom")

    @Test
    fun `status update carries only the fields of TaskStatusUpdateEvent`() {
        val result = streamResult(A2aJsonSerialization.eventStreamFrame(1, A2aTaskEvent.Status(completed)))
        val update = result.getJSONObject("statusUpdate")

        assertEquals(setOf("taskId", "contextId", "status"), update.keys().asSequence().toSet())
        assertEquals(TASK_ID, update.getString("taskId"))
        assertEquals(CONTEXT_ID, update.getString("contextId"))
        assertEquals(
            setOf("state"),
            update.getJSONObject("status").keys().asSequence().toSet()
        )
        assertEquals(
            A2aTaskManager.TASK_STATE_COMPLETED,
            update.getJSONObject("status").getString("state")
        )
    }

    @Test
    fun `status update never carries the schema-external final member`() {
        val frames = listOf(
            A2aJsonSerialization.eventStreamFrame(1, A2aTaskEvent.Status(working)),
            A2aJsonSerialization.eventStreamFrame(1, A2aTaskEvent.Status(completed)),
            A2aJsonSerialization.eventStreamFrame(1, A2aTaskEvent.Status(failed)),
            A2aJsonSerialization.taskStreamFrame(1, working),
            A2aJsonSerialization.taskStreamFrame(1, completed),
            A2aJsonSerialization.eventStreamFrame(1, A2aTaskEvent.Artifact(working, "partial"))
        )

        frames.forEach { frame ->
            val serialized = frame.payload.toString()
            assertFalse("payload must not expose a final member: $serialized", containsKey(frame.payload, "final"))
        }
    }

    @Test
    fun `status update keeps the status message of a failed task`() {
        val update = streamResult(
            A2aJsonSerialization.eventStreamFrame(1, A2aTaskEvent.Status(failed))
        ).getJSONObject("statusUpdate")
        val status = update.getJSONObject("status")
        val message = status.getJSONObject("message")

        assertEquals(setOf("state", "message"), status.keys().asSequence().toSet())
        assertEquals(
            setOf("messageId", "contextId", "taskId", "role", "parts"),
            message.keys().asSequence().toSet()
        )
        assertEquals("ROLE_AGENT", message.getString("role"))
        assertEquals("boom", message.getJSONArray("parts").getJSONObject(0).getString("text"))
    }

    @Test
    fun `artifact update carries only the fields of TaskArtifactUpdateEvent`() {
        val result = streamResult(
            A2aJsonSerialization.eventStreamFrame(1, A2aTaskEvent.Artifact(working, "chunk"))
        )
        val update = result.getJSONObject("artifactUpdate")

        assertEquals(
            setOf("taskId", "contextId", "artifact", "append", "lastChunk"),
            update.keys().asSequence().toSet()
        )
        assertEquals(
            setOf("artifactId", "parts"),
            update.getJSONObject("artifact").keys().asSequence().toSet()
        )
        assertEquals(
            setOf("text"),
            update.getJSONObject("artifact").getJSONArray("parts").getJSONObject(0).keys()
                .asSequence().toSet()
        )
        assertTrue(update.getBoolean("append"))
        assertFalse(update.getBoolean("lastChunk"))
    }

    @Test
    fun `task payload carries only the fields of Task`() {
        val task = A2aJsonSerialization.task(completed.copy(output = "answer"))

        assertEquals(
            setOf("id", "contextId", "status", "artifacts"),
            task.keys().asSequence().toSet()
        )
        assertEquals(
            setOf("artifactId", "parts"),
            task.getJSONArray("artifacts").getJSONObject(0).keys().asSequence().toSet()
        )
    }

    @Test
    fun `task payload omits artifacts until output exists`() {
        val task = A2aJsonSerialization.task(working)

        assertEquals(setOf("id", "contextId", "status"), task.keys().asSequence().toSet())
    }

    @Test
    fun `json rpc envelope keeps the jsonrpc id and result members`() {
        val payload = A2aJsonSerialization.taskStreamFrame("req-1", working).payload

        assertEquals(setOf("jsonrpc", "id", "result"), payload.keys().asSequence().toSet())
        assertEquals("2.0", payload.getString("jsonrpc"))
        assertEquals("req-1", payload.getString("id"))
        assertEquals(setOf("task"), payload.getJSONObject("result").keys().asSequence().toSet())
    }

    @Test
    fun `a stream stays open until the status is terminal`() {
        val sequence = listOf(
            A2aJsonSerialization.taskStreamFrame(1, working),
            A2aJsonSerialization.eventStreamFrame(1, A2aTaskEvent.Artifact(working, "first")),
            A2aJsonSerialization.eventStreamFrame(1, A2aTaskEvent.Artifact(working, "second")),
            A2aJsonSerialization.eventStreamFrame(1, A2aTaskEvent.Status(working)),
            A2aJsonSerialization.eventStreamFrame(1, A2aTaskEvent.Status(completed))
        )

        assertEquals(
            listOf(false, false, false, false, true),
            sequence.map { frame -> frame.endsStream }
        )
    }

    @Test
    fun `a terminal snapshot closes the stream without waiting for an event`() {
        assertTrue(A2aJsonSerialization.taskStreamFrame(1, completed).endsStream)
        assertFalse(A2aJsonSerialization.taskStreamFrame(1, working).endsStream)
    }

    @Test
    fun `every terminal state ends the stream and no active state does`() {
        val terminalStates = listOf(
            A2aTaskManager.TASK_STATE_COMPLETED,
            A2aTaskManager.TASK_STATE_CANCELED,
            A2aTaskManager.TASK_STATE_FAILED,
            A2aTaskManager.TASK_STATE_REJECTED
        )
        val activeStates = listOf(
            A2aTaskManager.TASK_STATE_SUBMITTED,
            A2aTaskManager.TASK_STATE_WORKING,
            A2aTaskManager.TASK_STATE_INPUT_REQUIRED,
            A2aTaskManager.TASK_STATE_AUTH_REQUIRED
        )

        terminalStates.forEach { state ->
            assertTrue(
                state,
                A2aJsonSerialization.eventStreamFrame(1, A2aTaskEvent.Status(snapshot(state)))
                    .endsStream
            )
        }
        activeStates.forEach { state ->
            assertFalse(
                state,
                A2aJsonSerialization.eventStreamFrame(1, A2aTaskEvent.Status(snapshot(state)))
                    .endsStream
            )
        }
    }

    private fun streamResult(frame: A2aJsonSerialization.StreamFrame): JSONObject {
        return frame.payload.getJSONObject("result")
    }

    private fun snapshot(
        state: String,
        error: String? = null
    ): A2aTaskSnapshot {
        return A2aTaskSnapshot(
            id = TASK_ID,
            contextId = CONTEXT_ID,
            state = state,
            output = "",
            error = error
        )
    }

    private fun containsKey(json: Any?, key: String): Boolean {
        return when (json) {
            is JSONObject -> json.keys().asSequence().any { name ->
                name == key || containsKey(json.opt(name), key)
            }

            is JSONArray -> (0 until json.length()).any { index ->
                containsKey(json.opt(index), key)
            }

            else -> false
        }
    }

    private companion object {
        const val TASK_ID = "task-1"
        const val CONTEXT_ID = "context-1"
    }
}
