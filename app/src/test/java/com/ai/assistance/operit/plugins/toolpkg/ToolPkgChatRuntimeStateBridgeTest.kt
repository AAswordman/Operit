package com.ai.assistance.operit.plugins.toolpkg

import com.ai.assistance.operit.api.chat.ChatRuntimeStateApplicationState
import com.ai.assistance.operit.api.chat.ChatRuntimeStateError
import com.ai.assistance.operit.api.chat.ChatRuntimeStateErrorSource
import com.ai.assistance.operit.api.chat.ChatRuntimeStatePhase
import com.ai.assistance.operit.api.chat.ChatRuntimeStateRetry
import com.ai.assistance.operit.api.chat.ChatRuntimeStateSnapshot
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolPkgChatRuntimeStateBridgeTest {
    @Test
    fun boundedQueueRecordsOverflowAndRequestsResync() {
        val droppedCounts = mutableListOf<Long>()
        val queue = BoundedRuntimeStateQueue<Int>(capacity = 2) { dropped ->
            droppedCounts += dropped
        }

        assertTrue(queue.enqueue(1))
        assertTrue(queue.enqueue(2))
        assertFalse(queue.enqueue(3))

        assertEquals(1L, queue.droppedCount())
        assertEquals(listOf(1L), droppedCounts)
        assertTrue(queue.takeResyncRequest())
        assertFalse(queue.takeResyncRequest())
    }

    @Test
    fun hookDeliveryRunsConcurrentlyAndIsolatesFailureAndTimeout() = runTest {
        val fast = hook("fast")
        val failing = hook("failing")
        val slow = hook("slow")
        val completed = mutableListOf<String>()

        val results = deliverRuntimeStateHooksConcurrently(
            hooks = listOf(slow, fast, failing),
            timeoutMillis = 100L
        ) { hook ->
            when (hook.hookId) {
                "slow" -> {
                    delay(1_000L)
                    completed += hook.hookId
                    Result.success(null)
                }
                "failing" -> Result.failure(IllegalStateException("hook failed"))
                else -> {
                    delay(10L)
                    completed += hook.hookId
                    Result.success(null)
                }
            }
        }

        val byId = results.associateBy { it.hook.hookId }
        assertNull(byId.getValue("fast").failure)
        assertTrue(byId.getValue("failing").failure is IllegalStateException)
        assertTrue(byId.getValue("slow").failure is TimeoutException)
        assertEquals(listOf("fast"), completed)
    }

    @Test
    fun sessionPayloadNestsErrorAndRetryAndOmitsLegacyFlatFields() {
        val payload = ToolPkgChatRuntimeStateBridge.buildSessionPayload(
            ChatRuntimeStateSnapshot(
                chatId = "chat-1",
                phase = ChatRuntimeStatePhase.RETRYING,
                userState = null,
                applicationState = ChatRuntimeStateApplicationState.FOREGROUND,
                error = ChatRuntimeStateError(
                    source = ChatRuntimeStateErrorSource.API,
                    code = "rate_limited",
                    message = "Too many requests",
                    recoverable = true,
                    providerCode = "rate_limit_error",
                    httpStatusCode = 429
                ),
                retry = ChatRuntimeStateRetry(
                    attempt = 1,
                    maxAttempts = 5,
                    retryAfterMs = 1000L
                )
            )
        )

        val error = payload["error"] as Map<*, *>
        val retry = payload["retry"] as Map<*, *>
        assertEquals("api", error["source"])
        assertEquals("rate_limited", error["code"])
        assertEquals("rate_limit_error", error["providerCode"])
        assertEquals(429, error["httpStatusCode"])
        assertEquals(1, retry["attempt"])
        assertEquals(5, retry["maxAttempts"])
        assertEquals(1000L, retry["retryAfterMs"])
        assertFalse(payload.containsKey("errorCode"))
        assertFalse(payload.containsKey("retryAttempt"))
        assertFalse(payload.containsKey("errorRetryAfterMs"))

        val idlePayload = ToolPkgChatRuntimeStateBridge.buildSessionPayload(
            ChatRuntimeStateSnapshot(
                chatId = "chat-1",
                phase = ChatRuntimeStatePhase.IDLE,
                userState = null,
                applicationState = ChatRuntimeStateApplicationState.FOREGROUND
            )
        )
        assertFalse(idlePayload.containsKey("error"))
        assertFalse(idlePayload.containsKey("retry"))
    }

    private fun hook(id: String) = ToolPkgChatRuntimeStateHookRegistration(
        containerPackageName = "test.package.$id",
        hookId = id,
        functionName = "onRuntimeState"
    )
}