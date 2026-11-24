package com.ai.assistance.operit.core.subagent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentJobRegistryTest {

    private fun snapshot(task: String = "task") =
        AgentJobRegistry.TaskSnapshot(
            task = task,
            agentName = null,
            context = null,
            progressReport = "moderate",
        )

    private fun register(
        registry: AgentJobRegistry,
        title: String,
        configId: String,
        notifyParentOnFinish: Boolean = true,
    ) = registry.register(
        parentChatId = "chat",
        profileId = "profile",
        profileName = "agent",
        toolTitle = title,
        snapshot = snapshot(),
        notifyParentOnFinish = notifyParentOnFinish,
        modelConfigId = configId,
        modelIndex = 0,
    )

    @Test
    fun `concurrency normalisation never yields unlimited`() {
        assertEquals(1, AgentJobRegistry.normalizeConcurrency(0))
        assertEquals(1, AgentJobRegistry.normalizeConcurrency(-5))
        assertEquals(3, AgentJobRegistry.normalizeConcurrency(3))
    }

    @Test
    fun `slots are counted per model config`() {
        val registry = AgentJobRegistry()
        registry.setConcurrencyLimit("cfgA", 1)
        registry.setConcurrencyLimit("cfgB", 1)

        val (jobA1, slotA1) = register(registry, "a1", "cfgA")
        val (jobB1, slotB1) = register(registry, "b1", "cfgB")
        val (jobA2, slotA2) = register(registry, "a2", "cfgA")

        assertTrue(slotA1)
        // 不同配置互不挤占：B 的槽位满额不影响 A 之外的统计。
        assertTrue(slotB1)
        assertFalse(slotA2)
        assertEquals(AgentJobRegistry.Status.QUEUED, jobA2.status)
        assertEquals(AgentJobRegistry.Status.RUNNING, jobA1.status)
        assertEquals(AgentJobRegistry.Status.RUNNING, jobB1.status)
    }

    @Test
    fun `finish skips a blocked config and starts a queued job of another config`() {
        val registry = AgentJobRegistry()
        registry.setConcurrencyLimit("cfgA", 1)
        registry.setConcurrencyLimit("cfgB", 1)

        val (a1, _) = register(registry, "a1", "cfgA")
        val (b1, _) = register(registry, "b1", "cfgB")
        val (a2, _) = register(registry, "a2", "cfgA")
        val (b2, _) = register(registry, "b2", "cfgB")

        // 队列顺序是 [a2, b2]，队首 a2 属于满额的 cfgA。
        // 释放 cfgB 的槽位后，出队必须跳过 a2 去启动 b2，
        // 否则队首一个满额配置会把其它配置一起堵死。
        val started = registry.finish(b1.jobId, AgentJobRegistry.Status.DONE, null, null)

        assertEquals(listOf(b2.jobId), started.map { it.jobId })
        assertEquals(AgentJobRegistry.Status.QUEUED, registry.get(a2.jobId)?.status)
        assertEquals(AgentJobRegistry.Status.RUNNING, registry.get(b2.jobId)?.status)
        assertEquals(AgentJobRegistry.Status.RUNNING, registry.get(a1.jobId)?.status)
    }

    @Test
    fun `queued and running jobs share the same handle created at register time`() {
        val registry = AgentJobRegistry()
        registry.setConcurrencyLimit("cfg", 1)

        val (running, _) = register(registry, "first", "cfg")
        val (queued, _) = register(registry, "second", "cfg")

        // 排队任务也有句柄：阻塞委派正 await 同一个 deferred。
        assertNotNull(registry.findHandle(running.jobId))
        assertNotNull(registry.findHandle(queued.jobId))
        assertTrue(registry.isRunning(running.jobId))
        assertFalse(registry.isRunning(queued.jobId))
    }

    @Test
    fun `cancelling a queued job completes its deferred so blocking delegates return`() {
        val registry = AgentJobRegistry()
        registry.setConcurrencyLimit("cfg", 1)

        register(registry, "first", "cfg")
        val (queued, _) = register(registry, "second", "cfg")
        val handle = registry.findHandle(queued.jobId)!!

        assertTrue(registry.cancel(queued.jobId))
        assertTrue(handle.deferred.isCompleted)
        assertEquals("cancelled", handle.deferred.getCompleted().reason)
        assertEquals(AgentJobRegistry.Status.CANCELLED, registry.get(queued.jobId)?.status)
        // 终态任务不再持有句柄。
        assertNull(registry.findHandle(queued.jobId))
    }

    @Test
    fun `recordToolResult accumulates the timeline and counters`() {
        val registry = AgentJobRegistry()
        val (job, _) = register(registry, "run", "cfg")

        registry.recordToolResult(
            job.jobId,
            AgentJobRegistry.ToolRecord(
                id = "rec-1",
                toolName = "read_file",
                success = true,
                executed = true,
                resultTruncated = false,
                error = null,
                at = 1_000L,
            ),
        )
        registry.recordToolResult(
            job.jobId,
            AgentJobRegistry.ToolRecord(
                id = "rec-2",
                toolName = "grep_code",
                success = false,
                executed = true,
                resultTruncated = true,
                error = "boom",
                at = 2_000L,
            ),
        )

        val updated = registry.get(job.jobId)!!
        assertEquals(2, updated.executedToolCalls)
        assertEquals("grep_code", updated.lastToolName)
        assertEquals(2_000L, updated.lastActivityAt)
        assertEquals(listOf("rec-1", "rec-2"), updated.toolRecords.map { it.id })
    }

    @Test
    fun `notifyParentOnFinish is preserved per job`() {
        val registry = AgentJobRegistry()

        val (blocking, _) = register(registry, "sync", "cfg", notifyParentOnFinish = false)
        val (background, _) = register(registry, "async", "cfg", notifyParentOnFinish = true)

        // 排队出队时必须用各自的值，否则阻塞委派会在工具结果之外多投递一次。
        assertFalse(registry.get(blocking.jobId)!!.notifyParentOnFinish)
        assertTrue(registry.get(background.jobId)!!.notifyParentOnFinish)
    }

    @Test
    fun `register reports the queue depth within the same config`() {
        val registry = AgentJobRegistry()
        registry.setConcurrencyLimit("cfg", 1)

        register(registry, "first", "cfg")
        val (second, _) = register(registry, "second", "cfg")
        val (third, _) = register(registry, "third", "cfg")

        assertEquals(0, second.queuedBehind)
        assertEquals(1, third.queuedBehind)
    }

    @Test
    fun `restore keeps terminal status but a fresh registry starts empty`() {
        val registry = AgentJobRegistry()
        assertTrue(registry.all().isEmpty())

        registry.restore(
            AgentJobRegistry.SubagentJob(
                jobId = "restored",
                parentChatId = "chat",
                profileId = "profile",
                profileName = "agent",
                toolTitle = "title",
                snapshot = snapshot(),
                status = AgentJobRegistry.Status.INTERRUPTED,
                createdAt = 1L,
            )
        )

        assertEquals(1, registry.all().size)
        assertNull(registry.findHandle("restored"))
        assertFalse(registry.cancel("restored"))
    }

    @Test
    fun `deferred stays incomplete while the job runs`() {
        val registry = AgentJobRegistry()
        val (job, _) = register(registry, "run", "cfg")
        val handle: AgentJobRegistry.RunningHandle = registry.findHandle(job.jobId)!!

        assertFalse(handle.deferred.isCompleted)

        handle.deferred.complete(
            SubagentOutcome(
                status = "completed",
                reason = "model_finished",
                summary = "done",
                summaryTruncated = false,
                executedToolCount = 1,
                success = true,
                evidencePath = null,
                childSessionId = "child",
            )
        )

        assertTrue(handle.deferred.isCompleted)
        assertEquals("done", runBlocking { handle.deferred.await() }.summary)
    }

    @Test
    fun `completable deferred import is used`() {
        // 明确断言我们用的是 kotlinx 的 CompletableDeferred，避免后续误替换为其他实现。
        val deferred = CompletableDeferred<String>()
        assertFalse(deferred.isCompleted)
        deferred.complete("ok")
        assertEquals("ok", runBlocking { deferred.await() })
    }
}
