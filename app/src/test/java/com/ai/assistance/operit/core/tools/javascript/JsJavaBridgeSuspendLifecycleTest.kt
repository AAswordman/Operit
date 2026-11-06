package com.ai.assistance.operit.core.tools.javascript

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class JavaSuspendLifecycleProbe {
    val started = CompletableDeferred<Unit>()
    val cancelled = CompletableDeferred<Unit>()

    suspend fun collectUntilCancelled(): String {
        started.complete(Unit)
        try {
            awaitCancellation()
        } finally {
            cancelled.complete(Unit)
        }
    }

    suspend fun echo(value: String): String = value
}

class JsJavaBridgeSuspendLifecycleTest {
    @Test
    fun `ending an owner cancels the real reflected suspend collection`() = runTest {
        val owner = SupervisorJob()
        val invocation = SupervisorJob(owner)
        val probe = JavaSuspendLifecycleProbe()
        val responses = mutableListOf<String>()
        val registry = ConcurrentHashMap<String, Any>().apply { put("probe", probe) }

        JsJavaBridgeDelegates.callInstanceSuspend(
            instanceHandle = "probe",
            methodName = "collectUntilCancelled",
            argsJson = "[]",
            objectRegistry = registry,
            callback = { responses.add(it) },
            coroutineContext = invocation + StandardTestDispatcher(testScheduler),
        )
        assertTrue(probe.started.isCompleted)
        assertTrue(responses.isEmpty())

        owner.cancel(CancellationException("execution ended"))
        runCurrent()

        assertTrue(probe.cancelled.isCompleted)
        assertEquals(1, responses.size)
        assertFalse(JSONObject(responses.single()).getBoolean("success"))
        assertTrue(invocation.isCancelled)
    }

    @Test
    fun `cancelling one execution leaves another collection active`() = runTest {
        val engineOwner = SupervisorJob()
        val firstOwner = SupervisorJob(engineOwner)
        val secondOwner = SupervisorJob(engineOwner)
        val first = JavaSuspendLifecycleProbe()
        val second = JavaSuspendLifecycleProbe()
        val registry = ConcurrentHashMap<String, Any>().apply {
            put("first", first)
            put("second", second)
        }
        val firstResponses = mutableListOf<String>()
        val secondResponses = mutableListOf<String>()
        for ((handle, owner, responses) in listOf(
            Triple("first", firstOwner, firstResponses),
            Triple("second", secondOwner, secondResponses),
        )) {
            JsJavaBridgeDelegates.callInstanceSuspend(
                instanceHandle = handle,
                methodName = "collectUntilCancelled",
                argsJson = "[]",
                objectRegistry = registry,
                callback = { responses.add(it) },
                coroutineContext = SupervisorJob(owner) + StandardTestDispatcher(testScheduler),
            )
        }

        firstOwner.cancel()
        runCurrent()
        assertTrue(first.cancelled.isCompleted)
        assertFalse(second.cancelled.isCompleted)
        assertEquals(1, firstResponses.size)
        assertTrue(secondResponses.isEmpty())

        engineOwner.cancel()
        runCurrent()
        assertTrue(second.cancelled.isCompleted)
        assertEquals(1, secondResponses.size)
    }

    @Test
    fun `a cancelled owner cannot start the reflected method`() = runTest {
        val owner = Job().apply { cancel() }
        val probe = JavaSuspendLifecycleProbe()
        val responses = mutableListOf<String>()

        JsJavaBridgeDelegates.callInstanceSuspend(
            instanceHandle = "probe",
            methodName = "collectUntilCancelled",
            argsJson = "[]",
            objectRegistry = ConcurrentHashMap<String, Any>().apply { put("probe", probe) },
            callback = { responses.add(it) },
            coroutineContext = owner + StandardTestDispatcher(testScheduler),
        )

        assertFalse(probe.started.isCompleted)
        assertEquals(1, responses.size)
        assertFalse(JSONObject(responses.single()).getBoolean("success"))
    }

    @Test
    fun `an immediate return still completes exactly once`() = runTest {
        val owner = SupervisorJob()
        val responses = mutableListOf<String>()

        JsJavaBridgeDelegates.callInstanceSuspend(
            instanceHandle = "probe",
            methodName = "echo",
            argsJson = "[\"ok\"]",
            objectRegistry = ConcurrentHashMap<String, Any>().apply { put("probe", JavaSuspendLifecycleProbe()) },
            callback = { responses.add(it) },
            coroutineContext = owner + StandardTestDispatcher(testScheduler),
        )
        owner.cancel()
        runCurrent()

        assertEquals(1, responses.size)
        val result = JSONObject(responses.single())
        assertTrue(result.getBoolean("success"))
        assertEquals("ok", result.getString("data"))
    }
}
